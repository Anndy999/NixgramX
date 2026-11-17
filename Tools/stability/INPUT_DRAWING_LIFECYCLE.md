# Input drawing and spoiler lifecycle regression

## Scope

This patch addresses exception-state recovery in `EditTextBoldCursor` and
`EditTextEffects`, and the canceled spoiler timeout when the **same** editor is
reattached. These patterns were inherited from Telegram. This is **not** evidence
that the OPPO translated-input invisibility problem has been fixed.

Drawing now restores the caller's Canvas stack on normal and exceptional exits.
The superclass-drawing section also restores `drawInMaim`, temporary padding
suppression and the reflected vertical scroll value. Both locally caught cursor
paths restore their Canvas before subsequent painting. Existing release catches
and private-debug rethrows remain in place; no new exceptions are swallowed.

Detach cancels the spoiler timeout and clears its scheduling flag. Reattach
reevaluates the existing selection rule and starts a **fresh full 10-second
interval** only if a spoiler remains revealed and the selection is outside it.
Time spent detached is not counted; entering a spoiler cancels the timer as before.
No new timer or global lifecycle observer is introduced.

## Executable host verification (not Android instrumentation)

```bash
python3 Tools/stability/test_input_drawing_lifecycle.py
```

Requires `java` and `javac` on PATH. With a host JRE instead, provide the `java`
executable as `INPUT_LIFECYCLE_JAVA` and an Eclipse Java compiler JAR as
`INPUT_LIFECYCLE_ECJ`. Missing compilers result in an explicit unittest **skip**,
not a successful behavior-verification result.

The harness extracts the current product methods into Java test doubles:

- Complete Effects drawing and lifecycle methods, including the timeout callback.
- Bold's drawing entry wrapper and superclass-drawing prefix, stopping before
  cursor painting; the cursor paths are separately extracted and executed.
- Both actual padding getters are executed, including after injected failures.
- Canvas, Android View superclass, layout, clock, quote/emoji/spoiler rendering
  and reflection target are **host doubles**. This is not a loaded Android View,
  Robolectric test, real Canvas, IME test or measurement of rendering speed.
- Normal gradient compositing/line drawing is outside the extracted prefix.
  Gradient-enabled cases therefore test exceptional exits only.

There are 91 parameterized cases in six unittest groups: 74 drawing cases,
12 cursor cases, and five spoiler lifecycle cases. They include wrapped Canvas,
private-debug/release policy, nested superclass saves, quote/emoji/spoiler/ripple
faults, a subsequent non-faulted frame, repeated attach, no pending timer,
hidden/selected spoilers, selection changes, and the full restarted timeout.

To run the identical expectations against the pre-fix source without changing
checkout (failures are intentional):

```bash
INPUT_LIFECYCLE_SOURCE_REF=85bb523c5f4dffdc7d7bdfa5f1146b5b8cdf6c60 python3 Tools/stability/test_input_drawing_lifecycle.py
```

Set `INPUT_LIFECYCLE_RESULTS` to a writable JSON path to retain per-case output.
At implementation time the baseline passed 12/91 cases and failed 79/91; the
candidate passed 91/91. All 10 ordinary drawing/cursor controls passed on both.
Seven existing diagnostic privacy/refresh tests also passed. Full-file Java syntax
parsing passed for both changed classes. These are host results only.

The complete stability suite ran with the existing host JRE/ECJ compiler at Java
17 level: **147/148 unittest methods passed, one failed**. The failure is the
unchanged `test_upstream_12103_incremental.test_version_and_identity`: it still
requires `NIXGRAMX_VERSION_NAME=13.1.1`, while the fixed beta baseline already
contains `13.2.2`. Neither that guard nor `gradle.properties` is changed here.
This is a remaining integration gate, not an all-green test result.

## Required private-package/device verification

**DEVICE NOT TESTED. Android/native/APK compilation is a separate gate.**

On the same private-package build and device:

1. Exercise ordinary text, mixed emoji, quotes, spoilers, multiline scrolling,
   selection/cursor movement and page changes in chat input and media captions.
2. Reveal a spoiler, move the selection outside it, detach/reuse the same View
   before timeout, return, and check it hides after a fresh ten seconds. Repeating
   attach must not accumulate callbacks. With the selection inside, it must not
   schedule a hide; moving outside must schedule it again.
3. For an Android runtime fault-injection test, inject one failure at a time in
   superclass/quote/emoji/spoiler drawing; verify the real Canvas save count,
   transform/clip, reflected scroll, padding and drawing flag, then the next frame.
   Do not ship any temporary fault injector.
4. Retest translation off/on with `abc` and `你好` and IME confirmation on the
   affected OPPO device separately; report its result, do not infer it from this
   host suite. Existing confirmed fixes remain protected.

The change does not include the wrong-axis fallback, directory threading,
performance claims, version/package/signing/API changes, public publishing, or
merging into beta/main. Revert this isolated patch to roll it back.
