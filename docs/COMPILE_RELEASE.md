# Compile a signed release with current fixes

Not `main`. Merge the bugfix lane onto the 12.10.1 sync branch, then assemble.

```bash
git fetch origin
git checkout upstream-sync/12.10.1
git merge --no-ff origin/fix/user-requested-bugs
Tools/scripts/apply-user-bugfixes.sh
cp local.properties.example local.properties
# fill Telegram API id/hash + keystore passwords
Tools/scripts/prepare-compile.sh          # checks secrets, does not build
./gradlew TMessagesProj:assembleRelease  # needs JDK 21, SDK, NDK 27, CMake, nasm
```

APKs: `TMessagesProj/build/outputs/apk/release/NixgramX-v…-arm64-v8a.apk`

`_base` flavor:

```bash
NIXGRAMX_BASE=true ./gradlew TMessagesProj:assembleRelease
```

This environment has no Android SDK, so the APK is not produced here. Run the commands on your compile machine / CI.

## What is already in `fix/user-requested-bugs`

- UB-1 translation bubble width (replace-original and keep-original)
- UB-4 attach pinch-zoom jank
- Auto-update framework, switch default OFF
- NixgramX release keystore (passwords not in git)

## What you still must supply

- `TELEGRAM_APP_ID` / `TELEGRAM_APP_HASH` from https://my.telegram.org/apps (do not use official Telegram ids)
- Keystore passwords (see the private `nixgramx-release-signing.txt` given to the owner)
