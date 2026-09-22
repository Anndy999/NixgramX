"""Statistics glass capture must hash and must not recapture on an idle frame."""
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
STATISTIC = ROOT / 'TMessagesProj/src/main/java/org/telegram/ui/StatisticActivity.java'


class StatisticBlurCaptureHashTest(unittest.TestCase):
    def test_statistic_capture_forwards_child_hashes(self):
        source = STATISTIC.read_text(encoding='utf-8')
        self.assertIn('iBlur3Capture = new IBlur3Capture()', source)
        self.assertIn('public void captureCalculateHash(IBlur3Hash builder, RectF position)', source)
        self.assertIn('cap = listBlur3Capture', source)
        self.assertIn('cap = boostLayout.iBlur3Capture', source)
        self.assertIn('cap = monetizationLayout.iBlur3Capture', source)
        self.assertIn('cap.captureCalculateHash(builder, position)', source)
        self.assertNotIn('iBlur3Capture = (canvas, position) ->', source)

    def test_dispatch_draw_skips_idle_recapture(self):
        source = STATISTIC.read_text(encoding='utf-8')
        self.assertIn('if (blurCapturesDirty)', source)
        self.assertIn('blurCapturesDirty = false', source)
        self.assertIn('(dx != 0 || dy != 0)', source)
        self.assertEqual(source.count('(dx != 0 || dy != 0)'), 3)
