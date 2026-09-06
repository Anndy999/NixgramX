import os
import re
import json
import contextlib
from pathlib import Path
from sys import argv

from pyrogram import Client
from pyrogram.enums import ParseMode
from pyrogram.errors import MessageNotModified
from pyrogram.types import InputMediaDocument

api_id = os.environ.get("APP_ID")
api_hash = os.environ.get("APP_HASH")
artifacts_path = Path("artifacts")
distribution = argv[3].strip().lower() if len(argv) > 3 else "release"
if distribution not in {"release", "stable", "test", "beta", "canary"}:
    raise SystemExit(f"Unknown distribution channel: {distribution}")
beta_version = distribution in {"test", "beta", "canary"}


# Known public APK channel Bot API id (from prior successful uploads).
DEFAULT_APK_CHAT_ID = "-1003819693045"


def normalize_chat_ref(value):
    """Accept @Name, Name, t.me/Name, or numeric -100… / positive ids."""
    if value is None:
        return None
    s = str(value).strip().strip('"').strip("'")
    if not s:
        return s
    # URLs / t.me links
    for prefix in ("https://t.me/", "http://t.me/", "tg://resolve?domain="):
        if s.lower().startswith(prefix):
            s = s[len(prefix):]
            break
    if s.lower().startswith("t.me/"):
        s = s[5:]
    s = s.strip().strip("/")
    # Numeric chat id (Bot API / MTProto)
    if s.lstrip("-").isdigit():
        return s
    # Positive public channel id used by BaseRemoteHelper → Bot API form
    if s.isdigit():
        return s
    if not s.startswith("@"):
        s = "@" + s
    return s


def describe_chat_ref(value) -> str:
    """Safe debug descriptor (no secret leak beyond shape)."""
    if value is None:
        return "None"
    s = str(value)
    if s.lstrip("-").isdigit():
        return f"numeric(len={len(s)})"
    if s.startswith("@"):
        return f"username(@…{s[-4:]},len={len(s)})"
    return f"other(len={len(s)})"



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


def get_user_release_notes() -> str:
    """User-facing changelog for the public APK caption only.

    Prefer RELEASE_NOTES env (multiline, each line typically "- …"),
    else docs/RELEASE_NOTES.txt. Never fall back to COMMIT_MESSAGE.
    """
    notes = (os.environ.get("RELEASE_NOTES") or "").strip()
    if notes:
        return notes
    notes_path = Path("docs/RELEASE_NOTES.txt")
    if notes_path.is_file():
        file_notes = notes_path.read_text(encoding="utf-8").strip()
        if file_notes:
            return file_notes
    return ""


def get_caption() -> str:
    version, version_code = resolve_version()
    title = "NixgramX Beta" if beta_version else "NixgramX"
    # The Beta label already identifies the track; keep build hashes off the caption.
    line1 = f"{title} · {version.split('-', 1)[0]} ({version_code})"
    notes = get_user_release_notes()
    if notes:
        return f"{line1}\n\n{notes}"
    return line1


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


def build_update_payload(document_ids: dict[str, int], sticker_message_id: int = 0) -> str:
    version, version_code = resolve_version()
    build_timestamp_raw = os.environ.get("BUILD_TIMESTAMP") or "0"
    try:
        build_timestamp = int(build_timestamp_raw)
    except ValueError:
        build_timestamp = 0
    # Never treat 0 as a real sticker message id (UpdateHelper skips fetch for 0).
    sticker_id = int(sticker_message_id) if sticker_message_id and int(sticker_message_id) > 0 else 0
    url = os.environ.get("UPDATE_URL") or DEFAULT_UPDATE_URL
    tag = "#updateBeta" if beta_version else "#updateRelease"
    body = {
        "can_not_skip": False,
        "version": version,
        "version_code": version_code,
        "build_timestamp": build_timestamp,
        "sticker": sticker_id,
        "message": 0,
        "document": document_ids,
        "url": url,
    }
    # Compact JSON after the tag (UpdateHelper strips tag then parses JSON)
    return f"{tag} {json.dumps(body, separators=(',', ':'))}"


