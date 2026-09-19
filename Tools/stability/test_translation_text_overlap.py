"""Guard translation swaps against drawing stale outgoing message text."""

from pathlib import Path
import unittest


ROOT = Path(__file__).resolve().parents[2]
CHAT_MESSAGE_CELL = ROOT / "TMessagesProj/src/main/java/org/telegram/ui/Cells/ChatMessageCell.java"

INCOMING_OFFSET_CALL = (
    "getTranslationIncomingTextOffsetY(getTranslationIncomingTextProgress())"
)


class TranslationTextOverlapTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.source = CHAT_MESSAGE_CELL.read_text(encoding="utf-8")
        start = cls.source.index("    public void drawMessageText(Canvas canvas)")
        end = cls.source.index("\n    public void drawMessageText(", start + 1)
        cls.draw_message_text = cls.source[start:end]

    def _slice(self, start_anchor, end_anchor):
        start = self.source.index(start_anchor)
        end = self.source.index(end_anchor, start + 1)
        return self.source[start:end]

    def test_translation_swap_skips_outgoing_text_layer(self):
        guard = "if (!transitionParams.animateTranslationText) {"
        outgoing = "drawMessageText(textX, textY, canvas, transitionParams.animateOutTextBlocks"
        incoming = "drawMessageText(textX, textY + getTranslationIncomingTextOffsetY(incomingProgress)"

        guard_start = self.draw_message_text.index(guard)
        guard_end = self.draw_message_text.index("\n                }", guard_start)
        outgoing_position = self.draw_message_text.index(outgoing)
        incoming_position = self.draw_message_text.index(incoming)

        self.assertLess(guard_start, outgoing_position)
        self.assertLess(outgoing_position, guard_end)
        self.assertLess(guard_end, incoming_position)

    def test_translation_incoming_motion_remains_enabled(self):
        self.assertIn("getTranslationIncomingTextProgress()", self.draw_message_text)
        self.assertIn("getTranslationIncomingTextOffsetY(incomingProgress)", self.draw_message_text)
        self.assertIn("incomingProgress, true, false, false", self.draw_message_text)

    def test_message_emoji_layer_carries_translation_offset(self):
        """Custom emoji are drawn in a separate pass from the text glyphs (drawMessageText).

        The emoji pass derives its Y from the textY argument it is handed, so it must
        receive the very same translation draw offset as the incoming text glyphs;
        otherwise animated emoji visibly detach from the translated text mid-swap.
        """
        emoji_pass = self._slice(
            "    private void drawAnimatedEmojiMessageText(Canvas canvas, float alpha) {",
            "\n    private void drawAnimatedEmojiMessageText(float textX, float textY, Canvas canvas,",
        )
        self.assertIn(INCOMING_OFFSET_CALL, emoji_pass)
        self.assertIn("textY + incomingEmojiOffsetY", emoji_pass)

    def test_caption_emoji_layer_carries_translation_offset(self):
        """Caption custom emoji live in their own pass and must mirror the text pass.

        drawCaptionLayout draws the caption text inside translate(0, offset); the caption
        emoji pass has to travel with it or the emoji stay behind while the caption slides.
        """
        caption_pass = self._slice(
            "    public void drawAnimatedEmojiCaption(Canvas canvas, float alpha) {",
            "\n    public void setHideSideButtonByQuickShare",
        )
        self.assertIn(INCOMING_OFFSET_CALL, caption_pass)
        self.assertIn("captionY + incomingOffsetY", caption_pass)

    def test_caption_text_layer_still_uses_translation_offset(self):
        caption_layout = self._slice(
            "    public void drawCaptionLayout(Canvas canvas, boolean selectionOnly, float alpha) {",
            "\n    public void drawCommentLayout(",
        )
        self.assertIn("getTranslationIncomingTextOffsetY(incomingProgress)", caption_layout)
        self.assertIn("canvas.translate(0f, incomingOffsetY)", caption_layout)


if __name__ == "__main__":
    unittest.main()
