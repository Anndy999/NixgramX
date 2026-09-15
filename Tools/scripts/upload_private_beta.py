#!/usr/bin/env python3
"""Private-only APK upload for NixgramX private beta CI.

Hard rules:
  - Target ONLY HELPER_BOT_PRIVATE_TARGET (env or argv[2]).
  - Fail hard if the private target is empty.
  - Never fall back to HELPER_BOT_TARGET, HELPER_BOT_CANARY_TARGET,
    DEFAULT_APK_CHAT_ID, public channels, metadata/#update* JSON,
    or GitHub Releases.
  - Never print chat.id / username / title / full target value.
"""

from __future__ import annotations

import html
import os
import re
import contextlib
from pathlib import Path
from sys import argv

from pyrogram import Client
from pyrogram.enums import ParseMode

REPO_ROOT = Path(__file__).resolve().parents[2]
ABI = "arm64-v8a"
DEFAULT_COMMITS_URL = "https://github.com/Anndy999/NixgramX/commits"


def normalize_chat_ref(value):
    if value is None:
        return None
    s = str(value).strip().strip('"').strip("'")
    if not s:
        return ""
    for prefix in ("https://t.me/", "http://t.me/", "tg://resolve?domain="):
        if s.lower().startswith(prefix):
            s = s[len(prefix) :]
            break
    if s.lower().startswith("t.me/"):
        s = s[5:]
    s = s.strip().strip("/")
    if s.lstrip("-").isdigit():
        return s
    if not s.startswith("@"):
        s = "@" + s
    return s


def describe_chat_ref(value) -> str:
    """Redacted shape only — never the full target."""
    if value is None:
        return "None"
    s = str(value)
    if s.lstrip("-").isdigit():
        return f"numeric(len={len(s)})"
    if s.startswith("@"):
        return f"username(len={len(s)})"
    return f"other(len={len(s)})"


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


def find_arm64_apk() -> Path:
    """Prefer PRIVATE_BETA_APK; else unique arm64 under staging outputs."""
    explicit = (os.environ.get("PRIVATE_BETA_APK") or "").strip()
    if explicit:
        p = Path(explicit)
        if not p.is_file():
            raise FileNotFoundError(f"PRIVATE_BETA_APK not a file: {p}")
        if ABI not in p.name:
            raise FileNotFoundError(f"PRIVATE_BETA_APK is not arm64-v8a: {p.name}")
        return p

    search_roots = [
        Path("TMessagesProj/build/outputs/apk/staging"),
        Path("TMessagesProj/build/outputs/apk"),
    ]
    found: list[Path] = []
    for root in search_roots:
        if not root.exists():
            continue
        for apk in sorted(root.rglob("*.apk")):
            if ABI in apk.name:
                found.append(apk)
    if not found:
        raise FileNotFoundError(
            f"No {ABI} APK found; set PRIVATE_BETA_APK or build staging first"
        )
    if len(found) > 1:
        raise FileNotFoundError(
            f"Expected exactly 1 {ABI} APK, found {len(found)}"
        )
    return found[0]


def resolve_version(apk: Path) -> tuple[str, int]:
    name = (
        os.environ.get("NIXGRAMX_VERSION_NAME")
        or _read_gradle_property("NIXGRAMX_VERSION_NAME")
        or os.environ.get("APP_VERSION_NAME")
        or _read_gradle_property("APP_VERSION_NAME")
    )
    code = 0
    m = re.search(r"[Vv]?(\d+\.\d+(?:\.\d+)?(?:-[0-9A-Za-z.-]+)?)\((\d+)\)", apk.name)
    if m:
        name = m.group(1)
        code = int(m.group(2))
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
    commit_message = os.environ.get("COMMIT_MESSAGE") or "Private beta build"
    return commit_id, commit_url, commit_message


def get_log_text() -> str:
    notes = (os.environ.get("RELEASE_NOTES") or "").strip()
    if notes:
        return notes.replace("\\n", "\n")
    notes_path = Path("docs/RELEASE_NOTES.txt")
    if notes_path.is_file():
        file_notes = notes_path.read_text(encoding="utf-8").strip()
        if file_notes:
            return file_notes.replace("\\n", "\n")
    _, _, commit_message = get_commit_info()
    return (commit_message or "Private beta test build.").replace("\\n", "\n")


