"""Emoji panel glass capture must hash so popup animation can skip a full grid recapture."""
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
EMOJI = ROOT / 'TMessagesProj/src/main/java/org/telegram/ui/Components/EmojiView.java'


class EmojiBlurCaptureHashTest(unittest.TestCase):
    def test_emoji_capture_implements_hash(self):
        source = EMOJI.read_text(encoding='utf-8')
        self.assertIn('blurCaptureMethod = new IBlur3Capture()', source)
        self.assertIn('public void captureCalculateHash(IBlur3Hash builder, RectF position)', source)
        self.assertIn('capture.captureCalculateHash(builder, position)', source)
        self.assertNotIn('blurCaptureMethod = (canvas, position) ->', source)

    def test_dispatch_draw_does_not_recapture_unconditionally(self):
        source = EMOJI.read_text(encoding='utf-8')
        self.assertIn('if (blurCapturesDirty)', source)
        self.assertIn('blurCapturesDirty = false', source)

    def test_on_scrolled_skips_zero_delta(self):
        source = EMOJI.read_text(encoding='utf-8')
        self.assertIn('private void onBlurPanelScrolled(int dx, int dy)', source)
        self.assertIn('if (dx == 0 && dy == 0)', source)
        self.assertIn('onBlurPanelScrolled(dx, dy)', source)
        self.assertEqual(source.count('onBlurPanelScrolled(dx, dy)'), 3)
