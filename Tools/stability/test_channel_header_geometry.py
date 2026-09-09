"""Regression coverage for broadcast-channel Liquid Glass menu geometry."""
import subprocess
import tempfile
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
ACTION_BAR = ROOT / 'TMessagesProj/src/main/java/org/telegram/ui/ActionBar/ActionBar.java'
CHAT_ACTIVITY = ROOT / 'TMessagesProj/src/main/java/org/telegram/ui/ChatActivity.java'


def extract_method(source, signature):
    start = source.index(signature)
    brace = source.index('{', start)
    depth = 0
    for index in range(brace, len(source)):
        if source[index] == '{':
            depth += 1
        elif source[index] == '}':
            depth -= 1
            if depth == 0:
                return source[start:index + 1]
    raise AssertionError('unterminated production method')


class ChannelHeaderGeometryTest(unittest.TestCase):
    def test_production_geometry_calculation(self):
        source = ACTION_BAR.read_text(encoding='utf-8')
        method = extract_method(source, '    static int calculateGlassMenuGeometryWidth(')
        harness = '''
class ActionBar {
METHOD
}
public class ChannelHeaderGeometryTest {
    static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
    public static void main(String[] args) {
        // Private: call plus overflow already occupy two standard 48dp cells.
        check(ActionBar.calculateGlassMenuGeometryWidth(94, 2, 48, 0) == 94, "private geometry changed");
        // Channel: overflow is the sole real cell, so reserve its real peer cell for glass only.
        check(ActionBar.calculateGlassMenuGeometryWidth(46, 1, 48, 2) == 94, "channel does not match private geometry");
        // Two channel actions already have the private geometry; do not add another cell.
        check(ActionBar.calculateGlassMenuGeometryWidth(94, 2, 48, 2) == 94, "channel double-reserved a menu cell");
        // Groups and forums do not opt in and keep their existing geometry.
        check(ActionBar.calculateGlassMenuGeometryWidth(46, 1, 48, 0) == 46, "group geometry changed");
        check(ActionBar.calculateGlassMenuGeometryWidth(46, 1, 48, 1) == 46, "forum geometry changed");
        check(ActionBar.calculateGlassMenuGeometryWidth(0, 0, 0, 2) == 0, "hidden menu gained glass");
        System.out.println("PASS: private reference, channel parity, group/forum isolation");
    }
}
'''.replace('METHOD', method)
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            path = root / 'ChannelHeaderGeometryTest.java'
            path.write_text(harness, encoding='utf-8')
            subprocess.run(['javac', '-encoding', 'UTF-8', '-d', str(root), str(path)], check=True)
            subprocess.run(['java', '-cp', str(root), 'ChannelHeaderGeometryTest'], check=True)

    def test_channel_only_opt_in_and_draw_bounds(self):
        action = ACTION_BAR.read_text(encoding='utf-8')
        chat = CHAT_ACTIVITY.read_text(encoding='utf-8')
        self.assertIn('final int glassMenuWidth = getGlassMenuGeometryWidth(menuWidth);', action)
        self.assertIn('final int menuWidthWithPadding = glassMenuWidth +', action)
        self.assertIn('Math.max(s, glassMenuWidth)', action)
        self.assertIn('ChatObject.isChannelAndNotMegaGroup(currentChat)', chat)
        self.assertIn('chatMode == MODE_DEFAULT && !isTopic', chat)
        self.assertIn('actionBar.setGlassMenuMinimumItems(2);', chat)
        self.assertNotIn('setGlassMenuMinimumItems(2);\n        } else if (isComments)', chat)


if __name__ == '__main__':
    unittest.main()
