# Regression matrix

Current candidate: Stability Phase v1, based on main e1fe2dca46 (Telegram 12.10.1 / 7038).
This is a reusable execution template, not evidence of device success.

Only PASS, FAIL, BLOCKED, NOT TESTED are allowed. For each release copy this matrix to an evidence report and record exact SHA, APK SHA256, full/_base, device/model, Android/ROM, date, tester, steps, observed result and log reference. Use separate rows per device and flavor; `_base` Save Deleted checks verify the feature is unavailable by design, not skipped.

Every official major-version sync must repeat ALL historical R cases, core messaging/login, data and Push cases. Reset statuses for every new candidate; never inherit PASS from an old APK.
Stable requires all critical cases PASS; FAIL, BLOCKED and NOT TESTED block publication. Beta may retain explicitly documented NOT TESTED, but this phase publishes nothing.

## Basic

| Case | Status | Evidence / expected observation |
| --- | --- | --- |
| Cold start | NOT TESTED | Record steps, expected/actual result and device evidence |
| Warm start | NOT TESTED | Record steps, expected/actual result and device evidence |
| App resume | NOT TESTED | Record steps, expected/actual result and device evidence |
| Account login | NOT TESTED | Record steps, expected/actual result and device evidence |
| QR login | NOT TESTED | Record steps, expected/actual result and device evidence |
| 2FA | NOT TESTED | Record steps, expected/actual result and device evidence |
| Logout | NOT TESTED | Record steps, expected/actual result and device evidence |
| Multi-account switch | NOT TESTED | Record steps, expected/actual result and device evidence |

## Messaging

| Case | Status | Evidence / expected observation |
| --- | --- | --- |
| Private send text | NOT TESTED | Record steps, expected/actual result and device evidence |
| Private receive text | NOT TESTED | Record steps, expected/actual result and device evidence |
| Group send | NOT TESTED | Record steps, expected/actual result and device evidence |
| Group receive | NOT TESTED | Record steps, expected/actual result and device evidence |
| Channel | NOT TESTED | Record steps, expected/actual result and device evidence |
| Reply | NOT TESTED | Record steps, expected/actual result and device evidence |
| Forward | NOT TESTED | Record steps, expected/actual result and device evidence |
| Edit | NOT TESTED | Record steps, expected/actual result and device evidence |
| Delete | NOT TESTED | Record steps, expected/actual result and device evidence |
| Long-press message menu | NOT TESTED | Record steps, expected/actual result and device evidence |
| Message selection | NOT TESTED | Record steps, expected/actual result and device evidence |

## Translation

| Case | Status | Evidence / expected observation |
| --- | --- | --- |
| EN → ZH | NOT TESTED | Record steps, expected/actual result and device evidence |
| ZH → EN | NOT TESTED | Record steps, expected/actual result and device evidence |
| Show original | NOT TESTED | Record steps, expected/actual result and device evidence |
| Keep original | NOT TESTED | Record steps, expected/actual result and device evidence |
| Manual translation | NOT TESTED | Record steps, expected/actual result and device evidence |
| Automatic translation | NOT TESTED | Record steps, expected/actual result and device evidence |
| Quoted message translation | NOT TESTED | Record steps, expected/actual result and device evidence |
| Caption translation | NOT TESTED | Record steps, expected/actual result and device evidence |
| Long text | NOT TESTED | Record steps, expected/actual result and device evidence |
| Multiline text | NOT TESTED | Record steps, expected/actual result and device evidence |
| Timestamp | NOT TESTED | Record steps, expected/actual result and device evidence |
| Translation badge | NOT TESTED | Record steps, expected/actual result and device evidence |

## Deleted Messages

