# NGX Diagnostics research

Research date: 2026-09-16.  This document was written before implementation.  It
describes the checked-out source, not an assumption that every fork has the
same current code.

## Current NixgramX

NixgramX is based on Telegram Android `12.10.1` (`APP_VERSION_CODE=7038`),
while this checkout's product version is `12.10.1 (1283)`.  Its upstream remote
is DrKLO/Telegram; the remote has no locally fetched `master` ref, so the
version properties are the reproducible base-version evidence.

`FileLog` is the existing Telegram logging transport.  It owns the log
directory, a `DispatchQueue` named `logQueue`, application log, optional native
network log path, Tonlib log path and private MTProto request/response dump.
The MTProto dump is already private-build gated, but it can contain protocol
objects; NGX Diagnostics must never duplicate it or make it a default export.
`BuildVars.LOGS_ENABLED` is debug or the existing developer log switch.
`ProfileActivity.sendLogs` and `SettingsActivity` provide Telegram's existing
log-export/debug controls.  Crashlytics is installed by `ApplicationLoader`.

NixgramX additionally has `org.telegram.messenger.diagnostics.Diagnostics` and
`DiagnosticStore`.  It records a small, fixed Push/update/translation/media
enum to separate rotating files through its own executor, presents a copy/clear
dialog in Neko settings, and stores a redacted crash stack.  It is careful about
payloads, but it is an independent logging backend, has no action session,
structured decision fields, capture lifecycle, package export, or allowlisted
typed field API.  In particular, stable currently permits some warning writes;
that does not meet the requested default-OFF rule.

## Official Telegram

Telegram's current public `FileLog` implementation has the same core model:
one `DispatchQueue`, app file, native network path and private-build MTProto
dump.  It supports log collection through the Settings debug/send-logs flow.
The upstream model is intentionally developer-gated; protocol dumps are not a
safe general-purpose diagnostic artifact.  Official code does not provide a
general structured, privacy-allowlisted user-action correlation layer.

## Third-party implementations

* Nagram (`NextAlone/Nagram`, HEAD `b7c10df`) retains the Telegram FileLog
  model, including private-build MTProto JSON dumping and a field exclusion
  list.  This demonstrates compatibility with FileLog, but blacklist-based TL
  filtering is unsuitable for new NGX events.
* Nekogram (`Nekogram/Nekogram`, HEAD `d769499`) and its descendants keep
  Telegram-style developer log/export plumbing.  The checked-out NixgramX
  source already contains Neko settings and a number of Neko utilities; there
  is no reusable structured event package to copy.
* exteraGram (`exteraSquad/exteraGram`, HEAD `6f78031`) is an active fork, but
  its public tree did not expose a same-path `FileLog.java` at the queried
  branch.  No unsupported claim about a bespoke logger is made here.
* Forkgram (`forkgram/TelegramAndroid`, HEAD `d37ded1`) documents a privacy
  posture of no additional telemetry/crash endpoints and follows the normal
  Telegram client logging model.  This is useful confirmation that a local,
  opt-in export is a better fit than telemetry upload.

The common useful ideas are: reuse the app's existing file queue and export
flow; gate detailed protocol logs; bound retained data; and avoid remote upload.
No inspected fork supplies the required action/session/decision architecture.

## Gap analysis

NixgramX lacks all of the following as one coherent facility: OFF/DIAGNOSTIC/
TRACE configuration; a fixed allowlist of diagnostic keys; session correlation;
bounded in-memory pre-context; decision/state-transition events; capture-next-
action lifecycle; a safe zip artifact and metadata; and automated privacy
checks.  Its existing custom store duplicates IO and has legacy event callers,
so adding another store would increase, rather than reduce, risk.

## Proposed architecture

`NgxDiagnostics` is a small facade above `FileLog`, not another logger.  It
accepts only `DiagnosticField` enum keys and scalar safe values; no caller can
pass arbitrary string keys or free-form content.  It writes a stable line form:

```
[NGX]|CATEGORY|sid=AB12|event=CAN_BEGIN_SLIDE|direction=NEXT|result=false|reason=NO_TARGET
```

The facade owns a bounded synchronized ring buffer and capture/session state.
It sends persisted lines through a new, bounded NGX writer on FileLog's existing
`logQueue`; it does not add a logging executor, network sink, JSON TL dump, or
blocking UI write.  A duplicate key plus short time window suppresses repeated
identical state.  OFF returns before field allocation/formatting.

Capture uses fixed event counts rather than clock-driven background work: retain
the prior 100 safe events, create a transient random session id, collect the
next 50 events, then close automatically.  Export makes a local zip with
metadata and the NGX log; it includes existing app/network/crash files only
when explicitly present and never creates them merely for export.

First-version instrumentation must be restricted to meaningful boundaries:
existing NixgramX Push/update/translation/media/network events become
structured compatibility events; Ghost request interception records only rule,
request class/category and decision; and a targeted media gesture path records
direction/threshold/decision transitions without frame-by-frame MOVE logging.
No message, peer, URL, path, payload, or account identifier is a valid field.
