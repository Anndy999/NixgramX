# Stable and Beta release channels

## Scope

NixgramX has two user-visible distribution channels:

| Channel | Branch | Build type | Public APK caption | Public metadata tag |
| --- | --- | --- | --- | --- |
| Stable | `main` | `release` | NagramX-style **「日志」** (`Commit Message:` + `<blockquote>`) from `RELEASE_NOTES` / commit | `#updateRelease` on `@NixgramXMetadata` (APK + 日志 + JSON) |
| Beta | `beta` | `staging` | Same **「日志」** caption with Beta / Dev label | `#updateBeta` on `@NixgramXMetadata` (APK + 日志 + JSON) |

Both use `app.nixgramx.android` and the same release signing identity. A Beta APK therefore replaces the installed Stable APK; it is an update channel, not a second installable app.

This follows the mature NagramX model of a Stable `main` lane and a test lane (`dev`, plus an optional canary lane), while keeping NixgramX to the two channels it currently needs. NixgramX deliberately does not create a public canary lane.

## Branch flow

```text
feature / bugfix ──> beta ──> main
upstream-sync/* ─────┘          │
                               Stable tag
```

- Normal feature and bugfix PRs target `beta`.
- `upstream-sync/*` is never published directly. It first passes its gate and then enters `beta`.
- Promote only tested Beta commits through a `beta` → `main` PR.
- An emergency Stable hotfix must be merged or cherry-picked back into `beta` afterward.
- Protect `main`: no direct pushes, require PR review and the build check. Apply the same no-force-push rule to `beta`.

## Version rule

`APP_VERSION_NAME` / `APP_VERSION_CODE` are Telegram upstream metadata. NixgramX package versions are the single source of truth:

```properties
NIXGRAMX_VERSION_NAME=12.10.1
NIXGRAMX_VERSION_CODE=1278
```

Every APK that is **published** to either channel must use a never-before-published, strictly higher `NIXGRAMX_VERSION_CODE`. The previous published build was `1275`, so this Beta uses `1278`. This preserves Android upgrade paths in both directions.

The Gradle channel is supplied only by CI:

- Stable: `NIXGRAMX_CHANNEL=stable` → `12.10.1`.
- Beta: `NIXGRAMX_CHANNEL=beta` → `12.10.1-beta-<commit>`.

Before publishing, bump the two `NIXGRAMX_VERSION_*` values in the same reviewed commit. Do not use the Telegram upstream code (`7038` for 12.10.1) as the NixgramX Android package code.

## GitHub Actions and Telegram

- **Stable Release** runs only from a `v*` tag, or manually from `main` with `publish=true`. A tag is the explicit decision to post a Stable build.
- **Beta Build** builds artifacts on every push to `beta`, but posts to the channel only when manually dispatched from `beta` with `publish=true`. This prevents unreviewed commits from spamming users.
- Both upload APKs to the existing public `@NixgramX` channel with a NagramX CI-style **「日志」** caption (`Commit Message:` + HTML blockquote + commit links). Do not call this 「人话说明」. **Public APK channel never receives `#update*`.**
- The second **public** metadata channel `@NixgramXMetadata` also receives APK copies (for in-app `document` download), a **「日志」** text message, and the matching `#updateRelease` / `#updateBeta` JSON. Never put that JSON into `@NixgramX`. Metadata must stay public (private breaks in-app `messages.search`). Secrets: `HELPER_BOT_TARGET=@NixgramX`, `HELPER_BOT_CANARY_TARGET=@NixgramXMetadata`.

After this configuration is merged to `main`, create the Beta branch once:

```bash
git switch main
git pull --ff-only
git switch -c beta
git push -u origin beta
```

Then publish a Beta only after its version bump and smoke test:

```bash
gh workflow run "Beta Build" --ref beta -f publish=true
```

For Stable, merge the approved promotion to `main`, create a `v<version>-<code>` tag from that exact commit, and push the tag. The release workflow uploads the Stable APK; GitHub Release notes/assets remain a separate explicit release step.

## References

- NagramX branch and CI pattern: https://github.com/risin42/NagramX
- NixgramX updater behavior: [AUTO_UPDATE.md](AUTO_UPDATE.md)