def find_repo_update_duck_asset() -> Path | None:
    """Locate an existing update-duck sticker asset (.tgs/.webm/.webp) in the repo.

    Does not invent new assets; only matches update/duck-oriented names outside
    ordinary Android drawable density folders.
    """
    skip_parts = {".git", "jni", "build", ".gradle", "node_modules", "drawable",
                  "drawable-hdpi", "drawable-mdpi", "drawable-xhdpi", "drawable-xxhdpi",
                  "drawable-xxxhdpi", "drawable-night-hdpi", "drawable-night-mdpi",
                  "drawable-night-xhdpi", "drawable-night-xxhdpi", "drawable-night-xxxhdpi",
                  "mipmap-hdpi", "mipmap-mdpi", "mipmap-xhdpi", "mipmap-xxhdpi", "mipmap-xxxhdpi"}
    exts = {".tgs", ".webm", ".webp"}
    preferred: list[Path] = []
    for p in REPO_ROOT.rglob("*"):
        if not p.is_file():
            continue
        if any(part in skip_parts for part in p.parts):
            continue
        if p.suffix.lower() not in exts:
            continue
        name = p.name.lower()
        if ("update" in name and "duck" in name) or name.startswith("update_duck") or name.startswith("updateduck"):
            preferred.append(p)
        elif "duck" in name and ("update" in name or "sticker" in name):
            preferred.append(p)
    if not preferred:
        # Narrow Tools/ + top-level assets folders for any *duck*.{tgs,webm,webp}
        for base in (REPO_ROOT / "Tools", REPO_ROOT / "tools", REPO_ROOT / "assets", REPO_ROOT / "stickers"):
            if not base.is_dir():
                continue
            for p in base.rglob("*"):
                if p.is_file() and p.suffix.lower() in exts and "duck" in p.name.lower():
                    preferred.append(p)
    preferred.sort(key=lambda x: (0 if "update" in x.name.lower() else 1, str(x)))
    return preferred[0] if preferred else None


def parse_positive_message_id(raw: str | None) -> int | None:
    if raw is None:
        return None
    s = str(raw).strip().strip('"').strip("'")
    if not s or not s.isdigit():
        return None
    value = int(s)
    return value if value > 0 else None



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


@retry
async def send_sticker_file(client: "Client", cid, path: Path) -> int:
    """Upload a sticker-like file to the metadata channel; return message id."""
    with contextlib.suppress(ValueError):
        cid = int(cid)
    path = Path(path)
    suffix = path.suffix.lower()
    print(f"Uploading update duck sticker to metadata chat: {path}", flush=True)
    if suffix == ".webm":
        # Video stickers: send as sticker when possible; fall back to document.
        try:
            msg = await client.send_sticker(chat_id=cid, sticker=str(path))
        except Exception as e:
            print(f"send_sticker failed for .webm ({e}); trying send_document", flush=True)
            msg = await client.send_document(chat_id=cid, document=str(path))
    elif suffix in {".tgs", ".webp"}:
        msg = await client.send_sticker(chat_id=cid, sticker=str(path))
    else:
        raise ValueError(f"Unsupported sticker asset type: {path}")
    print(f"Update duck sticker message_id={msg.id}", flush=True)
    return msg.id


async def obtain_sticker_message_id(client: "Client", metadata_cid) -> int:
    """Resolve sticker message id for private #update* JSON only.

    Priority:
      a) UPDATE_STICKER_MESSAGE_ID env (existing message in metadata channel)
      b) UPDATE_STICKER_PATH env pointing at .tgs/.webm/.webp — upload to metadata
      c) existing update-duck asset in repo — upload to metadata if found
    Returns 0 when none available (never a valid fetch id).
    """
    env_id = parse_positive_message_id(os.environ.get("UPDATE_STICKER_MESSAGE_ID"))
    if env_id is not None:
        print(f"Using UPDATE_STICKER_MESSAGE_ID={env_id}", flush=True)
        return env_id

    env_path_raw = (os.environ.get("UPDATE_STICKER_PATH") or "").strip().strip('"').strip("'")
    if env_path_raw:
        env_path = Path(env_path_raw)
        if not env_path.is_file():
            env_path = (REPO_ROOT / env_path_raw).resolve()
        if env_path.is_file() and env_path.suffix.lower() in {".tgs", ".webm", ".webp"}:
            return await send_sticker_file(client, metadata_cid, env_path)
        print(
            f"WARN: UPDATE_STICKER_PATH set but not a usable .tgs/.webm/.webp file: {env_path_raw}",
            flush=True,
        )

    asset = find_repo_update_duck_asset()
    if asset is not None:
        print(f"Found repo update-duck asset: {asset}", flush=True)
        return await send_sticker_file(client, metadata_cid, asset)

    print(
        "No UPDATE_STICKER_MESSAGE_ID / UPDATE_STICKER_PATH / repo update-duck asset; "
        "JSON sticker=0 (app local RLottie fallback).",
        flush=True,
    )
    return 0

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


