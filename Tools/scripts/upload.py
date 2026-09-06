import html
import os
import re
import json
import contextlib
from pathlib import Path
from sys import argv

from pyrogram import Client
from pyrogram.enums import ParseMode
from pyrogram.types import InputMediaDocument

api_id = os.environ.get("APP_ID")
api_hash = os.environ.get("APP_HASH")
artifacts_path = Path("artifacts")
distribution = argv[3].strip().lower() if len(argv) > 3 else "release"
if distribution not in {"release", "stable", "test", "beta", "canary"}:
    raise SystemExit(f"Unknown distribution channel: {distribution}")
beta_version = distribution in {"test", "beta", "canary"}
metadata_chat_id = argv[4] if len(argv) > 4 else None


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
DEFAULT_COMMITS_URL = "https://github.com/Anndy999/NixgramX/commits"


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
    commit_url = os.environ.get("COMMIT_URL") or DEFAULT_COMMITS_URL
    commit_message = os.environ.get("COMMIT_MESSAGE") or "unknown"
    return commit_id, commit_url, commit_message


def normalize_message(text: str) -> str:
    return (text or "").replace("\\n", "\n")


def get_log_text() -> str:
    """User-facing 日志 body for public caption / metadata changelog message.

    Prefer RELEASE_NOTES env (multiline, each line typically "- …"),
    else docs/RELEASE_NOTES.txt, else COMMIT_MESSAGE (NagramX-style fallback).
    Never use hash-only spam lines as the sole caption.
    """
    notes = (os.environ.get("RELEASE_NOTES") or "").strip()
    if notes:
        return normalize_message(notes)
    notes_path = Path("docs/RELEASE_NOTES.txt")
    if notes_path.is_file():
        file_notes = notes_path.read_text(encoding="utf-8").strip()
        if file_notes:
            return normalize_message(file_notes)
    _, _, commit_message = get_commit_info()
    return normalize_message(commit_message) or "Bug fixes and improvements."


# Back-compat alias used by older comments / callers
get_user_release_notes = get_log_text


def build_changelog_blockquote(max_length: int) -> str:
    """Escape 日志 text to fit inside an HTML <blockquote> budget (NagramXTurbo-style)."""
    text = html.escape(get_log_text(), quote=False)
    lines = [line for line in text.splitlines() if line.strip()]
    if not lines:
        return html.escape("Bug fixes and improvements.", quote=False)
    kept: list[str] = []
    used = 0
    for line in reversed(lines):
        if used + len(line) + 1 > max_length:
            break
        kept.append(line)
        used += len(line) + 1
    kept.reverse()
    dropped = len(lines) - len(kept)
    if dropped > 0:
        kept.insert(0, html.escape("… and %d earlier changes" % dropped, quote=False))
    return "\n".join(kept) if kept else lines[0][:max_length]


def build_full_changelog_tail() -> str:
    """Optional Full Changelog compare link when PREV_COMMIT_ID / FULL_CHANGELOG_URL exists."""
    full_url = (os.environ.get("FULL_CHANGELOG_URL") or "").strip()
    prev_raw = (os.environ.get("PREV_COMMIT_ID") or "").strip()
    commit_id, _, _ = get_commit_info()
    if not full_url and prev_raw and commit_id and commit_id != "unknown":
        prev = prev_raw[:7]
        full_url = (
            "https://github.com/Anndy999/NixgramX/compare/"
            + prev_raw
            + "..."
            + (os.environ.get("COMMIT_ID") or commit_id)
        )
        label = f"{prev}...{commit_id}"
    elif full_url:
        label = full_url.rsplit("/", 1)[-1] or "changelog"
    else:
        return ""
    return (
        "\n\nFull Changelog:\n"
        '<a href="' + html.escape(full_url, quote=False) + '">'
        + html.escape(label, quote=False)
        + "</a>"
    )


