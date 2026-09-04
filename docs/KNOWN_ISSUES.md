# Known Issues

| ID | Severity | Summary | Status |
| --- | --- | --- | --- |
| KI-001 | High | Workflow files cannot be pushed without GitHub Actions workflow OAuth/scope | Stale — workflows are on the repo |
| KI-002 | High | Real Telegram API id/hash still missing; FCM json present on sync branch | Blocked for quality login builds |
| KI-003 | Medium | Icons still temporary NagramX assets | Accepted for Phase 0/1 |
| KI-004 | Medium | Remote-config channel ID is placeholder `0` | By design Day-1 |
| KI-005 | Medium | Telegram 12.10.1 sync is on `upstream-sync/12.10.1`, not `main` | In progress |
| KI-006 | Low | String resource key still named `NagramX` (value `NixgramX`) | Cosmetic |
| KI-007 | Medium | `release.keystore` from upstream bootstrap must be replaced | Required before public release |
| UB-1 | High | Translation / LLM translate menu after 12.10.1 sync | Tracked — see `USER_REQUESTED_BUGFIXES.md` |
| UB-2 | High | Save deleted / edit history on full flavor | Tracked |
| UB-3 | High | Channel message menu sometimes does not open (#392) | Tracked |
| UB-4 | Medium | Attach-menu image pinch-zoom jank | Needs APK + device |
| UB-5 | Medium | 32-bit download boost ineffective (#448) | Needs armeabi-v7a APK |
