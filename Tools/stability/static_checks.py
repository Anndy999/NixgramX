"""Small source/privacy guards, not a substitute for Android lint or runtime tests."""
from pathlib import Path
import re
import subprocess
import xml.etree.ElementTree as ET
import yaml

for path in Path('.github/workflows').glob('*.yml'):
    yaml.safe_load(path.read_text())
for path in Path('TMessagesProj/src/main/res').rglob('*.xml'):
    ET.parse(path)
for path in Path('TMessagesProj/src').rglob('AndroidManifest.xml'):
    ET.parse(path)
pr = Path('.github/workflows/pr.yml').read_text()
assert "'**.xml'" not in pr, 'Android XML must trigger CI'
assert 'quick-verify.yml' in pr, 'Ordinary PRs must use quick-verify'
assert 'upstream-sync-ci.yml' in pr, 'upstream-sync PRs must keep using upstream-sync-ci'
assert pr.count('secrets: inherit') >= 2, 'PR reusable workflows must inherit TELEGRAM_APP_* secrets'
assert "!startsWith(github.head_ref, 'upstream-sync/')" in pr
assert "startsWith(github.head_ref, 'upstream-sync/')" in pr
qv = Path('.github/workflows/quick-verify.yml').read_text()
assert 'assembleDebug' not in qv, 'Quick Verify must not assembleDebug'
assert 'cache-disabled: true' not in qv, 'Quick Verify should enable Gradle cache'
assert ':TMessagesProj:lintDebug' not in qv, 'Quick Verify must not require full Android lint'
for name in ('staging.yml', 'canary.yml', 'private-beta.yml'):
    wf = Path('.github/workflows') / name
    text = wf.read_text()
    assert 'Tools/stability/static_checks.py' in text, f'{name} must run static checks before publish'
    assert "unittest discover -s Tools/stability" in text, f'{name} must run unit tests before publish'
    assert 'restore-keys:' in text, f'{name} Gradle cache must fall back when the exact key misses'
fv = Path('.github/workflows/full-verify.yml').read_text()
assert 'workflow_dispatch' in fv, 'Full Verify must be manual dispatch'
assert 'assembleDebug' in fv, 'Full Verify must assembleDebug'
assert 'lintDebug' in fv, 'Full Verify must run full Android lint'
assert 'upload.py' not in fv, 'Full Verify must not publish to Telegram'
assert 'softprops/action-gh-release' not in fv and 'gh release' not in fv
assert "if: inputs.source_sha != '' ||" in Path('.github/workflows/upstream-sync-ci.yml').read_text(), 'Upstream-sync reusable compile job gate must remain'
release = Path('.github/workflows/release.yml').read_text()
assert "if: github.event_name == 'workflow_dispatch' && inputs.publish" in release
assert 'python3 Tools/stability/release_gate.py' in release
root = Path('TMessagesProj/src/main/java/org/telegram/messenger')
for path in root.glob('*Push*.java'):
    for line in path.read_text().splitlines():
        if 'FileLog.' in line:
            assert not re.search(r'\+\s*(token|currentPushString|data|jsonString)\b|bytesToHex\(SharedConfig.pushAuthKey', line), (path, 'Sensitive push log')

# IBlur3Capture's default captureCalculateHash calls unsupported(), which disables the
# recapture guard. These shapes cannot override that method:
#   1. lambda assigned as the capture
#   2. method reference assigned as the capture (this:: / Type::method)
#   3. anonymous IBlur3Capture whose own body does not implement captureCalculateHash
#
# Not failures, and not a loosened pattern:
#   - new ViewGroupPartRenderer(...) implements captureCalculateHash. A listView::drawChild
#     or (canvas, child, drawingTime) -> on that line is the draw-child callback.
#   - new IBlur3Capture[n] is an array, not an instance.
#   - assigning an existing object (listView, gridView) keeps that object's own hash.
#     RecyclerListView implements captureCalculateHash.
#   - SearchViewPager implements IBlur3Capture and is passed into captureRelativeParent.
#     That is not one of the three assignment shapes. DialogsActivity hashes that path itself.
_CAPTURE_ASSIGN = re.compile(
    r'(?<![=!<>])=(?!=)\s*(?P<rhs>.+)$'
)
_CAPTURE_NAME = re.compile(
    r'\b(?P<name>[\w.]*(?:blurCaptureMethod|[\w]*Capture))\s*$'
)
_METHOD_REF = re.compile(r'^(?:this|[A-Za-z_][\w.]*)::[A-Za-z_]\w*')


def _source_files():
    root = Path('.')
    names = []
    try:
        listed = subprocess.check_output(
            ['git', 'grep', '-l', '-e', 'IBlur3Capture', '-e', 'iBlur3Capture', 'HEAD', '--', '*.java', '*.kt'],
            text=True, encoding='utf-8', errors='replace')
        for line in listed.splitlines():
            names.append(line.split(':', 1)[-1] if line.startswith('HEAD:') else line)
    except (OSError, subprocess.CalledProcessError):
        return [path for path in Path('TMessagesProj/src').rglob('*.java') if 'IBlur3Capture' in path.read_text(encoding='utf-8', errors='replace')]
    return [root / name for name in names]


def _read_source(path):
    if path.exists():
        return path.read_text(encoding='utf-8', errors='replace')
    rel = path.as_posix().lstrip('./')
    return subprocess.check_output(
        ['git', 'show', f'HEAD:{rel}'],
        text=True, encoding='utf-8', errors='replace')


def _anonymous_body(lines, start):
    depth = 0
    started = False
    chunks = []
    for index in range(start, len(lines)):
        line = lines[index]
        chunks.append(line)
        for ch in line:
            if ch == '{':
                depth += 1
                started = True
            elif ch == '}':
                depth -= 1
                if started and depth == 0:
                    return '\n'.join(chunks)
    return '\n'.join(chunks)


def blur_capture_assignment_failures(files=None):
    failures = []
    for path in files if files is not None else _source_files():
        try:
            text = _read_source(path)
        except (OSError, subprocess.CalledProcessError):
            continue
        lines = text.splitlines()
        for index, line in enumerate(lines):
            stripped = line.strip()
            if not stripped or stripped.startswith('//') or stripped.startswith('*'):
                continue
            assign = _CAPTURE_ASSIGN.search(line)
            if not assign:
                continue
            lhs = line[:assign.start()].rstrip()
            if not _CAPTURE_NAME.search(lhs):
                continue
            rhs = assign.group('rhs').strip()
            where = f'{path.as_posix()}:{index + 1}'
            window = rhs
            if rhs.startswith('(') and '->' not in rhs:
                window = '\n'.join(lines[index:index + 4])
            if rhs.startswith('(') and '->' in window:
                failures.append(f'{where}: IBlur3Capture lambda has no captureCalculateHash')
                continue
            if _METHOD_REF.match(rhs):
                failures.append(f'{where}: IBlur3Capture method reference has no captureCalculateHash')
                continue
            if rhs.startswith('new IBlur3Capture()'):
                body = _anonymous_body(lines, index)
                if 'captureCalculateHash' not in body:
                    failures.append(f'{where}: anonymous IBlur3Capture does not implement captureCalculateHash')
    return failures


blur_failures = blur_capture_assignment_failures()
assert not blur_failures, 'IBlur3Capture hash guard is missing:\n' + '\n'.join(blur_failures)
print('YAML / Android XML / manifest parsing and privacy gates PASS')
