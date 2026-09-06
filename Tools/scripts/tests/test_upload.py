"""Offline publisher regression tests: no credentials, Telegram calls, or APK build.

Only the Pyrogram import boundary is stubbed; production publication functions run
unchanged against an in-memory client with the documented Pyrogram method names.
"""
import copy
import importlib.util
import json
import os
from pathlib import Path
import sys
import types
import unittest
from unittest.mock import AsyncMock, Mock, patch


class InputMediaDocument:
    def __init__(self, media):
        self.media = media
        self.caption = None


class MessageNotModified(Exception):
    pass


modules = {
    "pyrogram": types.SimpleNamespace(Client=Mock()),
    "pyrogram.types": types.SimpleNamespace(InputMediaDocument=InputMediaDocument),
    "pyrogram.enums": types.SimpleNamespace(ParseMode=types.SimpleNamespace(DISABLED="disabled")),
    "pyrogram.errors": types.SimpleNamespace(MessageNotModified=MessageNotModified),
}
spec = importlib.util.spec_from_file_location("nixgramx_upload", Path(__file__).parents[1] / "upload.py")
upload = importlib.util.module_from_spec(spec)
with patch.dict(sys.modules, modules), patch.object(sys, "argv", ["upload.py"]):
    spec.loader.exec_module(upload)


CHAT = -1003819693045
MESSAGE_ID = 7


def metadata(version=1269, beta=False, sticker=0):
    tag = "#updateBeta" if beta else "#updateRelease"
    return tag + " " + json.dumps({
        "version": "12.10.1", "version_code": version, "sticker": sticker,
    })


def message(text=None, message_id=MESSAGE_ID, chat_id=CHAT, empty=False):
    return types.SimpleNamespace(
        id=message_id, chat=types.SimpleNamespace(id=chat_id),
        text=text, empty=empty,
    )


class FakeClient:
    def __init__(self, text=None):
        self.saved = message(metadata() if text is None else text)
        self.start = AsyncMock()
        self.log_out = AsyncMock()
        self.get_chat = AsyncMock(return_value=types.SimpleNamespace(id=CHAT))
        self.get_messages = AsyncMock(side_effect=self.read)
        self.send_media_group = AsyncMock(return_value=[message(message_id=101)])
        self.edit_message_text = AsyncMock(side_effect=self.edit)
        self.send_message = AsyncMock()
        self.send_sticker = AsyncMock()

    async def read(self, chat_id, message_ids, replies=0):
        return copy.deepcopy(self.saved)

    async def edit(self, *, chat_id, message_id, text, parse_mode, disable_web_page_preview):
        self.saved = message(text, message_id, chat_id)
        return copy.deepcopy(self.saved)


