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

Both workflows upload APKs to [@NixgramX](https://t.me/NixgramX) with a NagramX-style **「日志」** caption (`Commit Message:` + `<blockquote expandable>` from `RELEASE_NOTES` / `docs/RELEASE_NOTES.txt` / `COMMIT_MESSAGE`). Do not call this 「人话说明」. The same APKs, a 「日志」 text message, and `#updateRelease` / `#updateBeta` JSON go to `@NixgramXMetadata` (`HELPER_BOT_CANARY_TARGET`) so in-app Update can FileLoader-download via non-empty `document` ids.

## Required GitHub Secrets

Values must remain in GitHub Secrets and never be committed or echoed:

- `HELPER_BOT_TOKEN`, `HELPER_BOT_TARGET`, `HELPER_BOT_CANARY_TARGET`
- `APP_ID`, `APP_HASH`
- `LOCAL_PROPERTIES` and the existing signing-secret inputs

After publishing, verify: public `@NixgramX` shows APK + 「日志」 blockquote caption; `@NixgramXMetadata` has APKs + 「日志」 text + `#update*` JSON with non-empty `document` and `message`; APK version code/signature. Paste the upload log `CHANNEL_METADATA_ID candidate` into `BaseRemoteHelper` after the first successful metadata publish.
