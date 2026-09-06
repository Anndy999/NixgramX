# Telegram Upstream Sync

## Current Telegram base

The integrated Telegram base is **12.10.1 (7038)**,
`62b56a07ca7e30e39f7fd00a6728d6bbd716ca1c`. This is already in the
NixgramX beta/main history, with subsequent NixgramX fixes. The old “L3 Adapt
in progress” narrative is historical, not the current automation state.

The machine-readable source of truth is [upstream-base.json](upstream-base.json).
`base` and `synced_commits` describe integrated deltas; `pending` explicitly
blocks claiming an incomplete adaptation as the new base. APP_VERSION_NAME
must agree with that base. NIXGRAMX_VERSION_* remains owner-managed.

## Workflow and automation level

`upstream-watch.yml` runs hourly at minute 17 (GitHub schedules may be delayed)
and supports manual dispatch on the default branch. It scans official
DrKLO/Telegram master for exact `update to x.y.z (build)` subjects, up to 2,000
commits; ordinary commits do not create PRs. API/fetch/permission errors are
reported as failed Actions runs before proceeding, never interpreted as a new
version or empty PR list.

Flow: Telegram version → detect → beta (fallback main) →
`upstream-sync/<version>` → old-to-new upstream delta → three-way adaptation →
draft PR → arm64 CI → **PR waiting for review**.

This is **L2 automated preparation with L3 human/Codex adaptation and L4 CI/manual
review**. Mechanical three-way success does not establish feature equivalence.
No workflow here merges beta/main, publishes an APK, or creates a Release.

The delta engine uses full-index binary patches per path, with the official old
blob as the three-way base; it does not merge unrelated repository histories.
Clean updates and unchanged-local submodule gitlinks apply automatically.
Conflicts, local deletions, and protected identity/build/layout files stay
unchanged, with the official delta archived in `docs/upstream-sync-todo/` and
explicit TODOs in `docs/upstream-sync-report.md`. Gradle/dependency conflicts
require review; no guessing of equivalent dependency versions. Upstream
renames are treated as deletion/addition so local edits cannot silently vanish.
High-risk touched paths are listed even when the patch applies cleanly.

Preserve app.nixgramx.android, NixgramX branding, Firebase/signing configuration,
API injection, updater/channel settings, NagramX hooks and stability fixes.
For unresolved changes, adapt the new official implementation and re-weave local
features. Do not replace new Telegram files wholesale with old NagramX files,
remove features, suppress exceptions or comment out functionality to compile.
After resolving all TODOs, update `base`, append the target to `synced_commits`,
clear `pending`, and reconcile APP_VERSION_NAME/APP_VERSION_CODE. CI fails its
adaptation gate until this is done. The owner must still verify behavior.

## Deduplication, recovery and supersession

A serialized workflow checks base version/commit, synced commits, existing
remote branches and all open/closed PR pages. The same version is never recreated,
including a deliberately closed PR. A prepared branch without a PR is recovered
only when its recorded target and base match; foreign/existing branches are
never overwritten. Push uses a create-only lease with an empty expected value;
it cannot rewrite any existing remote ref. Only upstream-sync refs are pushed.

A newer patch in the same major/minor line creates a fresh PR from current beta,
explicitly naming the older open bot-generated PRs it supersedes. Only after the
new PR exists are those older PRs closed. Branches remain recoverable. Reviewers
must port useful edits from superseded PRs; automatic semantic transfer is not
claimed. Different minor/major version PRs remain for explicit owner decisions.
Concurrent branch creation or API failures stop safely; rerun to recover.

## CI and review

The watcher directly calls `upstream-sync-ci.yml` on the exact prepared SHA,
because PRs created with GITHUB_TOKEN do not trigger ordinary pull_request runs.
Later human pushes/reopens trigger the same dedicated CI through pull_request.
No path filter skips resource-only changes. Generic PR CI skips upstream-sync
branches to avoid duplicate builds; ordinary PRs now reuse this same secret-free compile workflow.

Checks: YAML/XML/privacy and host regressions, git diff check, Android lint,
Gradle configuration, Java/Kotlin compileDebugSources, resource merge,
manifest merge, externalNativeBuildDebug, arm64-v8a assembleDebug, adaptation gate.
Each build step records Passed / Failed / Not tested in the Actions summary. Failures
keep the PR open. The initial PR report says Not tested and links to the actual
run. Debug signing is changed only in the disposable CI checkout; credentials
are dummy compile-only values and no APK is uploaded. Universal and device
checks are Not tested. CI has read-only contents permission and no secrets.

Manually verify Login, Messaging, Translation, Deleted Messages, Media, Push/FCM,
Proxy/network, Notification and Multi-account before accepting the sync.

## Deployment and recent sync

Latest integrated sync: Telegram 12.10.1 (7038), release candidate merged as
[PR #4](https://github.com/Anndy999/NixgramX/pull/4); subsequent fixes are separate.
This automation change itself does not perform a new Telegram sync.

Current pending upstream PRs: use the live
[open upstream PR list](https://github.com/Anndy999/NixgramX/pulls?q=is%3Apr+is%3Aopen+head%3Aupstream-sync%2F).
Each generated branch holds its own report and prepared target; do not treat
this document as a frozen list of open PR numbers.

The automation is already integrated into **main** and beta. GitHub only
schedules workflows present on the default branch. Enable Settings → Actions →
General → “Allow GitHub Actions to create and approve pull requests”. The watcher
needs contents:write and pull-requests:write; it does not approve PRs. No PAT,
bot token, Firebase secret, or signing secret is required. The former upstream-watch
Issue is no longer the endpoint; reports live in the PR and CI.

Stability Phase v1: after every official major sync, reset and execute all permanent
R-series cases in TEST_MATRIX.md; compilation alone cannot satisfy Stable evidence.
