# Upstream Audit

## Baseline (confirmed)

| Item | Value |
| --- | --- |
| NagramX release baseline | `12.9.2.1260` / tag `1260` / commit `4335a2e589aac4a82f8fceb21b3102c5559db2bf` |
| Bootstrap commit on NixgramX | `e6d49a82` — Bootstrap NixgramX from NagramX 12.9.2.1260 (4335a2e) |
| Pre-sync tip | `9db59a81` on `main` |

## Telegram official master (re-checked 2026-09-04 UTC+8)

Rule: use latest `update to x.y.z (build)` commit on `DrKLO/Telegram` **master**, not GitHub Releases page.

| Field | Value |
| --- | --- |
| Latest `update to` | `update to 12.10.1 (7038)` |
| SHA | `62b56a07ca7e30e39f7fd00a6728d6bbd716ca1c` |
| Prior | `update to 12.10.0 (7031)` (`4e1a61eca6`) |

## Sync branch `upstream-sync/12.10.1` (L2 Assist — partial)

### Merge / apply strategy

- **Not** `git merge telegram` (NixgramX bootstrap has unrelated history).
- Applied upstream **delta** `b7561f0c641b` (12.9.2) → `62b56a07ca7e` (12.10.1) via filtered patch.
- Dropped paths absent from NixgramX: `TMessagesProj_App*`, `buildSrc`, `Dockerfile`, wholesale `README.md`.
- Conflicted Java/CMake: took Telegram 12.10.1 as base, re-applied NixgramX/NagramX feature delta; incomplete hunks saved as rejects.

### Submodule state (checked out)

| Path | SHA |
| --- | --- |
| `TMessagesProj/jni/third_party/dav1d` | `54706fc6bc` |
| `TMessagesProj/jni/third_party/ffmpeg` | `45f1910444` |
| `TMessagesProj/jni/third_party/libvpx` | `1024874c59` |
| `TMessagesProj/jni/third_party/libyuv` | `28ce69c274` |
| `TMessagesProj/jni/third_party/openh264` | `652bdb7719` |
| `TMessagesProj/jni/third_party/xiph/ogg` | `be05b13e98` |
| `TMessagesProj/jni/third_party/xiph/opus` | `22244de5a7` |
| `TMessagesProj/jni/third_party/xiph/opusfile` | `a55c164e98` |
| `TMessagesProj/jni/tlottie` | `3ce946c9ed` |
| `TMessagesProj/lib/jlatexmath` | `919e50b2f6` |

Notes: Telegram removed vendored `rlottie` / `exoplayer/libFLAC` / in-tree `openh264`+`libyuv` sources in favor of submodules / `jni/prebuild`. NixgramX tree follows that layout on this branch.

### Conflict / reject summary

| Category | Count | Notes |
| --- | --- | --- |
| Patch apply failures (filtered) | ~36 paths | Then resolved via 3-way / Telegram+feature-reapply |
| Feature reject archives | 19 files | Under `docs/sync-12.10.1-rejects/` |
| Remaining conflict markers in sources | 0 | Intentionally avoided leaving markers in tree |
| High-risk incomplete feature ports | see below | Prefer Telegram logic; features need L3 |

#### High-risk / blocked feature re-adaptations

| File | Reject hunks (approx) | What is blocked |
| --- | --- | --- |
| `ChatActivity.java` | 42 | Double-tap actions, Ayu deleted flows, text-style NaConfig gates, many context-menu adaptations |
| `ChatActivityEnterView.java` | 10 | Bot-command confirm, link confirm, vibration gates |
| `ChatMessageCell.java` | 4 | Ayu deleted mark, bookmark, translucent deleted |
| `ProfileActivity.java` | 1 (large import/block) | Some Neko/Ayu import + menu wiring may need re-check |
| `SendMessagesHelper.java` | 4 | Pangu-on-send, forward param guards |
| `ConnectionsManager.java` | 3 | Mostly resolved; `AyuGhostUtils` import+intercept restored |
| `FilterTabsView.java` | 2 | `NekoConfig.hideAllTab` |
| `VideoPlayer.java` | 1 | `NaConfig` player decoder |
| `RichMessageLayout.java` | 1 | `RichMessageTransHelper` import |
| `PeerStoriesView.java` | 1 | sticker send + NaConfig |
| Others (ActionBar*, FileLog, Utilities, AudioPlayerAlert, EditTextCaption, ChatAvatarContainer, ChatCustomReactionsEditActivity, CMakeLists) | 1–4 | Prefer Telegram; non-marker NagramX diffs may be intentionally dropped |

### Adapted on this branch

- Version props: `APP_VERSION_NAME=12.10.1`, `APP_VERSION_CODE=7038`; channel `verCode=1262`.
- Identity preserved: `APP_PACKAGE=app.nixgramx.android`, `NIXGRAMX_BASE` / `IS_BASE`, NixgramX APK naming.
- Root Gradle kept on NixgramX AGP 9.3.1 / Gradle 9.7.1 (did **not** downgrade to Telegram 8.x toolchain).
- `settings.gradle`: still single `:TMessagesProj` app module; added `:jlatexmath` submodule project.
- `TMessagesProj/build.gradle`: kept application plugin + NixgramX identity; added zxing / Play Integrity / recaptcha / wallet; markwon latex → `project(':jlatexmath')`.
- New Telegram Java/UI surfaces present (IV rich buttons, gifts message views, QR writer, ephemeral/keyboard TL, etc.).
- Ghost Mode **UI** still removed via `NekoExperimentalSettingsActivity` (`rows.remove(ghostModeRow)`). Intercept code path retained where it already applied (policy: keep disabled, do not expand).

### FEATURE_INVENTORY

Not re-exported in this L2 pass (tree not compile-validated). After L3 feature ports + successful `:TMessagesProj:compileDebugJavaWithJavac`, re-run the inventory export and note here.

### Build

Lightweight compile **not run** on this agent host if Android SDK/NDK absent — see PR body / sync commit notes.

## NagramX `_base` cut (as mirrored)

| Question | Finding |
| --- | --- |
| applicationId | `app.nixgramx.android` / `.base` via `APP_PACKAGE` + `NIXGRAMX_BASE` |
| Flavors | None; property switch |
| Feature strip | Save Deleted family gated with `BuildConfig.IS_BASE` |

## Policy removals (NixgramX-only)

Ghost Mode, hide-typing (ghost intercept), and online-status enhance/hide remain **removed-by-policy** in full and `_base` (UI row removed; do not re-enable during sync).
