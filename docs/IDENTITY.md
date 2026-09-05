# NixgramX Identity

Day-1 identity cut (Phase 0/1). NixgramX is an independent app — it does **not** overlay-install over NagramX.

## Locked applicationId / display name

| Product | applicationId | Display name | Notes |
| --- | --- | --- | --- |
| Full (default) | `app.nixgramx.android` | NixgramX | Keeps NagramX full-feature set except policy removals |
| `_base` | `app.nixgramx.android.base` | NixgramX | Mirrors NagramX `_base` cut (ToS-friendlier) |

Both packages:

- Can be installed side-by-side (different applicationId)
- Share the same NixgramX signing keystore (must **not** reuse NagramX cert)
- Updater must only pull matching flavor APKs once metadata channel is owned by NixgramX

## How package IDs are switched (NagramX method)

NagramX did **not** use Gradle `productFlavors`. It used one source tree + `APP_PACKAGE` in `gradle.properties` (`nu.gpu.nagram` vs `nu.gpu.nagramx`) and renamed release APKs to `NagramX` / `NagramX_base`.

NixgramX mirrors that:

- Default: `APP_PACKAGE=app.nixgramx.android` in `gradle.properties`
- `_base`: set `NIXGRAMX_BASE=true` **or** `APP_PACKAGE=app.nixgramx.android.base` (property/env)
- `TMessagesProj/build.gradle` sets `BuildConfig.IS_BASE` and APK prefix `NixgramX` / `NixgramX_base`

## Replacement paths

| Concern | Path | Status |
| --- | --- | --- |
| applicationId | `gradle.properties` `APP_PACKAGE` + `TMessagesProj/build.gradle` | Done |
| Display name | `res/values*/strings.xml` `AppName`, `strings_nax.xml` `NagramX` key | Done (icons still NagramX temporarily) |
| Account / contacts MIME | `res/xml/auth.xml`, `sync_contacts.xml`, `contacts.xml`, `auth_menu.xml`, `AndroidManifest.xml` | Done → `app.nixgramx.android` |
| FCM | `TMessagesProj/google-services.json` | Placeholder clients for full + base |
| Maps API | `AndroidManifest.xml` `com.google.android.maps.v2.API_KEY` | Placeholder `YOUR_GOOGLE_MAPS_API_KEY` |
| Telegram API | `local.properties` via `TELEGRAM_APP_ID` / `TELEGRAM_APP_HASH` (see `local.properties.example`) | Must be filled locally / CI secret |
| Signing | `TMessagesProj/release.keystore` + `KEYSTORE_*` in `local.properties` | Must replace with NixgramX keystore |
| Remote-config / updater channel | `BaseRemoteHelper.CHANNEL_METADATA_ID` / `CHANNEL_METADATA_NAME` | Neutralized to `0` / `nixgramx_remote_metadata` |
| About / source links | `NekoAboutActivity.java`, `Tools/scripts/upload.py` | Pointed at `Anndy999/NixgramX` |

## Why NagramX cannot be covered

Different `applicationId` **and** different signing certificate → Android treats NixgramX as a new app. Users must install NixgramX separately and re-login.

## Migration from NagramX

1. Chat history lives on Telegram servers — log into the same account to restore cloud chats.
2. Local-only data does **not** auto-migrate (saved deleted messages DB, edit history, some settings, translation cache, custom download paths).
3. Prefer NagramX/NixgramX settings import/export where available.
4. Do **not** attempt to read NagramX private data directories.
5. NagramX full users → install `app.nixgramx.android`; NagramX `_base` users → `app.nixgramx.android.base`.

## Signing fingerprints (current)

NixgramX release keystore (`TMessagesProj/release.keystore`, alias `nixgramx`, PKCS12, rotated 2026-09-05). Passwords are **not** in git (GitHub secrets `KEYSTORE_PASS` / `ALIAS_PASS`, local `nixgramx-release-signing.txt`).

- SHA-256: `52:54:81:59:97:91:41:62:6E:E5:B4:07:B8:4E:E7:0A:33:44:ED:91:29:7F:5F:BE:8E:91:DF:8F:0C:29:A3:1C`


## Policy removals (not part of `_base` cut)

Removed/disabled in **both** full and `_base` (see `FEATURE_INVENTORY.md`):

- Ghost Mode
- Hide typing (ghost intercept path)
- Online-status hide / enhance (`ShowOnlineStatus`)
