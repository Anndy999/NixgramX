# CJK layout vs custom emoji coordinates

Docs-only audit of how Chinese (and other CJK) takes a different `StaticLayout` path in NixgramX, and how `AnimatedEmojiSpan.measuredSize` relates to the start of the following text. No production code was changed for this write-up.

Companion: `docs/debug/custom-emoji-UPSTREAM_DIFF.md`.

## Commits read (`git show`)

| Short | Full SHA | Role |
| --- | --- | --- |
| `2323cc765b` | `2323cc765b14a37bffb7cf16eb984e914437ad30` | Introduces `containsCjk`, SIMPLE for CJK, `shrinkWidthToVisualContent`. Also clamped `lastLineWidth` to shrunken `textWidth` (later undone). |
| `ebcfc260` | `ebcfc26014f1df49a15022f441344078471a653b` | Stops clamping `lastLineWidth`. Adds `getLastLineWidthForTime()` using `lineRight - lineLeft`. |
| `b2f2788` | `b2f2788bff3cfd15b636c1573fdc448c25d9b482` | Translate toggle: do not paint outgoing + incoming layouts at the same `textX/textY`. |
| `fa9ccd14` | `fa9ccd14b148fc04de941d55159e351c6d754e49` | Restore official list MOVE; `animateOut*` stays null. Does not change layout math. |

Related (not in the four-SHA list, but on the same files): `aa2393e3` (time ReplacementSpans), `5ba817c3` (incoming-only fade).

---

## 1. Why Chinese takes a different layout path

### Gate: `MessageObject.containsCjk`

```java
// MessageObject.java:8707-8720  (added 2323cc765b)
public static boolean containsCjk(CharSequence text) {
    ...
    if (cp >= 0x4E00 && cp <= 0x9FFF || cp >= 0x3400 && cp <= 0x4DBF
            || cp >= 0x3040 && cp <= 0x30FF || cp >= 0xAC00 && cp <= 0xD7AF
            || cp >= 0xF900 && cp <= 0xFAFF) {
        return true;
    }
}
```

Any Han / kana / hangul code point in the **entire** `CharSequence` (message body, caption block, translated text, including strings that also hold `AnimatedEmojiSpan`s) flips the path. Custom-emoji-only messages (no CJK) stay on the Latin path.

### `makeStaticLayout` — two strategies

```java
// MessageObject.java:8748-8776  (2323cc765b)
final int breakStrategy = containsCjk(text)
        ? StaticLayout.BREAK_STRATEGY_SIMPLE
        : StaticLayout.BREAK_STRATEGY_HIGH_QUALITY;
...
.setBreakStrategy(breakStrategy)
...
if (realWidthLarger) { // any getLineRight(l) > width
    builder = ... .setBreakStrategy(StaticLayout.BREAK_STRATEGY_SIMPLE) ...
}
```

| | Latin / no CJK (and official Telegram for all languages) | NixgramX CJK |
| --- | --- | --- |
| First build | `BREAK_STRATEGY_HIGH_QUALITY` | `BREAK_STRATEGY_SIMPLE` |
| Fallback | SIMPLE only if `lineRight > width` | Already SIMPLE; fallback is a no-op unless something still overflows |
| Hyphenation | `HYPHENATION_FREQUENCY_NONE` in both | same |
| Layout constructor width | `maxWidth` (bubble max) | same — **not** the later shrunken `textWidth` |

`2323cc765b` commit message, quote:

> Replace-original EN→ZH left the bubble at the English width because CJK StaticLayout used `BREAK_STRATEGY_HIGH_QUALITY` and never filled the line. Use SIMPLE for CJK and size the bubble to visual glyph width.

HIGH_QUALITY looks for word boundaries / hyphenation opportunities. CJK has no spaces, so Android typically leaves the line **short** inside a **wide** layout (`getLineRight` stays ≤ `width`, so official SIMPLE fallback does not fire). SIMPLE may break between any two CJK code points (and around a `ReplacementSpan`), so lines actually fill toward `maxWidth`, then wrap.

### After layout: shrink the bubble, not the `StaticLayout`

```java
// MessageObject.java:8723-8739, called from generateLayout:9245
// and TextLayoutBlocks:9696
public static int shrinkWidthToVisualContent(ArrayList<TextLayoutBlock> blocks, int currentWidth) {
    ...
    visualWidth = Math.max(visualWidth, getLayoutContentWidth(block.textLayout) + extra);
    return Math.min(currentWidth, visualWidth);
}

// getLayoutContentWidth:8701-8702
w = Math.max(w, (int) Math.ceil(layout.getLineRight(i) - layout.getLineLeft(i)));
```

