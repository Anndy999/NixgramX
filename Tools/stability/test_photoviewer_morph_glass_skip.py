"""A/B contract: skip PhotoViewer Glass/RenderNode rebuild during morph only."""
import re
from pathlib import Path

root = Path('TMessagesProj/src/main/java/org/telegram/ui')
viewer = (root / 'PhotoViewer.java').read_text(encoding='utf-8')
attach = (root / 'Components/ChatAttachAlert.java').read_text(encoding='utf-8')

start = viewer.index('    private void invalidateBlur()')
end = viewer.index('\n    }', start) + len('\n    }')
method = viewer[start:end]

assert 'if (animationInProgress != 0) {\n            return;\n        }' in method
assert method.index('if (animationInProgress != 0)') < method.index('invalidateAllGlassAttachedViews()')
assert re.search(r'animationInProgress\s*=\s*0;\s*invalidateBlur\(\);', viewer)

for forbidden in (
    'setPhotoViewerTransitionAnimating',
    'isHeavyGlassPaused',
    'selectionChromeAnimating',
    'pendingWindowHdrColorModeUpdate',
    'showPhotoViewerWindow',
):
    assert forbidden not in viewer, forbidden
    assert forbidden not in attach, forbidden

print('photoviewer morph glass-skip A/B contract PASS')
