"""Peer color glass must not keep a stale list-capture pipeline.

Up to 13.1.1 the screen captured both page lists for its glass, so NixgramX hashed both lists
(and their scroll offsets) to skip redundant captures. Telegram 13.2.x moved the screen to color
glass sources (GlassSourceColor) with no capture at all; a leftover capture or noise suppressor
would re-introduce per-frame list redraws without anything consuming them.
"""
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
PEER = ROOT / 'TMessagesProj/src/main/java/org/telegram/ui/PeerColorActivity.java'


class PeerColorBlurCaptureHashTest(unittest.TestCase):
    def test_glass_uses_color_sources_without_list_capture(self):
        source = PEER.read_text(encoding='utf-8')
        self.assertNotIn('this::drawList', source)
        self.assertNotIn('iBlur3Capture = (canvas, position) ->', source)
        self.assertNotIn('DownscaleScrollableNoiseSuppressor', source)
        self.assertNotIn('IBlur3Capture', source)
        self.assertIn('new BlurredBackgroundDrawableViewFactory(iBlur3SourceButtonColor)', source)
        self.assertIn('new BlurredBackgroundDrawableViewFactory(iBlur3SourceBackgroundColor)', source)


if __name__ == '__main__':
    unittest.main()
