# Bug Fix Sources

Policy: subsequent forks may donate **bugfixes only** by default — no new feature ports unless explicitly approved.

| Source | Allowed | Notes |
| --- | --- | --- |
| `risin42/NagramX` | Baseline + historical fixes | Archived 2026-08-23 |
| `a6c7d0a` tip | 12.10.0 adaptation patches | Audit before take |
| Other Nagram/Neko/Ayu forks | Bugfix cherry-picks | Attribute SHA + reason here |

## Log

| Date | Upstream SHA | Summary | NixgramX commit |
| --- | --- | --- | --- |
| 2026-09-04 | `4335a2e` | Bootstrap baseline | `e6d49a82` |
| 2026-09-04 | — | UB-1 translation bubble + UB-4 pinch jank | `fix/user-requested-bugs` |
| 2026-09-04 | — | Auto-update default OFF + NixgramX release keystore | `fix/user-requested-bugs` |
| 2026-09-06 | `4e63d17d` (exteraless/exteraless) | Memory leak cleanup: VideoAds LruCache, mention adapters, ChatAvatarContainer observers, PhotoViewer blur watcher / animator (manual port; compile not run / Not device-tested) | `stability/exteraless-audit` |
