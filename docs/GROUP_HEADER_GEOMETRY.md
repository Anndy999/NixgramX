# Group header geometry — isolated Beta bugfix

Base: beta `12fe9bfc17ccd5dddb722355c4d490cd671c6428` (1284).
No Updater V2, version, workflow, composer or chat-bubble changes in this PR.

## Proven root cause and execution path

ChatActivity.createView calls ActionBar.setupGlass with ChatObject.isForum(currentChat).
For a forum group, setupGlass initializes the center drawable through the four-argument
setRadius(18.33dp, 23dp, 23dp, 18.33dp). Its search animation invokes that overload
again on every frame. Non-forum/private/channel headers and back/menu segments use
the single-argument 23dp overload.

The four-argument BlurredBackgroundDrawable.setRadius updated boundProps.radii
but left boundProps.shaderRadii zero (or stale). BlurredBackgroundDrawableRenderNode
onBoundPropsChanged builds the outline from radii; updateDisplayList passes
shaderRadii to LiquidGlassEffect.update on Android 13+. Consequently the center
clip/stroke and glass/refraction mask disagree, while the side buttons agree.

Fix: delegate the four-argument overload to the existing five-argument overload with
forceBottomZero=false. That implementation synchronizes both arrays and invalidates
once. No new radius, no child-margin workaround, no title/avatar repositioning.
The intentional 18.33/23 forum outline itself is preserved, not forced into a capsule.

Confidence: HIGH for this shader/outline mismatch. This is forum-group-specific,
not proof that every ordinary group screenshot has the same cause. A screenshot
of a non-forum group with remaining curvature problems needs separate evidence.

## Compared paths and geometry

| Path | Setter | Geometry before fix |
| --- | --- | --- |
| Private | single radius | clip/shader synchronized |
| Ordinary non-forum group | single radius | clip/shader synchronized |
| Forum group | per-corner | clip updated, shader stale/zero |
| Channel | single radius | clip/shader synchronized |
| Back/menu | single radius | clip/shader synchronized |

ActionBar.dispatchDraw uses the same top/bottom bounds for all three segments:
46dp visible height and 6dp drawable padding. ChatAvatarContainer.onLayout changes
title/subtitle positions, not these glass bounds. ActionBarMenu.onMeasure's glass
item margins are shared, not group-only. ActionBarMenuItem's popup glass flag is
not the top header's background renderer. Outline/clip setup is shared; it consumes
the radii array, while the effect consumes the separate shaderRadii array.
Pinned panels, title-only/member-count/mute content do not select a different setter.

## Official comparison

Official Telegram master at `62b56a07ca7e30e39f7fd00a6728d6bbd716ca1c` has the same
four-argument omission and forum call path. This is an inherited shared-glass defect,
not evidence that NixgramX introduced a new header height or that official is fixed.

- https://github.com/DrKLO/Telegram/blob/62b56a07ca7e30e39f7fd00a6728d6bbd716ca1c/TMessagesProj/src/main/java/org/telegram/ui/Components/blur3/drawable/BlurredBackgroundDrawable.java
- https://github.com/DrKLO/Telegram/blob/62b56a07ca7e30e39f7fd00a6728d6bbd716ca1c/TMessagesProj/src/main/java/org/telegram/ui/ActionBar/ActionBar.java

## Regression evidence

Host test compiles and executes the actual three radius overloads extracted verbatim
from the production class, stubbing only drawable state/invalidation. Before fix:
FAIL `clip/shader radius mismatch`. After fix: PASS across five display densities,
uniform corners, forum/search animation values and the intentional clipped-bottom
five-argument path. Reverting the production change reproduces the assertion.
This is geometry-state validation, not Android GPU rendering or screenshot QA.

## Device matrix — NOT DEVICE VERIFIED

| Screen | Light | Dark |
| --- | --- | --- |
| Private | NOT TESTED | NOT TESTED |
| Group (forum and ordinary) | NOT TESTED | NOT TESTED |
| Channel | NOT TESTED | NOT TESTED |

For groups repeat title-only and member subtitle; muted/unmuted; custom/default
avatar; pinned message visible/hidden. Inspect back/avatar/title/subtitle/mute/overflow.
Check search open/close as it updates the same per-corner setter.
Back and overflow tap bounds, title/avatar layout, status bar and pinned panel code
are unchanged; their visual/touch regression remains NOT TESTED on this candidate.
No APK was installed on the connected device for this fix.

## Commit review

- User operation reaches the function: forum setup plus search animation call it.
- Cause not symptom: synchronize the exact geometry consumed by the shader/outline.
- Revert brings failure back: host regression demonstrated red before/green after.
- Necessary files only: one production drawable, regression test and this evidence.
- Risk: other callers of this shared four-corner overload also receive corrected
  shader geometry. Single-radius and explicit five-argument semantics are unchanged.
