"""Statistics glass follows Telegram 13.2.x simple glass: one capture through
BaseFragmentSimpleGlass, invalidated by GlassEngine (no manual recapture pipeline)."""
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
STATISTIC = ROOT / 'TMessagesProj/src/main/java/org/telegram/ui/StatisticActivity.java'


class StatisticBlurCaptureHashTest(unittest.TestCase):
    def test_statistic_uses_simple_glass_capture(self):
        source = STATISTIC.read_text(encoding='utf-8')
        self.assertIn('getBaseSimpleGlass().setCapture(', source)
        self.assertIn('glassEngine.addScrolledView(recyclerListView)', source)

    def test_no_legacy_manual_recapture_pipeline(self):
        source = STATISTIC.read_text(encoding='utf-8')
        # the 12.x/13.1 pipeline (and Nix's idle-skip on top of it) is gone with official
        self.assertNotIn('blur3_InvalidateBlur', source)
        self.assertNotIn('blurCapturesDirty', source)
        self.assertNotIn('scrollableViewNoiseSuppressor.onScrolled', source)


if __name__ == '__main__':
    unittest.main()
