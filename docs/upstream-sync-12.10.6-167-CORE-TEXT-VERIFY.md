# 12.10.6 CORE-TEXT verify (Issue #167)

> **VERIFY-ONLY.** No production Java code change in this PR. L2 prepare `4faec58d` already applied the official rich-text / `parseInt` / `DispatchQueueMainThreadSync` delete onto `upstream-sync/12.10.6`. This document records the three-way evidence for Grok review.

_Verified: 2026-10-05 (Asia/Shanghai CST). Branch `upstream-task/12.10.6/167-core-text` from squash `72eb00141` (#165 into `upstream-sync/12.10.6`)._

## Coordinates

| Role | Value |
|---|---|
| Official OLD | `dc780e81ed1261c369c27870e8e0999a1eb0b600` (12.10.5 / 7105) |
| Official NEW | `f2908b14133bbffbf7ab04f641ecb5bfaf533242` (12.10.6 / 7112) |
| In between | `06c8275` (`Utilities.parseInt` bounds) → merge `e121ed9b` (#2094) → `f2908b14` |
| Nix tip | `72eb00141d28888bfc347701b4a2073daa853a64` |
| L2 apply | `4faec58d0` — `sync: Telegram 12.10.6 (7112)` |
| Compare | https://github.com/DrKLO/Telegram/compare/dc780e81ed1261c369c27870e8e0999a1eb0b600...f2908b14133bbffbf7ab04f641ecb5bfaf533242 |
| Scope | `docs/upstream-sync-12.10.6-SCOPE.md` § CORE-TEXT |

Commands used:

```bash
git fetch https://github.com/DrKLO/Telegram.git dc780e81… f2908b14… 06c8275…
git rev-parse OLD:PATH NEW:PATH HEAD:PATH
git diff OLD NEW -- PATH
git diff NEW HEAD -- PATH
rg -n 'getTextStyleRuns|TL_messageEntityCustomEmoji|MAX_STYLE_ENTITIES|DispatchQueueMainThreadSync' …
```

## Blob three-way

| Path | OLD | NEW | HEAD (`72eb0014`) | Notes |
|---|---|---|---|---|
| `…/MediaDataController.java` | `95495c32b…` | `754647fff…` | `d2f5f20b0…` | L2 applied style-run rewrite; Nix ≠ NEW due to pre-existing Neko/Nix locals elsewhere |
| `…/MessageObject.java` | `12c669c43…` | `905b514dc…` | `20e711dcc…` | Official +3 range-skip present; Nix ≠ NEW due to prior locals |
| `…/Utilities.java` | `29d76e995…` | `08b0e609f…` | `08b0e609f…` | **Nix ≡ NEW** |
| `…/DispatchQueueMainThreadSync.java` | `b69fa4c44…` | *(deleted)* | *(absent)* | L2 deleted with official |

## 1. `MediaDataController.java` — `getTextStyleRuns` safe/legacy

### Official OLD→NEW

- Caps: `MAX_STYLE_ENTITIES_COUNT=1000`, `MAX_LEGACY_STYLE_ENTITIES_COUNT=100`, `MAX_LEGACY_STYLE_RUNS_COUNT=100`, `MAX_LEGACY_STYLE_PROCESSING_STEPS=10_000`
- Entry `getTextStyleRuns` routes large lists to `getTextStyleRunsSafe`; small lists try `getTextStyleRunsLegacy` then fall back to Safe on `null`
- **Safe path:** copy entities without mutating network-owned objects; clamp with `entityStart + min(length, text.length()-start)`; **skip `TL_messageEntityCustomEmoji`**; boundary-array segment merge; clear+return if `runs.size() >= MAX_STYLE_RUNS_COUNT`
- **Legacy path:** same custom-emoji skip; processing-step / run-count caps return `null`; post-pass validates `run.start/end` ranges

### Nix tip evidence (`72eb0014`)

Markers present (line numbers from `rg` on sync tip):

| Marker | Line |
|---|---:|
| `MAX_STYLE_ENTITIES_COUNT` / legacy caps | 7083–7086 |
| `getTextStyleRuns` → Safe/Legacy dispatch | 7121–7126 |
| `getTextStyleRunsSafe` | 7129 |
| **Custom-emoji skip (Safe)** `if (entity instanceof TLRPC.TL_messageEntityCustomEmoji) { continue; }` | **7165** |
| `getTextStyleRunsLegacy` | 7262 |
| **Custom-emoji skip (Legacy)** in continue condition | **7281** |
| `MAX_LEGACY_STYLE_PROCESSING_STEPS` guard | 7318 |

**Critical Nix keep:** continue skipping `TL_messageEntityCustomEmoji` in both Safe and Legacy — matches official NEW and prior Nix/Nagram behavior. Do **not** drop these `continue`s when adapting future style-run changes.

### Nix locals intentionally untouched (non-overlapping with style-run rewrite)

`git diff NEW HEAD -- MediaDataController.java` is non-empty (~+159/−94) from pre-existing Neko/Nagram hooks (e.g. `NekoConfig` recent/fave stickers, `EntitiesHelper` / `NaConfig` imports, unlimited-fave merge, etc.). **None of those live inside the `getTextStyleRuns*` body.** Leave as-is; do not overwrite the whole file.

**Handling:** VERIFY-only — no production edit.

## 2. `MessageObject.java` — invalid style-run ranges

### Official OLD→NEW (+3)

In `addEntitiesToText` run loop, before applying spans:

```java
if (run.start < 0 || run.start >= run.end || run.end > text.length()) {
    continue;
}
```

### Nix tip

Same guard at **L8362–8364**. Extra redundant Nix check remains at L8366–8367 (`if (run.start >= run.end) continue;`) — harmless, keep. Other Nix locals (e.g. `SyntaxHighlight.highlight` on hashtags, `MessageHelper.getEntitiesForText`) are outside this hunk.

**Handling:** VERIFY-only — no production edit.

## 3. `Utilities.java` — `parseInt` bounds

### Official (`06c8275` / #2094)

- On first non-digit after `start`, **break** (do not `end++`)
- Accept substring only if `start >= 0 && (end - start > 1 || value.charAt(start) != '-')` — rejects lone `-`

### Nix tip

`git diff NEW HEAD -- Utilities.java` is **empty** (blob `08b0e609f…`). Confirmed in tree:

```java
} else if (!allowedChar && start >= 0) {
    break;
}
…
if (start >= 0 && (end - start > 1 || value.charAt(start) != '-')) {
```

**Handling:** VERIFY-only — no edit.

## 4. `DispatchQueueMainThreadSync.java` — confirm removed

| Check | Result |
|---|---|
| Present on OLD | yes (`b69fa4c44…`) |
| Present on NEW | **no** (deleted in 12.10.6) |
| Present on HEAD | **no** |
| Stray Java refs on NixgramX tip | none found (file gone; L2 clean-delete; no compile-time call sites surfaced on sync tip) |

If a future compile proves a live reference, stop with **NEEDS HUMAN** — do not invent a replacement.

**Handling:** VERIFY-only — no edit.

## Conclusion

| File | Official applied? | Custom-emoji skip? | Action |
|---|---|---|---|
| `MediaDataController.java` | Yes (Safe/Legacy + caps via `4faec58d`) | **Yes** Safe L7165 + Legacy L7281 | No code change |
| `MessageObject.java` | Yes (range skip L8362) | n/a (runs already exclude custom emoji) | No code change |
| `Utilities.java` | Yes (Nix ≡ NEW) | n/a | No code change |
| `DispatchQueueMainThreadSync.java` | Deleted on tip | n/a | No code change |

Do **not** mutate network-owned entities; do **not** drop custom-emoji style-run skips; do **not** touch ChatMessageCell / VoIP / InstantCamera / package / `NIXGRAMX_VERSION_*`. Device: **NOT TESTED**.

Refs #167
