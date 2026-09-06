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

Both workflows upload to the existing [@NixgramX](https://t.me/NixgramX) channel. Stable captions are `NixgramX · <version> (<code>)`; Beta captions use `NixgramX Beta`. Optional notes come only from `RELEASE_NOTES`, never the commit title/hash. The publisher edits the track's existing `#update*` JSON post in this same channel; it never appends JSON, canary logs, or sticker notices. No second channel is required.

Before publishing, set repository **Variables** `UPDATE_RELEASE_MESSAGE_ID` and `UPDATE_BETA_MESSAGE_ID` to the corresponding existing metadata text posts. These are message IDs, not credentials. The publishing job maps the appropriate variable to `UPDATE_METADATA_MESSAGE_ID` and validates the post before uploading any APK. Missing/deleted/wrong-track posts fail the publication rather than silently skipping updates. See [AUTO_UPDATE.md](AUTO_UPDATE.md) for migration and recovery limitations.

## Required GitHub Secrets

Values must remain in GitHub Secrets and never be committed or echoed:

- `HELPER_BOT_TOKEN`, `HELPER_BOT_TARGET` (the existing public channel)
- `APP_ID`, `APP_HASH`
- `LOCAL_PROPERTIES` and the existing signing-secret inputs

`HELPER_BOT_CANARY_TARGET` is no longer used by Stable/Beta publishing. Do not create a private metadata channel or change the app's update-channel constants.

After publishing, verify the caption, APK version code and signature, and the edited same-channel `#updateRelease` or `#updateBeta` post. Read-back verification checks its text; a real installed app must still verify discovery and download. Stable/Beta publication jobs share a concurrency group; running publications are not cancelled by a newer build. Operators must still choose a version code higher than **all** previously published Stable/Beta APKs (including APK-only releases); the script's rollback guard checks only the configured track's metadata.
