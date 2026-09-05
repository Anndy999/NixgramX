#!/usr/bin/env python3
"""Delete a bot-posted #update* message from the public APK channel.

One-off recovery after upload.py previously posted #updateRelease JSON into @NixgramX.

Usage (do not echo tokens):
  export HELPER_BOT_TOKEN=...   # or pass as argv[1]
  export HELPER_BOT_TARGET=@NixgramX
  python Tools/scripts/delete_public_update_json.py [bot_token] [chat] [message_id]

Default message_id=3 (prior accidental #updateRelease post).
Requires: pyrogram + APP_ID/APP_HASH env (same as upload.py), or uses Bot API HTTP delete.
"""
from __future__ import annotations

import os
import sys
import urllib.error
import urllib.parse
import urllib.request


def delete_via_bot_api(token: str, chat_id: str, message_id: int) -> None:
    # Prefer Bot API so we do not need APP_ID/APP_HASH for a simple delete.
    q = urllib.parse.urlencode(
        {"chat_id": chat_id, "message_id": str(message_id)}
    )
    url = f"https://api.telegram.org/bot{token}/deleteMessage?{q}"
    try:
        with urllib.request.urlopen(url, timeout=30) as resp:
            body = resp.read().decode("utf-8", errors="replace")
    except urllib.error.HTTPError as e:
        body = e.read().decode("utf-8", errors="replace")
        print(f"deleteMessage HTTP {e.code}: {body}", flush=True)
        raise SystemExit(1) from e
    # Never print the token; body is OK (ok/description only).
    print(f"deleteMessage result: {body}", flush=True)
    if '"ok":true' not in body.replace(" ", "").lower() and '"ok": true' not in body:
        # Bot API returns {"ok":true,...} or {"ok":false,...}
        if '"ok":false' in body.replace(" ", "").lower() or '"ok": false' in body:
            raise SystemExit(1)


def main() -> None:
    token = (sys.argv[1] if len(sys.argv) > 1 else "") or os.environ.get("HELPER_BOT_TOKEN") or ""
    chat = (sys.argv[2] if len(sys.argv) > 2 else "") or os.environ.get("HELPER_BOT_TARGET") or "@NixgramX"
    message_id = int(sys.argv[3] if len(sys.argv) > 3 else os.environ.get("MESSAGE_ID") or "3")
    if not token:
        print(
            "Missing HELPER_BOT_TOKEN. Set env or argv[1]. Example:\n"
            "  HELPER_BOT_TOKEN=*** HELPER_BOT_TARGET=@NixgramX \\\n"
            "    python Tools/scripts/delete_public_update_json.py\n"
            "Or in Telegram: open @NixgramX as channel admin and delete message_id=3 manually.",
            flush=True,
        )
        raise SystemExit(2)
    print(f"Deleting message_id={message_id} from chat={chat} (token not printed)", flush=True)
    delete_via_bot_api(token, chat, message_id)


if __name__ == "__main__":
    main()
