# CJK custom emoji: reproduction and SPAN clues (Agent 5)

## Status

Docs-only investigation at `1907172aaea1d652bfecd28b4f9cfaa5bc8301d2`. No reproduction has been run on a device and no root cause is confirmed. This file designs Debug-only `NixEmojiLayout` instrumentation; **Lead has not approved adding it**. Do not add logs or change Java behavior in Phase 1. No version bump, merge, or Beta publish.

Read the [reply lifecycle findings](custom-emoji-REPLY_LIFECYCLE.md) for source locations and commit analysis. Prioritize `2323cc765b` (CJK SIMPLE, visual width, transition decisions), with `b2f2788` as relevant outgoing-layout history. Do not begin with an AnimatedEmojiSpan rewrite.

## SPAN_EXCLUSIVE_EXCLUSIVE clues

`MessageObject.replaceAnimatedEmoji` (8099–8146) places a new custom emoji span at `[entity.offset, entity.offset + entity.length)` with `SPAN_EXCLUSIVE_EXCLUSIVE`. Its local acceptance check tests the upper endpoint against text length; it does not explicitly require positive length or nonnegative offset. A zero-length entity with an in-range offset can therefore reach `setSpan(start == end)`. This is an input-boundary hypothesis, not proof that a real message supplies such an entity.

The inspected AOSP `SpannableStringBuilder.setSpan` rejects zero-length exclusive-exclusive placement, logs an error on the public set path, and returns without installing the span. A generic SPAN warning does not identify a custom emoji caller or prove overlap. Verify the device's text container and Android implementation before generalizing to every `Spannable` implementation. [AOSP source](https://raw.githubusercontent.com/aosp-mirror/platform_frameworks_base/master/core/java/android/text/SpannableStringBuilder.java), inspected 2026-09-15.

Other relevant placement/copy patterns:

| Pattern | Evidence / implication |
| --- | --- |
| Entity-based custom emoji placement | `MessageObject.java:8141`; inspect attempted and resulting ranges separately, including invalid offsets and zero length. |
| Cloning during stack update | `AnimatedEmojiSpan.java:574–577` preserves source start/end with exclusive-exclusive flags; does not deliberately insert a zero-length placeholder. Normal reply update does not request cloning. |
| `cloneSpans` | `AnimatedEmojiSpan.java:1073–1086` copies source endpoints into new span objects; malformed source ranges need separate evidence. |
| Reply truncation/ellipsizing | `ChatMessageCell.java:19735–19770,19882` transforms text around custom-emoji replacement. Compare ranges before/after; do not assume ellipsizing always collapses spans. |
| Newline normalization | `AndroidUtilities.replaceNewLines:4412` uses one-code-unit replacement in a `SpannableStringBuilder`; inspect span survival when edits intersect spans. |
| Non-emoji spans | Reply formatting also uses exclusive-exclusive flags for names/styles/margins; an empty range warning can originate there. |

No intentional `setSpan(customEmoji, p, p, SPAN_EXCLUSIVE_EXCLUSIVE)` placeholder was found in the inspected custom-emoji replacement and clone paths. This is not a repository-wide proof of absence. Entity offsets and span endpoints are UTF-16 indices: supplementary emoji, variation selectors, and joined emoji must not be counted as one Java character by a test fixture. Never change flags to mask a warning without locating the caller and validating its range.

A rejected span more directly predicts a missing custom emoji or fallback glyph. To connect it to overlap, demonstrate an additional stale span, overlapping valid ranges, incorrect reserved width, or mismatched recorded coordinates.

## Hypothesized reproduction

Use synthetic messages in a test conversation and insert a real custom emoji via the picker; `[custom emoji]` below is a placeholder, not literal test input.

