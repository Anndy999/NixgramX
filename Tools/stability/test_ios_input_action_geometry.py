"""Regression coverage for iOS-style chat-input action geometry."""

from pathlib import Path
import unittest


ROOT = Path(__file__).resolve().parents[2]
CHAT_ENTER_VIEW = ROOT / "TMessagesProj/src/main/java/org/telegram/ui/Components/ChatActivityEnterView.java"


class IosInputActionGeometryTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.source = CHAT_ENTER_VIEW.read_text(encoding="utf-8")

    def test_ios_action_visual_is_40dp_inside_the_existing_touch_target(self):
        self.assertIn("private static final int IOS_ACTION_BUBBLE_SIZE_DP = 40;", self.source)
        self.assertIn(
            "private static final int IOS_ACTION_BUBBLE_RADIUS_DP = IOS_ACTION_BUBBLE_SIZE_DP / 2;",
            self.source,
        )

        start = self.source.index("    private void setIosActionBubbleBounds(BlurredBackgroundDrawable bubble, View view)")
        end = self.source.index("\n    public float getVisualHeight()", start)
        helpers = self.source[start:end]
        self.assertEqual(helpers.count("final int targetSize = dp(DEFAULT_HEIGHT);"), 2)
        self.assertEqual(helpers.count("final int visualSize = dp(IOS_ACTION_BUBBLE_SIZE_DP);"), 2)
        self.assertEqual(helpers.count("final int inset = (targetSize - visualSize) / 2;"), 2)
        self.assertIn("final int actionLeft = view.getRight() - targetSize;", helpers)
        self.assertIn("final int actionLeft = width - targetSize;", helpers)

    def test_all_right_hand_ios_action_states_share_the_same_bounds(self):
        for call in (
            "setIosActionBubbleBounds(sendBubbleDrawable, child);",
            "setIosActionBubbleBounds(voiceBubbleDrawable, getMeasuredWidth(), getMeasuredHeight());",
            "setIosActionBubbleBounds(bubble, view);",
        ):
            self.assertIn(call, self.source)

        self.assertNotIn(
            "sendBubbleDrawable.setBounds(child.getRight() - dp(DEFAULT_HEIGHT)",
            self.source,
        )
        self.assertNotIn(
            "voiceBubbleDrawable.setBounds(getMeasuredWidth() - dp(DEFAULT_HEIGHT)",
            self.source,
        )

    def test_action_bubble_radius_matches_the_40dp_visual(self):
        for name in (
            "sendBubbleDrawable",
            "voiceBubbleDrawable",
            "expandStickersBubbleDrawable",
            "cancelBotBubbleDrawable",
        ):
            self.assertIn(
                f"{name}.setRadius(dp(IOS_ACTION_BUBBLE_RADIUS_DP));",
                self.source,
            )


if __name__ == "__main__":
    unittest.main()
