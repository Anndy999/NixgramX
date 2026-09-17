"""Regression coverage for official broadcast-channel Liquid Glass geometry."""
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
ACTION_BAR = ROOT / 'TMessagesProj/src/main/java/org/telegram/ui/ActionBar/ActionBar.java'
CHAT_ACTIVITY = ROOT / 'TMessagesProj/src/main/java/org/telegram/ui/ChatActivity.java'
CHAT_AVATAR_CONTAINER = ROOT / 'TMessagesProj/src/main/java/org/telegram/ui/Components/ChatAvatarContainer.java'
ACTION_BAR_MENU = ROOT / 'TMessagesProj/src/main/java/org/telegram/ui/ActionBar/ActionBarMenu.java'


class ChannelHeaderGeometryTest(unittest.TestCase):
    def test_real_menu_width_drives_all_glass_bounds(self):
        action = ACTION_BAR.read_text(encoding='utf-8')
        self.assertIn('final int menuWidthWithPadding = menuWidth +', action)
        self.assertIn('Math.max(s, menuWidth)', action)
        self.assertIn('menu != null ? menu.getMeasuredWidth() : 0', action)

    def test_action_mode_width_reaches_the_real_glass_path(self):
        action = ACTION_BAR.read_text(encoding='utf-8')
        data_flow = (
            'actionMode.getItemsWidth()',
            'actionModeVisible ? actionMenuWidth : defaultMenuWidth',
            'animatorMenuItemsWidth.animateTo(width)',
            'animatorMenuItemsWidth.getFactor()',
            'final int menuWidth =',
            'final int menuWidthWithPadding = menuWidth +',
            'Math.max(s, menuWidth)',
        )
        positions = [action.index(step) for step in data_flow]
        self.assertEqual(positions, sorted(positions))

        action_mode_path = action[action.index('public void checkMenuItemsWidth()'):]
        self.assertNotIn('getVisibleItemCount', action_mode_path)
        self.assertNotIn('getLargestVisibleItemWidth', action_mode_path)

    def test_no_channel_only_fake_menu_geometry(self):
        sources = '\n'.join(path.read_text(encoding='utf-8') for path in (
            ACTION_BAR, ACTION_BAR_MENU, CHAT_ACTIVITY, CHAT_AVATAR_CONTAINER
        ))
        for obsolete in (
            'glassMenuMinimumItems',
            'calculateGlassMenuGeometryWidth',
            'getGlassMenuGeometryWidth',
            'setGlassMenuMinimumItems',
            'NIXGRAMX_CHANNEL_HEADER_GLASS_GEOMETRY_FROZEN',
            'getVisibleItemCount',
            'getLargestVisibleItemWidth',
        ):
            self.assertNotIn(obsolete, sources)


if __name__ == '__main__':
    unittest.main()
