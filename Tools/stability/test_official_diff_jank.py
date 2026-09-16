"""Static contract for the private attach-preview diagnostic."""
from pathlib import Path

root = Path('TMessagesProj/src/main/java/org/telegram/ui')
telemetry = (root / 'Components/NixOfficialDiffJank.java').read_text(encoding='utf-8')
viewer = (root / 'PhotoViewer.java').read_text(encoding='utf-8')
attach = (root / 'Components/ChatAttachAlert.java').read_text(encoding='utf-8')

assert 'NixOfficialDiffJank' in telemetry
assert 'NixOfficialDiffJank' in viewer and 'NixOfficialDiffJank' in attach
for field in ('chatType=', 'elapsedRealtime=', 'durationMs=', 'animationInProgress=',
              'ATTACH_OPEN_BEGIN', 'ATTACH_FIRST_DRAW', 'ATTACH_READY',
              'VIEWER_OPEN_BEGIN', 'MORPH_BEGIN', 'MORPH_END', 'TRANSITION_SUMMARY'):
    assert field in telemetry, field
for forbidden in ('chatId=', 'userId=', 'messageId=', 'username=', 'mediaPath=', 'filename='):
    assert forbidden not in telemetry, forbidden
blur_method = viewer[viewer.index('private void invalidateBlur()'):]
assert '\n        if (animationInProgress != 0) {' not in blur_method, 'diagnostic must not add a blur behavior guard'
print('official-vs-nix private diagnostic contract PASS')
