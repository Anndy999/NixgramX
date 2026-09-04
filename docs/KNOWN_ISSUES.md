# Known Issues

| ID | Severity | Summary | Status |
| --- | --- | --- | --- |
| KI-001 | High | Workflow files cannot be pushed without GitHub Actions workflow OAuth/scope | Stale — workflows are on the repo |
| KI-002 | High | Real Telegram API id/hash still missing; FCM json present on sync branch | Blocked — owner must fill my.telegram.org keys in local.properties |
| KI-003 | Medium | Icons still temporary NagramX assets | Accepted for Phase 0/1 |
| KI-004 | Medium | Remote-config / updater channel ID is placeholder `0` | Framework in; switch default OFF until you own a channel |
| KI-005 | Medium | Telegram 12.10.1 sync is on `upstream-sync/12.10.1`, not `main` | In progress |
| KI-006 | Low | String resource key still named `NagramX` (value `NixgramX`) | Cosmetic |
| KI-007 | Medium | NixgramX release keystore generated (alias `nixgramx`) | Done — passwords not in git; see SIGNING.md |
| UB-1 | High | Translation bubble width after EN→ZH (replace or keep-original) | Fixed on `fix/user-requested-bugs` |
| UB-2 | High | Save deleted / edit history on full flavor | Tracked |
| UB-3 | High | Channel message menu sometimes does not open (#392) | Tracked |
| UB-4 | Medium | Attach-menu image pinch-zoom jank | Fixed on `fix/user-requested-bugs` — verify on device |
| UB-5 | Medium | 32-bit download boost ineffective (#448) | Needs armeabi-v7a APK |
