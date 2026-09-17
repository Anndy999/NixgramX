"""Guard translation swaps against drawing stale outgoing message text."""

from pathlib import Path
import unittest


ROOT = Path(__file__).resolve().parents[2]
CHAT_MESSAGE_CELL = ROOT / "TMessagesProj/src/main/java/org/telegram/ui/Cells/ChatMessageCell.java"


class TranslationTextOverlapTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.source = CHAT_MESSAGE_CELL.read_text(encoding="utf-8")
        start = cls.source.index("    public void drawMessageText(Canvas canvas)")
        end = cls.source.index("\n    public void drawMessageText(", start + 1)
        cls.draw_message_text = cls.source[start:end]

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


if __name__ == "__main__":
    unittest.main()
