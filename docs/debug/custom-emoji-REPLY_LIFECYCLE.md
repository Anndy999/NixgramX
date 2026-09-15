# CJK custom emoji: reply lifecycle (Agent 4)

## Scope and conclusion

Source baseline: `1907172aaea1d652bfecd28b4f9cfaa5bc8301d2` (origin/main), investigated on `fix/cjk-custom-emoji-overlap`. Phase 1 is documentation only. No Java changes, temporary logs, version changes, merge, or Beta publish are authorized by this document. Instrumentation requires Lead approval. These are source findings and hypotheses, not a device reproduction or a confirmed root cause.

Start with `2323cc765b14a37bffb7cf16eb984e914437ad30`: CJK layout strategy, visual width reduction, and width-triggered transitions. Do not start by rewriting `AnimatedEmojiSpan`. Normal reply rebuilds already bind the stack to the new layout and remove obsolete holders. A stale holder alone would normally cause missing emoji because draw selection checks layout identity. A separate, testable risk is that a shared span records positions from a different layout, especially a spoiler-patched layout.

## Source map

Paths below are relative to `TMessagesProj/src/main/java/`; line numbers refer to the baseline.

| File | Entry points / lines |
| --- | --- |
| `org/telegram/ui/Cells/ChatMessageCell.java` | stack fields 1822–1824; `updateAnimatedEmojis` 6266; `checkImageReceiversAttachState` 6670; content reset 7059; `setMessageObjectInternal` 19000; reply preparation 19714–19929; reply draw 23214–23245; transition state 28624–28625, 28883, 28922–29015 |
| `org/telegram/ui/Components/AnimatedEmojiSpan.java` | `draw` 347; `drawAnimatedEmojis` 371; layout-based `update` 552; grouped `release` 782; `EmojiGroupedSpans` 790; `replaceLayout` 869; `SpansChunk.draw` 989; `cloneSpans` 1044 |
| `org/telegram/messenger/MessageObject.java` | `replaceAnimatedEmoji` 8099–8146; `containsCjk`, `makeStaticLayout`, `shrinkWidthToVisualContent` |
| `org/telegram/ui/Components/spoilers/SpoilerEffect.java` | `renderWithRipple` 819: patched-layout caching, construction, and draw |

## Call chains and ownership

### Reply construction and rebuild

`setMessageObject` / message binding → content setup and `setMessageObjectInternal` → choose quote, caption, or replied message text → normalize/truncate text → `Emoji.replaceEmoji` → `MessageObject.replaceAnimatedEmoji(..., top=true)` → ellipsize/style processing → build `replyTextLayout` → measure reply dimensions and create spoiler effects → `AnimatedEmojiSpan.update(..., animatedEmojiReplyStack, replyTextLayout)`.

The ordinary message preview takes up to 150 UTF-16 code units and replaces newlines; quote entities come from `reply_to.quote_entities`, while caption/message previews use the replied message's `messageOwner.entities`. `replaceAnimatedEmoji` removes intersecting animated spans and creates new span objects for accepted custom-emoji entities. A new builder or layout does not itself guarantee new span identities: copied spanned text can retain references, and the entity-null path returns existing spans. Track identities instead of assuming either universal reuse or universal cloning.

The API 23+ reply builder explicitly selects SIMPLE at line 19892. This is a separate builder from `MessageObject.makeStaticLayout`. The legacy reply path uses the older constructor. Successful reply layout creation is followed by stack update at 19929 inside the same try block. An exception between assignment and update is a candidate skipped-update path, not evidence that it happens.

`animatedEmojiStack` handles body text or captions, `animatedEmojiReplyStack` handles the reply preview, and `animatedEmojiDescriptionStack` handles descriptions. There is no separate class named ReplyStack in this path. `animateOutAnimateEmojiReply` is a retained transition field; it is not the active reply stack.

### Update and release

`AnimatedEmojiSpan.update` enumerates `AnimatedEmojiSpan` objects in each supplied layout. It reuses a holder only when **both** `holder.span == span` and `holder.layout == textLayout`. Otherwise it creates a holder, assigns `holder.layout`, groups it by layout, and attaches the drawable via `addView`. It removes spans missing from supplied layouts and then removes holders whose layout is absent from the supplied layout list.

Thus passing new reply layout B should remove holders for old A, even if A and B share span S. Optional `clone=true` replaces span objects; the normal reply call uses the non-cloning overload.

`checkImageReceiversAttachState` updates reply, description, and main stacks on attachment and releases them on detachment. The normal content reset sets `replyTextLayout = null` and releases the reply stack. Grouped `release` removes holders/chunks and drawable view registrations; it need not set the cell's stack field to null. Special joined-channel/expired-story paths also null the layout; null-layout drawing is guarded.

### Text draw then emoji draw

