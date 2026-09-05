import os
import re
import json
import contextlib
from pathlib import Path
from sys import argv

from pyrogram import Client
from pyrogram.types import InputMediaDocument

api_id = os.environ.get("APP_ID")
api_hash = os.environ.get("APP_HASH")
artifacts_path = Path("artifacts")
distribution = argv[3].strip().lower() if len(argv) > 3 else "release"
if distribution not in {"release", "stable", "test", "beta", "canary"}:
    raise SystemExit(f"Unknown distribution channel: {distribution}")
beta_version = distribution in {"test", "beta", "canary"}
metadata_chat_id = argv[4] if len(argv) > 4 else None

ABIS = ["arm64-v8a", "universal"]
REPO_ROOT = Path(__file__).resolve().parents[2]
DEFAULT_UPDATE_URL = "https://t.me/NixgramX"
FALLBACK_RELEASES_URL = "https://github.com/Anndy999/NixgramX/releases"


def find_apk(abi: str) -> Path | None:
    return next((apk for apk in artifacts_path.rglob("*.apk") if abi in apk.name), None)


def _read_gradle_property(key: str) -> str | None:
    props = REPO_ROOT / "gradle.properties"
    if not props.is_file():
        return None
    for line in props.read_text(encoding="utf-8", errors="replace").splitlines():
        line = line.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue
        k, _, v = line.partition("=")
        if k.strip() == key:
            return v.strip()
    return None


def resolve_version() -> tuple[str, int]:
    """Prefer the NixgramX APK filename over Telegram upstream version properties."""
    name = (
        os.environ.get("NIXGRAMX_VERSION_NAME")
        or _read_gradle_property("NIXGRAMX_VERSION_NAME")
        or os.environ.get("APP_VERSION_NAME")
        or _read_gradle_property("APP_VERSION_NAME")
    )
    code = 0

    # NixgramX-v12.10.1-beta-a1b2c3d(1265)-arm64-v8a.apk
    for apk in artifacts_path.rglob("*.apk"):
        m = re.search(r"[Vv]?(\d+\.\d+(?:\.\d+)?(?:-[0-9A-Za-z.-]+)?)\((\d+)\)", apk.name)
        if m:
            name = m.group(1)
            code = int(m.group(2))
            break
        if not name:
            m2 = re.search(r"[Vv]?(\d+\.\d+(?:\.\d+)?)", apk.name)
            if m2:
                name = m2.group(1)

    if not code:
        code_raw = (
            os.environ.get("NIXGRAMX_VERSION_CODE")
            or _read_gradle_property("NIXGRAMX_VERSION_CODE")
            or os.environ.get("BUILD_VERSION_CODE")
            or os.environ.get("APP_VERSION_CODE")
            or _read_gradle_property("APP_VERSION_CODE")
        )
        if code_raw:
            with contextlib.suppress(ValueError):
                code = int(code_raw)

    if not name:
        name = "0.0.0"
    return name, code


def get_commit_info():
    commit_id_raw = os.environ.get("COMMIT_ID") or "unknown"
    commit_id = commit_id_raw[:7]
    commit_url = os.environ.get("COMMIT_URL") or "https://github.com/Anndy999/NixgramX/commits"
    commit_message = os.environ.get("COMMIT_MESSAGE") or "unknown"
    return commit_id, commit_url, commit_message


def get_caption() -> str:
    import html

    commit_id, commit_url, commit_message = get_commit_info()
    version, version_code = resolve_version()
    pre = "[BETA] Test version / 测试版本" if beta_version else "[STABLE] Release version / 正式版本"
    caption = f"{pre}\nVersion: {version} ({version_code})\n\n"
    caption += f"Commit Message:\n<blockquote expandable>{html.escape(commit_message)}</blockquote>\n\n"
    caption += f"See commit details [{commit_id}]({commit_url})"
    return caption


def get_documents_with_abis() -> list[tuple[str, "InputMediaDocument"]]:
    items: list[tuple[str, InputMediaDocument]] = []
    for abi in ABIS:
        if apk := find_apk(abi):
            items.append((abi, InputMediaDocument(media=str(apk))))
    if not items:
        raise FileNotFoundError("No APK artifacts found")
    base_caption = get_caption()
    if base_caption and len(base_caption) > 1024:
        base_caption = base_caption[:1020] + "..."
    items[-1][1].caption = base_caption
    return items


def get_canary_metadata() -> str:
    import html

    commit_id = "<code>" + (os.environ.get("COMMIT_ID") or "unknown")[:7] + "</code>"
    commit_message = "<code>" + html.escape(os.environ.get("COMMIT_MESSAGE") or "unknown") + "</code>"
    build_timestamp = "<code>" + (os.environ.get("BUILD_TIMESTAMP") or "-1") + "</code>"
    return build_timestamp + " " + commit_id + "\n" + commit_message


