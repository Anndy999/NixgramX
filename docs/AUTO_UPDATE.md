# In-app auto-update framework

NixgramX reuses NagramX `UpdateHelper` / `BaseRemoteHelper` (metadata channel posts JSON tagged `#updateRelease` / `#updateBeta`).

**Default: OFF.** `NaConfig.autoUpdateChannel = 0`. Background checks do not run until you set a numeric `CHANNEL_METADATA_ID` and users flip the switch.

## Publish channels (important)

| Channel | Role |
| --- | --- |
| Public [@NixgramX](https://t.me/NixgramX) | Stable/Beta APK media groups only. Caption is `NixgramX · <version> (<code>)` or `NixgramX Beta · <version> (<code>)`, optional blank line + user notes from `RELEASE_NOTES` / `docs/RELEASE_NOTES.txt`. **Never** post `#updateRelease` / `#updateBeta` JSON, canary hash lines, commit titles, or CI metadata here. |
| Private metadata channel | Receives `#update*` JSON (+ optional canary line) for in-app updates. |

`Tools/scripts/upload.py`:

1. Always uploads APKs to `HELPER_BOT_TARGET` (argv[2], e.g. `@NixgramX`).
2. Posts `#updateRelease` / `#updateBeta` JSON **only** to `HELPER_BOT_CANARY_TARGET` (argv[4]) when that chat is **different** from the APK chat.
3. If canary is unset or the same chat as the APK channel: **skips** JSON and canary hash log, prints a warning, APK-only on public. **Public channel never receives `#update*`.**
4. When JSON goes to a private metadata chat, the `document` map is left empty (APK message IDs are not in that chat); `url` stays `https://t.me/NixgramX` so the updater can open the public channel.

`stable` / `beta` public captions never fall back to `COMMIT_MESSAGE`. Private metadata (when `HELPER_BOT_CANARY_TARGET` ≠ APK chat) still gets `#updateRelease` / `#updateBeta` for the in-app updater. Same chat or unset canary → skip JSON entirely (public channel never gets `#update*`).

## App constants

- `BaseRemoteHelper.CHANNEL_METADATA_NAME` / `CHANNEL_METADATA_ID` should eventually match the **private** metadata channel.
- May temporarily keep the public channel id until the private channel exists; switch both when ready.
- Do not point these at NagramX author endpoints.

## GitHub Actions secrets (upload lane)

Already expected on the repo (do not commit values):

| Secret | Role |
| --- | --- |
| `HELPER_BOT_TOKEN` | Bot that posts APKs; also posts `#update*` only to the private metadata chat |
| `HELPER_BOT_TARGET` | Public APK chat (`@NixgramX`) |
| `HELPER_BOT_CANARY_TARGET` | **Private** metadata/canary chat (must differ from `HELPER_BOT_TARGET`) |
| `APP_ID` / `APP_HASH` | Telegram API credentials for Pyrogram |
| `LOCAL_PROPERTIES` | Base64 (or raw) `local.properties` including signing passwords |

Bot must be an **admin** of `@NixgramX` (post APKs) and of the private metadata channel (post JSON).

NixgramX release fields come from `NIXGRAMX_VERSION_NAME` / `NIXGRAMX_VERSION_CODE`; `APP_VERSION_*` remains Telegram upstream metadata. `BUILD_TIMESTAMP` comes from the workflow env. APK filename `(verCode)` is preferred for `version_code` in JSON.

## Delete accidental public `#updateRelease` message

If an older upload posted JSON into `@NixgramX` (e.g. `message_id=3`):

```bash
# Local / CI one-off — never echo the token
HELPER_BOT_TOKEN='…' HELPER_BOT_TARGET='@NixgramX' \
  python Tools/scripts/delete_public_update_json.py
# or: python Tools/scripts/delete_public_update_json.py "$HELPER_BOT_TOKEN" @NixgramX 3
```

Alternatively, as channel admin in Telegram: open [@NixgramX](https://t.me/NixgramX) → delete the `#updateRelease {…}` message manually.

## User-facing switch

Settings → long-press version row → **Auto-check updates**:

| Value | Meaning |
| --- | --- |
| OFF (default) | No background check. Manual "Check update" still no-ops until a channel ID is set |
| Release | `#updateRelease` posts |
| Beta | `#updateBeta` posts |

`BuildVars.CHECK_UPDATES` is `false` (no Play/official Telegram updater).

## Enable in-app checks (after private metadata channel exists)

1. Create a private channel; add the helper bot as admin.
2. Set `HELPER_BOT_CANARY_TARGET` to that channel (must ≠ `@NixgramX`).
3. Run a Release Build upload; copy the printed positive id into `BaseRemoteHelper.java`:

```java
public static final long CHANNEL_METADATA_ID = <private_channel_id_from_upload_log>;
public static final String CHANNEL_METADATA_NAME = "<private_channel_username_or_title>";
```

4. Ship that follow-up commit; users flip the switch to Release/Beta.

JSON post body after the tag, example (url-only while APKs live on the public channel):

```json
{
  "can_not_skip": false,
  "version": "12.10.1",
  "version_code": 1269,
  "build_timestamp": 0,
  "sticker": 0,
  "message": 0,
  "document": {},
  "url": "https://t.me/NixgramX"
}
```

Until a private metadata channel is configured and `CHANNEL_METADATA_ID` points at it, turning the switch on may still no-op or only open the public URL depending on stored id.
