"""NGX settings onResume must never call notifyDataSetChanged while RecyclerView
is computing a layout.

Real-device crash (build 1330 / SHA 43ae84d):

    FATAL EXCEPTION: main
    java.lang.RuntimeException: Unable to resume activity {app.nixgramx.android/...}
    Caused by: java.lang.IllegalStateException:
        Cannot call this method while RecyclerView is computing a layout or scrolling
      at androidx.recyclerview.widget.RecyclerView.assertNotInLayoutOrScroll(...)
      at BaseNekoSettingsActivity.onResume(BaseNekoSettingsActivity.java:203)
      at ProblemDiagnosticsActivity.onResume(ProblemDiagnosticsActivity.java:92)

The base class onResume called notifyDataSetChanged() unconditionally, and
ProblemDiagnosticsActivity.onResume called it a second time after updateRows().
On the first cold start the RecyclerView is still laying out, so the second
notify hit assertNotInLayoutOrScroll and killed the process ("first launch
crashes, second launch works").

The fix converges all resume-time refreshes onto a single guarded helper,
BaseNekoSettingsActivity.refreshRowsSafely(), which defers via post() while the
RecyclerView is computing layout. This test guards that contract:

  1. BaseNekoSettingsActivity.onResume delegates to refreshRowsSafely().
  2. refreshRowsSafely() guards isComputingLayout() and defers via post().
  3. ProblemDiagnosticsActivity no longer overrides onResume nor calls
     notifyDataSetChanged() directly.
"""

from pathlib import Path
import re
import unittest


ROOT = Path(__file__).resolve().parents[2]
SETTINGS = ROOT / "TMessagesProj/src/main/java/tw/nekomimi/nekogram/settings"
BASE = SETTINGS / "BaseNekoSettingsActivity.java"
PROBLEM_DIAGNOSTICS = SETTINGS / "ProblemDiagnosticsActivity.java"


def _on_resume_body(text):
    """Return the body of the (first) onResume() method, if present."""
    match = re.search(
        r"public\s+void\s+onResume\s*\(\s*\)\s*\{(.*?)\n    \}",
        text,
        re.S,
    )
    return match.group(1) if match else None


class NgxDiagnosticsRefreshTest(unittest.TestCase):
    def test_base_on_resume_delegates_to_guarded_refresh(self):
        base = BASE.read_text(encoding="utf-8")
        body = _on_resume_body(base)
        self.assertIsNotNone(body, "BaseNekoSettingsActivity lost its onResume")
        self.assertIn("refreshRowsSafely()", body,
                      "BaseNekoSettingsActivity.onResume must delegate to "
                      "refreshRowsSafely() instead of calling notifyDataSetChanged()")
        self.assertNotIn("notifyDataSetChanged()", body,
                         "BaseNekoSettingsActivity.onResume must not call "
                         "notifyDataSetChanged() directly")

    def test_base_refresh_guards_computing_layout(self):
        base = BASE.read_text(encoding="utf-8")
        match = re.search(
            r"protected\s+void\s+refreshRowsSafely\s*\(\s*\)\s*\{(.*?)\n    \}",
            base,
            re.S,
        )
        self.assertIsNotNone(match, "BaseNekoSettingsActivity lost refreshRowsSafely()")
        body = match.group(1)
        self.assertIn("isComputingLayout()", body,
                      "refreshRowsSafely() must check isComputingLayout()")
        self.assertIn(".post(", body,
                      "refreshRowsSafely() must defer via post() while computing layout")

    def test_problem_diagnostics_does_not_redefine_refresh(self):
        text = PROBLEM_DIAGNOSTICS.read_text(encoding="utf-8")
        self.assertNotIn("public void onResume", text,
                         "ProblemDiagnosticsActivity must not override onResume; "
                         "the base class already refreshes safely")
        self.assertNotIn("notifyDataSetChanged()", text,
                         "ProblemDiagnosticsActivity must not call "
                         "notifyDataSetChanged() directly")


if __name__ == "__main__":
    unittest.main()