def get_caption() -> str:
    """NagramX CI-style public APK caption: version + Commit Message blockquote + commit links.

    Terminology: this block is 「日志」 (not 「人话说明」).
    """
    version, version_code = resolve_version()
    commit_id, commit_url, _ = get_commit_info()
    title = "NixgramX Beta" if beta_version else "NixgramX"
    # Keep product line, then NagramX-style Commit Message block.
    pre = f"{title} · {version} ({version_code})"
    if beta_version:
        pre = f"Dev version. {pre}"
    else:
        pre = f"Release version. {pre}"

    caption = html.escape(pre) + "\n\n"
    caption += "Commit Message:\n"
    # Reserve room for closing tags + See commit details + optional Full Changelog.
    see = (
        'See commit details <a href="'
        + html.escape(commit_url, quote=False)
        + '">'
        + html.escape(commit_id)
        + "</a>"
    )
    full = build_full_changelog_tail()
    open_tag = "<blockquote expandable>"
    close_tag = "</blockquote>\n\n"
    budget = 1024 - len(caption) - len(open_tag) - len(close_tag) - len(see) - len(full) - 8
    if budget < 32:
        budget = 32
    body = build_changelog_blockquote(budget)
    caption += open_tag + body + close_tag + see + full
    if len(caption) > 1024:
        caption = caption[:1020] + "..."
    return caption


def get_changelog_message_html() -> str:
    """Standalone 「日志」 HTML for metadata channel (fetched by UpdateHelper → UpdateAppAlertDialog)."""
    commit_id, commit_url, _ = get_commit_info()
    version, version_code = resolve_version()
    title = "NixgramX Beta" if beta_version else "NixgramX"
    head = html.escape(f"{title} · {version} ({version_code})") + "\n\n"
    head += "Commit Message:\n"
    see = (
        'See commit details <a href="'
        + html.escape(commit_url, quote=False)
        + '">'
        + html.escape(commit_id)
        + "</a>"
    )
    full = build_full_changelog_tail()
    open_tag = "<blockquote expandable>"
    close_tag = "</blockquote>\n\n"
    budget = 4096 - len(head) - len(open_tag) - len(close_tag) - len(see) - len(full) - 8
    if budget < 64:
        budget = 64
    body = build_changelog_blockquote(budget)
    return head + open_tag + body + close_tag + see + full


def get_documents_with_abis(*, with_caption: bool = True) -> list[tuple[str, "InputMediaDocument"]]:
    items: list[tuple[str, InputMediaDocument]] = []
    for abi in ABIS:
        if apk := find_apk(abi):
            items.append((abi, InputMediaDocument(media=str(apk))))
    if not items:
        raise FileNotFoundError("No APK artifacts found")
    if with_caption:
        base_caption = get_caption()
        items[-1][1].caption = base_caption
        items[-1][1].parse_mode = ParseMode.HTML
    return items


def get_canary_metadata() -> str:
    commit_id = "<code>" + html.escape((os.environ.get("COMMIT_ID") or "unknown")[:7]) + "</code>"
    commit_message = "<code>" + html.escape(os.environ.get("COMMIT_MESSAGE") or "unknown") + "</code>"
    build_timestamp = "<code>" + html.escape(os.environ.get("BUILD_TIMESTAMP") or "-1") + "</code>"
    return build_timestamp + " " + commit_id + "\n" + commit_message


