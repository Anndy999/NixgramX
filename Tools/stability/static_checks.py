"""Small source/privacy guards, not a substitute for Android lint or runtime tests."""
from pathlib import Path
import re
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
release = Path('.github/workflows/release.yml').read_text()
assert "if: github.event_name == 'workflow_dispatch' && inputs.publish" in release
assert 'python3 Tools/stability/release_gate.py' in release
root = Path('TMessagesProj/src/main/java/org/telegram/messenger')
for path in root.glob('*Push*.java'):
    for line in path.read_text().splitlines():
        if 'FileLog.' in line:
            assert not re.search(r'\+\s*(token|currentPushString|data|jsonString)\b|bytesToHex\(SharedConfig.pushAuthKey', line), (path, 'Sensitive push log')
print('YAML / Android XML / manifest parsing and privacy gates PASS')