`textWidth` (bubble) can become the visual run width. `block.textLayout.getWidth()` remains the original `maxWidth`. Glyph / span coordinates inside the layout **do not** reflow when the bubble shrinks.

`ebcfc260` then **refused** to clamp `lastLineWidth` to that shrunken `textWidth`, because EN after 显示原文 was being tucked under the clock. Time reservation now uses `getLastLineWidthForTime()` (`:1844`), which is `max(lastLineWidth, ceil(lineRight - lineLeft) + quote/code extra)` — the same visual metric, but only for the last line.

### Paths that do **not** take the CJK gate

- `replyTextLayout`: always SIMPLE in both trees (`ChatMessageCell.java:19892`).
- `StaticLayoutEx`: HIGH_QUALITY first, SIMPLE if overflow — no `containsCjk`.
- Voice transcription height: hard-coded HIGH_QUALITY (`MessageObject.java:7622`).

### Why this matters for custom emoji

Custom emoji is a `ReplacementSpan`. HIGH_QUALITY’s justification pass is the Android path that often **ignores** `ReplacementSpan.getSize()` when placing the next cluster. SIMPLE uses the span width as a box. Forcing SIMPLE for CJK was done for bubble width, but it is also a different wrap/positioning model for “汉字 + custom emoji” than official Telegram (which leaves CJK on HIGH_QUALITY when it does not overflow).

---

## 2. How span width relates to text start position

### Slot vs drawable (same in NixgramX and official)

`AnimatedEmojiSpan` is byte-identical to DrKLO/Telegram `62b56a07`.

```
StaticLayout LTR run:

  [  CJK ... ][  ReplacementSpan slot  ][ next CJK ... ]
              ^                         ^
              x = getPrimaryHorizontal(spanStart)
              slot width = getSize() = max(0, measuredSize - 1)
              next glyph starts at x + getSize()

  drawable (drawn later, not by ReplacementSpan.draw):
              center = x + measuredSize / 2
              box    = [x, x + measuredSize]
```

Evidence:

```java
// AnimatedEmojiSpan.getSize:261-296
measuredSize = (int) (size * scale);          // size from fontMetrics ascent+descent, default scale 1.2f
return Math.max(0, measuredSize - 1);

// draw:351  (called from StaticLayout.draw / SpoilerEffect.renderWithRipple)
float cx = x + measuredSize / 2f;

// SpansChunk.draw:1004-1008
float halfSide = holder.span.measuredSize / 2f;
holder.drawableBounds.set((int) (cx - halfSide), ..., (int) (cx + halfSide), ...);
```

`ReplacementSpan.draw` **does not paint**. It only stores `lastDrawnCx/Cy` and `spanDrawn`. `ChatMessageCell.drawMessageText` draws the `StaticLayout` first (`:17247-17248`); `drawAnimatedEmojis` later (`:21496` → `:21840`) blits the drawable at that center, after the same `canvas.translate(textX - rtl, textY + block.textYOffset + padTop)` (`:21815` vs text `:17087`).

### Numeric relation

Let `S = measuredSize` (int). Next CJK cluster origin in layout space:

```
next_x = span_x + max(0, S - 1)
emoji_right = span_x + S
overlap_into_next = 1 px          // if S >= 1
```

Default `scale = 1.2f` (`AnimatedEmojiSpan:154,164`) makes `S` ~1.2 × line height, so the reserved slot is **1 px thinner than the square that is painted**. Latin HIGH_QUALITY often has extra inter-glyph space; CJK SIMPLE does not. That 1 px is the only **intentional** overlap in the span contract. Anything larger is wrap/strategy or stale coordinates (section 3).

### `getLineLeft` / `getLineRight` / `getPrimaryHorizontal` vs `measuredSize`

`generateLayout` never reads `measuredSize`. It sizes the bubble from layout line metrics:

