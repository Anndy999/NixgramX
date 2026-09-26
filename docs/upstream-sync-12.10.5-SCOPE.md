# Telegram 12.10.5 Upstream Sync — Scope Analysis

> **DOCUMENT / ISSUE SCAFFOLD ONLY.** No further production Java in this Lead turn. Do **not** merge to `main`/`beta`, do **not** publish Stable, do **not** change package / signing / API / `NIXGRAMX_VERSION_*`.

_Generated: 2026-09-26 (Asia/Shanghai CST). Branch `upstream-sync/12.10.5` tip `6d91a850` — L2 prepare commit `sync: Telegram 12.10.5 (7105)` on beta tip `f2169df4` (NIXGRAMX 12.10.4 / 1353)._

## Coordinates

| Role | Value |
|---|---|
| Official upstream | `DrKLO/Telegram` master |
| OLD | `c84801762fd5f936c8296ecf09a14a48ebfc4fe4` — update to **12.10.4 (7099)** |
| NEW | `dc780e81ed1261c369c27870e8e0999a1eb0b600` — update to **12.10.5 (7105)** (single commit, published ~2026-09-25) |
| Compare | https://github.com/DrKLO/Telegram/compare/c84801762fd5f936c8296ecf09a14a48ebfc4fe4...dc780e81ed1261c369c27870e8e0999a1eb0b600 |
| NixgramX base | branch **`beta`**, tip `f2169df448815332bcc18627548a38b866722d70` — `chore(release): 12.10.4 Stable prep — NIXGRAMX 12.10.4 (1353) (#140)` — **verified** (both `beta` and `main` ship 12.10.4/1353; prior syncs base off beta; L2 prepare already used beta) |
| Working branch | `upstream-sync/12.10.5` (do not merge to main/beta from this scaffold) |
| Recorded base on `main` (`docs/upstream-base.json`) | still **12.10.4 / 7099** / `c8480176…` |
| On this branch | `pending` + `prepared_target` → 12.10.5/7105; `base` still 12.10.4 until final sync close-out |

### Tooling notes (read-only)

- `docs/UPSTREAM_SYNC.md` + `.github/scripts/upstream_sync.py`: L2 automated prepare with three-way `--cached --3way` adapt; protected identity/build paths stay local; **do not replace** this tooling.
- `docs/upstream-sync-report.md` on this branch: prepare result for 12.10.5 (40 clean / 8 conflict-todo).
- `docs/upstream-sync-todo/*.patch`: hunks that must be adapted by the owning issue (never blind `git apply` over Nix hooks).

### Base tip note

