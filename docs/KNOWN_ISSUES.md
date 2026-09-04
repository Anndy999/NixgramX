# Known Issues

| ID | Severity | Summary | Status |
| --- | --- | --- | --- |
| KI-001 | High | Workflow files cannot be pushed without GitHub Actions workflow OAuth/scope | Blocked — workflows kept local |
| KI-002 | High | Real Telegram / Firebase / Maps credentials not yet provisioned | Blocked for FCM/Maps/login-quality builds |
| KI-003 | Medium | Icons still temporary NagramX assets | Accepted for Phase 0/1 |
| KI-004 | Medium | Remote-config channel ID is placeholder `0` — in-app updater inert until owned channel configured | By design Day-1 |
| KI-005 | Medium | Deep local diff of `a6c7d0a` / Telegram 12.10.1 not applied yet | Tracked in UPSTREAM_AUDIT / UPSTREAM_SYNC |
| KI-006 | Low | String resource key still named `NagramX` (value `NixgramX`) to minimize churn | Cosmetic |
| KI-007 | Medium | `release.keystore` from upstream bootstrap must be replaced before public release | Required |