def build_update_payload(document_ids: dict[str, int]) -> str:
    version, version_code = resolve_version()
    build_timestamp_raw = os.environ.get("BUILD_TIMESTAMP") or "0"
    try:
        build_timestamp = int(build_timestamp_raw)
    except ValueError:
        build_timestamp = 0
    url = os.environ.get("UPDATE_URL") or DEFAULT_UPDATE_URL
    tag = "#updateBeta" if beta_version else "#updateRelease"
    body = {
        "can_not_skip": False,
        "version": version,
        "version_code": version_code,
        "build_timestamp": build_timestamp,
        "sticker": 0,
        "message": 0,
        "document": document_ids,
        "url": url,
    }
    # Compact JSON after the tag (UpdateHelper strips tag then parses JSON)
    return f"{tag} {json.dumps(body, separators=(',', ':'))}"


def channel_id_for_app(chat_id) -> int:
    """Derive positive CHANNEL_METADATA_ID used by BaseRemoteHelper from Bot API chat.id."""
    try:
        cid = int(chat_id)
    except (TypeError, ValueError):
        return 0
    s = str(abs(cid))
    if s.startswith("100") and len(s) > 3:
        with contextlib.suppress(ValueError):
            return int(s[3:])
    return abs(cid)


def retry(func):
    async def wrapper(*args, **kwargs):
        for attempt in range(3):
            try:
                return await func(*args, **kwargs)
            except Exception as e:
                print(e)
                if attempt == 2:
                    raise

    return wrapper


async def resolve_and_print_chat(client: "Client", cid):
    with contextlib.suppress(ValueError):
        cid = int(cid)
    chat = await client.get_chat(cid)
    app_id = channel_id_for_app(chat.id)
    print(f"Resolved chat.id={chat.id} username={getattr(chat, 'username', None)} title={getattr(chat, 'title', None)}", flush=True)
    print(f"CHANNEL_METADATA_ID candidate (BaseRemoteHelper positive id)={app_id}", flush=True)
    print(f"NOTE: set BaseRemoteHelper.CHANNEL_METADATA_ID={app_id} in a follow-up commit after confirming this publish target.", flush=True)
    return chat


@retry
async def send_to_channel(client: "Client", cid) -> dict[str, int]:
    with contextlib.suppress(ValueError):
        cid = int(cid)
    items = get_documents_with_abis()
    print("Uploading to Telegram:", flush=True)
    for abi, document in items:
        print(f"- [{abi}] {document.media}", flush=True)
    messages = await client.send_media_group(
        cid,
        media=[doc for _, doc in items],
    )
    document_ids: dict[str, int] = {}
    for (abi, _), message in zip(items, messages):
        document_ids[abi] = message.id
        print(f"APK message id abi={abi} message_id={message.id}", flush=True)
    return document_ids


@retry
async def send_update_json(client: "Client", cid, document_ids: dict[str, int]):
    with contextlib.suppress(ValueError):
        cid = int(cid)
    text = build_update_payload(document_ids)
    print(f"Posting updater metadata ({text.split(' ', 1)[0]}):", flush=True)
    print(text, flush=True)
    msg = await client.send_message(chat_id=cid, text=text)
    print(f"Updater metadata message_id={msg.id}", flush=True)
    return msg


@retry
async def send_canary_metadata(client: "Client", cid: str):
    with contextlib.suppress(ValueError):
        cid = int(cid)
    await client.send_message(
        chat_id=cid,
        text=get_canary_metadata(),
    )


def get_client(bot_token: str):
    return Client(
        "helper_bot",
        api_id=api_id,
        api_hash=api_hash,
        bot_token=bot_token,
    )


def same_chat(a, b) -> bool:
    if a is None or b is None:
        return False
    return str(a).strip().lower() == str(b).strip().lower()


async def main():
    bot_token = argv[1]
    chat_id = argv[2]
    client = get_client(bot_token)
    await client.start()
    await resolve_and_print_chat(client, chat_id)
    # Public/APK lane: upload APKs only (caption OK). Never post #update* JSON here when
    # metadata target is missing or identical — that would spam the public channel.
    document_ids = await send_to_channel(client, chat_id)
    if metadata_chat_id and not same_chat(metadata_chat_id, chat_id):
        await resolve_and_print_chat(client, metadata_chat_id)
        # Document message IDs live in the APK chat; they are not usable from a different
        # private metadata channel. Ship url-only JSON so the in-app updater opens @NixgramX.
        url_only_docs: dict[str, int] = {}
        await send_update_json(client, metadata_chat_id, url_only_docs)
        await send_canary_metadata(client, metadata_chat_id)
        print(
            "Posted #update* JSON to private metadata chat only "
            f"(APK chat={chat_id}, metadata chat={metadata_chat_id}); document map empty (url-only).",
            flush=True,
        )
    else:
        print(
            "WARNING: HELPER_BOT_CANARY_TARGET is missing or equals HELPER_BOT_TARGET "
            f"(APK chat={chat_id}, metadata={metadata_chat_id}). "
            "Skipping #updateRelease/#updateBeta JSON so the public channel does not show "
            "ugly updater metadata. Create a *private* metadata channel, set "
            "HELPER_BOT_CANARY_TARGET to it, and point BaseRemoteHelper.CHANNEL_METADATA_* at it. "
            "APKs were still uploaded with caption only.",
            flush=True,
        )
    await client.log_out()


if __name__ == "__main__":
    from asyncio import run

    run(main())