1. On an API 24+ device, send `这是用于测试回复换行的文字[custom emoji]后面继续中文文字。` and reply to it. Adjust the repeated CJK text so the emoji is near the preview's truncation/wrap boundary. Record whether the issue is in the reply preview, body, or both.
2. Repeat as a quoted reply capable of multiple lines; place the custom emoji at the start, middle, and wrap boundary. Test adjacent custom emoji and mixed Latin/CJK punctuation.
3. Test an English message with custom emoji and its reply, then use EN→ZH replace-original translation and switch back. Observe immediately and after animations settle. Verify that custom-emoji entities survive the translation; a case with no resulting animated span is not a coordinate test. Test keep-original separately.
4. Trigger a layout rebuild by rotation or changing available width, then scroll the cell offscreen/back and leave/re-enter the conversation. Distinguish a transient transition issue from a persistent one and note whether attachment clears it.
5. Repeat the same text with a spoiler elsewhere in the preview, then with the custom emoji inside a spoiler, then reveal it. Compare no-spoiler, hidden-spoiler, and ripple-animation frames. Keep all other inputs fixed.
6. Repeat around the ordinary preview's 150-code-unit boundary and with a media-caption reply. Check whether truncation drops an entity, clips its fallback text, or changes span identity.

Controls: Latin-only with custom emoji; CJK with standard Unicode emoji; CJK with no emoji; wide/narrow layouts; fixed font size/density; ordinary versus quoted reply; incoming/outgoing; no spoiler versus spoiler. Record Android API, build SHA, font scale, density, and available width as test metadata. Older API paths are useful controls but do not exercise the same builder branch.

A future authorized comparison should first reproduce at baseline, then compare `2323cc765b` with its parent in isolated test builds. That historical comparison has other changes and is not conclusive alone. Separately controlling break strategy, width shrinking, and width-triggered transitions would require a later authorized experiment; this document does not authorize production edits or a revert.

## How strategy changes could expose coordinate mismatch

SIMPLE and HIGH_QUALITY can choose different line breaks, changing emoji x/y without changing character content or span offsets. The emoji renderer reads `lastDrawnCx/Cy` stored on the span rather than calculating a position from its holder's layout. Therefore a strategy/width change only causes stale-coordinate overlap if the new layout does not refresh the relevant center, a different layout subsequently overwrites it, or coordinate recording is locked/animated.

Normal reply update removes obsolete layout bindings, and `drawAnimatedEmojis` selects an exact matching layout chunk. The reply builder itself already uses SIMPLE. The suspect's direct changes are in the body/caption factory and sizing; an indirect reply-width effect must be demonstrated.

The spoiler path is especially discriminating: its patched layout uses HIGH_QUALITY on API 24+, whereas the reply uses SIMPLE. It can share span objects or reuse a cached layout whose span objects differ from the current reply. Log both actual text-draw layout and stack-requested layout. Matching `holder.layout` alone cannot eliminate this hypothesis.

## Proposed Debug-only NixEmojiLayout log plan — approval pending

### Guard and privacy contract

After explicit Lead approval only: compile-time Debug-build guard plus an explicit local, default-off diagnostic switch. Release builds must emit nothing and avoid constructing diagnostic records. Use a bounded local buffer or capped capture window, rate-limit repeated frames, and stop after reproduction. No upload or telemetry. Verify the project's actual Debug build flag before implementation.

Strict allowlist: numeric geometry, process-local object identity hashes, enum event/reason labels, booleans, and counters. **Forbid message bodies or fragments, usernames/display names, chat IDs, message/account IDs, tokens, credentials, URLs, emoji document IDs, and arbitrary object/text `toString()` output.** Do not hash message text or identifiers as a substitute. Use `System.identityHashCode` only on layout/span/holder/stack/cell objects for ephemeral correlation, with a null sentinel; these hashes may collide and are not durable identifiers. Do not dump exception messages, entity objects, or full device logs.

### Fields

