# Channel header Liquid Glass geometry — isolated Beta bugfix

Base: beta `a21bfd1fae10a28f893445c087d6a52108dde085` (NixgramX 12.10.1, 1285).
This change does not modify Updater V2, versioning, workflows, message bubbles,
composer code, ActionBar touch handling, or the PR #44 radius synchronization fix.

## Root cause and execution paths

All normal chats call `ChatActivity.createView` → `ActionBar.setupGlass` and use
`ActionBar.dispatchDraw` for the three top-header glass backgrounds. Ordinary
private chats normally expose two real standard-width right menu cells: Call and
More. `ActionBarMenu.getItemsWidth()` therefore supplies their combined geometry
to both the center glass right edge and the separate menu glass background.

A broadcast channel (`ChatObject.isChannelAndNotMegaGroup`) has no Call cell and
normally exposes only More. Its real menu width is consequently one standard cell
smaller. The old common draw path used that smaller width directly, which stretched
the center capsule to the right and made the channel header silhouette diverge from
the known-good private reference. This is independent of avatar/title/subtitle
measurement and independent of PR #44's forum-only shader/outline radius repair.

The fix opts in only a normal, non-topic broadcast channel to a minimum of two
**glass geometry** cells. `ActionBar` derives the added width from the measured
visible menu cell, rather than a new dp constant. It uses that width only for the
center and menu `BlurredBackgroundDrawable` bounds. The actual `ActionBarMenu`
layout, overflow icon position, content, visibility, and tap targets still use the
real one-cell menu unchanged.

## Private, channel, and group behavior

| Path | Real menu cells | Glass geometry cells after change | Result |
| --- | --- | --- | --- |
| Private chat | Call + More | 2 | unchanged reference |
| Broadcast channel with More | More | 2 | matches private glass silhouette |
| Broadcast channel with 2 actions | 2 | 2 | no extra reservation |
| Ordinary group | unchanged | unchanged | unaffected |
| Forum/topic | unchanged | unchanged | unaffected; #44 retained |

## Focused regression coverage

`Tools/stability/test_channel_header_geometry.py` extracts and compiles the exact
production geometry calculator. It proves private geometry stays 94 from two real
48dp cells; a one-cell channel reserves its measured 48dp peer and also becomes 94;
two-action channels do not gain a third slot; group/forum and hidden-menu cases do
not opt in. A source assertion also verifies the draw bounds use only the derived
glass width and that ChatActivity enables the policy only for normal non-topic
broadcast channels.

## Regression risk and device test requirement

Risk is limited to the drawn bounds of the center/right Liquid Glass backgrounds
in regular broadcast channels with a single visible action. The right background
will intentionally include the visual slot that a private chat uses for Call, while
the channel still has no Call action and its overflow hit target does not move.
No Android device or screenshot automation is available for this candidate.

**DEVICE TEST REQUIRED:** compare private, broadcast channel, ordinary group and
forum/topic in light and dark mode; verify title/subtitle, mute state, avatar,
overflow hit target and pinned panel visible/hidden. Do not claim the visual result
is verified until that matrix is run on a device.
