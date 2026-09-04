# Upstream Audit

## Baseline (confirmed)

| Item | Value |
| --- | --- |
| NagramX release baseline | `12.9.2.1260` / tag `1260` / commit `4335a2e589aac4a82f8fceb21b3102c5559db2bf` |
| Bootstrap commit on NixgramX | `e6d49a82` — Bootstrap NixgramX from NagramX 12.9.2.1260 (4335a2e) |
| Tree used for this audit | Local checkout of NixgramX tracking that bootstrap (Cloud Agents unavailable) |

## Telegram official master (re-checked)

Rule: use latest `update to x.y.z (build)` commit on `DrKLO/Telegram` **master**, not GitHub Releases page.

| Field | Value |
| --- | --- |
| Latest `update to` | `update to 12.10.1 (7038)` |
| SHA (short) | `62b56a07ca` |
| Prior | `update to 12.10.0 (7031)` (`4e1a61eca6`) |

NixgramX Phase 1 sync target must not be below 12.10.1 (7038). This Day-1 cut does **not** perform the sync yet.

## Audit of `a6c7d0a` (NagramX 12.10.0 test tip)

| Field | Value |
| --- | --- |
| Full SHA | `a6c7d0aec95f829a63aaa7bc591b8e809af84636` |
| Message | `update to 12.10.0 (7031)` |
| Date (UTC) | 2026-08-22 |
| vs `4335a2e` | ahead by 3 commits, 0 behind |

Commits on path `4335a2e...a6c7d0a`:

1. `8899cbd167` — Update submodules  
2. `25dbf39b9d` — update submodule fix link  
3. `a6c7d0aec9` — update to 12.10.0 (7031)

### What changed (from GitHub compare API)

- **~300 files** in the compare payload; overwhelmingly **jni / third_party / submodule** moves aligned with Telegram’s 12.10.0 native layout (FLAC/exoplayer paths removed from tree in favor of submodule wiring).
- Non-jni surface files in the compare sample: `.gitmodules` (modified), `TMessagesProj/build.gradle` (modified; verCode → 1261 on upstream tip).

### Interpretation

| Category | Assessment |
| --- | --- |
| Valid 12.10.0 adaptation | Yes — submodule/native rebase toward official 12.10.0 (7031) |
| Incomplete rebase risk | Medium — archived before a full maintenance cycle; channel package noted as 1261 |
| New product features | No evidence of unrelated feature work in the 3-commit window |
| Regression risk | Native build breakage if submodules not synced; treat as adaptation candidate, not drop-in product baseline |

### NixgramX decision

- **Feature behavior baseline:** keep `4335a2e` / 12.9.2.1260 full behavior (minus NixgramX policy removals).
- **Code adaptation for 12.10.x:** prefer absorbing explainable, buildable pieces from `a6c7d0a`, then continue to official `62b56a07…` 12.10.1 (7038).
- **Do not** blindly replace the tree with `a6c7d0a` as the product baseline.

### Limits of this audit

Deep line-by-line diff of all 300 files was not inlined into this tree (Cloud Agents unavailable; audit via GitHub compare + local 4335a2e-derived sources). Follow-up: local fetch of `a6c7d0a` and `DrKLO/Telegram@62b56a07` for a patch-level sync plan in `UPSTREAM_SYNC.md`.

## NagramX `_base` cut (as mirrored)

| Question | Finding |
| --- | --- |
| applicationId | `nu.gpu.nagram` → `nu.gpu.nagramx` via `APP_PACKAGE` |
| Flavors | None in `build.gradle`; same sources + property switch |
| APK name | `NagramX_base-v…` in GitHub Releases |
| CI | Release notes: “Release only, no CI updates” |
| Feature strip | Documented as without advanced features such as Save Deleted Messages; no public `productFlavors` / compile-flag in the 4335a2e tree — NixgramX gates Save Deleted family with `BuildConfig.IS_BASE` |

## Policy removals (NixgramX-only)

Ghost Mode, hide-typing (ghost intercept), and online-status enhance/hide are **removed-by-policy** in full and `_base` (not part of NagramX `_base` list).
