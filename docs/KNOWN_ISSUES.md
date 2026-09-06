# Known issues

Current facts for Stability Phase v1 (baseline main e1fe2dca46). Source audit is not a device pass.
Resolved historical bugs are regression cases in TEST_MATRIX.md, not open tracked entries.

| ID | Severity | Current issue | Validation / next action |
| --- | --- | --- | --- |
| DATA-01 | P1 | main clears deleted/edit history across accounts sharing dialogId | Account filters added in this branch; host SQLite regression PASS; Android/device pending |
| DATA-02 | P1 | Deleted-message indexes are non-unique; concurrent insert/edit/delete and restart/migration behavior unverified | Stress Room with two accounts, bulk operations and migration fixtures before schema changes |
| DATA-03 | P2 | Bulk delete queries media after deleting its record, so cleanup can miss files | Source confirmed; defer media deletion change until shared-reference ownership tested |
| PUSH-01 | P1 | Real Telegram → FCM → device delivery not verified for this candidate | Test login/registeredForPush, locked/background/reboot, network/proxy switches and battery saver |
| PUSH-02 | P1 | External provider can leave stale local service/alarm running on main | Service/alarm cleanup and restart guards added; device transition tests pending |
| PUSH-03 | P1 | Fallback repeating alarm uses broadcast PendingIntent targeting a Service class | Source audit only; correct ROM/background scheduling needs device evidence, not a blind PendingIntent swap |
| CHAT-01 | P2 | Channel message long-press sometimes fails to open | No reproducible trace; preserve Telegram permission/selection conditions; collect channel role, message type and state sequence |
| COMPAT-01 | P2 | Custom app passkeys require telegram.org DAL trust unavailable to this package/signature | Existing phone/QR fallback; see PASSKEYS.md |
| COMPAT-02 | P2 | 32-bit download boost behavior unverified | Needs armeabi-v7a device/APK |
| CI-01 | P1 | Android lint internally crashes in Kotlin/FIR analysis of TranslateController / UElementAsPsiDetector | Earlier local run FAIL; retain lint gate, final isolated run in FINAL_REPORT.md |
| MEM-01 | P1 | Remaining lifecycle paths and existing #27 fixes lack heap/device regression evidence | No additional leak proven; repeat detach/open/close/rotation tests |
| DIAG-01 | P2 | Native fatal signals/OS kills are outside Java uncaught exception handler | Java last-crash only; no claim of native/ANR capture |

No new P0 runtime issue proven by this audit. main Push logs exposed tokens/payload and push
auth key under logging conditions; sensitive outputs removed in this branch. Existing general
Telegram FileLog/Crashlytics is separate from local diagnostics and has not received a full
repository privacy audit. Do not export legacy logs as if sanitized diagnostics.

Telegram 12.10.1 is on main; updater is configured; workflows are pushable and present;
signing is configured (secret validity unverified). No placeholder/In progress assertions remain.
