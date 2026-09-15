# Custom emoji overlap — root cause

## Observed symptom

In NixgramX chat messages containing Telegram Custom Emoji / Animated Emoji, Chinese (CJK) text sometimes shows emoji covering or misaligned with adjacent glyphs. English-only strings with the same emoji usually look fine. Affects normal bubbles and can appear in reply layouts that share `makeStaticLayout`.

## Evidence

- Agent 2 upstream diff (`custom-emoji-UPSTREAM_DIFF.md`): NixgramX `MessageObject.makeStaticLayout` forced `BREAK_STRATEGY_SIMPLE` whenever `containsCjk(text)` was true (`2323cc765b`). Official Telegram always builds with `BREAK_STRATEGY_HIGH_QUALITY`, then rebuilds with `BREAK_STRATEGY_SIMPLE` only if any `getLineRight(l) > width`.
- Agent 3 CJK layout (`custom-emoji-CJK_LAYOUT.md`): CJK under HIGH_QUALITY often under-fills lines (few spaces); forcing SIMPLE changes wrap/metrics for ReplacementSpan slots. `AnimatedEmojiSpan` (including `measuredSize - 1`) matches upstream.
- Agent 4 reply lifecycle (`custom-emoji-REPLY_LIFECYCLE.md`): Does **not** support “reply emoji stack permanently bound to deleted old Layout” as the primary Chinese-only cause; both sides use the same layout factory for reply text.
- `shrinkWidthToVisualContent()` only shrinks bubble `textWidth` after layout; it does not rewrite StaticLayout glyph / span coordinates.

## Root cause

**A. StaticLayout CJK regression.** Forcing SIMPLE for any CharSequence that contains CJK changed line breaking and horizontal metrics vs official HQ-first behavior, exposing custom-emoji ReplacementSpan slot / neighbor-glyph overlap that English HQ layouts usually hide.

## Why Chinese only

CJK detection routes those strings into SIMPLE immediately. Latin-only messages stay on HIGH_QUALITY (unless a line overflows), matching upstream more closely.

## Why English works

English messages do not trip `containsCjk`, so they use HIGH_QUALITY (+ optional overflow SIMPLE), aligned with official Telegram.

## Affected path

- `MessageObject.makeStaticLayout(...)` used by message / caption / related text blocks that go through this helper, including CJK bubbles with custom emoji.

## Unaffected path

- `AnimatedEmojiSpan.java` (matches upstream; not the first fix target)
- Official-style overflow SIMPLE fallback when `lineRight > width`
- Voice transcription hard-coded HQ path
- Translation crossfade / `animateOut*` (not required for this minimal fix)

## Regression commit

`2323cc765b14a37bffb7cf16eb984e914437ad30` — “fix: shrink translated bubbles and skip blur during attach pinch-zoom” introduced CJK → SIMPLE.

## Recommended minimal fix

Restore official break-strategy policy in `makeStaticLayout`:

1. Always start with `BREAK_STRATEGY_HIGH_QUALITY`.
2. Keep existing rebuild with `BREAK_STRATEGY_SIMPLE` only when a line’s `getLineRight` exceeds width.
3. Do **not** force SIMPLE via `containsCjk`.
4. Leave `containsCjk` / `shrinkWidthToVisualContent` in tree for bubble-width contrast (optional follow-up), but they must not drive break strategy.
5. Do **not** edit `AnimatedEmojiSpan.java`.
6. Do **not** restore simultaneous old+new layout drawing (would reintroduce EN/ZH overlay).

## Alternative fixes rejected

- Padding / +N dp visual patches on emoji draw
- Rewriting `AnimatedEmojiSpan` first
- Restoring dual old+new layout draw for translation
- Removing `shrinkWidthToVisualContent` in the same patch without separate bubble-width verification

## Implementation note (this PR)

Code change: `MessageObject.makeStaticLayout` initial strategy = HIGH_QUALITY only; overflow SIMPLE retained. Docs-only investigations remain under `docs/debug/`.