- Prefer **beta** (matches prior 12.10.4 scaffold and the existing L2 branch).
- `main` tip `4c7e1f37` is SHA-ahead of beta (includes `resolve/main-12.10.4-1353` + EPK3 runtime verifier #143). Content version is the same 12.10.4/1353. **Do not silently rebase this branch onto main** in task PRs; if EPK3 verifier #143 is required for device gate, call it out as a follow-up merge from main — not in-scope for Round Video tasks.

## Upstream delta summary

- Commits OLD→NEW: **1** (`dc780e81` — `update to 12.10.5 (7105)`).
- Paths changed: **48** (A 21 / M 27). Shortstat ≈ **+10364 / −725**.
- Headline theme: **new Round Video / Camera2 recorder stack** (`org.telegram.utils.camera.roundvideo.*` + InstantCameraView2/Base + settings helpers + UI wiring), plus ThemeDescription churn, DB migrate **178→179** (`DELETE FROM downloading_documents`), and misc media/stories/settings touches.

## L2 prepare status (already on branch)

| Bucket | Count | Notes |
|---|---:|---|
| Clean applied | 40 | Includes full `utils/camera/roundvideo/*`, InstantCameraView{,2,Base}, RoundVideoSettings*, TelegramRoundVideoUpload, utils/settings/*, ThemeDescription, ChatActivity InstantCamera wiring, gradle APP_VERSION_* bump, DB migrate, etc. |
| Conflict / adaptation TODO | 8 | See patches below — **task owners must three-way adapt; do not overwrite Nix files** |

### TODO patches (owner lock)

| Patch | Path | Suggested issue |
|---|---|---|
| `00001.patch` | `ChannelBoostsController.java` | STORIES-MISC |
| `00004.patch` | `MediaController.java` | MEDIA-SESSION |
| `00006.patch` | `MusicBrowserService.java` | MEDIA-SESSION |
| `00009.patch` | `ConnectionsManager.java` | STORIES-MISC |
| `00013.patch` | `ChatActivityEnterView.java` | **ROUND-UI** (HIGH — Neko hooks) |
| `00024.patch` | `SettingsActivity.java` | SETTINGS-UTIL |
| `00026.patch` | `StoriesController.java` | STORIES-MISC |
| `00046.patch` | `values/strings.xml` (RoundVideo* strings) | ROUND-UI |

## File inventory by area

### BUILD / VERSION (1 + docs)

| Status | Path |
|---|---|
| M (clean) | `gradle.properties` — `APP_VERSION_CODE` 7099→**7105**, `APP_VERSION_NAME` 12.10.4→**12.10.5** (keep `NIXGRAMX_VERSION_*` / `APP_PACKAGE`) |
| M (prepare) | `docs/upstream-base.json` — pending/prepared_target 12.10.5 (final `base` bump is close-out only) |
| M (prepare) | `docs/upstream-sync-report.md` |

### ROUND-CORE — Camera2 recorder stack (11 new + upload/base)

| Status | Path |
|---|---|
| A | `…/utils/camera/roundvideo/RoundVideoCameraController.java` |
| A | `…/utils/camera/roundvideo/RoundVideoSession.java` |
| A | `…/utils/camera/roundvideo/RoundVideoOverlayRenderer.java` |
| A | `…/utils/camera/roundvideo/RoundVideoGlProcessor.java` |
| A | `…/utils/camera/roundvideo/RoundVideoCodecRecorder.java` |
| A | `…/utils/camera/roundvideo/RoundVideoMp4Writer.java` |
| A | `…/utils/camera/roundvideo/RoundVideoRemuxer.java` |
| A | `…/utils/camera/roundvideo/RoundVideoSwitchTimingStore.java` |
| A | `…/utils/camera/roundvideo/RoundVideoDiagnostics.java` |
| A | `…/ui/Components/InstantCameraViewBase.java` |
| A | `…/ui/Components/InstantCameraView2.java` |
| A | `…/ui/Components/TelegramRoundVideoUpload.java` |
| M | `…/ui/Components/InstantCameraView.java` (legacy path / factory glue) |

### ROUND-UI — settings UI + chat wiring + strings

| Status | Path |
|---|---|
| A | `…/ui/Components/RoundVideoSettings.java` |
| A | `…/ui/RoundVideoSettingsActivity.java` |
| M (clean) | `…/ui/Components/RoundVideoProgressView.java` |
| M (clean) | `…/ui/ChatActivity.java` — `InstantCameraView` → `InstantCameraViewBase.create(…)` + recording UI frame callback into EnterView |
| **TODO** | `…/ui/Components/ChatActivityEnterView.java` — `00013.patch` (external frame clock + InstantCamera glue; **Neko hooks**) |
| **TODO** | `…/res/values/strings.xml` — `00046.patch` (RoundVideo* strings only) |

### SETTINGS-UTIL

| Status | Path |
|---|---|
| A | `…/utils/settings/{Boolean,Enum,Float,Int,Long,String}Setting.java` |
| A | `…/utils/settings/SettingsPreferences.java` |
| A | `…/utils/settings/SharedSettings.java` (`experimentalSettingsAllowed`, etc.) |
| **TODO** | `…/ui/SettingsActivity.java` — `00024.patch` (experimental Round Video entry + debug toggle) |

### THEME

| Status | Path |
|---|---|
| M (clean) | `…/ui/ActionBar/ThemeDescription.java` (sizable churn) |

### MEDIA-SESSION

| Status | Path |
|---|---|
| M (clean) | `…/messenger/TelegramMediaSession.java` |
| M (clean) | `…/messenger/DownloadController.java` |
| M (clean) | `…/ui/Components/AnimatedFileDrawable.java` |
| M (clean) | `…/ui/Components/VideoTimelineView.java` |
| M (clean) | `…/ui/PhotoViewer.java` |
| **TODO** | `…/messenger/MediaController.java` — `00004.patch` |
| **TODO** | `…/messenger/MusicBrowserService.java` — `00006.patch` |

### STORIES-MISC

| Status | Path |
|---|---|
| M (clean) | `…/ui/Stories/SelfStoryViewsPage.java` |
| M (clean) | `…/ui/Stories/StoriesStorage.java` |
| M (clean) | `…/messenger/UserNameResolver.java` |
| M (clean) | `…/ui/WebAppDisclaimerAlert.java` |
| M (clean) | `…/messenger/AndroidUtilities.java` |
| M (clean) | `…/ui/Components/RLottieDrawable.java` |
| M (clean) | `…/messenger/MessagesStorage.java` (`LAST_DB_VERSION` 179 — co-touch with DB) |
| **TODO** | `…/ui/Stories/StoriesController.java` — `00026.patch` |
| **TODO** | `…/messenger/ChannelBoostsController.java` — `00001.patch` |
| **TODO** | `…/tgnet/ConnectionsManager.java` — `00009.patch` |

### DB

| Status | Path |
|---|---|
| M (clean) | `…/messenger/DatabaseMigrationHelper.java` — migrate **178→179**: `DELETE FROM downloading_documents` |
| M (clean) | `…/messenger/MessagesStorage.java` — `LAST_DB_VERSION = 179` (**file lock: DB owns migrate helper; STORIES-MISC must not re-edit LAST_DB_VERSION**) |

> **Lock rule:** `MessagesStorage.java` LAST_DB_VERSION / migrate call site → **DB** sole owner. Other STORIES-MISC hunks in the same file (if any beyond version bump) stay under DB for this sync to avoid dual writers; STORIES-MISC skips MessagesStorage edits.

## Proposed task split (one file owner lock)

| Issue key | Owner files (exclusive) | Implementer → Reviewer | Depends on |
|---|---|---|---|
| **BUILD/VERSION** | `gradle.properties` (APP_VERSION_* only); close-out notes for `docs/upstream-base.json` (do not bump `NIXGRAMX_VERSION_*`) | Codex → Grok | — |
| **ROUND-CORE** | `utils/camera/roundvideo/*`, `InstantCameraViewBase.java`, `InstantCameraView2.java`, `InstantCameraView.java`, `TelegramRoundVideoUpload.java` | Grok → Codex | BUILD/VERSION |
| **ROUND-UI** | `RoundVideoSettings.java`, `RoundVideoSettingsActivity.java`, `RoundVideoProgressView.java`, `ChatActivity.java` (InstantCamera wiring already applied — verify/fix only), **`ChatActivityEnterView.java` (00013)**, **`strings.xml` RoundVideo* (00046)** | Codex → Grok | ROUND-CORE, SETTINGS-UTIL |
| **SETTINGS-UTIL** | `utils/settings/*`, **`SettingsActivity.java` (00024)** | Grok → Codex | BUILD/VERSION |
| **THEME** | `ThemeDescription.java` | Codex → Grok | — |
| **MEDIA-SESSION** | `TelegramMediaSession.java`, `DownloadController.java`, `AnimatedFileDrawable.java`, `VideoTimelineView.java`, `PhotoViewer.java`, **`MediaController.java` (00004)**, **`MusicBrowserService.java` (00006)** | Grok → Codex | BUILD/VERSION |
| **STORIES-MISC** | `SelfStoryViewsPage.java`, `StoriesStorage.java`, `UserNameResolver.java`, `WebAppDisclaimerAlert.java`, `AndroidUtilities.java`, `RLottieDrawable.java`, **`StoriesController.java` (00026)**, **`ChannelBoostsController.java` (00001)**, **`ConnectionsManager.java` (00009)** | Codex → Grok | BUILD/VERSION |
| **DB** | `DatabaseMigrationHelper.java`, `MessagesStorage.java` (version 179 / downloading_documents only) | Grok → Codex | BUILD/VERSION |

### Co-own / BLOCKED_BY notes

- **`ChatActivityEnterView`**: ROUND-UI sole owner. Heavy **Neko** imports/hooks (`NekoConfig`, attach menu, confirm AV, vibration, link preview). Upstream 00013 also touches ObserversGroup-era recording blink clock — **three-way only**; never replace the file with official. If InstantCamera compile needs ROUND-CORE APIs, ROUND-UI is **BLOCKED_BY** ROUND-CORE.
- **`ChatActivity`**: InstantCamera typing already applied cleanly; ROUND-UI verifies against EnterView methods (`setRoundVideoUiFrameClockActive` / `onRoundVideoUiFrame`) once 00013 lands. Do not let ROUND-CORE edit ChatActivity.
- **`SettingsActivity`**: SETTINGS-UTIL sole owner; needs `SharedSettings` + `RoundVideoSettingsActivity` symbols from SETTINGS-UTIL / ROUND-UI. Prefer SETTINGS-UTIL lands utils first; ROUND-UI may land Settings Activity **only if** SETTINGS-UTIL explicitly defers the entry row — default: SETTINGS-UTIL owns 00024.
- **`strings.xml`**: ROUND-UI owns **only** the RoundVideo* additions in 00046; do not reformat / reshuffle unrelated Nix strings.

## Known Nix local risks (carry forward)

| Risk | Why it bites this sync |
|---|---|
| CJK emoji / custom emoji glue (**#59**) | Unrelated to Round Video, but ThemeDescription + chat input churn can regress glyph layout — smoke CJK bubbles after ROUND-UI / THEME. |
| Folder swipe (**#53–56**) | Out of file scope, but device gate after any EnterView work. |
| Send-button contrast | EnterView / theme adjacent — preserve Nix send FAB contrast when adapting 00013. |
| Light picker jank | PhotoViewer / MediaController touches — do not reintroduce light-theme picker frame drops (see 12.10.4 MEDIA notes). |
| Rename vs local note | New `InstantCameraView2` / `InstantCameraViewBase` / `utils.*` packages — keep Nix package `app.nixgramx.android`; do not “align” applicationId. |
| Proxy package (`org.telegram.utils.proxy`) | ConnectionsManager TODO may touch imports — keep Nix utils.proxy adaptations from 12.10.4 (#122). |
| EPK3 | Verifier fix #143 is on **main** only; beta-based branch may still need that follow-up before Stable. Round Video tasks must not rewrite emoji.pack. |
| LocaleController | No LocaleController delta in 12.10.5 official commit — **out of scope**. Do not reopen #129 work. |
| Glass | ThemeDescription churn can interact with Nix glass / LiquidGlass — THEME must not strip Nix theme keys; ROUND-UI must not disable glass hooks in EnterView. |
| Neko hooks in `ChatActivityEnterView` / InstantCamera | **Highest risk file.** Preserve `NekoConfig.useChatAttachMediaMenu`, `confirmAVMessage`, vibration guards, dice/markdown helpers. Adapt InstantCameraBase frame-clock API around them. |

## Three-way compare checklist (per issue)

For every owned path before editing production code, write on the issue:

1. Official OLD→NEW hunk (link commit `dc780e81` + path).
2. Current NixgramX behavior on `upstream-sync/12.10.5` (and whether L2 already applied or left a todo patch).
3. Local delta vs official OLD (Neko / Nix identity / prior sync adaptations) and **why keep**.
4. Exact files to touch (must match owner lock).
5. What must stay (hooks, package, version policy).
6. Regression risk + validation plan.
7. If evidence is thin → **NEEDS HUMAN**; stop.

Then:

- Prefer adapting `docs/upstream-sync-todo/NNNNN.patch` with three-way awareness over copying official blobs.
- PR targets **`upstream-sync/12.10.5` only**, as a **Draft**.
- Compiling ≠ runtime verification; Private Beta device gate only when Owner asks.

## Out of scope

- Merge / fast-forward to `main` or `beta`.
- Stable / Public Beta / Private Beta publish or tag.
- Changing `APP_PACKAGE`, signing, API IDs, `NIXGRAMX_VERSION_NAME` / `NIXGRAMX_VERSION_CODE`.
- Re-litigating 12.10.4 areas with no 12.10.5 delta (EPK3 pack rewrite, GlassEngine package move, ObserversGroup cascade, VoIP/WAMR, proxy package move, LocaleController rewrite) except where a TODO file necessarily touches an import.
- Blind overwrite of any Nix-modified Java with DrKLO versions.
- Implementing “nice to have” refactors while syncing.

## Suggested validation (integration, after all issues)

- Arm64 assemble on `upstream-sync/12.10.5`.
- Round video: hold-to-record, switch camera, lock, send; toggle Camera2 recorder + resolution/FPS settings; disable composition styling path.
- Chat input: Neko attach-menu / confirm-AV / vibration still honor config.
- Media session / download / music browser smoke.
- DB: cold start migrates 178→179; downloading_documents cleared without crash.
- Theme: settings + chat chrome still themed under Nix glass / Monet.
- No package / updater regression.
