#!/usr/bin/env python3
"""Execute product Java source slices against host doubles, not Android instrumentation.

Effects drawing and lifecycle methods are included whole. Bold drawing stops before
cursor rendering (tested separately as a source slice): this proves restoration, NOT cursor glyphs,
IME behavior, GPU rendering or real View attach/detach. Source can be selected with
INPUT_LIFECYCLE_SOURCE_REF to demonstrate baseline failures without changing checkout.
Use javac/java on PATH, or INPUT_LIFECYCLE_JAVA plus INPUT_LIFECYCLE_ECJ for a host JRE.
"""
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[2]
HERE = Path(__file__).resolve().parent
COMPONENTS = "TMessagesProj/src/main/java/org/telegram/ui/Components/"


def source(name):
    ref = os.environ.get("INPUT_LIFECYCLE_SOURCE_REF")
    if ref:
        return subprocess.check_output(
            ["git", "show", f"{ref}:{COMPONENTS}{name}.java"], cwd=ROOT, text=True
        )
    return (ROOT / COMPONENTS / f"{name}.java").read_text()


def block(text, signature):
    """These selected methods contain no brace literals; keep their Java text intact."""
    start = text.index(signature)
    brace = text.index("{", start)
    depth = 1
    end = brace + 1
    while depth:
        depth += (text[end] == "{") - (text[end] == "}")
        end += 1
    return text[start:end]


def drawing(text, helper, prefix_only=False):
    if f"private void {helper}(Canvas canvas)" in text:
        wrapper = block(text, "protected void onDraw(Canvas canvas)")
        body = block(text, f"private void {helper}(Canvas canvas)")
    else:
        # No extra restoration is added for the old source; this is a call-only shim.
        wrapper = f"protected void onDraw(Canvas canvas) {{{helper}(canvas);}}"
        body = block(text, "protected void onDraw(Canvas canvas)").replace(
            "protected void onDraw", f"private void {helper}", 1
        )
    if prefix_only:
        body = body.split("        if (cursorDrawable == null)", 1)[0] + "\n}"
    return (wrapper + "\n" + body).replace(
        "org.telegram.messenger.diagnostics.InputTextDiagnostics", "InputTextDiagnostics"
    )


class InputDrawingLifecycleTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.java = os.environ.get("INPUT_LIFECYCLE_JAVA") or shutil.which("java")
        javac = shutil.which("javac")
        ecj = os.environ.get("INPUT_LIFECYCLE_ECJ")
        if not cls.java or not (javac or ecj):
            raise unittest.SkipTest("Requires Java and javac (or INPUT_LIFECYCLE_ECJ); not a device test")
        cls.temp = tempfile.TemporaryDirectory(prefix="input-drawing-host-")
        cls.addClassCleanup(cls.temp.cleanup)
        bold = source("EditTextBoldCursor")
        effects = source("EditTextEffects")
        bold_body = block(bold, "private void drawWithCursor(Canvas canvas)"
                          if "private void drawWithCursor(Canvas canvas)" in bold
                          else "protected void onDraw(Canvas canvas)")
        replacements = {
            "BOLD_DRAW_PREFIX": drawing(bold, "drawWithCursor", prefix_only=True),
            "BOLD_PADDING": block(bold, "public int getExtendedPaddingTop()")
                + "\n" + block(bold, "public int getExtendedPaddingBottom()"),
            "BOLD_CURSOR_PATH": "private void drawCursorPath(Canvas canvas) {\n        if (cursorDrawable == null)"
                + bold_body.split("        if (cursorDrawable == null)", 1)[1].split("        if (lineVisible", 1)[0] + "\n}",
            "EFFECTS_DRAW": drawing(effects, "drawWithEffects"),
            "EFFECTS_LIFECYCLE": "\n".join(block(effects, sig) for sig in (
                "private void checkSpoilerTimeout()",
                "protected void onDetachedFromWindow()",
                "protected void onAttachedToWindow()",
            )),
            "TIMEOUT_CONSTANT": re.search(r"private final static int SPOILER_TIMEOUT = \d+;", effects).group(),
            "TIMEOUT_CALLBACK": block(effects, "private Runnable spoilerTimeout = () ->") + ";",
        }
        fixture = (HERE / "InputDrawingLifecycleHarness.java.in").read_text()
        for name, value in replacements.items():
            fixture = fixture.replace(f"/* {name} */", value)
        generated = Path(cls.temp.name) / "InputDrawingLifecycleHarness.java"
        generated.write_text(fixture)
        command = [javac] if javac else [cls.java, "-jar", ecj, "-warn:none"]
        result = subprocess.run(command + ["-d", cls.temp.name, str(generated)], capture_output=True, text=True)
        if result.returncode:
            raise AssertionError(result.stdout + result.stderr)
        cls.records = []

    @classmethod
    def tearDownClass(cls):
        if os.environ.get("INPUT_LIFECYCLE_RESULTS"):
            Path(os.environ["INPUT_LIFECYCLE_RESULTS"]).write_text(json.dumps(cls.records, indent=2) + "\n")

    def run_case(self, *args):
        result = subprocess.run(
            [self.java, "-cp", self.temp.name, "InputDrawingLifecycleHarness", *args],
            capture_output=True, text=True,
        )
        self.records.append({"args": args, "returncode": result.returncode,
                             "stdout": result.stdout, "stderr": result.stderr})
        self.assertEqual(result.returncode, 0, result.stdout + result.stderr)

    def test_bold_superclass_exception_restoration(self):
        for debug in ("false", "true"):
            for wrapped in ("false", "true"):
                for gradient in ("false", "true"):
                    for at in ("none", "super", "quote", "updateEmoji", "emoji", "ripple", "spoiler", "hint"):
                        # The omitted normal tail composites/restores the gradient layer.
                        # Only exception paths can assert its state at this prefix boundary.
                        if gradient == "true" and at == "none":
                            continue
                        with self.subTest(debug=debug, wrapped=wrapped, gradient=gradient, failure=at):
                            self.run_case("draw", at, debug, "bold", wrapped, gradient)

    def test_effects_exception_restoration(self):
        for wrapped in ("false", "true"):
            for at in ("none", "super", "quote", "updateEmoji", "emoji", "ripple", "spoiler"):
                with self.subTest(wrapped=wrapped, failure=at):
                    self.run_case("draw", at, "false", "effects", wrapped, "false")

    def test_cursor_exception_restoration_before_subsequent_painting(self):
        for debug in ("false", "true"):
            for custom in ("false", "true"):
                for at in ("none", "cursor-position", "cursor-draw"):
                    with self.subTest(debug=debug, custom=custom, failure=at):
                        self.run_case("cursor", at, debug, custom)

    def test_spoiler_pending_timeout_restarts_after_reattach(self):
        for scenario in ("pending", "noPending"):
            with self.subTest(scenario=scenario):
                self.run_case("lifecycle", scenario)

    def test_spoiler_hidden_or_selected_does_not_schedule(self):
        for scenario in ("hidden", "inside"):
            with self.subTest(scenario=scenario):
                self.run_case("lifecycle", scenario)

    def test_spoiler_selection_cancels_restarted_timeout(self):
        self.run_case("lifecycle", "moveInside")


if __name__ == "__main__":
    unittest.main()
