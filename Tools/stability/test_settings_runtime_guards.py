"""Guards for the small settings/keyboard fixes that do not change call signatures."""
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
ENTER = ROOT / 'TMessagesProj/src/main/java/org/telegram/ui/Components/ChatActivityEnterView.java'
CONFIG = ROOT / 'TMessagesProj/src/main/java/tw/nekomimi/nekogram/config/ConfigItem.java'
CELL = ROOT / 'TMessagesProj/src/main/java/tw/nekomimi/nekogram/config/CellGroup.java'
BASE = ROOT / 'TMessagesProj/src/main/java/tw/nekomimi/nekogram/settings/BaseNekoSettingsActivity.java'


class SettingsRuntimeGuardsTest(unittest.TestCase):
    def test_keyboard_height_is_written_asynchronously(self):
        source = ENTER.read_text(encoding='utf-8')
        self.assertIn('.putInt("kbd_height_land3", keyboardHeightLand).apply()', source)
        self.assertIn('.putInt("kbd_height", keyboardHeight).apply()', source)
        self.assertNotIn('kbd_height_land3", keyboardHeightLand).commit()', source)
        self.assertNotIn('kbd_height", keyboardHeight).commit()', source)
        # These three still commit because their read-after-write timing is separate.
        self.assertEqual(source.count('.commit()'), 3)

    def test_config_value_is_visible_across_threads(self):
        source = CONFIG.read_text(encoding='utf-8')
        self.assertIn('public volatile Object value;', source)

    def test_row_map_reverse_is_cleared_with_the_forward_map(self):
        source = BASE.read_text(encoding='utf-8')
        start = source.index('protected void updateRows()')
        body = source[start:source.index('}', start)]
        self.assertIn('rowMap.clear();', body)
        self.assertIn('rowMapReverse.clear();', body)

    def test_need_set_divider_does_not_read_past_the_last_row(self):
        source = CELL.read_text(encoding='utf-8')
        start = source.index('public boolean needSetDivider')
        body = source[start:source.index('}', start)]
        self.assertIn('index < 0 || index + 1 >= rows.size()', body)
        self.assertIn('return false;', body)
        self.assertNotIn('rows.get(rows.indexOf(cell) + 1)', body)