| Group | Allowed fields |
| --- | --- |
| Correlation | monotonic event/frame counter, local cell generation, event enum, stack role (`reply`, `body`, `caption`, `description`), cell/stack/holder/span identity hashes |
| Layout identity | old/new/requested layout identity hash, `holder.layout` hash, chunk layout hash, actual text-draw layout hash, spoiler-patched layout hash |
| Span validity | span start/end, attempted start/end, flags, text length only, span count, placement-present boolean; distinguish missing `-1` from zero-length `start == end` |
| Geometry | layout width/height, line count, relevant line index/start/end/left/right/top/bottom, measured emoji size, `lastDrawnCx`, `lastDrawnCy`, current draw x/top/bottom and candidate cx/cy |
| Construction | effective `breakStrategy` enum and builder origin, API level, CJK-present boolean, includePad/spacing/maxLines/ellipsis metadata |
| State | `spanDrawn`, `recordPositions`, position lock, move-animation active, transition flags/progress, attached state, spoiler/ripple state, reply canvas translation/clip bounds |

Capture effective break strategy where a layout is constructed; do not infer it from the current locale or assume every Layout exposes a getter. Use `legacy/unknown` when appropriate. If approved, store metadata in a bounded/weak identity association so diagnostics do not retain old layouts. Clear context in a finally block. Reading private span coordinates should be designed inside their owning class, without changing public production APIs.

### Proposed event sites

1. **`span_place_attempt/result`:** in custom-emoji placement and clone paths, before and after `setSpan`. Numeric range, flags, container enum and presence only. This captures rejected spans that will never appear in stack enumeration.
2. **`reply_build` / `layout_build`:** before/after reply construction and the suspect body/caption factory; record construction settings and old/new geometry. Add a patched-layout build/reuse event in `SpoilerEffect` to identify cache reuse and strategy differences.
3. **`stack_update_begin/end`:** record supplied layout hashes, holder count, and add/reuse/remove decisions with span/holder/layout hashes. Include normal rebuild and attach paths.
4. **`text_draw_begin/end` + `span_record`:** bracket the actual original or patched Layout draw using Debug-only context so `AnimatedEmojiSpan.draw` can identify the coordinate producer. Record candidate center, prior/final center, and a reason enum for recording skipped/locked/animated. Include every spoiler draw pass; a single “reply draw” context would conceal patched-layout ownership.
5. **`emoji_draw`:** at layout chunk selection and `SpansChunk.draw`, record requested/chunk/holder layout identities, span range, last coordinate-producer event/layout, spanDrawn and resulting bounds. Record a no-matching-chunk event too.
6. **`transition` / `release`:** snapshot old/current layout hashes and relevant animate flags at state recording/change/reset; record detach/reset and holder counts after release. Avoid logging text comparison operands.

### What to look for once approved

| Trace pattern | Interpretation / next check |
| --- | --- |
| Update B ends with only B holders; text B records S; emoji B consumes that same event | Weakens stale-layout/coordinate explanations; inspect size, line geometry and transforms. |
| Update B is missing or ends with holder A, then draw requests B | Candidate lifecycle failure; check early exits/exceptions and missing chunk. Old binding alone normally skips drawing. |
| Holder B + requested B, but S's final coordinate producer is patched P | Candidate cross-layout position contamination. Compare P/B strategy, line geometry and actual drawn glyphs. |
| Patched P draws old S1 while current B holds S2 | Candidate patched-cache identity problem; see whether S2 is recorded before use. |
| S is not recorded this frame but emoji uses prior center | Check spanDrawn reset, skipped draw, ellipsis, record/lock/animation flags; movement during active interpolation is not sufficient evidence of a bug. |
| Attempt start == end followed by absent span | Placement rejected; identify custom-emoji caller before connecting a generic SPAN warning to this issue. |
| Distinct valid spans overlap in numeric ranges or centers | Inspect entity transformation and duplicate placement; strategy does not itself change stored span offsets. |
| Layouts/centers agree but reply canvas origin changes | Investigate reply/bubble transition geometry rather than stale local coordinates. |

For each reproduced visual failure, capture the complete build → update → text draw → emoji draw order and a settled frame, using only approved fields. A result is conclusive only if it links the affected emoji's identity and geometry to the observed overlap. No instrumentation, runtime test, or fix has been performed in this phase.
