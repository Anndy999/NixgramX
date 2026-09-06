# Bug Fix Sources

Policy: subsequent forks may donate **bugfixes only** by default — no new feature ports during Stability Phase v1.

| Source | Allowed | Notes |
| --- | --- | --- |
| `risin42/NagramX` | Baseline + historical fixes | Archived 2026-08-23 |
| `a6c7d0a` tip | 12.10.0 adaptation patches | Audit before take |
| Other Nagram/Neko/Ayu forks | Bugfix cherry-picks | Attribute SHA + reason here |

## Log

| Date | Upstream SHA | Summary | NixgramX commit |
| --- | --- | --- | --- |
| 2026-09-04 | `4335a2e` | Bootstrap baseline | `e6d49a82` |
| 2026-09-04 | — | UB-1 translation bubble + UB-4 pinch jank | `2323cc765b` (integrated; device verification pending) |
| 2026-09-04 | — | Auto-update default OFF + NixgramX release keystore | `cc67431ba6` (integrated) |
| 2026-09-06 | `4e63d17d` (exteraless/exteraless) | Memory leak cleanup: VideoAds LruCache, mention adapters, ChatAvatarContainer observers, PhotoViewer blur watcher / animator (manual port; original PR had compile not run / device NOT TESTED) | `33b4dde726` (#27, integrated) |
| 2026-09-06 | — | UB-8 follow-up: incoming-only translate swap animation (no EN/ZH overlay; item MOVE still off) | `a5058361c8` (#28, integrated) |

## Stability Phase v1 source/adaptation review

| Source repo | Source SHA | Problem | NixgramX adaptation | NixgramX commit | Test status |
| --- | --- | --- | --- | --- | --- |
| exteraless/exteraless | 95b4e525e4437d59fc5be40fdd0673ab071fafc9 | Local service remains active with external Push | Stop stale local service and both alarm identities; prevent service restart with available external provider; preserve NixgramX FCM hybrid connection and restrict FCM helper to FCM modes | `c295279dbf` | Compile/host guards; device NOT TESTED |
| exteraless/exteraless | 738786ec1eb0f863273396662909630b59f769a1 | Last Java crash unavailable next launch | Independently implemented bounded no-backup local store, sanitized frames (no exception messages/thread names), preserve existing Crashlytics filter chain | `695e1db2ce`, `b14fa0d188` | Host JVM PASS; crash/relaunch device NOT TESTED |
| Anndy999/NixgramX | d7bc4dcb90 | Cross-account clear of deleted/edit history | Add userId to clear/query/bulk-edit-delete DAO parameters and pass account identity from ChatActivity | `ab5627ea78` | Host SQLite regression PASS; Room/device NOT TESTED |

The Exteraless source commits were read using GitHub API, not transplanted as whole files.
Neither selected change depends on plugins. Current official base does not implement
NixgramX local push selection, Ayu history or this diagnostic UI, so these are local adaptations,
not replacements for Telegram base code. Benefit: privacy, retained crash evidence and service
lifecycle correctness; risk: ROM background service restrictions and startup dialog behavior.

Rejected/deferred intelligence: 8543f0a0f3e0dbdfd206911f8dd59779080c2d8d (plugin-cancelled Push response)
is plugin-coupled; no port. 5c3993e63591c872ab6fa5d51b145668e0a6b7c0 (deduplicate push connection updates)
requires native behavior evidence; no native semantics port. Other forks are intelligence sources,
not approval to import features. No new claims of exhaustive fork review.

Latest main integration retained:

| 2026-09-06 | — | UB-8 follow-up: incoming-only translate swap animation (no EN/ZH overlay; item MOVE still off) | `a5058361c8` (#28, integrated) |
