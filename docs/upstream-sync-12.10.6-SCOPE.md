# Telegram 12.10.6 Upstream Sync — Scope Analysis

> **DOCUMENT / ISSUE SCAFFOLD ONLY.** No further production Java in this Lead turn. Do **not** merge to `main`/`beta`, do **not** publish Stable, do **not** change package / signing / API / `NIXGRAMX_VERSION_*`.

_Generated: 2026-10-05 (Asia/Shanghai CST). Branch `upstream-sync/12.10.6` tip `4faec58d` — L2 prepare commit `sync: Telegram 12.10.6 (7112)` on beta tip `2eed997f` (NIXGRAMX 12.10.5 / 1354). Aggregate Draft PR #164 → `beta`._

## Coordinates

| Role | Value |
|---|---|
| Official upstream | `DrKLO/Telegram` master |
| OLD | `dc780e81ed1261c369c27870e8e0999a1eb0b600` — update to **12.10.5 (7105)** |
| NEW | `f2908b14133bbffbf7ab04f641ecb5bfaf533242` — update to **12.10.6 (7112)** (published ~2026-09-30) |
| In between (include) | `e121ed9b4317d4719379e129a8e43392e280e73d` — Merge PR #2094 `difaj/fix/theme-crlf-parsing` (community; part of official master). Also `06c8275039af60ee647b8bab743b638d6720e34a` (Utilities.parseInt bounds) brought by that merge. |
| Compare | https://github.com/DrKLO/Telegram/compare/dc780e81ed1261c369c27870e8e0999a1eb0b600...f2908b14133bbffbf7ab04f641ecb5bfaf533242 |
| NixgramX base | branch **`beta`**, tip `2eed997f761e9443358ffa8e8668e01ae5383006` — `chore(release): 12.10.5 Stable prep — NIXGRAMX 12.10.5 (1354) (#162)`. After #163, beta≈main content (EPK3 verifier present on both). |
| Working branch | `upstream-sync/12.10.6` (do not merge to main/beta from this scaffold) |
| Aggregate PR | Draft **#164** `upstream-sync/12.10.6` → `beta` (keep open; do not close / auto-merge) |
| Recorded base on tip (`docs/upstream-base.json`) | `base` still **12.10.5 / 7105** / `dc780e81…`; `pending` + `prepared_target` → 12.10.6/7112 |

### Tooling notes (read-only)

- `docs/UPSTREAM_SYNC.md` + `.github/scripts/upstream_sync.py`: L2 automated prepare with three-way `--cached --3way` adapt; protected identity/build paths stay local; **do not replace** this tooling.
- Preserve `.github/workflows/upstream-watch.yml`, `upstream-sync-ci.yml`, `.github/scripts/upstream_sync.py` — do not bypass their gates.
- `docs/upstream-sync-report.md` on this branch: prepare result for 12.10.6 (17 clean / 2 conflict-todo).
- `docs/upstream-sync-todo/*.patch`: only **00007** + **00014** are active for 12.10.6. Stale 12.10.5 patches (`00001`, `00004`, `00006`, `00009`, `00013`, `00024`, `00026`, `00046`) remain on beta from incomplete close-out — **BUILD must delete the stale ones early** (and strip trailing whitespace / blank-at-EOF on any kept patches so `git diff --check` passes).

### Base tip note

- Branched from **beta** `2eed997f` (matches prior cycles + L2 prepare).
- `main` tip `155a3b6a` shipped Stable 1354 via #163; EPK3 startup-asset verifier already on beta/main — **present on this branch**. Do not silently rebase onto main.

## Upstream delta summary