| Case | Status | Evidence / expected observation |
| --- | --- | --- |
| Deleted text | NOT TESTED | Record steps, expected/actual result and device evidence |
| Deleted photo | NOT TESTED | Record steps, expected/actual result and device evidence |
| Deleted video | NOT TESTED | Record steps, expected/actual result and device evidence |
| Deleted file | NOT TESTED | Record steps, expected/actual result and device evidence |
| Deleted album | NOT TESTED | Record steps, expected/actual result and device evidence |
| Edit then delete | NOT TESTED | Record steps, expected/actual result and device evidence |
| Multiple edits | NOT TESTED | Record steps, expected/actual result and device evidence |
| Group | NOT TESTED | Record steps, expected/actual result and device evidence |
| Channel | NOT TESTED | Record steps, expected/actual result and device evidence |
| Restart recovery | NOT TESTED | Record steps, expected/actual result and device evidence |
| Multi-account isolation | NOT TESTED | Record steps, expected/actual result and device evidence |

## Media

| Case | Status | Evidence / expected observation |
| --- | --- | --- |
| PhotoViewer | NOT TESTED | Record steps, expected/actual result and device evidence |
| Album | NOT TESTED | Record steps, expected/actual result and device evidence |
| Attach picker | NOT TESTED | Record steps, expected/actual result and device evidence |
| Multiple photos | NOT TESTED | Record steps, expected/actual result and device evidence |
| Video playback | NOT TESTED | Record steps, expected/actual result and device evidence |
| Seek | NOT TESTED | Record steps, expected/actual result and device evidence |
| GIF | NOT TESTED | Record steps, expected/actual result and device evidence |
| Audio | NOT TESTED | Record steps, expected/actual result and device evidence |
| Voice | NOT TESTED | Record steps, expected/actual result and device evidence |
| File download | NOT TESTED | Record steps, expected/actual result and device evidence |
| File send | NOT TESTED | Record steps, expected/actual result and device evidence |

## Push

| Case | Status | Evidence / expected observation |
| --- | --- | --- |
| FCM token | NOT TESTED | Record steps, expected/actual result and device evidence |
| registeredForPush | NOT TESTED | Record steps, expected/actual result and device evidence |
| Foreground message | NOT TESTED | Record steps, expected/actual result and device evidence |
| Background message | NOT TESTED | Record steps, expected/actual result and device evidence |
| Lock-screen message | NOT TESTED | Record steps, expected/actual result and device evidence |
| Swipe away recent task | NOT TESTED | Record steps, expected/actual result and device evidence |
| Device reboot | NOT TESTED | Record steps, expected/actual result and device evidence |
| Wi-Fi → Cellular | NOT TESTED | Record steps, expected/actual result and device evidence |
| Cellular → Wi-Fi | NOT TESTED | Record steps, expected/actual result and device evidence |
| Proxy switch | NOT TESTED | Record steps, expected/actual result and device evidence |
| Multi-account | NOT TESTED | Record steps, expected/actual result and device evidence |
| Battery saver | NOT TESTED | Record steps, expected/actual result and device evidence |
| UnifiedPush | NOT TESTED | Record steps, expected/actual result and device evidence |
| In-App Push | NOT TESTED | Record steps, expected/actual result and device evidence |
| Keep Alive provider switch | NOT TESTED | Record steps, expected/actual result and device evidence |

## UI

| Case | Status | Evidence / expected observation |
| --- | --- | --- |
| Light theme | NOT TESTED | Record steps, expected/actual result and device evidence |
| Dark theme | NOT TESTED | Record steps, expected/actual result and device evidence |
| AMOLED | NOT TESTED | Record steps, expected/actual result and device evidence |
| Liquid Glass | NOT TESTED | Record steps, expected/actual result and device evidence |
| Light wallpaper | NOT TESTED | Record steps, expected/actual result and device evidence |
| Dark wallpaper | NOT TESTED | Record steps, expected/actual result and device evidence |
| ChatAttachAlert | NOT TESTED | Record steps, expected/actual result and device evidence |
| PhotoViewer transition | NOT TESTED | Record steps, expected/actual result and device evidence |
| 120Hz | NOT TESTED | Record steps, expected/actual result and device evidence |

## Compatibility

