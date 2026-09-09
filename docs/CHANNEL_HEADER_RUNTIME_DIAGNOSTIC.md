# Temporary channel-header runtime diagnostic

This candidate deliberately contains no visual fix. It captures the actual normal
private-chat and broadcast-channel header geometry after `ActionBar.dispatchDraw`
has set the three Liquid Glass drawable bounds. The payload contains only the lane
(`private` or `channel`) and numeric layout/rendering state; it contains no chat
name, peer ID, account, message, URL, or text content.

## Capture

1. Install the diagnostic APK and fully open one ordinary private chat.
2. Run `adb logcat -d -s NixgramXHeaderGeometry:I` and save its output.
3. Clear Logcat: `adb logcat -c`.
4. Fully open the affected ordinary broadcast channel, then run the same command.

One settled snapshot is emitted per changed geometry state after a 500 ms debounce.
Open/close search or action mode only if their geometry is also being compared.

## Fields

`centerRaw` / `menuRaw` are the `Drawable.setBounds` rectangles. `centerPadded` /
`menuPadded` are the actual clip and RenderNode translation rectangles. `Outline`
and `LiquidShader` are relative to those padded rectangles, matching
`BlurredBackgroundDrawableRenderNode.updateDisplayList`; `Shader` is the source
sampling rectangle after source offsets. `centerRadius`, `menuRadius`, padding,
clip state, menu animator/current target, menu widths, right offset, avatar factor,
search/action factors and forced-menu flags cover every input used by
`ActionBar.dispatchDraw` for this header path.

`renderNodeClipToOutline=true` is the fixed constructor state of the Android Q+
RenderNode implementation. `ActionBar.setupGlass` sets `clipChildren=false` for
both lanes. Ordinary private/channel lanes are non-forum and therefore use the
same 23dp center radius; PR #44's per-corner forum setter is not altered.

## Removal

After the two snapshots have been compared, revert this diagnostic commit before
any production replacement fix. It is intentionally not a permanent telemetry
feature and must not be included in a public release build.