- Commits OLD→NEW: **3** (`06c8275` parseInt bounds → `e121ed9b` Merge #2094 → `f2908b14` update to 12.10.6/7112).
- Paths changed: **19** (M 18 / D 1). Shortstat ≈ **+322 / −265** (PR #164 also adds prepare docs → ~+386/−335 / 21 files).
- Headline themes (plain words for users):
  1. **Safer rich-text / styled entities** — rewrite of `getTextStyleRuns` with caps + a safe splitter so hostile/huge entity lists cannot hang or blow memory when rendering bold/links/spoilers.
  2. **Round-video output path** — round videos write under app `cache/` (explicit output directory) instead of only `getCacheDir()`.
  3. **Playback-speed hint polish** — hint attaches to an explicit parent; new `HintsController.PlaybackSpeedHint`.
  4. **Edited-message time** — when primary-edited-date is on and edit_date is still 0 while editing, show “now”.
  5. **Group-call / H.265 hardening** — reject empty SSRC groups; bound long-term reference picture counts in the bitstream parser.
  6. **Misc crash/UX fixes** — sponsored-message retry loop, FileProvider share no longer falls back to `file://`, PhotoViewer caption account switch + transparent placeholder, RLottieNative raw-pointer API narrowed, operator-precedence fix in blur dark detection, glass tab colours in stories dark provider, remove unused `DispatchQueueMainThreadSync`.

## L2 prepare status (already on branch)

| Bucket | Count | Notes |
|---|---:|---|
| Clean applied | 17 | Native VoIP/H265, MediaDataController, MessageObject, Utilities, ChatActivity, attach audio, FragmentContextView, HintsController, InstantCameraView2, RoundVideoSession, PhotoViewer, RLottieNative, DarkThemeResourceProvider, DispatchQueueMainThreadSync delete, gradle APP_VERSION_* |
| Conflict / adaptation TODO | 2 | See patches below — **task owners must three-way adapt; do not overwrite Nix files** |

### TODO patches (owner lock) — 12.10.6 active only

| Patch | Path | Suggested issue |
|---|---|---|
| `00007.patch` | `ChatMessageCell.java` | **UI-MEDIA** (HIGH — translation / CJK / time-measure Nix locals) |
| `00014.patch` | `BlurredBackgroundProviderBuilder.java` | **UI-MEDIA** (glass dark detection; Nix #19 glass clamp ancestry) |

## Lessons from 12.10.5 (bake into this cycle)

1. **`git diff --check` fails** on trailing whitespace **and** blank-at-EOF in `docs/upstream-sync-todo/*.patch` — clean early (BUILD).
2. **`Tools/stability/test_upstream_12103_incremental.py`** pins `upstream-base.json` shape **and** `APP_VERSION_*` / NIXGRAMX name — **BUILD must update the pin** for 12.10.6 (APP 7112/12.10.6). Do **not** bump `NIXGRAMX_VERSION_*` here (Stable prep later).
3. **Adaptation gate** needs `pending` cleared before aggregate #164 can merge; leave `prepared_target` until close-out / release-prep promotes into `base`.
4. **Chicken-egg QV:** merge content-safe task PRs first when strings/resources are shared. This cycle has **no strings.xml delta** — lower risk, but still land independent clean-verify PRs before pending-clear.
5. **Product decision (standing):** InstantCameraView2 does **not** get Nix zoom/facing/vibration ported (follow official) unless 12.10.6 changes that area materially.
   - **FLAG:** InstantCameraView2 **did** change (+1: `.setOutputDirectory(cache)`); RoundVideoSession gained configurable output directory. Still **follow official** (L2 already applied). No Nix zoom/facing/vibration port unless Owner reopens.

## File inventory by area

### BUILD / VERSION

| Status | Path |
|---|---|
| M (clean) | `gradle.properties` — `APP_VERSION_CODE` 7105→**7112**, `APP_VERSION_NAME` 12.10.5→**12.10.6** (keep `NIXGRAMX_VERSION_*` / `APP_PACKAGE`) |
| M (prepare) | `docs/upstream-base.json` — pending/prepared_target 12.10.6 (final `base` bump is close-out only) |
| M (prepare) | `docs/upstream-sync-report.md` |
| M (BUILD) | `Tools/stability/test_upstream_12103_incremental.py` — retarget APP pins + pending-aware assertions for in-flight sync |
| M (BUILD) | Delete **stale** `docs/upstream-sync-todo/{00001,00004,00006,00009,00013,00024,00026,00046}.patch`; keep/clean `00007`/`00014` whitespace |

### NATIVE / VOIP

| Status | Path |
|---|---|
| M (clean) | `TMessagesProj/jni/voip/tgcalls/group/GroupInstanceCustomImpl.cpp` — skip empty SSRC groups |
| M (clean) | `…/webrtc/common_video/h265/h265_bitstream_parser.cc` — long-term ref pic bounds |
| M (clean) | `…/webrtc/common_video/h265/h265_sps_parser.cc` — index bounds |

### CORE-TEXT (messenger)

| Status | Path |
|---|---|
| M (clean) | `…/messenger/MediaDataController.java` — `getTextStyleRuns` safe/legacy split + caps |
| M (clean) | `…/messenger/MessageObject.java` — skip invalid style-run ranges |
| M (clean) | `…/messenger/Utilities.java` — `parseInt` non-digit / lone-`-` bounds (`06c8275` / #2094) |
| D (clean) | `…/messenger/DispatchQueueMainThreadSync.java` — removed upstream |

### UI-MEDIA (chat / camera / glass)

| Status | Path |
|---|---|
| **TODO** | `…/ui/Cells/ChatMessageCell.java` — `00007.patch` (edited-time while editing) |
| M (clean) | `…/ui/ChatActivity.java` — speed-hint parent; share FileProvider; photo size −1; sponsored retry guard |
| M (clean) | `…/ui/Components/ChatAttachAlertAudioLayout.java` — null-safe FragmentContextView setup |
| M (clean) | `…/ui/Components/FragmentContextView.java` — `setSpeedHintViewParent` + HintsController |
| M (clean) | `…/ui/Components/HintsController.java` — `PlaybackSpeedHint` |
| M (clean) | `…/ui/Components/InstantCameraView2.java` — `setOutputDirectory(cache)` (**material but follow official**) |
| M (clean) | `…/utils/camera/roundvideo/RoundVideoSession.java` — outputDirectory API |
| M (clean) | `…/ui/PhotoViewer.java` — caption account + transparent placeholder; drop requestLayout stack dump |
| M (clean) | `…/ui/Components/RLottieNative.java` — narrow legacy static wrappers |
| **TODO** | `…/blur3/.../BlurredBackgroundProviderBuilder.java` — `00014.patch` (isDark parentheses) |
| M (clean) | `…/ui/Stories/DarkThemeResourceProvider.java` — glass tab colour keys |

## Proposed task split (one file owner lock)

| Issue key | Owner files (exclusive) | Implementer → Reviewer | Depends on | Risk |
|---|---|---|---|---|
| **BUILD/VERSION** | `gradle.properties` (APP_VERSION_* only); `Tools/stability/test_upstream_12103_incremental.py`; close-out notes for `docs/upstream-base.json`; delete stale todo patches + whitespace-clean `00007`/`00014` (do not bump `NIXGRAMX_VERSION_*`) | Codex → Grok | — | LOW |
| **NATIVE-VOIP** | `GroupInstanceCustomImpl.cpp`, `h265_bitstream_parser.cc`, `h265_sps_parser.cc` | Grok → Codex | BUILD/VERSION | MED (native; L2 clean — verify) |
| **CORE-TEXT** | `MediaDataController.java`, `MessageObject.java`, `Utilities.java`, `DispatchQueueMainThreadSync.java` (confirm delete + no stray refs) | Codex → Grok | BUILD/VERSION | MED–HIGH (text-style rewrite; CJK/custom-emoji adjacent) |
| **UI-MEDIA** | `ChatMessageCell.java` (**00007**), `ChatActivity.java`, `ChatAttachAlertAudioLayout.java`, `FragmentContextView.java`, `HintsController.java`, `InstantCameraView2.java`, `RoundVideoSession.java`, `PhotoViewer.java`, `RLottieNative.java`, `BlurredBackgroundProviderBuilder.java` (**00014**), `DarkThemeResourceProvider.java` | Grok → Codex | BUILD/VERSION; content-order: prefer after CORE-TEXT if style-run smoke needed | HIGH (ChatMessageCell Nix translation/CJK; glass) |

### Co-own / BLOCKED_BY notes

- **`ChatMessageCell`**: UI-MEDIA sole owner. Heavy Nix locals (translation swap, CJK bubble width, custom-emoji span isolation, time/badge reserve). Upstream delta is **only** the edited-time branch in `measureTime` — three-way that hunk; never replace the file.
- **`BlurredBackgroundProviderBuilder`**: UI-MEDIA sole owner. Upstream is operator-precedence parentheses around `isDark()`. Preserve Nix glass / ThemeDelegate.isDark ancestry from #19.
- **`InstantCameraView2` / `RoundVideoSession`**: UI-MEDIA sole owner. Follow official output-directory behaviour; **do not** port Nix zoom/facing/vibration (Owner product decision).
- **`ChatActivity`**: already clean-applied; UI-MEDIA verifies against FragmentContextView `setSpeedHintViewParent` and share/sponsored fixes. Do not let CORE-TEXT edit ChatActivity.
- **`MediaDataController` / `MessageObject`**: CORE-TEXT sole owners. Style-run caps must still skip `TL_messageEntityCustomEmoji` (official does). Smoke CJK + custom emoji after land.
- **No strings.xml / LocaleController / DB migrate** in this official range — out of scope.

## Known Nix local risks (carry forward)

| Risk | Why it bites this sync |
|---|---|
| CJK emoji / custom emoji glue (**#59** / translation cells) | MediaDataController style-run rewrite + ChatMessageCell time hunk — smoke CJK bubbles + custom emoji after CORE-TEXT / UI-MEDIA. |
| Folder swipe (**#53–56**) | Out of file scope; device gate optional. |
| Send-button contrast | Not in delta; do not touch EnterView. |
| Glass / LiquidGlass (#19 clamp) | Blur `isDark()` TODO + DarkThemeResourceProvider glass keys — preserve Nix clamps; do not strip glass_target* seeds. |
| Ghost Mode / proxy package / private beta CI | No ConnectionsManager / LocaleController delta — out of scope. |
| EPK3 verifier | Already on beta (`#143` / #163); do not rewrite emoji.pack. |
| InstantCamera Nix zoom/facing/vibration | **Do not port** — follow official InstantCameraView2/RoundVideoSession. |
| Stale todo patches on beta | Mislead adaptation gate / `git diff --check` — BUILD deletes early. |

## Three-way compare checklist (per issue)

For every owned path before editing production code, write on the issue:

1. Official OLD→NEW hunk (link commits in `dc780e81..f2908b14` + path).
2. Current NixgramX behavior on `upstream-sync/12.10.6` (L2 applied vs todo patch).
3. Local delta vs official OLD (Neko / Nix identity / prior sync adaptations) and **why keep**.
4. Exact files to touch (must match owner lock).
5. What must stay (hooks, package, version policy).
6. Regression risk + validation plan.
7. If evidence is thin → **NEEDS HUMAN**; stop.

Then:

- Prefer adapting `docs/upstream-sync-todo/NNNNN.patch` with three-way awareness over copying official blobs.
- PR targets **`upstream-sync/12.10.6` only**, as a **Draft**. Branch naming: `upstream-task/12.10.6/<issue>-<area>`.
- Implementer/reviewer alternate Codex↔Grok as assigned.
- Compiling ≠ runtime verification; Private Beta device gate only when Owner asks.

## Out of scope

- Merge / fast-forward to `main` or `beta`; closing or merging aggregate **#164**.
- Stable / Public Beta / Private Beta publish or tag; auto-merge / auto Stable.
- Changing `APP_PACKAGE`, signing, API IDs, `NIXGRAMX_VERSION_NAME` / `NIXGRAMX_VERSION_CODE`.
- Porting Nix zoom/facing/vibration onto InstantCameraView2.
- Re-litigating areas with no 12.10.6 delta (EnterView, strings, DB, LocaleController, proxy package, EPK3 pack rewrite) except where a TODO necessarily touches an import.
- Blind overwrite of any Nix-modified Java with DrKLO versions; commenting-out / disabling tests/CI gates; unrelated reformatting.

## Suggested validation (integration, after all issues)

- Arm64 assemble on `upstream-sync/12.10.6`.
- Rich text: bold/links/spoilers/custom emoji; hostile large entity list does not hang.
- Round video: hold-to-record writes under cache/; send still works (no Nix zoom/facing expectations).
- Chat: edited-time while editing; playback-speed hint; sponsored messages do not infinite-loop; share sheet uses FileProvider.
- Glass: attach/gallery dark chrome still clamped; stories dark glass tabs visible.
- QV pin test green; `upstream-base.json` pending cleared only at Owner close-out; no stale todo patches; `git diff --check` clean.
- No package / updater / EPK3 regression.
