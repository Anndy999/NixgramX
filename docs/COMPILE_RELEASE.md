# Build and publish NixgramX

Use [RELEASE_CHANNELS.md](RELEASE_CHANNELS.md) for the Stable/Beta policy, version rule, branch flow, and updater behavior. `upstream-sync/*` and historical release-candidate branches are not publish sources.

## Local builds

```bash
# Stable source: main
./gradlew TMessagesProj:assembleRelease

# Beta source: beta
NIXGRAMX_CHANNEL=beta ./gradlew TMessagesProj:assembleStaging

# _base remains an explicit, separate package build
NIXGRAMX_BASE=true ./gradlew TMessagesProj:assembleRelease
```

Before any channel publication, update `NIXGRAMX_VERSION_NAME` and `NIXGRAMX_VERSION_CODE` in `gradle.properties`. Never reuse a published NixgramX version code.

## GitHub Actions

| Channel | Workflow | Allowed source | Publish action |
| --- | --- | --- | --- |
| Stable | `Stable Release` | `main` or a `v*` tag | Push the reviewed tag, or dispatch from `main` with `publish=true` |
| Beta | `Beta Build` | `beta` | Dispatch from `beta` with `publish=true` after smoke testing |

Examples:

```bash
# Publish a reviewed Beta from beta
gh workflow run "Beta Build" --ref beta -f publish=true

# Build a Stable artifact without publishing it
gh workflow run "Stable Release" --ref main -f publish=false
```

Both workflows upload to [@NixgramX](https://t.me/NixgramX). Stable captions are `NixgramX · <version> (<code>)` followed by the commit title; Beta captions use `NixgramX Beta` instead. Updater JSON is sent only to the separate private metadata channel.

## Required GitHub Secrets

Values must remain in GitHub Secrets and never be committed or echoed:

- `HELPER_BOT_TOKEN`, `HELPER_BOT_TARGET`, `HELPER_BOT_CANARY_TARGET`
- `APP_ID`, `APP_HASH`
- `LOCAL_PROPERTIES` and the existing signing-secret inputs

After publishing, verify the channel label, APK version code, signature, and the matching private `#updateRelease` or `#updateBeta` metadata post.