class PublisherTests(unittest.IsolatedAsyncioTestCase):
    def setUp(self):
        self.client = FakeClient()
        patches = [
            patch.dict(os.environ, {"UPDATE_METADATA_MESSAGE_ID": str(MESSAGE_ID), "BUILD_TIMESTAMP": "1234"}, clear=True),
            patch.object(upload, "argv", ["upload.py", "test-token-never-used", "@NixgramX", "stable"]),
            patch.object(upload, "beta_version", False),
            patch.object(upload, "resolve_version", return_value=("12.10.1", 1270)),
            patch.object(upload, "get_client", return_value=self.client),
            patch.object(upload, "get_documents_with_abis", return_value=[("arm64-v8a", InputMediaDocument("fixture.apk"))]),
        ]
        for p in patches:
            p.start()
            self.addCleanup(p.stop)

    def assert_no_publication(self):
        self.client.send_media_group.assert_not_awaited()
        self.client.edit_message_text.assert_not_awaited()
        self.client.send_message.assert_not_awaited()
        self.client.send_sticker.assert_not_awaited()

    async def test_missing_or_invalid_id_fails_before_login(self):
        for value in ("", "0", "-1", "abc", "1.5", "2147483648"):
            with self.subTest(value=value), patch.dict(os.environ, {"UPDATE_METADATA_MESSAGE_ID": value}):
                with self.assertRaises(ValueError):
                    await upload.main()
        upload.get_client.assert_not_called()
        self.assert_no_publication()

    async def test_wrong_publish_target_fails_before_login(self):
        for target in ("@another_channel", "-1001111111111"):
            with self.subTest(target=target), patch.object(upload, "argv", ["upload.py", "unused", target]):
                with self.assertRaises(ValueError):
                    await upload.main()
        upload.get_client.assert_not_called()

    async def test_missing_wrong_track_or_wrong_chat_never_uploads(self):
        cases = [
            message(empty=True), message(metadata(), message_id=99),
            message(metadata(), chat_id=-1001111111111), message(metadata(beta=True)),
            message("NixgramX · 12.10.1 (1269)"), message("#updateReleaseExtra {}"),
            message("#updateRelease not-json"), message("#updateRelease []"),
        ]
        for candidate in cases:
            with self.subTest(candidate=candidate.text):
                self.client.saved = candidate
                with self.assertRaises(ValueError):
                    await upload.main()
                self.assert_no_publication()

    async def test_invalid_remote_version_identity_never_uploads(self):
        for value in (True, 0, -1, 1269.5, "1269", None):
            with self.subTest(value=value):
                self.client.saved = message("#updateRelease " + json.dumps({"version": "12.10.1", "version_code": value}))
                with self.assertRaises(ValueError):
                    await upload.main()
                self.assert_no_publication()

    async def test_same_or_older_version_never_uploads(self):
        for code in (1270, 1271):
            with self.subTest(code=code):
                self.client.saved = message(metadata(code))
                with self.assertRaises(ValueError):
                    await upload.main()
                self.assert_no_publication()

    async def test_invalid_new_version_fails_before_login(self):
        with patch.object(upload, "resolve_version", return_value=("12.10.1", 0)):
            with self.assertRaises(ValueError):
                await upload.main()
        upload.get_client.assert_not_called()

    async def test_success_edits_same_post_with_actual_apk_ids(self):
        await upload.main()
        self.client.send_media_group.assert_awaited_once()
        self.client.edit_message_text.assert_awaited_once()
        args = self.client.edit_message_text.call_args.kwargs
        self.assertEqual((args["chat_id"], args["message_id"]), (CHAT, MESSAGE_ID))
        self.assertEqual(args["parse_mode"], "disabled")
        self.assertTrue(args["disable_web_page_preview"])
        body = json.loads(self.client.saved.text.removeprefix("#updateRelease "))
        self.assertEqual(body["version_code"], 1270)
        self.assertEqual(body["build_timestamp"], 1234)
        self.assertEqual(body["document"], {"arm64-v8a": 101})
        self.assertEqual(body["sticker"], 0)
        self.assertEqual(body["url"], "https://t.me/NixgramX")
        self.client.send_message.assert_not_awaited()
        self.client.send_sticker.assert_not_awaited()
        self.client.log_out.assert_awaited_once()

    async def test_beta_updates_only_its_own_tag(self):
        self.client.saved = message(metadata(beta=True))
        with patch.object(upload, "beta_version", True):
            await upload.main()
        self.assertTrue(self.client.saved.text.startswith("#updateBeta "))

    async def test_existing_sticker_is_reused_without_new_notification(self):
        self.client.saved = message(metadata(sticker=11))
        await upload.main()
        self.assertEqual(json.loads(self.client.saved.text.split(" ", 1)[1])["sticker"], 11)
        self.client.send_sticker.assert_not_awaited()

    async def test_invalid_sticker_fails_before_upload(self):
        for value in (-1, "11", True):
            self.client.saved = message(metadata(sticker=value))
            with self.assertRaises(ValueError):
                await upload.main()
            self.assert_no_publication()

    async def test_private_target_is_not_contacted(self):
        with patch.object(upload, "argv", ["upload.py", "unused", "@NixgramX", "stable", "-1009999999999"]):
            await upload.main()
        self.client.get_chat.assert_awaited_once_with(CHAT)
        for call in self.client.get_messages.call_args_list:
            self.assertEqual(call.args[0], CHAT)
        self.client.send_message.assert_not_awaited()

    async def test_lost_edit_response_does_not_duplicate_apk_or_edit(self):
        async def edit_then_disconnect(**kwargs):
            await self.client.edit(**kwargs)
            raise OSError("simulated lost response")
        self.client.edit_message_text.side_effect = edit_then_disconnect
        await upload.main()
        self.client.send_media_group.assert_awaited_once()
        self.client.edit_message_text.assert_awaited_once()
        self.assertIn('"version_code":1270', self.client.saved.text)

    async def test_changed_metadata_is_not_overwritten(self):
        async def upload_and_change(*args, **kwargs):
            self.client.saved = message(metadata(version=1271))
            return [message(message_id=101)]
        self.client.send_media_group.side_effect = upload_and_change
        with self.assertRaisesRegex(RuntimeError, "changed during APK upload"):
            await upload.main()
        self.client.send_media_group.assert_awaited_once()
        self.client.edit_message_text.assert_not_awaited()
        self.client.log_out.assert_awaited_once()

    async def test_failed_readback_is_not_reported_as_success(self):
        self.client.edit_message_text.side_effect = None
        with self.assertRaisesRegex(RuntimeError, "read-back"):
            await upload.main()
        self.client.send_media_group.assert_awaited_once()
        self.client.log_out.assert_awaited_once()

    async def test_noop_response_still_requires_matching_readback(self):
        self.client.edit_message_text.side_effect = MessageNotModified()
        with self.assertRaisesRegex(RuntimeError, "read-back"):
            await upload.main()

    async def test_upload_failure_leaves_old_metadata(self):
        original = self.client.saved.text
        self.client.send_media_group.side_effect = OSError("simulated upload failure")
        with self.assertRaises(OSError):
            await upload.main()
        self.assertEqual(self.client.saved.text, original)
        self.client.edit_message_text.assert_not_awaited()