def metadata_message_id() -> int:
    value = parse_positive_message_id(os.environ.get("UPDATE_METADATA_MESSAGE_ID"))
    if value is None or value > 2147483647:
        raise ValueError(
            "Set UPDATE_METADATA_MESSAGE_ID to the existing track's #update* post "
            "in @NixgramX. No APK or new metadata post was sent."
        )
    return value


async def read_update_metadata(client: "Client", cid: int, message_id: int):
    message = await client.get_messages(cid, message_id, replies=0)
    tag = "#updateBeta" if beta_version else "#updateRelease"
    if (not message or getattr(message, "empty", False)
            or message.id != message_id or message.chat.id != cid
            or not message.text or not message.text.startswith(tag + " ")):
        raise ValueError("Metadata ID must identify this track's existing text post in @NixgramX.")
    try:
        body = json.loads(message.text[len(tag):].strip())
    except (ValueError, TypeError) as e:
        raise ValueError("Existing updater metadata is not valid JSON.") from e
    if (not isinstance(body, dict)
            or type(body.get("version_code")) is not int
            or body["version_code"] <= 0
            or not isinstance(body.get("version"), str)):
        raise ValueError("Existing updater metadata has no valid version identity.")
    return message, body


@retry
async def send_update_json(client: "Client", cid: int, document_ids: dict[str, int],
                           message_id: int, expected_text: str, sticker_message_id: int = 0):
    """Edit the existing public metadata post; never append a notification."""
    text = build_update_payload(document_ids, sticker_message_id=sticker_message_id)
    current, _ = await read_update_metadata(client, cid, message_id)
    if current.text == text:
        return current  # An edit may have succeeded before a transport error.
    if current.text != expected_text:
        raise RuntimeError("Metadata changed during APK upload; refusing to overwrite another publication.")
    try:
        await client.edit_message_text(
            chat_id=cid, message_id=message_id, text=text,
            parse_mode=ParseMode.DISABLED, disable_web_page_preview=True,
        )
    except MessageNotModified:
        pass  # Read back below; never treat a no-op response alone as success.
    saved, _ = await read_update_metadata(client, cid, message_id)
    if saved.text != text:
        raise RuntimeError("Updater metadata read-back does not match this publication.")
    print(f"Verified updater metadata edit: message_id={message_id}", flush=True)
    return saved


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
    # Validate configuration before authenticating or publishing any media.
    message_id = metadata_message_id()
    bot_token = argv[1]
    chat_id = normalize_chat_ref(argv[2]) or DEFAULT_APK_CHAT_ID
    if chat_id.lower() == "@nixgramx" or chat_id == "3819693045":
        chat_id = DEFAULT_APK_CHAT_ID
    if chat_id != DEFAULT_APK_CHAT_ID:
        raise ValueError("Publish target must match the app's existing @NixgramX update channel.")
    chat_id = int(chat_id)
    if len(argv) > 4 and argv[4].strip():
        print("Ignoring obsolete private metadata target; only @NixgramX is used.", flush=True)
    _, version_code = resolve_version()
    if version_code <= 0:
        raise ValueError("A positive NixgramX version code is required before publication.")
    client = get_client(bot_token)
    await client.start()
    try:
        await resolve_and_print_chat(client, chat_id)
        previous, body = await read_update_metadata(client, chat_id, message_id)
        if version_code <= body["version_code"]:
            raise ValueError("Refusing to publish a reused or older version code for this track.")
        # Reuse an existing same-channel sticker, or the app's local duck fallback.
        # Never send a separate sticker/description/hash notification.
        sticker_id = body.get("sticker", 0)
        if type(sticker_id) is not int or sticker_id < 0:
            raise ValueError("Existing metadata has an invalid sticker message ID.")
        document_ids = await send_to_channel(client, chat_id)
        await send_update_json(
            client, chat_id, document_ids, message_id, previous.text, sticker_id,
        )
    finally:
        await client.log_out()


if __name__ == "__main__":
    from asyncio import run

    run(main())