| Metric | Where | What it is on a mixed CJK+emoji line |
| --- | --- | --- |
| `getLineLeft(n)` | `MessageObject.java:9107,9173` | LTR usually `0`. Non-zero ⇒ `textXOffset` and `FLAG_RTL`. |
| `getLineWidth(n)` | `:9121,9161` | Advance of the line; feeds `lastLineWidth` / `textWidth` **before** shrink. |
| `getLineRight(n) - getLineLeft(n)` | `getLayoutContentWidth:8702`, `getLastLineWidthForTime:1850` | Visual run used to shrink the bubble and reserve the clock. |
| `getPrimaryHorizontal(offset)` | only the “Read more” trim (`:8899`) | Not used for emoji placement. |
| `ReplacementSpan` `x` in `draw` | Android `StaticLayout` | Should equal `getPrimaryHorizontal(spanStart)` for LTR. |

If `getLineRight` on a line that **ends** with a custom emoji is closer to `span_x + (S-1)` than `span_x + S`, shrink/time math matches the slot, not the drawable. The extra 1 px of emoji can sit on the clock or on the next 汉字, depending on whether that next cluster is on the same line.

### Hit-testing uses line box, not `measuredSize`

```java
// ChatMessageCell.checkTextBlockMotionEvent:2487-2500
final int line = block.textLayout.getLineForVertical(y);
final int off  = block.charactersOffset + block.textLayout.getOffsetForHorizontal(line, x);
final float left = block.textLayout.getLineLeft(line);
if (left <= x && left + block.textLayout.getLineWidth(line) >= x) {
    link = buffer.getSpans(off, off, AnimatedEmojiSpan.class);
```

`x` here is already shifted by `textX - (rtl ? textXOffset : 0)` (`:2468`). Touch uses `getLineWidth`, not the drawable square.

### RTL

If `lineLeft > 0` or paragraph direction is RTL (`:9183`), `textXOffset` tracks min `lineLeft`. Drawing subtracts it only when `block.isRtl()` (`:17087`, `:21815`). Emoji `lastDrawnCx` is in layout space, so it stays aligned with CJK **as long as** both use that same translate. A stale `lastDrawnCx` from an LTR layout reused after an RTL relayout would miss by `textXOffset`.

---

## 3. Coordinates before vs after line breaks

### Before wrap (single line, LTR CJK + emoji)

```
layout width = maxWidth          // still the wide constructor width
textWidth    = min(maxWidth, visual)   // after 2323cc765b shrink

canvas origin for glyphs+emoji = (textX, textY + padTop)

span i at layout x_i
  reserved [x_i, x_i + S - 1]
  drawn    [x_i, x_i + S]
next CJK   at x_i + S - 1
```

`textX` itself can still move after shrink: if `textXOffset != 0 && replyNameLayout != null`, `ChatMessageCell.java:16815-16822` adds `backgroundWidth - 31dp - textWidth` (minus time). That shifts **all** glyphs and emoji together. It does not change intra-line overlap.

HIGH_QUALITY (official CJK, NixgramX Latin): the same string may stay on one line with a large unused tail (`lineRight << width`). SIMPLE (NixgramX CJK): the run fills toward `maxWidth` and the emoji is more likely to be the wrap candidate.

### At the wrap decision

Android asks: does `getSize()` fit in the remaining width?

- Remaining `R >= S - 1` → span stays on this line; following CJK may wrap instead (SIMPLE can break before the next 汉字).
- Remaining `R < S - 1` → span moves to the next line. The `-1` exists so `R == S` still wraps (comment `AnimatedEmojiSpan.java:80-83`).

`lockPositionChanging` (`:47`, `:356`) skips writing a new `lastDrawnCx` during the add animation so the emoji does not flicker while wrapping. During that window `SpansChunk.draw` still uses the **old** center if `spanDrawn` is already true (`:1000-1007`).

### After wrap (emoji on the next line)

```
previous line: CJK only; lineRight ≈ last CJK advance
new line:      lineLeft ≈ 0 (LTR)
               span_x ≈ 0  (or LeadingMarginSpan on reply quotes)
               next CJK on that line at (S - 1)
```

`generateLayout` rebuilds a **new** `StaticLayout` (`makeStaticLayout` at `:8872` / block `:9091`). `AnimatedEmojiSpan` **instances** on the `Spanned` are reused. `AnimatedEmojiSpan.update` (`:584-587`) reuses a holder only when `holder.span == span && holder.layout == textLayout`. New layout object ⇒ new holder, but the span still carries:

- `lastDrawnCx` / `lastDrawnCy` from the previous line
- `spanDrawn == true`

