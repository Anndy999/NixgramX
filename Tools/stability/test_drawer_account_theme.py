"""Static regression coverage for Drawer account reorder and theme switching."""
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
DRAWER = ROOT / "TMessagesProj/src/main/java/tw/nekomimi/nekogram/drawer"


class DrawerAccountThemeTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.accounts = (DRAWER / "DrawerAccountPickerView.java").read_text(encoding="utf-8")
        cls.container = (DRAWER / "DrawerContainer.java").read_text(encoding="utf-8")

    def test_account_reorder_uses_item_touch_helper_and_persists(self):
        for required in (
            "new RecyclerView(context)",
            "new LinearLayoutManager(context)",
            "new ItemTouchHelper.Callback()",
            "ItemTouchHelper.UP | ItemTouchHelper.DOWN",
            "isLongPressDragEnabled()",
            "itemTouchHelper.startDrag(holder)",
            "Collections.swap(accounts, from, to)",
            "first.saveConfig(false)",
            "second.saveConfig(false)",
            "notifyItemMoved(from, to)",
            "!PasscodeHelper.isAccountHidden(account)",
        ):
            self.assertIn(required, self.accounts)
        for obsolete in ("reorderMode", "addReorderControls", "reorderButton"):
            self.assertNotIn(obsolete, self.accounts)

    def test_add_account_is_not_draggable_and_unread_badge_remains(self):
        self.assertIn("position >= accounts.size()", self.accounts)
        self.assertIn("VIEW_TYPE_ADD", self.accounts)
        self.assertIn("MessagesStorage.getInstance(account).getMainUnreadCount()", self.accounts)
        self.assertIn("Theme.key_chats_unreadCounter", self.accounts)

    def test_theme_toggle_uses_telegram_pipeline(self):
        for required in (
            'getSharedPreferences("themeconfig"',
            'getString("lastDayTheme", "Blue")',
            'getString("lastDarkTheme", "Dark Blue")',
            "DialogsActivity.switchingTheme = true",
            "headerView.animateThemeToggle(toDark)",
            "NotificationCenter.needSetDayNightTheme",
            "Theme.turnOffAutoNight",
            "NotificationCenter.didSetNewTheme",
            "updateThemeColors()",
        ):
            self.assertIn(required, self.container)
        self.assertNotIn("Theme.applyTheme", self.container)

    def test_drawer_events_use_exported_diagnostics(self):
        for event in (
            "ACCOUNT_REORDER_START",
            "ACCOUNT_REORDER_MOVE",
            "ACCOUNT_REORDER_END",
            "THEME_TOGGLE",
            "DRAWER_THEME_REFRESH",
        ):
            self.assertIn(event, self.accounts + self.container)
        self.assertIn("NgxDiagnostics.event", self.accounts)
        self.assertIn("NgxDiagnostics.event", self.container)
        self.assertNotIn("Diagnostics.navigationEvent", self.container)


if __name__ == "__main__":
    unittest.main()