def build_changelog_blockquote(max_length: int) -> str:
    text = html.escape(get_log_text(), quote=False)
    lines = [line for line in text.splitlines() if line.strip()]
    if not lines:
        return html.escape("Private beta test build.", quote=False)
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


def get_caption(apk: Path) -> str:
    """NagramX-style 「日志」 caption, private-beta labeled. No public/update tags."""
    version, version_code = resolve_version(apk)
    commit_id, commit_url, _ = get_commit_info()
    pr_number = (os.environ.get("PR_NUMBER") or "").strip()
    pre = f"[PRIVATE BETA] NixgramX Beta · {version} ({version_code})"
    if pr_number:
        pre += f" · PR #{pr_number}"
    pre = f"Private test only. {pre}"

    caption = html.escape(pre) + "\n\n"
    caption += "Commit Message:\n"
    see = (
        'See commit details <a href="'
        + html.escape(commit_url, quote=False)
        + '">'
        + html.escape(commit_id)
        + "</a>"
    )
    open_tag = "<blockquote expandable>"
    close_tag = "</blockquote>\n\n"
    budget = 1024 - len(caption) - len(open_tag) - len(close_tag) - len(see) - 8
    if budget < 32:
        budget = 32
    body = build_changelog_blockquote(budget)
    caption += open_tag + body + close_tag + see
    if len(caption) > 1024:
        caption = caption[:1020] + "..."
    return caption


def require_private_target() -> str:
    """Resolve private target; fail hard with no public fallback."""
    private_raw = ""
    if len(argv) > 2:
        private_raw = argv[2]
    if not str(private_raw).strip():
        private_raw = os.environ.get("HELPER_BOT_PRIVATE_TARGET") or ""
    target = normalize_chat_ref(private_raw)
    if not target:
        raise SystemExit(
            "FATAL: HELPER_BOT_PRIVATE_TARGET is empty. "
            "Private beta upload refuses public fallback "
            "(no HELPER_BOT_TARGET / CANARY / DEFAULT_APK_CHAT_ID)."
        )

    # Equality vs public/canary without printing values.
    for banned_name in (
        "HELPER_BOT_TARGET",
        "HELPER_BOT_CANARY_TARGET",
        "DEFAULT_APK_CHAT_ID",
    ):
        other = normalize_chat_ref(os.environ.get(banned_name) or "")
        if other and other == target:
            raise SystemExit(
                f"FATAL: HELPER_BOT_PRIVATE_TARGET equals {banned_name}"
            )

    if len(argv) > 3 and str(argv[3]).strip():
        raise SystemExit(
            "FATAL: upload_private_beta.py accepts only bot_token + private target. "
            "Extra args (metadata/canary/public) are forbidden."
        )
    return target


async def main():
    if len(argv) < 2 or not str(argv[1]).strip():
        raise SystemExit(
            "Usage: upload_private_beta.py <HELPER_BOT_TOKEN> [HELPER_BOT_PRIVATE_TARGET]"
        )

    bot_token = argv[1].strip()
    chat_id = require_private_target()

    api_id = os.environ.get("APP_ID") or os.environ.get("TELEGRAM_APP_ID")
    api_hash = os.environ.get("APP_HASH") or os.environ.get("TELEGRAM_APP_HASH")
    if not api_id or not api_hash:
        raise SystemExit(
            "FATAL: APP_ID/APP_HASH (or TELEGRAM_APP_ID/TELEGRAM_APP_HASH) required"
        )

    apk = find_arm64_apk()
    caption = get_caption(apk)
    print(
        f"Private beta upload target={describe_chat_ref(chat_id)} apk={apk.name}",
        flush=True,
    )
    print("Caption preview:", flush=True)
    print(caption[:400] + ("…" if len(caption) > 400 else ""), flush=True)

    client = Client(
        "helper_bot_private",
        api_id=api_id,
        api_hash=api_hash,
        bot_token=bot_token,
    )
    await client.start()
    try:
        with contextlib.suppress(ValueError):
            chat_id = int(chat_id)
        # Resolve chat for send only — do not log id/username/title/full target.
        await client.get_chat(chat_id)
        print("Private chat resolved (details redacted)", flush=True)
        msg = await client.send_document(
            chat_id=chat_id,
            document=str(apk),
            caption=caption,
            parse_mode=ParseMode.HTML,
        )
        print(
            f"Private APK message_id={msg.id} (no metadata/#update*/public)",
            flush=True,
        )
    finally:
        await client.log_out()


if __name__ == "__main__":
    from asyncio import run

    run(main())
