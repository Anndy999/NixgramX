"""Peer color glass capture must hash both page lists, including their scroll offsets."""
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
PEER = ROOT / 'TMessagesProj/src/main/java/org/telegram/ui/PeerColorActivity.java'


class PeerColorBlurCaptureHashTest(unittest.TestCase):
    def test_method_reference_is_gone_and_both_lists_are_hashed(self):
        source = PEER.read_text(encoding='utf-8')
        self.assertNotIn('this::drawList', source)
        self.assertNotIn('iBlur3Capture = (canvas, position) ->', source)
        self.assertIn('public void captureCalculateHash(IBlur3Hash builder, RectF position)', source)
        self.assertIn('hashColorList(namePage != null ? namePage.listView : null, builder, position)', source)
        self.assertIn('hashColorList(profilePage != null ? profilePage.listView : null, builder, position)', source)
        self.assertIn('listView.computeVerticalScrollOffset()', source)
        self.assertIn('listView.computeHorizontalScrollOffset()', source)
        self.assertIn('Blur3Utils.hashRelativeParent(listView, builder, position, listView, contentView)', source)
