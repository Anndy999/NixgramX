# Upstream sync automation change report

- Feature branch: `feature/automated-upstream-sync`, created from `origin/beta`.
- PR base: `beta`. No merge, release, APK publication, or protected-branch push performed.
- Modified: `.github/workflows/upstream-watch.yml`, `.github/workflows/pr.yml`, `docs/UPSTREAM_SYNC.md`.
- Added: `.github/workflows/upstream-sync-ci.yml`, `.github/scripts/upstream_sync.py`, `.github/scripts/test_upstream_sync.py`, `docs/upstream-base.json`, this report.

## Behavior

Hourly official master version-subject detection replaces the 12-hour Issue-only
watcher. Manual dispatch remains. Ordinary commits never generate PRs. The latest
formal version was queried read-only during implementation: 12.10.1 (7038), commit
62b56a07ca7e30e39f7fd00a6728d6bbd716ca1c, matching the integrated base; therefore no
new Telegram sync PR was needed during implementation.

New versions create `upstream-sync/<version>` from beta, falling back to main,
and a draft `sync: Telegram <version> (<build>)` PR. Only that new ref can be
pushed, with a create-only lease. Official old-to-new full-index binary deltas
are applied per file using three-way Git application. Pure Telegram version
properties and unchanged-local gitlinks can update automatically. Protected
identity/build paths and unsafe conflicts retain local files and get official
patch archives/TODOs. No unresolved adaptation is claimed complete.

Dedup checks version, base commit, synced commits, branch and all open/closed PR
pages. Existing prepared branches can recover from PR creation failure; other
existing branches are never overwritten. New patches supersede older open
bot-generated PRs in the same major/minor line, closing them only after creating
the replacement. Old branches remain available to recover human adaptations.

PR reports include From/To, changed/clean/conflict paths, dependency/submodule
changes, high-risk paths, preservation notes, checks and manual verification.
Untested checks say Not tested; semantic preservation is not falsely certified.
Large reports remain complete in the branch with a compact PR body and link.

The watcher explicitly calls dedicated read-only CI on the prepared SHA, so
GITHUB_TOKEN event suppression cannot leave a newly created sync PR unbuilt.
Subsequent human PR updates run the same CI. Checks cover Gradle configuration,
Java/Kotlin compilation, resources, manifests, arm64 Debug APK, native build and
pending-adaptation gate. Errors stay in CI logs/summary and leave the PR open.
No secrets or APK upload in sync CI. Universal/device checks: Not tested.

## Verification actually performed

- All seven workflow YAML files parsed successfully with PyYAML BaseLoader.
- actionlint 1.7.7 passed all three changed/new workflows.
- Three Python regression tests passed, including a real temporary repository
  with unrelated histories, clean delta application, conflicting high-risk code,
  identity preservation, locally modified deletion protection, locally removed
  file protection, pure version updates and gitlink updates.
- Mocked orchestration tested same-version PR/commit dedup and API failure before
  any Git mutation; formal-version filtering and numeric ordering tested.
- `git diff --check` passed.
- Static review: hourly cron, serialization, beta/main read-only behavior,
  create-only ref push, no merge/release calls, explicit API failures, no CI secrets.
- GitHub Actions create-PR permission was enabled and read back successfully;
  default workflow permissions remain read-only. GitHub combines create/approve
  capability in this setting; this implementation never approves PRs.

## Not tested / deployment

No upstream-watch or sync CI workflow was manually executed. Android/Gradle/native
builds and device checks were not run locally; build results are Not tested.
Live creation/recovery/supersession of automated Telegram PRs was not exercised
against GitHub; these paths received static review and partial mocked coverage.
The implementation PR creation/push is separate from running Telegram sync.

Owner review is required. Automation files must reach default branch **main**
for the hourly schedule, and beta for the recorded baseline/build configuration.
This feature PR targets beta as requested and is not automatically merged or
promoted to main. High-risk/semantic conflicts, build failures, useful edits from
superseded PRs, base metadata finalization and device verification remain for
human/Codex follow-up. Final state: **PR waiting for review**.