class CaptionAndProtocolTests(unittest.TestCase):
    def test_caption_has_only_version_and_user_notes(self):
        with patch.object(upload, "resolve_version", return_value=("12.10.1-beta-abcdef0", 1270)), \
                patch.object(upload, "beta_version", True), \
                patch.object(upload, "get_user_release_notes", return_value="修复更新检测"):
            self.assertEqual(upload.get_caption(), "NixgramX Beta · 12.10.1 (1270)\n\n修复更新检测")

    def test_empty_notes_do_not_fall_back_to_commit_message(self):
        with patch.dict(os.environ, {"COMMIT_MESSAGE": "internal commit hash/noise", "RELEASE_NOTES": ""}, clear=True), \
                patch.object(upload.Path, "is_file", return_value=False):
            self.assertEqual(upload.get_user_release_notes(), "")

    def test_stable_caption_has_no_beta_or_metadata_label(self):
        with patch.object(upload, "resolve_version", return_value=("12.10.1", 1270)), \
                patch.object(upload, "beta_version", False), \
                patch.object(upload, "get_user_release_notes", return_value=""):
            self.assertEqual(upload.get_caption(), "NixgramX · 12.10.1 (1270)")

    def test_apk_version_is_preferred_over_upstream_properties(self):
        with patch.dict(os.environ, {}, clear=True), \
                patch.object(upload.Path, "rglob", return_value=iter([Path("NixgramX-v12.10.1(1270)-arm64-v8a.apk")])), \
                patch.object(upload, "_read_gradle_property", side_effect={"NIXGRAMX_VERSION_NAME": "12.10.1"}.get):
            self.assertEqual(upload.resolve_version(), ("12.10.1", 1270))

    def test_public_channel_and_json_contract_match_installed_client(self):
        source = (upload.REPO_ROOT / "TMessagesProj/src/main/java/tw/nekomimi/nekogram/helpers/remote/BaseRemoteHelper.java").read_text(encoding="utf-8")
        self.assertIn('CHANNEL_METADATA_NAME = "NixgramX"', source)
        self.assertIn("CHANNEL_METADATA_ID = 3819693045L", source)
        self.assertIn("message.message.startsWith(tag)", source)
        self.assertIn("new JSONObject(message.message.substring(tag.length()).trim())", source)
        with patch.object(upload, "resolve_version", return_value=("12.10.1", 1270)), \
                patch.object(upload, "beta_version", False):
            payload = upload.build_update_payload({"arm64-v8a": 101})
        self.assertTrue(payload.startswith("#updateRelease"))
        body = json.loads(payload[len("#updateRelease"):].strip())
        self.assertEqual(body["document"], {"arm64-v8a": 101})
        self.assertEqual(set(body), {"version", "version_code", "build_timestamp", "can_not_skip", "sticker", "message", "document", "url"})


if __name__ == "__main__":
    unittest.main()
