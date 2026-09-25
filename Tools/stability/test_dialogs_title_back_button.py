"""Root-list back button occupancy after forum topics close."""
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
DIALOGS = ROOT / 'TMessagesProj/src/main/java/org/telegram/ui/DialogsActivity.java'
ACTION_BAR = ROOT / 'TMessagesProj/src/main/java/org/telegram/ui/ActionBar/ActionBar.java'
FLOATING = ROOT / 'TMessagesProj/src/main/java/org/telegram/ui/Components/FragmentFloatingButton.java'


class DialogsTitleBackButtonTest(unittest.TestCase):
    def test_bottom_tabs_do_not_reoccupy_root_back_slot(self):
        dialogs = DIALOGS.read_text(encoding='utf-8')
        helper = dialogs[dialogs.index('private boolean shouldShowRootListBackButton()'):]
        helper = helper[:helper.index('private void checkUi_itemBackButtonVisibility()')]
        self.assertIn('NixNavigationConfig.isDrawerEnabled()', helper)
        self.assertIn('folderId != 0', helper)
        self.assertIn('communityId != 0', helper)
        self.assertIn('onlySelect', helper)
        self.assertIn('searchString != null', helper)

        method = dialogs[dialogs.index('private void checkUi_itemBackButtonVisibility()'):]
        method = method[:method.index('private void checkUi_itemOptionsVisibility()')]
        self.assertIn('if (!shouldShowRootListBackButton())', method)
        self.assertIn('factor = progressToActionMode', method)
        self.assertIn('setAnimatedVisibility(actionBar.getBackButton(), factor)', method)

        open_progress = dialogs[dialogs.index('void setOpenProgress(float progress)'):]
        open_progress = open_progress[:open_progress.index('checkUi_menuItems()')]
        self.assertIn('shouldShowRootListBackButton()', open_progress)
        self.assertNotIn(
            'if (actionBar.getBackButton() != null) {\n                    actionBar.getBackButton().setAlpha(progress == 1f ? 0f : 1f);',
            open_progress,
        )

    def test_visible_back_button_still_owns_title_inset(self):
        action = ACTION_BAR.read_text(encoding='utf-8')
        layout = action[action.index('protected void onLayout('):]
        self.assertIn('backButtonImageView.getVisibility() != GONE', layout)
        self.assertIn('dp(AndroidUtilities.isTablet() ? 80 : 72)', layout)

        if FLOATING.exists():
            floating = FLOATING.read_text(encoding='utf-8')
            self.assertIn('v.setVisibility(f > 0 ? VISIBLE : GONE)', floating)


if __name__ == '__main__':
    unittest.main()