def build_update_payload(
    document_ids: dict[str, int],
    sticker_message_id: int = 0,
    changelog_message_id: int = 0,
) -> str:
    version, version_code = resolve_version()
    build_timestamp_raw = os.environ.get("BUILD_TIMESTAMP") or "0"
    try:
        build_timestamp = int(build_timestamp_raw)
    except ValueError:
        build_timestamp = 0
    # Never treat 0 as a real sticker/message id (UpdateHelper skips fetch for 0).
    sticker_id = int(sticker_message_id) if sticker_message_id and int(sticker_message_id) > 0 else 0
    message_id = int(changelog_message_id) if changelog_message_id and int(changelog_message_id) > 0 else 0
    url = os.environ.get("UPDATE_URL") or DEFAULT_UPDATE_URL
    tag = "#updateBeta" if beta_version else "#updateRelease"
    # document must point at APK message ids in the *metadata* chat so FileLoader can download in-app.
    # message points at the 「日志」 text message so UpdateAppAlertDialog can show changelog via getMessages.
    # url remains https://t.me/NixgramX as fallback when document fetch fails.
    body = {
        "can_not_skip": False,
        "version": version,
        "version_code": version_code,
        "build_timestamp": build_timestamp,
        "sticker": sticker_id,
        "message": message_id,
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
    """Resolve sticker message id for metadata-channel #update* JSON only.

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
async def send_to_channel(client: "Client", cid, *, with_caption: bool = True) -> dict[str, int]:
    with contextlib.suppress(ValueError):
        cid = int(cid)
    items = get_documents_with_abis(with_caption=with_caption)
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
async def send_changelog_message(client: "Client", cid) -> int:
    """Post 「日志」 text on metadata channel; return message id for JSON `message` field."""
    with contextlib.suppress(ValueError):
        cid = int(cid)
    text = get_changelog_message_html()
    print("Posting 「日志」 changelog message to metadata chat:", flush=True)
    print(text[:500] + ("…" if len(text) > 500 else ""), flush=True)
    msg = await client.send_message(chat_id=cid, text=text, parse_mode=ParseMode.HTML)
    print(f"日志 message_id={msg.id}", flush=True)
    return msg.id


@retry
async def send_update_json(
    client: "Client",
    cid,
    document_ids: dict[str, int],
    sticker_message_id: int = 0,
    changelog_message_id: int = 0,
):
    with contextlib.suppress(ValueError):
        cid = int(cid)
    text = build_update_payload(
        document_ids,
        sticker_message_id=sticker_message_id,
        changelog_message_id=changelog_message_id,
    )
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
    chat_id = normalize_chat_ref(argv[2]) or DEFAULT_APK_CHAT_ID
    global metadata_chat_id
    metadata_chat_id = normalize_chat_ref(metadata_chat_id)
    # Prefer numeric id: username resolve has been flaky for the helper bot.
    if chat_id and not str(chat_id).lstrip("-").isdigit():
        print(
            f"WARN: chat ref is {describe_chat_ref(chat_id)}; "
            f"falling back to DEFAULT_APK_CHAT_ID for reliability.",
            flush=True,
        )
        chat_id = DEFAULT_APK_CHAT_ID
    # BUGFIX: do NOT null metadata_chat_id when it is a @username.
    # Second public metadata channel (@NixgramXMetadata) must resolve by username.
    if metadata_chat_id and not str(metadata_chat_id).lstrip("-").isdigit():
        print(
            f"INFO: metadata ref is {describe_chat_ref(metadata_chat_id)}; "
            f"will resolve @username for the second public metadata channel "
            f"(APK chat still never receives #update* JSON).",
            flush=True,
        )
    print(
        f"Using APK chat={describe_chat_ref(chat_id)} metadata={describe_chat_ref(metadata_chat_id)}",
        flush=True,
    )
    client = get_client(bot_token)
    await client.start()
    await resolve_and_print_chat(client, chat_id)
    # Public @NixgramX: APK media group + NagramX-style 「日志」 caption only (never #update*).
    await send_to_channel(client, chat_id, with_caption=True)
    if metadata_chat_id and not same_chat(metadata_chat_id, chat_id):
        await resolve_and_print_chat(client, metadata_chat_id)
        # Metadata chat must host its own APK copies so document message ids are valid
        # for channels.getMessages(CHANNEL_METADATA_ID) → FileLoader in-app download.
        metadata_document_ids = await send_to_channel(client, metadata_chat_id, with_caption=False)
        if not metadata_document_ids:
            raise RuntimeError(
                "Metadata APK upload returned empty document map; "
                "in-app download would fall back to url only."
            )
        # 「日志」 text message → JSON message id for UpdateAppAlertDialog changelog.
        changelog_message_id = await send_changelog_message(client, metadata_chat_id)
        sticker_message_id = await obtain_sticker_message_id(client, metadata_chat_id)
        await send_update_json(
            client,
            metadata_chat_id,
            metadata_document_ids,
            sticker_message_id=sticker_message_id,
            changelog_message_id=changelog_message_id,
        )
        print(
            "Posted metadata APKs + 「日志」 + #update* JSON "
            f"(APK chat={chat_id}, metadata chat={metadata_chat_id}); "
            f"sticker={sticker_message_id}; message={changelog_message_id}; "
            f"document={metadata_document_ids}.",
            flush=True,
        )
    else:
        # Public APK channel must never receive #update* JSON or canary hash logs.
        print(
            "WARN: HELPER_BOT_CANARY_TARGET missing or same as APK chat; "
            "skipping metadata APKs / 「日志」 / #update* JSON. "
            "Public channel gets APK media group + 日志 caption only; "
            "in-app Update will only have url fallback until metadata is configured.",
            flush=True,
        )
    await client.log_out()


if __name__ == "__main__":
    from asyncio import run

    run(main())
