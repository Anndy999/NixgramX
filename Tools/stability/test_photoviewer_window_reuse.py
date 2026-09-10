"""Regression checks for PhotoViewer's normal-window reuse path."""

from pathlib import Path
import unittest


SOURCE = (
    Path(__file__).resolve().parents[2]
    / "TMessagesProj/src/main/java/org/telegram/ui/PhotoViewer.java"
)


def method_body(source: str, signature: str) -> str:
    start = source.index(signature)
    brace = source.index("{", start)
    depth = 0
    for index in range(brace, len(source)):
        if source[index] == "{":
            depth += 1
        elif source[index] == "}":
            depth -= 1
            if depth == 0:
                return source[brace : index + 1]
    raise AssertionError(f"Unclosed method: {signature}")


class PhotoViewerWindowReuseTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.source = SOURCE.read_text(encoding="utf-8")

    def test_normal_open_reuses_attached_window(self):
        body = method_body(self.source, "private void showPhotoViewerWindow(WindowManager wm)")
        self.assertIn("if (windowView.getParent() == null)", body)
        self.assertIn("wm.addView(windowView, windowLayoutParams)", body)
        self.assertIn("windowView.setVisibility(View.VISIBLE)", body)
        self.assertIn("wm.updateViewLayout(windowView, windowLayoutParams)", body)

    def test_normal_close_hides_instead_of_detaching(self):
        closed = method_body(self.source, "private void onPhotoClosed(PlaceProviderObject object)")
        self.assertIn("hidePhotoViewerWindow()", closed)
        self.assertNotIn("wm.removeView(windowView)", closed)
        hidden = method_body(self.source, "private void hidePhotoViewerWindow()")
        self.assertIn("!isVisible", hidden)
        self.assertIn("windowView.setVisibility(View.INVISIBLE)", hidden)

    def test_pip_and_final_destroy_still_detach(self):
        pip = method_body(self.source, "public void pipHidePrimaryWindowView(Runnable firstFrameCallback)")
        self.assertIn("wm.removeView(windowView)", pip)
        destroy = method_body(self.source, "public void destroyPhotoViewer()")
        self.assertIn("wm.removeViewImmediate(windowView)", destroy)

    def test_back_callback_does_not_accumulate_while_window_is_hidden(self):
        show = method_body(self.source, "private void registerPhotoViewerBackCallback()")
        hide = method_body(self.source, "private void unregisterPhotoViewerBackCallback()")
        self.assertIn("photoViewerBackDispatcher != null", show)
        self.assertIn("unregisterOnBackInvokedCallback", hide)
        self.assertIn("unregisterPhotoViewerBackCallback()", self.source)


if __name__ == "__main__":
    unittest.main()
