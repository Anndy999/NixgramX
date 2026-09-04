# In-app auto-update framework

NixgramX reuses NagramX `UpdateHelper` / `BaseRemoteHelper` (metadata channel posts JSON tagged `#updateRelease` / `#updateBeta`).

**Default: OFF.** `NaConfig.autoUpdateChannel = 0`. Background checks do not run. The metadata channel ID is still `0` (`nixgramx_remote_metadata` placeholder) until you own a channel.

## User-facing switch

Settings → long-press version row → **Auto-check updates**:

| Value | Meaning |
| --- | --- |
| OFF (default) | No background check. Manual "Check update" still no-ops until a channel ID is set |
| Release | `#updateRelease` posts |
| Beta | `#updateBeta` posts |

`BuildVars.CHECK_UPDATES` is `false` (no Play/official Telegram updater).

## Enable later (after you own a channel)

1. Create a private Telegram channel; post APKs + JSON metadata.
2. Set in `BaseRemoteHelper.java`:

```java
public static final long CHANNEL_METADATA_ID = <channel_id>;
public static final String CHANNEL_METADATA_NAME = "your_channel_username";
```

3. Users (or you) flip the switch to Release/Beta.

JSON post body after the tag, example:

```json
{
  "can_not_skip": false,
  "version": "12.10.1",
  "version_code": 1262,
  "build_timestamp": 0,
  "sticker": 0,
  "message": 0,
  "document": { "arm64-v8a": 0, "universal": 0 },
  "url": "https://github.com/Anndy999/NixgramX/releases"
}
```

`document` values are message IDs of APK files posted in the same channel. `Tools/scripts/upload.py` uploads APKs; wire `CHANNEL_METADATA_ID` before relying on it.

Until step 2 is done, turning the switch on is harmless: `UpdateHelper.isChannelConfigured()` is false and no network call is made.
