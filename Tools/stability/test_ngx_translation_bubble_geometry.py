"""Guard: the bubble box must match the text layout that is actually drawn.

Translation swaps replace ``MessageObject.textLayoutBlocks`` in place (via
``generateLayout``) from outside the cell. The cell keeps the geometry it
measured against the *previous* layout, so the bubble box, the text origin and
the timestamp position no longer match the glyphs that get drawn -- the text
runs past the bubble edge until the row happens to be rebound.

``ChatMessageCell`` must therefore remember the layout it measured with and
force a re-measure when the layout it is about to draw is a different object.

These assertions fail on the unfixed tree (no snapshot, no guard) and pass
after the fix. They describe the contract, not a line position.
"""

from pathlib import Path
import unittest


ROOT = Path(__file__).resolve().parents[2]
CHAT_MESSAGE_CELL = ROOT / "TMessagesProj/src/main/java/org/telegram/ui/Cells/ChatMessageCell.java"

SNAPSHOT = "measuredTextLayoutBlocks = messageObject.textLayoutBlocks;"
GUARD = "currentMessageObject.textLayoutBlocks != measuredTextLayoutBlocks"
REMEASURE = "forceResetMessageObject();"


class TranslationBubbleGeometryTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.source = CHAT_MESSAGE_CELL.read_text(encoding="utf-8")

    def _window_after(self, marker, span):
        start = self.source.index(marker)
        return self.source[start:start + span]

    def test_cell_snapshots_the_layout_it_measured_with(self):
        body = self._window_after(
            "private void setMessageObjectInternal(MessageObject messageObject)", 2500
        )
        self.assertIn("measureTime(messageObject);", body)
        measured_at = body.index("measureTime(messageObject);")
        # 快照必须紧跟测量，中间不能插入任何可能再次替换布局的逻辑
        self.assertIn(SNAPSHOT, body[measured_at:measured_at + 200])

    def test_drawn_layout_change_triggers_a_remeasure(self):
        body = self._window_after("protected void onDraw(Canvas canvas)", 2000)
        self.assertIn(GUARD, body)
        self.assertIn(REMEASURE, body)
        # 重测必须发生在绘制路径上，否则布局被替换后无人修复几何
        self.assertLess(body.index(GUARD), body.index(REMEASURE))

    def test_remeasure_is_deferred_until_the_change_animation_settles(self):
        body = self._window_after("protected void onDraw(Canvas canvas)", 2000)
        guard_at = body.index(GUARD)
        # 动画进行中不能重测：换入动画正是由 animateChangeProgress 驱动的
        self.assertIn("transitionParams.animateChangeProgress == 1f", body[guard_at:guard_at + 400])

    def test_remeasure_is_posted_off_the_draw_pass(self):
        body = self._window_after("protected void onDraw(Canvas canvas)", 2000)
        guard_at = body.index(GUARD)
        window = body[guard_at:guard_at + 1200]
        self.assertIn("post(() -> {", window)
        self.assertIn("attachedToWindow", window)
        # 重测不能在绘制过程中同步执行
        self.assertLess(window.index("post(() -> {"), window.index(REMEASURE))


if __name__ == "__main__":
    unittest.main()
