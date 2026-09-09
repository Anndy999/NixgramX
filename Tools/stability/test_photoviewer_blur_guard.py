"""Guard the PhotoViewer blur A/B change without exercising Android UI."""
import re
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
VIEWER = ROOT / 'TMessagesProj/src/main/java/org/telegram/ui/PhotoViewer.java'


class PhotoViewerBlurGuardTest(unittest.TestCase):
    def test_transition_skips_blur_invalidation_and_final_refresh_remains(self):
        source = VIEWER.read_text(encoding='utf-8')
        start = source.index('    private void invalidateBlur()')
        end = source.index('\n    }', start) + len('\n    }')
        method = source[start:end]

        self.assertIn('if (animationInProgress != 0) {\n            return;\n        }', method)
        self.assertLess(
            method.index('if (animationInProgress != 0)'),
            method.index('invalidateAllGlassAttachedViews()'),
        )
        self.assertRegex(source, r'animationInProgress\s*=\s*0;\s*invalidateBlur\(\);')

    def test_no_attach_alert_pause_architecture_is_reintroduced(self):
        source = VIEWER.read_text(encoding='utf-8')
        self.assertNotIn('setPhotoViewerTransitionAnimating', source)
        self.assertNotIn('isHeavyGlassPaused', source)
        self.assertNotIn('selectionChromeAnimating', source)


if __name__ == '__main__':
    unittest.main()
