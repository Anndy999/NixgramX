# In-app auto-update framework

NixgramX reuses NagramX `UpdateHelper` / `BaseRemoteHelper` (metadata channel posts JSON tagged `#updateRelease` / `#updateBeta`).

**Default: OFF.** `NaConfig.autoUpdateChannel = 0`. Background checks do not run until you set a numeric `CHANNEL_METADATA_ID` and users flip the switch.

## Publish channel

- Public channel: [@NixgramX](https://t.me/NixgramX)
- `BaseRemoteHelper.CHANNEL_METADATA_NAME = "NixgramX"`
- `BaseRemoteHelper.CHANNEL_METADATA_ID = 0L` until the first successful helper-bot upload log prints the resolved positive id

## GitHub Actions secrets (upload lane)

Already expected on the repo (do not commit values):

| Secret | Role |
| --- | --- |
| `HELPER_BOT_TOKEN` | Bot that posts APKs + `#update*` JSON |
| `HELPER_BOT_TARGET` | APK chat (e.g. `@NixgramX`) |
| `HELPER_BOT_CANARY_TARGET` | Metadata/canary chat (can be the same `@NixgramX`) |
| `APP_ID` / `APP_HASH` | Telegram API credentials for Pyrogram |
| `LOCAL_PROPERTIES` | Base64 (or raw) `local.properties` including signing passwords |

Bot must be an **admin** of `@NixgramX` with permission to post messages.

`Tools/scripts/upload.py` (Release Build workflow):

1. Resolves the target chat and prints `chat.id` plus `CHANNEL_METADATA_ID candidate=…`
2. Uploads `arm64-v8a` / `universal` APKs via `send_media_group`
3. Posts `#updateRelease` (or `#updateBeta` when argv is `test`) + JSON with those message IDs

Version fields come from `APP_VERSION_NAME` / `APP_VERSION_CODE` env, else `gradle.properties`. `BUILD_TIMESTAMP` comes from the workflow env.

## User-facing switch

Settings → long-press version row → **Auto-check updates**:

| Value | Meaning |
| --- | --- |
| OFF (default) | No background check. Manual "Check update" still no-ops until a channel ID is set |
| Release | `#updateRelease` posts |
| Beta | `#updateBeta` posts |

`BuildVars.CHECK_UPDATES` is `false` (no Play/official Telegram updater).

## Enable in-app checks (after first publish)

1. Confirm CI upload succeeded to `@NixgramX`.
2. Copy the printed positive id into `BaseRemoteHelper.java`:

```java
public static final long CHANNEL_METADATA_ID = <channel_id_from_upload_log>;
public static final String CHANNEL_METADATA_NAME = "NixgramX";
```

3. Ship that follow-up commit; users flip the switch to Release/Beta.

JSON post body after the tag, example:

```json
{
  "can_not_skip": false,
  "version": "12.10.1",
  "version_code": 7038,
  "build_timestamp": 0,
  "sticker": 0,
  "message": 0,
  "document": { "arm64-v8a": 0, "universal": 0 },
  "url": "https://t.me/NixgramX"
}
```

`document` values are message IDs of APK files posted in the same channel.

Until step 2 is done, turning the switch on is harmless: `UpdateHelper.isChannelConfigured()` is false (`CHANNEL_METADATA_ID == 0`) and no network call is made.
