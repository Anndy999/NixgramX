# Updater V2 — Beta review only

Base: beta `12fe9bfc17ccd5dddb722355c4d490cd671c6428`, 12.10.1 (1284).
No version bump, main merge, Stable release or UI geometry changes in this PR.

## Transport and state

`checkNewVersionAvailable` snapshots account/lane → resolves the public
`NixgramXMetadata` channel → `channels.getMessages` of the lane's permanent ID →
validates metadata before comparison → existing APK/changelog getMessages path.
Normal V2 performs zero messages.search requests. There is no search fallback,
silent delayed recheck, sleep, join or change to automatic-check scheduling.
Other BaseRemoteHelper consumers retain their existing bounded search behavior.

Malformed/missing metadata and RPC errors report errors, preserving pending state.
Every completion is generation-checked on its consumer queue. UI consumers must
write synchronously inside that callback, not enqueue another pending write.
FileRefController retains stage-queue delivery for its account-owned maps.

## Publisher and one-time setup

Permanent IDs live in gradle.properties; BuildConfig and the publisher read the
same properties. Zero deliberately fails closed and is NOT a deployable V2 setup.

Verified initialization run: https://github.com/Anndy999/NixgramX/actions/runs/34190498353
Beta pointer: https://t.me/NixgramXMetadata/65 (1284).
Release pointer: https://t.me/NixgramXMetadata/66 (1278).
Both were created, edited and read back by the existing publisher. No new APK or
legacy metadata was published in this initialization. Do not recreate these IDs.

The existing HELPER_BOT_TOKEN identity creates, edits and reads back pointers.
Beta Build's `initialize_pointers=true, publish=false` mode builds no APK and
publishes no new version. Supply reviewed legacy message IDs and expected codes.
Audited successful logs: Beta 1284 message 64 (run 34104482460); Release 1278
message 25 (run 34031293431). Recheck these if newer publication occurs.

Initialization validates referenced documents, copies existing metadata without a
search hashtag, performs a real edit and verifies the exact returned text. Copy
the printed VERIFIED IDs into the two properties before building V2. If creation
succeeds but edit/readback fails, STOP and retain the printed CREATED ID; do not
rerun blindly or create duplicate pointers. Existing configured IDs are reused.

Normal publishing validates the configured channel, uploads APKs and changelog,
publishes legacy #updateBeta/#updateRelease, then edits the permanent pointer LAST.
All publishing and initialization jobs share a concurrency lock. Missing referenced
messages or a backwards version/timestamp fail without advancing the pointer.
Do not delete permanent pointers or change publisher identity without an edit test.

## Verification and limits

Host tests execute production BaseRemoteHelper/UpdateHelper with queued RPC and
Android/JSON boundary stubs. They cover version comparison, lane/account snapshots,
pending-state errors, overlapping and duplicate completions, universal-only maps,
zero search and zero delayed jobs. The JSON stub does not test Android's parser.
Python publisher tests execute real validation/bootstrap/edit methods and upload's
main function with simulated Telegram operations, including earlier-step failures.
Quick Verify/Android compile and device results must be recorded from actual runs.

## Review questions

- Actual path? Manual/automatic callers both enter UpdateHelper.load; fixed-ID
  dispatch is before the search construction. FileRef refresh uses the same V2 path.
- Root cause? Direct message IDs remove dependence on Telegram search indexing.
- Revert consequence? Discovery again depends on search propagation and delayed retry.
- Necessary scope? Updater, its pending-state consumers, publisher, build-time IDs,
  directly related Beta setup/artifact gate and publisher serialization only.

## Version-only acceptance test (not publication approval)

Build a V2-enabled 1284 Beta baseline first. On a separate test branch change only
NIXGRAMX_VERSION_CODE to 1285. Dispatch Beta Build with publish=false for both
arm64-v8a and universal artifacts; branch artifact builds do not permit publication.
Keep versionName 12.10.1. Do NOT publish/upload 1285 or edit either lane pointer
until the owner confirms: `1284 V2 已安装，可以发布 1285`.
The 1285 code diff from the reviewed V2 baseline must be exactly that one property.
