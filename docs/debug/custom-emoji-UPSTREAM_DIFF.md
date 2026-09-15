# Custom emoji / CJK — NixgramX vs official Telegram Android

Docs-only audit. No production Java/Kotlin/XML was changed for this write-up.

## Comparison basis

| Side | Source | SHA | Note |
| --- | --- | --- | --- |
| Official Telegram Android | [DrKLO/Telegram](https://github.com/DrKLO/Telegram) `master` | `62b56a07ca7e30e39f7fd00a6728d6bbd716ca1c` | Message `update to 12.10.1 (7038)`, 2026-08-25. Fetched 2026-09-15 via `raw.githubusercontent.com` (HTTP 200). |
| NixgramX declared base | `docs/upstream-base.json` | same `62b56a07ca7e30e39f7fd00a6728d6bbd716ca1c` | `"version": "12.10.1", "build": "7038"` |
| NixgramX tree at audit | branch `fix/cjk-custom-emoji-overlap` | `1907172aaea1d652bfecd28b4f9cfaa5bc8301d2` | Equals `main` / `origin/main` at start of this work |

Files compared (byte-level / function extract):

- `TMessagesProj/src/main/java/org/telegram/messenger/MessageObject.java` — `makeStaticLayout`, `generateLayout`, `TextLayoutBlocks`
- `TMessagesProj/src/main/java/org/telegram/ui/Cells/ChatMessageCell.java` — custom-emoji draw / `updateAnimatedEmojis` / `replyTextLayout` / `TransitionParams.animateChange`
- `TMessagesProj/src/main/java/org/telegram/ui/Components/AnimatedEmojiSpan.java` — `update`, `getSize`, `draw`, `SpansChunk.draw`

Upstream fetch: **succeeded**. No `INSUFFICIENT_EVIDENCE` for these three files.

---

## 1. NixgramX-only changes (paths / commits)

`AnimatedEmojiSpan.java` is **byte-identical** to DrKLO/Telegram `62b56a07`. Git history on that path in NixgramX is only bootstrap/desugar (`9f2e88d029ae2b6027730079232f9743d4f05412`); no later functional commit.

`MessageObject.generateLayout` body is the official function plus the two helpers below (diff after stripping those inserts: one blank line).

### 1.1 Layout / CJK width — `MessageObject.java`

| SHA | Subject | What landed |
| --- | --- | --- |
| `2323cc765b14a37bffb7cf16eb984e914437ad30` | `fix: shrink translated bubbles and skip blur during attach pinch-zoom` | Adds `containsCjk`, `getLayoutContentWidth`, `shrinkWidthToVisualContent`. `makeStaticLayout` picks `BREAK_STRATEGY_SIMPLE` when any CJK/kana/hangul code point is present, else official `HIGH_QUALITY`. After `generateLayout` / `TextLayoutBlocks`, `textWidth = shrinkWidthToVisualContent(...)`. Originally also clamped `lastLineWidth` to the shrunken `textWidth`. |
| `ebcfc26014f1df49a15022f441344078471a653b` | `fix: keep last-line timestamp space after toggling chat translate` | Adds `getLastLineWidthForTime()` (`ceil(lineRight - lineLeft)` on the last block). **Stops** clamping `lastLineWidth` to shrunken `textWidth` (`// lastLineWidth stays the real last-line width so the timestamp never sits on glyphs.`). Sets `messageOwner.translated` in sync with `translated`. |

`containsCjk` (`MessageObject.java:8707`) returns true on:

- U+4E00–U+9FFF CJK Unified Ideographs
- U+3400–U+4DBF Extension A
- U+3040–U+30FF Hiragana/Katakana
- U+AC00–U+D7AF Hangul Syllables
- U+F900–U+FAFF CJK Compatibility Ideographs

It does **not** cover CJK Ext. B+ (U+20000+), bopomofo, or fullwidth punctuation. One matching code point is enough for the **whole** `CharSequence` (including custom-emoji spans in the same string) to take the SIMPLE path.

### 1.2 Chat bubble / emoji draw — `ChatMessageCell.java`

| SHA | Subject | What landed (emoji / CJK-adjacent) |
| --- | --- | --- |
| `2323cc765b` | shrink translated bubbles | `TransitionParams.animateChange`: `textWidth != lastDrawingTextWidth` alone sets `sameText = false` (official also requires `lastDrawingSideMenuEnabled != isSideMenuEnabled`). |
| `ebcfc260` | last-line timestamp space | `getLastLineWidthForTime()` for time-row reservation; reset `transitionParams.deltaLeft/Right` when `isTranslated()` flips. |
| `aa2393e3f2e4dc7a956a7be0bab661c5875c955f` | reserve last-line space for translate badge + time (#20) | `HintView2.measureCorrectly` for time width (ReplacementSpans in the clock row). Extra `newLineForTime` when translated metadata cannot share the last glyph line. |
| `b2f2788bff3cfd15b636c1573fdc448c25d9b482` | 翻译切换时原文和译文不再叠字 (#25) | Stops drawing `animateOutTextBlocks` / `animateOutAnimateEmoji` on top of the incoming layout at the same `textX/textY`. Drops reply-quote crossfade (`animateReplyTextLayout` + `animateOutAnimateEmojiReply`). Sets `animateOutTextBlocks = null` on content swap. |
| `5ba817c37c421ec6a5074814eb8c0e91c0d4c23b` | restore incoming-only translate swap animation (#28) | Re-enables `animateMessageText = true` but **keeps** `animateOutTextBlocks = null` (“Fade incoming glyphs only”). Same for caption replace. |
| `fa9ccd14b148fc04de941d55159e351c6d754e49` | restore official translate MOVE | Comment-only on `startChangeAnimation()` (NixgramX-only method; absent upstream). |

### 1.3 `replyTextLayout` construction

Builder still uses official `LineBreaker.BREAK_STRATEGY_SIMPLE` (not CJK-conditional). NixgramX-only clamp vs `62b56a07`:

```text
// official ChatMessageCell.java:19619
StaticLayout.Builder.obtain(..., textPaint, maxWidth)

// NixgramX ChatMessageCell.java:19889
StaticLayout.Builder.obtain(..., textPaint, Math.max(dp(1), maxWidth))
```

Same `Math.max(dp(1), maxWidth)` on the pre-M `StaticLayout` fallback (`ChatMessageCell.java:19903` vs official `:19633`). Not from the four named CJK commits (present since the 12.10.1 port / NagramX lineage). Prevents `StaticLayout` width ≤ 0; does not change break strategy.

### 1.4 Other paths that call `makeStaticLayout`

All go through the CJK SIMPLE gate once `2323cc765b` landed:

- `MessageObject.generateLayout` (`:8872`, `:8918`, `:9051`, `:9091`)
- `MessageObject.TextLayoutBlocks` ctor (captions / keep-original) (`:9352`, `:9397`, `:9517`, `:9544`)
- `RichMessageLayout.java:2469`

`StaticLayoutEx.createStaticLayout*` is **not** CJK-aware (still official HIGH_QUALITY → SIMPLE-if-overflow). Voice transcription (`MessageObject.measureVoiceTranscriptionHeight:7622`) is hard-coded `BREAK_STRATEGY_HIGH_QUALITY`.

---

## 2. Official Telegram logic in the same areas

### 2.1 `MessageObject.makeStaticLayout` (`62b56a07` `:8584`)

Always:

1. `StaticLayout.Builder` with `BREAK_STRATEGY_HIGH_QUALITY`, `HYPHENATION_FREQUENCY_NONE`.
2. If any `layout.getLineRight(l) > width`, rebuild with `BREAK_STRATEGY_SIMPLE`.

No `containsCjk`. No post-pass `textWidth` shrink. `generateLayout` ends at `hasWideCode = ...` with `textWidth` from `max(lineWidth)` / `lineWidth + lineLeft` only (`:9083`).

CJK under HIGH_QUALITY typically **under-fills** the line (no spaces to justify against) rather than overflowing, so the SIMPLE fallback often **does not run**. That is the bug `2323cc765b` describes: EN→ZH replace-original left the bubble at English `maxWidth`.

### 2.2 `generateLayout` line metrics (shared)

Both trees, after each block (`NixgramX MessageObject.java:9106` / official `:8944` area):

- `lastLeft = textLayout.getLineLeft(last)`
- `lastLine = textLayout.getLineWidth(last)` (+ quote 32dp / code 15dp)
- `textXOffset = min(textXOffset, lineLeft)` when `lineLeft > 0` or paragraph is RTL
- Multi-line: `textRealMaxWidth = max(lineWidth)`, `textRealMaxWidthWithLeft = max(lineWidth + lineLeft)`
- If any LTR line with `lineLeft == 0`, `hasNonRTL` → use width-with-left

NixgramX then does `textWidth = shrinkWidthToVisualContent(textLayoutBlocks, textWidth)` (`:9245`). That uses `ceil(getLineRight(i) - getLineLeft(i))`, **not** `getLineWidth`. Official never does this.

### 2.3 `AnimatedEmojiSpan` (identical)

Quotes from the shared file:

```java
// AnimatedEmojiSpan.java:80-83
/**
 * To correctly move emoji to a new line, we need to return the final size in {@link #getSize}.
 * However, this approach causes flickering. So fix this using {@link #lockPositionChanging} flag.
 */
```

```java
// getSize :236-:296
measuredSize = (int) (size * scale);  // or sz * scale if fontMetrics == null
return Math.max(0, measuredSize - 1);
```

```java
// draw :348-:364  — records center, does not paint the glyph
float cx = x + measuredSize / 2f;
float cy = top + (bottom - top) / 2f;
lastDrawnCx = cx; lastDrawnCy = cy; spanDrawn = true;
```

```java
// SpansChunk.draw :1004-:1008
float halfSide = holder.span.measuredSize / 2f;
cx = holder.span.lastDrawnCx;
cy = holder.span.lastDrawnCy;
holder.drawableBounds.set((int) (cx - halfSide), (int) (cy - halfSide),
                          (int) (cx + halfSide), (int) (cy + halfSide));
```

`update(...)` (`:552`) walks each `Layout`, `getSpans(..., AnimatedEmojiSpan.class)`, reuses a holder when `holder.span == span && holder.layout == textLayout`, else builds a new `AnimatedEmojiDrawable`. Stale holders whose layout is not in the new varargs are removed.

Default ctor scale is `1.2f` (`:154`, `:164`).

### 2.4 `ChatMessageCell` custom emoji (official)

- `updateAnimatedEmojis()` (`official :6214` / NixgramX `:6266`): `AnimatedEmojiSpan.update(cache, this, ..., textLayoutBlocks)` or caption blocks.
- `drawMessageText` first: `SpoilerEffect.renderWithRipple(..., block.textLayout, ...)` → `StaticLayout.draw` → `AnimatedEmojiSpan.draw` writes `lastDrawnCx/Cy`.
- Later `drawAnimatedEmojis` (`official :21423`) paints drawables at those centers, with the same `canvas.translate(textX - rtlOffset, textY + block.textYOffset + ...)` as the text (`drawAnimatedEmojiMessageText` NixgramX `:21815`).
- On `animateChange` when block text strings differ, official **keeps** outgoing layouts:

```java
// official ChatMessageCell.java:28556-28561
animateMessageText = true;
animateOutTextBlocks = lastDrawingTextBlocks;
animateOutTextXOffset = lastTextXOffset;
animateOutAnimateEmoji = AnimatedEmojiSpan.update(..., lastDrawingTextBlocks, true); // clone=true
animatedEmojiStack = AnimatedEmojiSpan.update(..., currentMessageObject.textLayoutBlocks);
```

and **paints both** at the same origin:

```java
// official :21454-21455
drawAnimatedEmojiMessageText(..., animateOutTextBlocks, animateOutAnimateEmoji, ..., alpha * (1 - p), ...);
drawAnimatedEmojiMessageText(..., textLayoutBlocks, animatedEmojiStack, ..., alpha * p, ...);
```

NixgramX `5ba817c3` still sets `animateMessageText = true` but forces `animateOutTextBlocks = null`, so the outgoing draw is a no-op (`drawAnimatedEmojiMessageText` returns immediately on null/empty blocks). Incoming fade remains.

### 2.5 `replyTextLayout` (official)

Always SIMPLE (`ChatMessageCell.java:19622`). Width from `getLineWidth` / `getLineLeft` (`:19642-19648`); `replyTextOffset = min(lineLeft)`. RTL with `replyTextOffset > 0` translates to `replySelectorRect.right - dp(8) - replyTextLayout.getWidth()` (`official :22933`). Then `AnimatedEmojiSpan.update(..., replyTextLayout)` and `drawAnimatedEmojis` in that translated canvas.

Official still crossfades `animateReplyTextLayout` + `animateOutAnimateEmojiReply` (`:22889-22908`). NixgramX `b2f2788` deleted that block.

---

## 3. Changes that may affect CJK + custom emoji overlap

These are the NixgramX deltas that can move custom-emoji pixels relative to CJK glyphs. Ranked by how directly they touch the overlap geometry.

### 3.1 `BREAK_STRATEGY_SIMPLE` for any CJK string — **high**

Official CJK (no overflow) stays on HIGH_QUALITY. NixgramX CJK always SIMPLE (`makeStaticLayout:8748-8750`).

HIGH_QUALITY + ReplacementSpan is the Android path that historically fails to reserve span width during hyphenation/justification. SIMPLE treats `ReplacementSpan.getSize()` as a hard box and allows breaks between CJK code points.

Effects on mixed “中文 + custom emoji”:

- Wrap points change. An emoji that sat at the end of a wide HIGH_QUALITY line can move to the next line (or stay) under SIMPLE.
- Following CJK start `x` is `span_x + getSize()` = `span_x + measuredSize - 1` (see CJK layout note).
- Bubble `textWidth` then shrinks to visual glyph width, but **layout width stays `maxWidth`**. Internal `getPrimaryHorizontal` / `getLineLeft` are computed for the wide layout, not the shrunken bubble.

### 3.2 `shrinkWidthToVisualContent` vs span box — **medium**

```java
// MessageObject.getLayoutContentWidth:8701-8702
w = Math.max(w, (int) Math.ceil(layout.getLineRight(i) - layout.getLineLeft(i)));
```

Custom emoji is drawn as a square of side `measuredSize` centered at `x + measuredSize/2`. If `getLineRight` under-reports a trailing ReplacementSpan (seen on some API levels with SIMPLE), `textWidth` / bubble becomes narrower than the drawable. That is clip-against-bubble, not glyph-on-glyph, unless `textX` is also shifted (`textXOffset != 0 && replyNameLayout != null`, `ChatMessageCell.java:16815`).

`getLastLineWidthForTime` (`ebcfc260`) uses the same `lineRight - lineLeft` formula so the clock is reserved against visual glyphs including spans. `aa2393e3` notes `Paint.measureText` ignored ReplacementSpans on the **time string**; that was clock-vs-text, not emoji-vs-CJK.

### 3.3 `getSize` returns `measuredSize - 1` — **official, always on**

Not a NixgramX delta, but it is the span-vs-text contract:

| Quantity | Formula | Role |
| --- | --- | --- |
| Slot reserved by `StaticLayout` | `getSize()` = `max(0, measuredSize - 1)` | Next glyph `x` |
| Drawable box | `[x, x + measuredSize]` | Custom emoji pixels |
| Overlap into next glyph | **1 px** | Both trees |

`-1` is what lets the span wrap when remaining width equals `measuredSize` (comment at `:80`). Latin HIGH_QUALITY often inserts extra gaps, hiding 1 px. CJK SIMPLE does not. Visible “emoji sits on the next 汉字” is therefore more likely on the NixgramX CJK path even though the span math is official.

### 3.4 `textWidth` change ⇒ `sameText = false` — **medium (animation)**

`2323cc765b` treats a CJK shrink (width change, same string) as a content swap. Combined with `b2f2788` / `5ba817c3`, `animatedEmojiStack` is rebuilt from the new blocks with `animateOutAnimateEmoji` left unset. First frames after translate/relayout: `spanDrawn` / `lastDrawnCx` on reused `AnimatedEmojiSpan` instances can still be the **pre-wrap** center until the new `StaticLayout.draw` runs. `lockPositionChanging` can skip one update (`draw:356-358`).

### 3.5 Incoming-only fade (null `animateOut*`) — **low for static overlap, high for translate toggle**

Official paints old+new custom emoji at the same `textX/textY` during `animateChangeProgress`. For EN/ZH that is the documented 叠字. For CJK + custom emoji it would also composite two stacks. NixgramX no longer does that. Static (non-animating) overlap is **not** explained by this commit; toggle-time overlap is.

### 3.6 `replyTextLayout` SIMPLE + `Math.max(dp(1), maxWidth)` — **low**

Same strategy as official. Width clamp only avoids zero-width layouts. Reply CJK + custom emoji uses SIMPLE in **both** trees, so reply overlap is not a NixgramX CJK fork.

---

## 4. Unrelated differences

Keep these out of a CJK+custom-emoji geometry fix unless a later trace proves otherwise.

| Area | SHA / path | Why unrelated |
| --- | --- | --- |
| PhotoViewer skip `invalidateBlur` during pinch | `2323cc765b` `PhotoViewer.java` | Same commit as CJK shrink; attach preview, not chat text. |
| In-app update tag / `UpdateHelper` / `ApplicationLoader.checkUpdate` | `fa9ccd14` | Beta metadata. |
| `TranslateButton` → ordinary `TextView` | `b2f2788` | Chat-bar label word-diff; not bubble glyphs. |
| `NekoDelegateFragment` bar wiring | `b2f2788`, `fa9ccd14` | Settings / update UI. |
| `startChangeAnimation()` | NixgramX-only (`ChatMessageCell.java:28444`) | Fallback when item animator is missing; does not change span `getSize`. |
| Ayu deleted translucent `chat_msgTextPaint` alpha | `ChatMessageCell.drawMessageText:16830` | NagramX deleted-message style. |
| `ChatMessageCell` ~565 extra lines vs official 29572 | Neko/Ayu/NagramX features | Bookmarks, deleted marks, etc. Official emoji draw/update in the compared functions is the same except the animateOut / `textWidth` / time-width deltas above. |
| `StaticLayoutEx` HIGH_QUALITY-first | unchanged vs official | Not used by `generateLayout` / `makeStaticLayout`. |
| Folder-tab swipe PRs #54–#58 | later `main` | Unrelated UI. |

---

## Pointers for a follow-up fix (not done here)

1. Do not patch `AnimatedEmojiSpan.getSize` without comparing official — file is already upstream-identical.
2. If overlap is **static** mixed CJK+emoji: inspect SIMPLE wrap + `measuredSize - 1` vs `getLineRight`/`getPrimaryHorizontal` (see `docs/debug/custom-emoji-CJK_LAYOUT.md`).
3. If overlap is **only on 翻译为中文 / 显示原文**: `animateOut*` is already null; leftover is likely stale `lastDrawnCx` on reused spans or MOVE (`fa9ccd14`) sliding neighbors.
4. `StaticLayoutEx` / transcription still HIGH_QUALITY — only a problem if those surfaces show the same overlap.
