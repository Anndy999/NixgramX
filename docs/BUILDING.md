# Building NixgramX

## Prerequisites

- JDK 21, Android SDK (platform 37+ as used by CI), NDK 27.x, CMake 3.31.6+, `nasm`
- Submodules initialized: `git submodule update --init --recursive --depth=1`

## Configure secrets (local)

```bash
cp local.properties.example local.properties
# fill TELEGRAM_APP_ID / TELEGRAM_APP_HASH, KEYSTORE_*, sdk.dir
# Gradle refuses the public sample api_id 6 (Telegram mass-revokes those sessions).
# CI: TELEGRAM_APP_ID must be inside the LOCAL_PROPERTIES secret, not secrets.APP_ID (upload.py only).
```

Replace:

- `TMessagesProj/release.keystore` with your NixgramX keystore
- `TMessagesProj/google-services.json` with your Firebase apps for `app.nixgramx.android` (+ `.base` if needed)
- Maps API key in `AndroidManifest.xml`

## Full release APK

```bash
./gradlew TMessagesProj:assembleRelease
```

Default `APP_PACKAGE=app.nixgramx.android`.

## `_base` release APK

```bash
NIXGRAMX_BASE=true ./gradlew TMessagesProj:assembleRelease
# or: ./gradlew TMessagesProj:assembleRelease -PAPP_PACKAGE=app.nixgramx.android.base -PNIXGRAMX_BASE=true
```

APK name prefix: `NixgramX_base-…`.

## Skip native (smoke)

```bash
NATIVE_TARGET=SKIP ./gradlew TMessagesProj:assembleDebug
```

## CI

Workflows under `.github/workflows/` are adapted for NixgramX but may be **local-only** until GitHub Actions workflow scope is granted. Secrets: `LOCAL_PROPERTIES` (base64), helper bot tokens as in README.
