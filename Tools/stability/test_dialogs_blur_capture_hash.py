"""Home-list glass capture must hash so idle dispatchDraw can skip a full list recapture."""
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
DIALOGS = ROOT / 'TMessagesProj/src/main/java/org/telegram/ui/DialogsActivity.java'
CAPTURE = ROOT / 'TMessagesProj/src/main/java/org/telegram/ui/Components/blur3/capture/IBlur3Capture.java'
SUPPRESSOR = ROOT / 'TMessagesProj/src/main/java/org/telegram/ui/Components/blur3/DownscaleScrollableNoiseSuppressor.java'


class DialogsBlurCaptureHashTest(unittest.TestCase):
    def test_default_capture_hash_is_unsupported(self):
        source = CAPTURE.read_text(encoding='utf-8')
        self.assertIn('default void captureCalculateHash', source)
        self.assertIn('builder.unsupported()', source)

    def test_suppressor_skips_when_hash_matches(self):
        source = SUPPRESSOR.read_text(encoding='utf-8')
        self.assertIn('if (!builder.isUnsupported() && sourcePart.lastHash == hash', source)

    def test_dialogs_capture_implements_hash(self):
        source = DIALOGS.read_text(encoding='utf-8')
        self.assertIn('iBlur3Capture = new IBlur3Capture()', source)
        self.assertIn('public void captureCalculateHash(IBlur3Hash builder, RectF position)', source)
        self.assertIn('hashDialogsBlurCapture(builder, position)', source)
        self.assertIn('list.computeVerticalScrollOffset()', source)
        self.assertNotIn('iBlur3Capture = (canvas, position) ->', source)

    def test_on_scrolled_does_not_recapture_on_zero_delta(self):
        # 13.2.2: the list is registered with GlassEngine.addScrolledView; onScrolled no longer
        # invalidates the blur itself, so a zero-delta scroll cannot force a recapture
        source = DIALOGS.read_text(encoding='utf-8')
        self.assertIn('glassEngine.addScrolledView(viewPage.listView)', source)
        self.assertNotIn('blur3_InvalidateBlur', source)
        self.assertIn('private void invalidateBlurCaptures(int flags)', source)
        self.assertIn('findFirstVisibleItemPosition()', source)
