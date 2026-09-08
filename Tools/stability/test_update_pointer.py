"""Execute publisher transaction and pointer validation without Telegram access."""
import ast
import importlib.util
import json
import unittest
from pathlib import Path
from types import SimpleNamespace
from unittest.mock import patch

ROOT = Path(__file__).resolve().parents[2]
SPEC = importlib.util.spec_from_file_location('update_pointer', ROOT / 'Tools/scripts/update_pointer.py')
pointer = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(pointer)


def metadata(version=1284):
    return dict(version='12.10.1', version_code=version, build_timestamp=10,
                can_not_skip=False, sticker=0, message=63,
                document={'arm64-v8a': 61, 'universal': 62}, url='https://t.me/NixgramX')


class Client:
    def __init__(self):
        self.events = []
        self.messages = {
            61: SimpleNamespace(id=61, empty=False, document=True),
            62: SimpleNamespace(id=62, empty=False, document=True),
            63: SimpleNamespace(id=63, empty=False, document=False),
            64: SimpleNamespace(id=64, empty=False, text='#updateBeta ' + json.dumps(metadata())),
            101: SimpleNamespace(id=101, empty=False, text=json.dumps(metadata())),
        }

    async def get_messages(self, cid, ids):
        self.events.append('read')
        def get(mid):
            return self.messages.get(mid, SimpleNamespace(id=mid, empty=True))
        return [get(mid) for mid in ids] if isinstance(ids, list) else get(ids)

    async def edit_message_text(self, cid, mid, text, **kwargs):
        self.events.append('edit')
        self.messages[mid].text = text

    async def send_message(self, cid, text, **kwargs):
        self.events.append('create')
        self.messages[102] = SimpleNamespace(id=102, empty=False, text=text)
        return self.messages[102]

    async def start(self):
        pass

    async def log_out(self):
        pass

    async def get_chat(self, cid):
        return SimpleNamespace(id=pointer.METADATA_CHANNEL)


class PointerTest(unittest.IsolatedAsyncioTestCase):
    def test_schema(self):
        self.assertEqual(pointer.parse_metadata(json.dumps(metadata()), 'beta'), metadata())
        for field in ('version', 'version_code', 'document', 'message', 'build_timestamp'):
            value = metadata()
            del value[field]
            with self.subTest(field=field), self.assertRaises(ValueError):
                pointer.parse_metadata(json.dumps(value), 'beta')
        with self.assertRaises(ValueError):
            pointer.parse_metadata('{invalid', 'beta')

    async def test_missing_reference_never_edits(self):
        client = Client()
        del client.messages[62]
        with patch.object(pointer, 'pointer_id', return_value=101), self.assertRaises(ValueError):
            await pointer.edit_pointer(client, 'beta', metadata(1285))
        self.assertNotIn('edit', client.events)

    async def test_no_downgrade(self):
        client = Client()
        with patch.object(pointer, 'pointer_id', return_value=101), self.assertRaises(ValueError):
            await pointer.edit_pointer(client, 'beta', metadata(1283))
        self.assertNotIn('edit', client.events)

    async def test_edit_after_reference_validation(self):
        client = Client()
        with patch.object(pointer, 'pointer_id', return_value=101):
            await pointer.edit_pointer(client, 'beta', metadata(1285))
        self.assertEqual(client.events, ['read', 'read', 'edit'])

    async def test_initialize_proves_edit_and_readback(self):
        client = Client()
        with patch.object(pointer, 'pointer_id', side_effect=ValueError('unconfigured')):
            mid = await pointer.initialize(client, 'beta', 64, 1284)
        self.assertEqual(mid, 102)
        self.assertEqual(client.events, ['read', 'read', 'create', 'edit', 'read'])
        self.assertEqual(json.loads(client.messages[mid].text), metadata())

    async def test_initialize_reuses_configured_pointer(self):
        client = Client()
        with patch.object(pointer, 'pointer_id', return_value=101):
            self.assertEqual(await pointer.initialize(client, 'beta', 64, 1284), 101)
        self.assertNotIn('create', client.events)

    async def test_publish_order_and_failures(self):
        # Execute the actual upload.main AST; replace only Telegram/formatting boundaries.
        tree = ast.parse((ROOT / 'Tools/scripts/upload.py').read_text(encoding='utf-8'))
        main = next(node for node in tree.body if isinstance(node, ast.AsyncFunctionDef) and node.name == 'main')
        for failure in (None, 'apk', 'metadata-apk', 'changelog', 'sticker', 'legacy'):
            with self.subTest(failure=failure):
                client = Client()
                events = []

                async def event(name, result=None):
                    events.append(name)
                    if failure == name:
                        raise ValueError(name)
                    return result

                async def upload(client, cid, with_caption):
                    return await event('apk' if with_caption else 'metadata-apk', metadata()['document'])

                async def legacy(*args, **kwargs):
                    return await event('legacy', SimpleNamespace(text='#updateBeta ' + json.dumps(metadata())))

                async def edit(*args):
                    await event('pointer')

                async def noop(*args):
                    pass

                async def changelog(*args):
                    return await event('changelog', 63)

                async def sticker(*args):
                    return await event('sticker', 0)

                ns = dict(argv=['upload.py', 'redacted-test-token', '-1003819693045'],
                          metadata_chat_id=str(pointer.METADATA_CHANNEL), beta_version=True,
                          normalize_chat_ref=lambda value: value, describe_chat_ref=lambda value: 'test',
                          DEFAULT_APK_CHAT_ID='-1003819693045', get_client=lambda token: client,
                          pointer_id=lambda lane: 101, METADATA_CHANNEL=pointer.METADATA_CHANNEL,
                          resolve_and_print_chat=noop, send_to_channel=upload,
                          same_chat=lambda a, b: a == b, send_changelog_message=changelog,
                          obtain_sticker_message_id=sticker, send_update_json=legacy,
                          edit_pointer=edit, parse_metadata=pointer.parse_metadata)
                exec(compile(ast.Module(body=[main], type_ignores=[]), 'upload.py', 'exec'), ns)
                if failure:
                    with self.assertRaises(ValueError):
                        await ns['main']()
                    self.assertNotIn('pointer', events)
                else:
                    await ns['main']()
                    self.assertEqual(events, ['apk', 'metadata-apk', 'changelog', 'sticker', 'legacy', 'pointer'])