Until the new layout is `draw()`n, `drawAnimatedEmojis` paints at the **pre-wrap** center. `ChatMessageCell` order is text-then-emoji in one `onDraw`, so a correct frame updates `lastDrawnCx` first — **unless** `lockPositionChanging` or `animateChanges()` returns early (`draw:353-357`).

`2323cc765b` makes a width-only shrink look like a content change (`textWidth != lastDrawingTextWidth` ⇒ `sameText = false`). `5ba817c3` then sets `animateOutTextBlocks = null` and rebuilds `animatedEmojiStack` from the new blocks. Outgoing clone (`update(..., lastDrawingTextBlocks, true)`) is **not** created (official still does). Incoming spans may therefore keep pre-toggle centers for a frame, on the new line’s CJK.

### After wrap: bubble vs layout coordinates

Shrink uses max over lines of `lineRight - lineLeft`. A wrapped last line that is only a custom emoji contributes ~`S-1` (slot), not `S` (drawable). `ebcfc260` keeps `lastLineWidth` as the real last-line advance so the clock does not sit on that line’s glyphs; it does not widen the reserved span.

Multi-line `textXOffset` is `min(lineLeft)` (`:9184`). A post-wrap line with `lineLeft == 0` and an earlier RTL line can still set `hasRtl` / `textXOffset`. Emoji on the LTR wrapped line is drawn without subtracting `textXOffset` (`isRtl()` is per-block flags). If SIMPLE wrap splits a mixed line into one RTL-looking leftover (`lineLeft > 0` because HIGH_QUALITY vs SIMPLE changed paragraph direction), `FLAG_RTL` on the block shifts the whole block — emoji and CJK together.

### Reply quotes (always SIMPLE)

`replyTextLayout` SIMPLE + optional `LeadingMarginSpan` for quote thumbnails (`ChatMessageCell.java:19876-19880`). `replyTextOffset = min(lineLeft)` (`:19911-19918`). RTL with offset > 0 places the layout at `replySelectorRect.right - 8dp - layout.getWidth()` (`:23239`). Emoji is drawn in that same canvas (`:23245`). Wrap inside a 5-line quote uses the same `S-1` slot. `b2f2788` removed the outgoing reply-emoji crossfade; it did not change these coordinates.

### Translate toggle (EN line-breaks vs ZH line-breaks)

EN HIGH_QUALITY wrap points ≠ ZH SIMPLE wrap points. Same `AnimatedEmojiSpan` object can jump from mid-line (English) to line-start (Chinese) or the reverse. Official composites both layouts (`animateOutAnimateEmoji` clone + current stack) at one `textX/textY` — that is the 叠字 `b2f2788` killed. After `b2f2788`/`5ba817c3`/`fa9ccd14`, only the incoming layout is drawn; MOVE animates the cell. Remaining overlap after a break is then:

1. 1 px from `measuredSize - 1` (static, CJK-visible), or
2. one-frame stale `lastDrawnCx` from the pre-break line, or
3. `getLineRight`/`getSize` mismatch placing the next 汉字 at `x + S - 1` while the drawable still occupies `x + S`.

---

## Evidence map (file:symbol)

| Claim | Location |
| --- | --- |
| CJK ⇒ SIMPLE | `MessageObject.containsCjk`, `MessageObject.makeStaticLayout` |
| Bubble shrink to visual run | `MessageObject.shrinkWidthToVisualContent`, `generateLayout:9245`, `TextLayoutBlocks:9696` |
| Do not clamp last line to shrink | `ebcfc260` comment at `generateLayout:9246`; `getLastLineWidthForTime` |
| Span slot = `measuredSize - 1` | `AnimatedEmojiSpan.getSize` |
| Drawable = square `measuredSize` at `lastDrawnCx` | `AnimatedEmojiSpan.draw`, `SpansChunk.draw` |
| Text and emoji share translate | `ChatMessageCell.drawMessageText:17087`, `drawAnimatedEmojiMessageText:21815` |
| Width change counts as new text | `ChatMessageCell.TransitionParams.animateChange` (`2323cc765b`) |
| No outgoing glyph/emoji paint | `animateOutTextBlocks = null` (`b2f2788` / `5ba817c3`); draw still calls the null branch |
| Reply always SIMPLE | `ChatMessageCell` `slb.setBreakStrategy(LineBreaker.BREAK_STRATEGY_SIMPLE)` |
