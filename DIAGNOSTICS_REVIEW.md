# NGX Diagnostics infrastructure review

Head under review is the `diagnostic/ngx-diagnostics-framework` branch of PR #76.

## Senior review

| Question | Result | Evidence |
| --- | --- | --- |
| Does OFF have near-zero overhead? | PASS | `enabled(Level)` is a volatile ordinal compare. `event()` returns before regex, formatting, or disk when OFF and not capturing. |
| Can diagnostics crash Telegram? | PASS | Core and Android facade catch at diagnostic/file/export boundaries and fail closed. Settings export/share errors show a bulletin. |
| Can disk grow without bound? | PASS | `NgxDiagnosticStore` keeps two 512 KB files (1 MB). Infrastructure test wrote 20000 lines and asserted `totalBytes <= MAX_TOTAL_BYTES`. Zips pruned to 5. |
| Can queue grow without bound? | PASS | Writer queue is `ArrayBlockingQueue(128)` with drop-on-overflow. FileLog queue is not used. |
| Can sensitive content enter events? | PASS | Typed Value API only. Names containing TOKEN/PASSWORD/USERNAME/SECRET are dropped. Dummy secrets are absent from the synthetic log. |
| Can sensitive content enter metadata? | PASS | Allowlisted keys only. `Meta.safe` rejects TOKEN/PASSWORD/USERNAME/SECRET/PHONE/`@`. No phone/account fields. |
| Can sensitive content enter export? | PASS | ZIP contains only `metadata.txt` and `ngx_diagnostics.log`. Privacy scan of both passed. |
| Can Capture get stuck? | PASS | Auto-stop after 50 events; `cancelCapture()` restores level and keeps the ring. UI disables level changes while capturing. remaining==1 cancel no longer double-finishes to OFF. |
| Can level restoration fail? | PASS | Core `finishCapture()` / `cancelCapture()` restore the pre-capture level. Tests cover OFF/DIAGNOSTIC/TRACE and remaining==1 cancel. |
| Can clear leave old disk data? | PASS | Writer epoch skips stale queued lines; `clear()` drains LineJobs then clears the sink. Test: persist old, clear, persist new. |
| Can export produce corrupt ZIP? | PASS | Write to `.tmp` then rename. Test opened the ZIP and read both entries. |
| Can concurrency deadlock? | PASS | Core methods are synchronized; store methods are synchronized; writer is a single thread. Concurrent event/capture/cancel/snapshot/append test passed. |
| Did legacy Diagnostics change? | PASS | `Diagnostics.java` / `DiagnosticStore.java` unchanged. Existing N-Settings diagnostics dialog remains. |
| Did any production behavior change? | PASS | No ChatActivity / PhotoViewer / ConnectionsManager / Ghost / translation / media instrumentation. ApplicationLoader only calls `NgxDiagnostics.init` (default OFF). |
| Does synthetic E2E prove the full pipeline? | PASS | Capture → self-test → persist → cancel → ZIP. Ordered events verified. Real output below. |

## Synthetic E2E output (from `NgxDiagnosticInfrastructureTest`)

```
[NGX]|APP|sid=0001|event=CAPTURE_START|count=0
[NGX]|APP|sid=0001|event=SELF_TEST_START
[NGX]|UI|sid=0001|event=STATE_A|result=TRUE
[NGX]|NAVIGATION|sid=0001|event=DECISION_A|result=TRUE
[NGX]|UI|sid=0001|event=STATE_B|result=TRUE
[NGX]|NAVIGATION|sid=0001|event=DECISION_B|result=TRUE
[NGX]|APP|sid=0001|event=SELF_TEST_END
```

## Adversarial review

| Finding | Severity | Resolution |
| --- | --- | --- |
| FileLog mix-in would leak NGX lines into MTProto/network logs | HIGH | Did not use FileLog. Dedicated bounded writer + independent artifact. |
| Unbounded disk if append-forever | HIGH | Two-file rotation, 1 MB cap, zip retention 5. |
| `setLevel` during capture corrupting restore | HIGH | Facade rejects `setLevel` while capturing; Settings disables the selector. |
| `REQUEST_TOKEN` field inviting auth tokens | HIGH | Already removed in Core freeze (`REQUEST_ID`). |
| Cancel capture wiping useful events | MEDIUM | `cancelCapture()` restores level but keeps the ring. `clear()` is explicit. |
| Writer overflow silently dropping lines | MEDIUM | Events may drop (bounded queue). Export must `flush()` successfully; a full queue fails export instead of writing a truncated ZIP. |
| `cancelCapture()` remaining==1 double `finishCapture()` | HIGH | remaining is zeroed before emit so record() cannot finish capture; `finishCapture()` runs once. |
| Clear vs writer race rewriting old lines | HIGH | Epoch + drain LineJobs under the same lock as append. |
| ApplicationLoader reference | LOW | Init is fail-safe, default OFF, no production behavior. |
| FileProvider `file://` fallback on API < 24 | LOW | Matches existing Telegram share pattern; min-API 24 devices use content URI. |

BLOCKER / HIGH remaining: 0.

Full Verify `lintAnalyzeDebug` can crash inside AGP's `JoinEffectDetector`
(`ThreadConstraint`). That is a lint-runtime bug, not an NGX finding. The
workaround is CI-only: Full Verify writes a disposable `TMessagesProj/lint.xml`
in the checkout. Product `build.gradle` / committed lint config do not disable
`ThreadConstraint`.

## FileLog decision

Telegram `FileLog.logQueue` is private, unbounded, and writes app/MTProto/network files. NGX diagnostics need a privacy-safe, independently exportable, size-capped artifact. A 1-thread bounded queue with drop-on-overflow is the smallest writer that cannot starve Telegram logs or mix dumps.

## Diff classification

See `git diff main...HEAD --stat` on the PR. Expected classes: CORE, BRIDGE, PERSISTENCE, SETTINGS, EXPORT, TEST, CI, DOC, LOCALIZATION. ApplicationLoader init is BRIDGE. No UNRELATED production instrumentation.
