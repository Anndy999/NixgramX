# NixgramX Stability Phase v1

No new user features. Allowed work: bug/crash/ANR fixes, data correctness, Push/network,
lifecycle/memory, measured performance, upstream/Android compatibility, diagnostics,
automated and device regressions, CI/release gates. No plugin or experimental ports.

Priority: official Telegram compatibility → existing NixgramX/NagramX functionality →
crash/data correctness → FCM/Push/network → memory/lifecycle → performance → UI bugs → cleanup.
No broad rewrites, feature removal, swallowed functional exceptions, delay workarounds or
unmeasured cache/thread-pool changes.

Reproduce → logs → root cause → minimum patch → compile → automation → device → regression.
P0: startup/login failure, message loss, corruption, widespread crash, serious security.
P1: Push failure, ANR/frequent crash, deleted-history/translation/media/network failure, leaks.
P2: isolated behavior/UI/performance bug. P3: cosmetic/text.

Current base: main e1fe2dca46, Telegram 12.10.1 (7038); recent translation and Exteraless
lifecycle fixes are already integrated. Updater public metadata and release workflows exist.
See KNOWN_ISSUES.md for open risks; historical fixes belong in TEST_MATRIX.md.

## CI and Stable gate

PR XML changes trigger CI. Only pure Markdown changes are ignored. Ordinary PRs run
path-aware Quick Verify with read-only access and dummy compile-only credentials:
YAML/XML/privacy checks, host regression tests and diff check always; Java/Kotlin
compile, resources, manifest or native only when those paths change. Full Android
lint and APK assemble are not required on ordinary PRs; run Actions → Full Verify
(workflow_dispatch) for that. `upstream-sync/*` PRs keep the full upstream-sync-ci
workflow. No PR APK is published. An Android build is not feature/device validation.

Tags build artifacts but NEVER publish automatically. Stable publishing requires explicit
workflow_dispatch publish=true, the stable environment, a commit reachable from main, a
successful release build, and JSON stability_evidence for the exact SHA. Each of compile,
device, core_regression, fcm, crash_blockers, data_correctness must have status PASS and a
nonempty evidence reference; reviewer is required. FAIL/BLOCKED/NOT TESTED/missing/stale
records fail closed. Evidence is a named human attestation, not an automated device test.
Configure environment reviewers/branch protection in repository settings; YAML cannot prove
those settings exist. This phase does not merge, dispatch releases or publish any channel.

Evidence format (replace NOT TESTED only after actual verification):

```json
{"commit":"<40-character SHA>","reviewer":"<tester>","checks":{"compile":{"status":"NOT TESTED","evidence":""},"device":{"status":"NOT TESTED","evidence":""},"core_regression":{"status":"NOT TESTED","evidence":""},"fcm":{"status":"NOT TESTED","evidence":""},"crash_blockers":{"status":"NOT TESTED","evidence":""},"data_correctness":{"status":"NOT TESTED","evidence":""}}}
```

Use TEST_MATRIX.md for full per-device/flavor evidence. Current candidate is NOT Stable ready.