| Case | Status | Evidence / expected observation |
| --- | --- | --- |
| Android 14 | NOT TESTED | Record steps, expected/actual result and device evidence |
| Android 15 | NOT TESTED | Record steps, expected/actual result and device evidence |
| Android 16 | NOT TESTED | Record steps, expected/actual result and device evidence |
| Samsung One UI | NOT TESTED | Record steps, expected/actual result and device evidence |

## Diagnostics

| Case | Status | Evidence / expected observation |
| --- | --- | --- |
| Copy local diagnostics | NOT TESTED | Record steps, expected/actual result and device evidence |
| Clear diagnostics | NOT TESTED | Record steps, expected/actual result and device evidence |
| Crash then restart and copy | NOT TESTED | Record steps, expected/actual result and device evidence |
| Crash original handler chain | NOT TESTED | Record steps, expected/actual result and device evidence |
| Rotation and full disk | NOT TESTED | Record steps, expected/actual result and device evidence |
| No sensitive data in export | NOT TESTED | Record steps, expected/actual result and device evidence |
| Push timestamp changes only on receipt | NOT TESTED | Record steps, expected/actual result and device evidence |

## Permanent historical regressions

| ID | Bug | Origin | Status | Procedure / assertion |
| --- | --- | --- | --- | --- |
| R01 | Original/translation glyph overlap | PR #25, #28 follow-up | NOT TESTED | Toggle original/translation repeatedly with visible translate bar; no overlaid glyphs. |
| R02 | Multiple translated bubbles slide/overlap | PR #26 | NOT TESTED | Translate a viewport of differently sized messages and scroll; bubbles keep separate bounds. |
| R03 | Translation badge and timestamp overlap | PR #20 | NOT TESTED | Toggle short/long/multiline translations and Keep original; badge/time remain readable. |
| R04 | Light wallpaper attach album jank | PR #17, #23 supersedes interim drawing workarounds | NOT TESTED | Group/channel light wallpaper; open album, scroll, multiselect; capture frame timing. |
| R05 | PhotoViewer liquid-glass transition | PR #12/#23; verify history and device | NOT TESTED | Open/close photos through attach picker with glass on; no frozen/incorrect blur. |
| R06 | Dark glass fringe | PR #19/#22/#23 | NOT TESTED | Dark/AMOLED attach header and tabs; no bright edge. |
| R07 | Rename reverts after account switch | 876aa5a1b1 (#14) | NOT TESTED | Rename, switch accounts twice, restart; name persists independently. |
| R08 | Updater metadata search failure | PR #16/#21 | NOT TESTED | Manual update check against public metadata; distinguish search failure from no update. |
| R09 | Updater incorrectly says latest | PR #16/#18/#21 | NOT TESTED | Test remote version newer/equal/older, malformed metadata and network failure. |
| R10 | Attach camera permission jank | 42e1f64930 (#17), d22cad8565 (#23) | NOT TESTED | Denied/granted camera permission, repeated picker open; collect frame timing. |
| R11 | Deleted/edit history cross-account clear | Stability Phase v1 | NOT TESTED | Two accounts in same channel, clear one; other account records and media remain. Repeat merged group and selected bulk delete. |

## Automated checks

| Check | Status | Evidence |
| --- | --- | --- |
| SQLite account isolation queries | PASS | Tools/stability/test_account_isolation.py; local host SQLite, not Android Room |
| Stable evidence validation | PASS | Tools/stability/test_release_gate.py |
| Logger privacy, rotation, crash cap, clear | PASS | Tools/stability/DiagnosticStoreTest.java; host JVM |
| YAML / XML / privacy source guards | PASS | Tools/stability/static_checks.py |
| Gradle configuration | PASS | Local ./gradlew help |
| Android Java/Kotlin/resources/manifest | NOT TESTED | See FINAL_REPORT.md for final build outcome |
| arm64 Debug APK | NOT TESTED | See FINAL_REPORT.md for final build outcome |
| Real device / FCM end-to-end | NOT TESTED | No device attached |
