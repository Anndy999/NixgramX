# NGX Diagnostics research

Research date: 2026-09-16. This document describes the checked-out source and
the frozen NGX Diagnostics Core semantics implemented in this PR.

## Current NixgramX

NixgramX is based on Telegram Android `12.10.1` (`APP_VERSION_CODE=7038`),
while this checkout's product version is `12.10.1 (1283)`. Its upstream remote
is DrKLO/Telegram; the remote has no locally fetched `master` ref, so the
version properties are the reproducible base-version evidence.

`FileLog` is the existing Telegram logging transport. It owns the log
directory, a `DispatchQueue` named `logQueue`, application log, optional native
network log path, Tonlib log path and private MTProto request/response dump.
The MTProto dump is already private-build gated, but it can contain protocol
objects; NGX Diagnostics must never duplicate it or make it a default export.
`BuildVars.LOGS_ENABLED` is debug or the existing developer log switch.
`ProfileActivity.sendLogs` and `SettingsActivity` provide Telegram's existing
log-export/debug controls. Crashlytics is installed by `ApplicationLoader`.

NixgramX additionally has `org.telegram.messenger.diagnostics.Diagnostics` and
`DiagnosticStore`. It records a small, fixed Push/update/translation/media
enum to separate rotating files through its own executor, presents a copy/clear
dialog in Neko settings, and stores a redacted crash stack. It is careful about
payloads, but it is an independent logging backend, has no action session,
structured decision fields, capture lifecycle, package export, or allowlisted
typed field API. In particular, stable currently permits some warning writes;
that does not meet the requested default-OFF rule.

## Official Telegram

Telegram's current public `FileLog` implementation has the same core model:
one `DispatchQueue`, app file, native network path and private-build MTProto
dump. It supports log collection through the Settings debug/send-logs flow.
The upstream model is intentionally developer-gated; protocol dumps are not a
safe general-purpose diagnostic artifact. Official code does not provide a
general structured, privacy-allowlisted user-action correlation layer.

## Third-party implementations

* Nagram (`NextAlone/Nagram`, HEAD `b7c10df`) retains the Telegram FileLog
  model, including private-build MTProto JSON dumping and a field exclusion
  list. This demonstrates compatibility with FileLog, but blacklist-based TL
  filtering is unsuitable for new NGX events.
* Nekogram (`Nekogram/Nekogram`, HEAD `d769499`) and its descendants keep
  Telegram-style developer log/export plumbing. The checked-out NixgramX
  source already contains Neko settings and a number of Neko utilities; there
  is no reusable structured event package to copy.
* exteraGram (`exteraSquad/exteraGram`, HEAD `6f78031`) is an active fork, but
  its public tree did not expose a same-path `FileLog.java` at the queried
  branch. No unsupported claim about a bespoke logger is made here.
* Forkgram (`forkgram/TelegramAndroid`, HEAD `d37ded1`) documents a privacy
  posture of no additional telemetry/crash endpoints and follows the normal
  Telegram client logging model. This is useful confirmation that a local,
  opt-in export is a better fit than telemetry upload.

The common useful ideas are: reuse the app's existing file queue and export
flow; gate detailed protocol logs; bound retained data; and avoid remote upload.
No inspected fork supplies the required action/session/decision architecture.

## Gap analysis

NixgramX lacks all of the following as one coherent facility: OFF/DIAGNOSTIC/
TRACE configuration; a fixed allowlist of diagnostic keys; session correlation;
decision/state-transition events; capture-next-action lifecycle; a safe zip
artifact and metadata; and automated privacy checks. Its existing custom store
duplicates IO and has legacy event callers, so adding another store would
increase, rather than reduce, risk.

This Core freeze is paired with an Android infrastructure layer in the same PR:
`NgxDiagnostics` facade, bounded local persistence, Settings/Capture/Export,
and a developer-only synthetic self-test. Production Telegram paths are not
instrumented.

## Frozen Core semantics

`NgxDiagnosticCore` is an Android-free in-memory state machine. Production
callers must keep the OFF fast path:

```
if (!core.enabled(Level.DIAGNOSTIC)) {
    return;
}
```

`enabled(null)` is false and never throws. When OFF and no capture is active,
`event()` returns immediately: no ring update, no formatting, no regex, no
session generation, no persistence, no pre-capture history.

### OFF

Truly disabled. OFF retains no pre-capture diagnostic history. This is a
deliberate performance and privacy trade-off.

### Capture

Capture starts a bounded diagnostic session and records the next 50 meaningful
events. It does not replay or retain prior events.

* `CAPTURE_EVENTS = 50` means 50 diagnostic events after `CAPTURE_START`.
* `CAPTURE_START` is recorded and does not consume `remaining`.
* Capture from OFF temporarily enables DIAGNOSTIC.
* Capture from DIAGNOSTIC or TRACE keeps that configured level during capture.
* After 50 following events, capture finishes, the original level is restored,
  and the capture session id is cleared.
* A second `capture()` while one is active is rejected and must not replace
  `restore`.
* `clear()` during an active capture aborts capture and restores the original
  level.

### Ring

`RING_LIMIT = 100`. The ring stores only events produced while diagnostics or
capture is active. Oldest events are dropped; latest events are preserved.

### Session

* `begin()` emits `SESSION_START` with a new sid.
* A second `begin()` replaces the current session id. It does not emit
  `SESSION_END` for the replaced session.
* `end()` emits `SESSION_END` with the current sid, then always sets `sid` to
  null, even if emit fails.
* After `end()`, later events use `sid=NONE`.

### Clear

Normal clear (no active capture): ring, dedupe, session, and dropped counter
are reset. The configured diagnostic level is unchanged.

Clear during capture: abort capture, restore the pre-capture level, clear
session, `remaining = 0`, dropped = 0.

### Privacy

Values are typed only: boolean, int, long, Enum. There is no `Value.string`
and no arbitrary runtime string pairs. Event names are constant-style
`[A-Z0-9_]+`. Obviously sensitive names (`TOKEN`, `PASSWORD`, `USERNAME`,
`SECRET`) are rejected. The allowlisted field for Telegram request correlation
is `REQUEST_ID`, not `REQUEST_TOKEN`.

### Fail-safe

Diagnostics may fail and may drop events. They must never crash NixgramX.
No public Core API throws because of diagnostic-only input.

## Infrastructure (this PR)

`NgxDiagnostics` is the Android facade over Core. Persistence is a dedicated
bounded writer (`current.log` / `previous.log`, 512 KB each, 1 MB total) on a
128-deep drop-on-overflow queue. FileLog is not used: its queue is private,
unbounded, and mixed with MTProto/network dumps.

Settings live under N-Settings as a developer page: level, capture, cancel,
export ZIP, clear, and self-test. Export is a local share-sheet ZIP with
allowlisted metadata plus `ngx_diagnostics.log`. No Telegram Send Logs
replacement, no remote upload.

Production instrumentation of ChatActivity / PhotoViewer / ConnectionsManager /
Ghost / translation / media is out of scope.
