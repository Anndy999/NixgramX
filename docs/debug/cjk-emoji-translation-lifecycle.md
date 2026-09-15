# DIAGNOSTIC ONLY — DO NOT MERGE

Custom emoji / CJK translation overlay: layout-lifecycle trace.

## Stage

DIAGNOSIS. Production behavior is unchanged.

## Rejected / weakened

- BREAK_STRATEGY_SIMPLE for CJK: DEVICE FAIL
- shrinkWidthToVisualContent: DEVICE FAIL
- AnimatedEmojiSpan.getSize / measuredSize-1: blob SHA matches official Telegram 12.10.1

## Current hypotheses

| Rank | Id | Probability | Claim |
| --- | --- | --- | --- |
| 1 | H1 | 60% | After translation rebind, body/reply Layout identity and AnimatedEmoji stack/holder.layout / lastDrawnCx,Cy are out of sync |
| 2 | H2 | 20% | Translated custom-emoji span range does not match the current CJK text length |
| 3 | H3 | 15% | ChatListItemAnimator MOVE and emoji lastDrawnCx,Cy animation are desynchronized |
| 4 | H4 | 5% | Ordinary CJK ReplacementSpan geometry (only after H1–H3 fail) |

## Single variable this round

Targeted `NixEmojiTrace` logging. No stack lifecycle, transition, coordinate, Layout, or span-size change.

## How to read logs

Tag: `NixEmojiTrace`. Also copied into Settings → Diagnostics (STATE lines).

- `TRANSLATION_REBIND_BEGIN/END` — cell/layout/stack identities around setMessageObject
- `EMOJI_STACK_UPDATE` CREATE/REUSE/REMOVE/REPLACE_LAYOUT
- `SPAN_POSITION_WRITE` — lastDrawnCx/Cy actually changed (or ANIMATE_INTERCEPT / LOCK_SKIP)
- `EMOJI_DRAW` — drawable box vs holder.layout
- `BODY_LAYOUT_DRAW` / `REPLY_LAYOUT_DRAW`
- Alerts: `MISMATCH_LAYOUT`, `STALE_POSITION`, `SPAN_RANGE_INVALID`, `STACK_NOT_REBOUND`

Numbers only. `xy`/`origin`/`fin` are pixels. Identities are identityHashCode hex. No message text, ids, or document ids.

## Export

After the overlay appears: NixgramX Settings → Diagnostics → Copy.
