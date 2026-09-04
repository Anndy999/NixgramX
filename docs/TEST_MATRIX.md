# Test Matrix

| Area | Full | `_base` | CI | Device | Notes |
| --- | --- | --- | --- | --- | --- |
| Assemble release arm64 | Required | Required (Release path) | Planned | — | `_base` via `NIXGRAMX_BASE=true` |
| applicationId install side-by-side | Required | Required | — | Required | `app.nixgramx.android` vs `.base` |
| Login / send message | Required | Required | — | Required | Needs real `TELEGRAM_APP_ID/HASH` |
| FCM push | Required | Optional | — | Required | Needs real `google-services.json` |
| In-app updater | Skip until metadata channel owned | Skip | — | — | Channel ID placeholder |
| Ghost Mode absent | Required | Required | — | Required | Policy |
| Save Deleted available | Required ON path | Required OFF | — | Required | |
| Settings import/export | Required | Required | — | Preferred | Migration aid |
| Crash smoke (cold start) | Required | Required | Planned | Required | |

Statuses: `pass` / `fail` / `blocked` / `skipped` — fill per release.
