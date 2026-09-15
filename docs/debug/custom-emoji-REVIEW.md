# Agent 6 — Independent Review (`968371fc51`)

Reviewer: Grok (Independent Reviewer). **Did not implement.** Device **not** tested.

## Verdict: **WARNING**

Code intent matches the agreed minimal fix and upstream Telegram break-strategy shape. Residual product risk: EN→ZH “replace original” bubbles may again stay English-wide (the original reason for `2323cc765b`). Hold merge until that scenario is device-checked; not a BLOCK on the emoji-overlap logic itself.

PR: https://github.com/Anndy999/NixgramX/pull/59
Commit: `968371fc51494060e92eaa5acb6d0a29d03d9cfc`
Production diff: `MessageObject.makeStaticLayout` only (docs + ROOT_CAUSE also in PR).

## What changed (code)

- First `StaticLayout` build always `BREAK_STRATEGY_HIGH_QUALITY`.
- Rebuild with `BREAK_STRATEGY_SIMPLE` only if any `getLineRight(l) > width` (unchanged overflow path).
- Removed CJK-forced SIMPLE (`containsCjk` gate in `makeStaticLayout`).
- **Not changed:** `AnimatedEmojiSpan.java`, reply crossfade / `animateOut*`, `shrinkWidthToVisualContent`, `getLastLineWidthForTime` / `ebcfc260` clock logic, `ChatMessageCell` draw order.

## Checklist

| # | Risk | Code assessment |
| --- | --- | --- |
| 1 | 中文翻译气泡过宽 | **WARNING** — HQ again for CJK may under-fill; shrink still clamps bubble to visual width but may not fully restore the 2323cc765b bubble-fit win. Device-verify EN→ZH replace-original. |
| 2 | EN/ZH 叠字 | **SAFE** — does not restore dual old+new layout paint (`b2f2788` / `5ba817c3` untouched). |
| 3 | 翻译硬闪 | **SAFE** — no transition/animation edits. |
| 4 | timestamp 压字 | **SAFE** — `lastLineWidth` / time reservation paths untouched. |
| 5 | Reply Custom Emoji | **SAFE (this patch)** — reply builder was already SIMPLE; not gated by `containsCjk`. Separate Agent-4 lifecycle risks remain out of scope. |
| 6 | 普通消息 Custom Emoji | **SAFE (expected)** — CJK returns to HQ-first like official; addresses SIMPLE-forced span placement. |
| 7 | 英文布局 | **SAFE** — Latin already used HQ-first. |
| 8 | 中文正常换行 | **WARNING** — wrap points may differ from forced-SIMPLE; intentional alignment with official. Device-spot-check multi-line ZH. |
| 9 | caption Emoji | **SAFE (expected)** — same `makeStaticLayout` / shrink callers. |
| 10 | quote / code | **SAFE** — no quote/code path edits. |

## Regression commit addressed

`2323cc765b` forced SIMPLE whenever `containsCjk` — Agent 2/3 evidence that this diverges from DrKLO HQ-first and changes ReplacementSpan placement for CJK+custom emoji. This patch undoes that gate only.

## Gaps / residual

- **Not device tested** (Agent 6 cannot claim emoji overlap is fixed on hardware).
- Wide ZH bubble after EN→ZH remains the main product WARNING.
- `containsCjk` helper still exists (unused by `makeStaticLayout` now) — harmless leftover; optional cleanup later, not required for SAFE.
- Agent-4 reply stack / spoiler patched-layout hypotheses are **not** addressed by this PR (correct: out of minimal patch).

## Recommendation

- **WARNING** — OK to proceed to device Beta / QA for: ZH+custom emoji overlap, EN→ZH bubble width, multi-line ZH wrap, caption emoji.
- Do **not** merge as “fully verified” until those device checks pass.
- Do **not** expand patch (no emoji padding, no AnimatedEmojiSpan rewrite) unless device fails after this HQ-first restore.
