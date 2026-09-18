"""Permanent Telegram pointers. No credentials or APK publication in bootstrap."""
import json
import os
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
METADATA_CHANNEL = -1004419000687


def pointer_id(lane):
    key = f'NIXGRAMX_UPDATE_{lane.upper()}_POINTER_ID'
    properties = dict(line.split('=', 1) for line in
                      (ROOT / 'gradle.properties').read_text().splitlines()
                      if '=' in line and not line.lstrip().startswith('#'))
    value = int(properties[key])
    if value <= 0:
        raise ValueError(f'{key} must be initialized before publishing')
    return value


def parse_metadata(text, lane):
    tag = '#updateBeta' if lane == 'beta' else '#updateRelease'
    if text.startswith(tag + ' '):
        text = text[len(tag):].strip()
    data = json.loads(text)
    if not isinstance(data, dict):
        raise ValueError('Metadata must be an object')
    if type(data.get('version_code')) is not int or data['version_code'] <= 0:
        raise ValueError('Missing version_code')
    if not isinstance(data.get('version'), str) or not data['version'].strip():
        raise ValueError('Missing version')
    if type(data.get('build_timestamp')) is not int or data['build_timestamp'] < 0:
        raise ValueError('Invalid build_timestamp')
    if type(data.get('can_not_skip')) is not bool:
        raise ValueError('Missing can_not_skip')
    for key in ('message', 'sticker'):
        if type(data.get(key)) is not int or data[key] < 0:
            raise ValueError(f'Invalid {key}')
    documents = data.get('document')
    if not isinstance(documents, dict) or not documents:
        raise ValueError('Missing APK messages')
    if not any(abi in documents for abi in ('arm64-v8a', 'universal')):
        raise ValueError('No supported APK')
    if any(type(value) is not int or value <= 0 for value in documents.values()):
        raise ValueError('Invalid APK message ID')
    if not isinstance(data.get('url'), str) or not data['url'].startswith('https://'):
        raise ValueError('Invalid fallback URL')
    return data


async def validate_references(client, data):
    ids = set(data['document'].values())
    ids.update(data[key] for key in ('message', 'sticker') if data[key] > 0)
    messages = await client.get_messages(METADATA_CHANNEL, sorted(ids))
    found = {message.id: message for message in messages if not message.empty}
    if set(found) != ids:
        raise ValueError('A referenced Telegram message is missing')
    if any(not found[mid].document for mid in data['document'].values()):
        raise ValueError('A referenced APK is not a document')


async def edit_pointer(client, lane, data):
    """Call only after legacy publication. No fallback that advances a pointer."""
    mid = pointer_id(lane)
    await validate_references(client, data)
    current = await client.get_messages(METADATA_CHANNEL, mid)
    if current.empty:
        raise ValueError('Permanent pointer missing; do not create a replacement')
    previous = parse_metadata(current.text or '', lane)
    if (data['version_code'], data['build_timestamp']) < (previous['version_code'], previous['build_timestamp']):
        raise ValueError('Refusing to roll back permanent pointer')
    text = json.dumps(data, separators=(',', ':'))
    if current.text != text:
        await client.edit_message_text(METADATA_CHANNEL, mid, text, parse_mode=None)
    print(f'V2 {lane} pointer committed: message_id={mid} version_code={data["version_code"]}')


async def initialize(client, lane, legacy_id, expected_version):
    """Copy a verified legacy post once, then prove this publisher can edit it.

    If a pointer is already configured, reuse it without overwriting its version.
    Never rerun after partial failure without inspecting the printed created ID.
    """
    try:
        existing_id = pointer_id(lane)
    except ValueError:
        existing_id = 0
    if existing_id:
        message = await client.get_messages(METADATA_CHANNEL, existing_id)
        data = parse_metadata(message.text or '', lane)
        await validate_references(client, data)
        print(f'REUSE {lane} pointer ID={existing_id}')
        return existing_id
    source = await client.get_messages(METADATA_CHANNEL, legacy_id)
    expected_tag = '#updateBeta ' if lane == 'beta' else '#updateRelease '
    if source.empty or not (source.text or '').startswith(expected_tag):
        raise ValueError('Wrong legacy source/lane')
    data = parse_metadata(source.text, lane)
    if data['version_code'] != expected_version:
        raise ValueError('Legacy source version differs from reviewed baseline')
    await validate_references(client, data)
    text = json.dumps(data, separators=(',', ':'))
    # Whitespace-only initial variant is valid JSON but guarantees an actual edit.
    message = await client.send_message(METADATA_CHANNEL, text.replace(':', ': ', 1), parse_mode=None)
    print(f'CREATED {lane} pointer ID={message.id}; retain this ID if verification fails', flush=True)
    await client.edit_message_text(METADATA_CHANNEL, message.id, text, parse_mode=None)
    verified = await client.get_messages(METADATA_CHANNEL, message.id)
    if verified.text != text:
        raise ValueError('Pointer edit/read-back verification failed')
    print(f'VERIFIED {lane} permanent pointer ID={message.id}', flush=True)
    return message.id


async def main():
    from pyrogram import Client
    async with Client('pointer_initializer', in_memory=True,
                      api_id=os.environ['APP_ID'], api_hash=os.environ['APP_HASH'],
                      bot_token=os.environ['HELPER_BOT_TOKEN']) as client:
        for lane in ('beta', 'release'):
            await initialize(client, lane, int(os.environ[f'{lane.upper()}_LEGACY_ID']),
                             int(os.environ[f'{lane.upper()}_EXPECTED_VERSION']))


if __name__ == '__main__':
    import asyncio
    asyncio.run(main())
