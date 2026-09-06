# In-app auto-update framework

NixgramX reuses NagramX `UpdateHelper` / `BaseRemoteHelper` (metadata channel posts JSON tagged `#updateRelease` / `#updateBeta`).

**Default: OFF.** `NaConfig.autoUpdateChannel = 0`. Background checks do not run until you set a positive `CHANNEL_METADATA_ID` and users flip the switch (manual long-press check still works once the ID is set).

There is **no server update API** — the app only `messages.search`es the metadata channel.

## Publish channels (important)

| Channel | Role |
| --- | --- |
| Public [@NixgramX](https://t.me/NixgramX) | Stable/Beta **APK** media groups only. Caption is NagramX CI-style **「日志」**: `Release version.` / `Dev version.` + product line, then `Commit Message:` + HTML `<blockquote expandable>` with the log text, plus `See commit details <hash>` (and optional Full Changelog). **Never** post `#updateRelease` / `#updateBeta` JSON or hash-only spam lines here. Do **not** call this 「人话说明」. |
| Second public [@NixgramXMetadata](https://t.me/NixgramXMetadata) | Receives **APK copies** (so `document` message IDs live in this chat), a **「日志」** text message, `#update*` JSON, and optional sticker. **Must stay PUBLIC** so logged-in user accounts can `messages.search`. A private metadata channel breaks checks. |

`Tools/scripts/upload.py`:

1. Always uploads APKs to `HELPER_BOT_TARGET` (argv[2], e.g. `@NixgramX`) with the NagramX-style **「日志」** caption.
2. When `HELPER_BOT_CANARY_TARGET` (argv[4]) is **different** from the APK chat:
   - Uploads the same APKs again to the metadata chat (no public-style caption required).
   - Posts a **「日志」** text message (Commit Message + blockquote + commit links).
   - Posts `#updateRelease` / `#updateBeta` JSON with:
     - `document`: abi → metadata APK `message_id` (**non-empty** — required for in-app FileLoader download)
     - `message`: id of the 「日志」 text message (UpdateAppAlertDialog loads changelog via `channels.getMessages`)
     - `url`: `https://t.me/NixgramX` fallback
     - `sticker`: existing duck-sticker logic
3. If canary is unset or the same chat as the APK channel: **skips** metadata APKs / 「日志」 / JSON, prints a warning, APK+日志 caption only on public. **Public APK channel never receives `#update*`.**
4. `@username` refs for the metadata chat are **resolved** (do not null them). After each publish, the log prints a `CHANNEL_METADATA_ID candidate` — paste a positive id into `BaseRemoteHelper` once confirmed.

**Direct download:** Update dialog uses `FileLoader` when `pendingAppUpdate.document` is set. That requires non-empty `document` ids that resolve in `@NixgramXMetadata`. Empty `document` maps force the url-only path (`Browser.openUrl` → t.me/NixgramX).

「日志」 body prefers `RELEASE_NOTES` / `docs/RELEASE_NOTES.txt`, else `COMMIT_MESSAGE` (NagramX-style). Never use hash-only canary lines as the sole public caption.

## App constants

- `BaseRemoteHelper.CHANNEL_METADATA_NAME` = `"NixgramXMetadata"` (metadata channel, **not** `@NixgramX`).
- `BaseRemoteHelper.CHANNEL_METADATA_ID` is `4419000687L` (paste the positive candidate from `upload.py` if it changes).
- **Do not** set ID to `3819693045` (that is the public APK channel — searching it for `#update*` is wrong once JSON lives on metadata only).
- Do not point these at NagramX author endpoints.
- Metadata channel must remain **public**.

## GitHub Actions secrets (upload lane)

Already expected on the repo (do not commit values):

| Secret | Role |
| --- | --- |
| `HELPER_BOT_TOKEN` | Bot that posts APKs to both chats; posts `#update*` only to the metadata chat |
| `HELPER_BOT_TARGET` | Public APK chat (`@NixgramX`) |
| `HELPER_BOT_CANARY_TARGET` | Second **public** metadata chat (`@NixgramXMetadata`; must differ from `HELPER_BOT_TARGET`) |
| `APP_ID` / `APP_HASH` | Telegram API credentials for Pyrogram |
| `LOCAL_PROPERTIES` | Base64 (or raw) `local.properties` including signing passwords |

Bot must be an **admin** of `@NixgramX` (post APKs) and of `@NixgramXMetadata` (post APKs + 「日志」 + JSON).

NixgramX release fields come from `NIXGRAMX_VERSION_NAME` / `NIXGRAMX_VERSION_CODE`; `APP_VERSION_*` remains Telegram upstream metadata. `BUILD_TIMESTAMP` comes from the workflow env. APK filename `(verCode)` is preferred for `version_code` in JSON.

## Delete accidental public `#update*` on `@NixgramX`

If an older upload posted JSON into `@NixgramX`, delete those messages:

```bash
# Local / CI one-off — never echo the token
HELPER_BOT_TOKEN='…' HELPER_BOT_TARGET='@NixgramX' \
  python Tools/scripts/delete_public_update_json.py
# or: python Tools/scripts/delete_public_update_json.py "$HELPER_BOT_TOKEN" @NixgramX 3
```

Alternatively, as channel admin in Telegram: open [@NixgramX](https://t.me/NixgramX) → delete any `#updateRelease` / `#updateBeta` `{…}` messages manually.

## User-facing switch

Settings → long-press version row → **Auto-check updates**:

| Value | Meaning |
| --- | --- |
| OFF (default) | No background check. Manual "Check update" still runs once `CHANNEL_METADATA_ID` is set |
| Release | `#updateRelease` posts |
| Beta | `#updateBeta` posts |

`BuildVars.CHECK_UPDATES` is `false` (no Play/official Telegram updater).

## Enable in-app checks

1. Ensure [@NixgramXMetadata](https://t.me/NixgramXMetadata) is **public**; add the helper bot as admin.
2. Set secret `HELPER_BOT_CANARY_TARGET=@NixgramXMetadata` (must ≠ `@NixgramX`). Keep `HELPER_BOT_TARGET=@NixgramX`.
3. Run a Release/Beta upload; confirm metadata has **APKs + 「日志」 + `#update*`**. Copy the printed positive id into `BaseRemoteHelper.java`:

```java
public static final long CHANNEL_METADATA_ID = 4419000687;
public static final String CHANNEL_METADATA_NAME = "NixgramXMetadata";
```

4. Rebuild/ship that follow-up commit. Users can long-press version → Check update, or flip Auto-check to Release/Beta.

JSON post body after the tag, example (direct-download ready):

```json
{
  "can_not_skip": false,
  "version": "12.10.1",
  "version_code": 1273,
  "build_timestamp": 0,
  "sticker": 0,
  "message": 42,
  "document": {
    "arm64-v8a": 40,
    "universal": 41
  },
  "url": "https://t.me/NixgramX"
}
```

`message` is the 「日志」 text message id; `document` ids are the metadata-channel APK messages. Until `CHANNEL_METADATA_ID` points at `@NixgramXMetadata`, checks return `updater_not_configured`.
