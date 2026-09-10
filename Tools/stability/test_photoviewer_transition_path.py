"""Guard the Telegram-aligned PhotoViewer open transition contract."""

from pathlib import Path
import unittest


ROOT = Path(__file__).resolve().parents[2]
VIEWER = ROOT / "TMessagesProj/src/main/java/org/telegram/ui/PhotoViewer.java"
ACTION_BAR = ROOT / "TMessagesProj/src/main/java/org/telegram/ui/ActionBar/ActionBar.java"
CHAT_ACTIVITY = ROOT / "TMessagesProj/src/main/java/org/telegram/ui/ChatActivity.java"


class PhotoViewerTransitionPathTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.viewer = VIEWER.read_text(encoding="utf-8")
        cls.action_bar = ACTION_BAR.read_text(encoding="utf-8")
        cls.chat_activity = CHAT_ACTIVITY.read_text(encoding="utf-8")
        start = cls.viewer.index("    public boolean openPhoto(final MessageObject messageObject,")
        end = cls.viewer.index("\n    public void closePhoto", start)
        cls.open_photo = cls.viewer[start:end]

    def test_open_keeps_the_official_add_view_then_predraw_transition_order(self):
        add_view = self.open_photo.index("wm.addView(windowView, windowLayoutParams);")
        animation_state = self.open_photo.index("animationInProgress = 1;")
        pre_draw = self.open_photo.index("addOnPreDrawListener")
        clock = self.open_photo.index("transitionAnimationStartTime = System.currentTimeMillis();", pre_draw)
        start = self.open_photo.index("animatorSet.start();", clock)

        self.assertLess(add_view, animation_state)
        self.assertLess(animation_state, pre_draw)
        self.assertLess(pre_draw, clock)
        self.assertLess(clock, start)
        self.assertIn("backgroundDrawable.setAlpha(0);", self.open_photo[pre_draw:start])
        self.assertIn("containerView.setAlpha(0);", self.open_photo[pre_draw:start])

    def test_transition_retains_blur_guard_final_refresh_and_normal_window_lifecycle(self):
        blur_start = self.viewer.index("    private void invalidateBlur()")
        blur_end = self.viewer.index("\n    }", blur_start) + len("\n    }")
        blur = self.viewer[blur_start:blur_end]

        self.assertIn("if (animationInProgress != 0) {\n            return;\n        }", blur)
        self.assertLess(blur.index("if (animationInProgress != 0)"), blur.index("invalidateAllGlassAttachedViews()"))
        self.assertRegex(self.viewer, r"animationInProgress\s*=\s*0;\s*invalidateBlur\(\);")
        self.assertIn("wm.addView(windowView, windowLayoutParams);", self.open_photo)
        self.assertIn("wm.removeView(windowView);", self.viewer)

    def test_no_reuse_delay_or_synthetic_menu_workarounds_are_present(self):
        forbidden_viewer = (
            "showPhotoViewerWindow",
            "hidePhotoViewerWindow",
            "setPhotoViewerTransitionAnimating",
            "isHeavyGlassPaused",
            "selectionChromeAnimating",
        )
        for symbol in forbidden_viewer:
            self.assertNotIn(symbol, self.viewer)

        self.assertNotIn("postDelayed", self.open_photo)
        self.assertNotIn("glassMenuMinimumItems", self.action_bar)
        self.assertNotIn("calculateGlassMenuGeometryWidth", self.action_bar)
        self.assertNotIn("setGlassMenuMinimumItems", self.chat_activity)


if __name__ == "__main__":
    unittest.main()