`drawNamesLayout` → translate canvas to reply origin → `SpoilerEffect.renderWithRipple(..., replyTextLayout, ...)` → layout draw → `AnimatedEmojiSpan.draw` records the span's local center → `drawAnimatedEmojis(..., replyTextLayout, animatedEmojiReplyStack, ...)` → find chunk with `chunk.layout == replyTextLayout` → `SpansChunk.draw` reads `span.lastDrawnCx/Cy` → drawable bounds and draw.

`AnimatedEmojiSpan.draw` records `cx = x + measuredSize / 2` and `cy = top + (bottom - top) / 2` when `recordPositions` is enabled. It marks `spanDrawn`; position locking or `animateChanges` can defer a direct coordinate update. A move animator interpolates the stored center over 140 ms. These are span-local coordinates, not screen coordinates. `SpansChunk.draw` skips spans without `spanDrawn` and uses the stored centers; it does not recompute glyph positions from `holder.layout`.

`clearPositions()` resets `spanDrawn`, not `lastDrawnCx/Cy`. Main text/caption paths call it; the reply draw site has no corresponding explicit clear. Stale coordinates therefore require checking whether the current frame actually recorded the span, including any lock/animation state. Do not infer that those flags are active in ordinary replies merely because the mechanism exists.

## Transitions and commit history

`TransitionParams.recordDrawingState` retains `lastDrawnReplyTextLayout = replyTextLayout`. `animateChange` compares reply layout references, then compares text with `TextUtils.equals`; character equality alone does not establish equal span identities or geometry. The actual reply rebuild site, rather than this comparison, refreshes the active stack.

`b2f2788bff3cfd15b636c1573fdc448c25d9b482` removed the outgoing reply layout draw and outgoing emoji draw, removed creation of the outgoing reply stack, and set `animateReplyTextLayout = null` on text changes. It did not delete the active reply layout update. In the baseline, `animateOutAnimateEmojiReply` has only its declaration, and the old layout can remain referenced as drawing-state history without being painted or bound to an active outgoing stack. Source inspection does **not** support “deleting the old draw left the active reply stack permanently attached to the old Layout.”

That commit also removed body/caption outgoing crossfades. Later `5ba817c37c` restored incoming-only translation animation. At this baseline, body changes set `animateMessageText = true`, keep `animateOutTextBlocks = null`, and update the main stack. Do not mistake the original patch's `animateMessageText = false` for current behavior. Reply position/background animations can still move the canvas origin; separate that movement from local span movement.

For equal body text, `animateChange` calls `animatedEmojiStack.replaceLayout(new, old)`, which changes the chunk key, chunk layout, and holders' layouts, followed by a normal update. This is a main-body path, not the reply update path. A correct holder layout does not independently prove its span's centers were recorded by that layout.

## Relation to the preferred suspect

`2323cc765b` made `MessageObject.makeStaticLayout` use SIMPLE for detected CJK on API 24+, retained HIGH_QUALITY for other text, introduced visual-width shrinking for body/caption layouts, and made any text-width change invalidate the transition's same-text decision. The width shrink calls are not themselves restricted to CJK. The commit did not change the reply builder or `AnimatedEmojiSpan`.

Hypothesis: changed body/caption geometry or bubble sizing changes the reply's available width/origin and rebuild timing. Establish that geometry actually changes in a reproduction before attributing reply overlap to this commit. A SIMPLE/HIGH_QUALITY comparison must separate strategy effects from its width and transition changes.

A concrete secondary clue is spoiler rendering: `SpoilerEffect.renderWithRipple` copies spanned text, builds a HIGH_QUALITY patched layout on API 24+, and can draw that layout before the active stack is drawn using the original layout identity. Its cache checks characters, width, and height, but not span identity or break strategy. Copied custom spans can share centers with the original layout; a retained patched layout can instead retain old span objects. Either possibility needs measurement. An active ripple may also draw the original layout later, changing which layout last recorded a center. This mechanism predates the suspect's reply behavior and must not be labeled a regression introduced by that commit without evidence.

## Discriminating observations

- **Stale holder binding:** after update to B, a holder/chunk remains on A. Record update completion and draw layout. With no B chunk, expect skipped emoji, not automatically an overlapping old emoji.
- **Shared-coordinate mismatch:** holder and draw layout both B, but span S last recorded coordinates during patched layout P (or another layout) with different line geometry.
- **Stale patched span:** active B contains S2 while cached P still draws S1 after an equal-text rebuild; determine whether S2 records any center before emoji draw.
- **Geometry-only issue:** all identities and recording order agree, yet line extents, measured emoji size, clipping, or canvas origins conflict. Investigate the preferred suspect's layout/width effects first.

See [reproduction and proposed logging](custom-emoji-REPRO.md). No runtime outcome is claimed here.
