# In-app auto-update framework

NixgramX reuses NagramX `UpdateHelper` / `BaseRemoteHelper` (metadata channel posts JSON tagged `#updateRelease` / `#updateBeta`).

**Default: OFF.** `NaConfig.autoUpdateChannel = 0`. Background checks do not run until you set a positive `CHANNEL_METADATA_ID` and users flip the switch (manual long-press check still works once the ID is set).

There is **no server update API** — the app only `messages.search`es the metadata channel.

## Publish channels (important)

| Channel | Role |
| --- | --- |
| Public [@NixgramX](https://t.me/NixgramX) | Stable/Beta **APK** media groups only. Caption is `NixgramX · <version> (<code>)` or `NixgramX Beta · <version> (<code>)`, optional blank line + user notes from `RELEASE_NOTES` / `docs/RELEASE_NOTES.txt`. **Never** post `#updateRelease` / `#updateBeta` JSON, canary hash lines, commit titles, or CI metadata here. |
| Second public [@NixgramXMetadata](https://t.me/NixgramXMetadata) | Receives `#update*` JSON (+ optional sticker / canary line) for in-app updates. **Must stay PUBLIC** so logged-in user accounts can `messages.search`. A private metadata channel breaks checks. |

`Tools/scripts/upload.py`:

1. Always uploads APKs to `HELPER_BOT_TARGET` (argv[2], e.g. `@NixgramX`).
2. Posts `#updateRelease` / `#updateBeta` JSON **only** to `HELPER_BOT_CANARY_TARGET` (argv[4]) when that chat is **different** from the APK chat.
3. If canary is unset or the same chat as the APK channel: **skips** JSON and canary hash log, prints a warning, APK-only on public. **Public APK channel never receives `#update*`.**
4. When JSON goes to the second public metadata chat, the `document` map is left empty (APK message IDs are not in that chat); `url` stays `https://t.me/NixgramX` so the updater can open the public APK channel.
5. `@username` refs for the metadata chat are **resolved** (do not null them). After each publish, the log prints a `CHANNEL_METADATA_ID candidate` — paste a positive id into `BaseRemoteHelper` once confirmed.

`stable` / `beta` public APK captions never fall back to `COMMIT_MESSAGE`. Metadata (when `HELPER_BOT_CANARY_TARGET` ≠ APK chat) still gets `#updateRelease` / `#updateBeta` for the in-app updater.

## App constants

- `BaseRemoteHelper.CHANNEL_METADATA_NAME` = `"NixgramXMetadata"` (metadata channel, **not** `@NixgramX`).
- `BaseRemoteHelper.CHANNEL_METADATA_ID` is `4419000687L` → `updater_not_configured` until you paste the positive candidate from `upload.py`.
- **Do not** set ID to `3819693045` (that is the public APK channel — searching it for `#update*` is wrong once JSON lives on metadata only).
- Do not point these at NagramX author endpoints.
- Metadata channel must remain **public**.

## GitHub Actions secrets (upload lane)

Already expected on the repo (do not commit values):

| Secret | Role |
| --- | --- |
| `HELPER_BOT_TOKEN` | Bot that posts APKs; also posts `#update*` only to the metadata chat |
| `HELPER_BOT_TARGET` | Public APK chat (`@NixgramX`) |
| `HELPER_BOT_CANARY_TARGET` | Second **public** metadata chat (`@NixgramXMetadata`; must differ from `HELPER_BOT_TARGET`) |
| `APP_ID` / `APP_HASH` | Telegram API credentials for Pyrogram |
| `LOCAL_PROPERTIES` | Base64 (or raw) `local.properties` including signing passwords |

Bot must be an **admin** of `@NixgramX` (post APKs) and of `@NixgramXMetadata` (post JSON).

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
3. Run a Release/Beta upload; confirm JSON appears **only** on metadata. Copy the printed positive id into `BaseRemoteHelper.java`:

```java
public static final long CHANNEL_METADATA_ID = 4419000687;
public static final String CHANNEL_METADATA_NAME = "NixgramXMetadata";
```

4. Rebuild/ship that follow-up commit. Users can long-press version → Check update, or flip Auto-check to Release/Beta.

JSON post body after the tag, example (url-only while APKs live on the public APK channel):

```json
{
  "can_not_skip": false,
  "version": "12.10.1",
  "version_code": 1270,
  "build_timestamp": 0,
  "sticker": 0,
  "message": 0,
  "document": {},
  "url": "https://t.me/NixgramX"
}
```

Until `CHANNEL_METADATA_ID` is a positive id pointing at `@NixgramXMetadata`, checks return `updater_not_configured`.
