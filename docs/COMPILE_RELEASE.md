# Compile a signed release with current fixes

Prefer the release-candidate branch (or merge the same lanes yourself), then assemble.

```bash
git fetch origin
git checkout release/candidate-12.10.1   # or recreate merges below
# If recreating:
#   git checkout -b release/candidate-12.10.1 origin/upstream-sync/12.10.1
#   git merge --no-ff origin/fix/user-requested-bugs
#   git merge --no-ff origin/branding/nixgram-display
Tools/scripts/apply-user-bugfixes.sh
cp local.properties.example local.properties
# fill Telegram API id/hash + keystore passwords (KEYSTORE_PASS / ALIAS_PASS unconfirmed until owner verifies)
Tools/scripts/prepare-compile.sh          # checks secrets, does not build
./gradlew TMessagesProj:assembleRelease  # needs JDK 21, SDK, NDK 27, CMake, nasm
```

APKs: `TMessagesProj/build/outputs/apk/release/NixgramX-v…-arm64-v8a.apk`

`_base` flavor:

```bash
NIXGRAMX_BASE=true ./gradlew TMessagesProj:assembleRelease
```

## CI: Release Build → `@NixgramX`

Workflow: `.github/workflows/release.yml` (`name: Release Build`), already has `workflow_dispatch` on whatever ref you run.

Dispatch from the candidate branch (workflow file must exist on that ref):

```bash
gh workflow run "Release Build" --ref release/candidate-12.10.1
# optional: skip upload
# gh workflow run "Release Build" --ref release/candidate-12.10.1 -f upload=y
```

Secrets used for Telegram upload (values stay in GitHub Secrets — never in git/logs/PR text):

- `HELPER_BOT_TOKEN`, `HELPER_BOT_TARGET` (`@NixgramX`), `HELPER_BOT_CANARY_TARGET`
- `APP_ID`, `APP_HASH`
- `LOCAL_PROPERTIES` (must contain working keystore passwords)

After upload, check the job log for `CHANNEL_METADATA_ID candidate=` and `chat.id=`.

## What is already in this candidate

- Upstream sync 12.10.1 compile fixes
- UB-1 translation bubble width, UB-4 attach pinch-zoom, double-tap reaction brace patch
- Auto-update framework, switch default OFF; metadata name `NixgramX`, id still `0`
- Nixgram display branding
- NixgramX release keystore binary (passwords not in git)
- `upload.py` posts APKs to public channel; `#update*` JSON only to private metadata chat when distinct

## What you still must supply / verify

- Keystore passwords inside `LOCAL_PROPERTIES` secret (`KEYSTORE_PASS` / alias pass) — **unconfirmed**
- Helper bot is admin of [@NixgramX](https://t.me/NixgramX)
- Follow-up commit setting numeric `CHANNEL_METADATA_ID` after first successful publish
