package tw.nekomimi.nekogram.drawer;

import android.os.Bundle;
import android.view.View;

import org.telegram.messenger.BuildVars;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MediaDataController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionIntroActivity;
import org.telegram.ui.CallLogActivity;
import org.telegram.ui.CameraScanActivity;
import org.telegram.ui.ChannelCreateActivity;
import org.telegram.ui.ChatActivity;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.ContactsActivity;
import org.telegram.ui.DialogsActivity;
import org.telegram.ui.GroupCreateActivity;
import org.telegram.ui.NewContactBottomSheet;
import org.telegram.ui.ProfileActivity;
import org.telegram.ui.SettingsActivity;

import tw.nekomimi.nekogram.BackButtonMenuRecent;
import tw.nekomimi.nekogram.NekoConfig;
import tw.nekomimi.nekogram.settings.GhostModeActivity;
import tw.nekomimi.nekogram.ui.BookmarkManagerActivity;

public final class MainMenuHelper {

    public static final class MenuItemInfo {
        public final int iconRes;
        public final CharSequence text;
        public final Runnable onClick;
        public final Runnable onLongClick;

        public MenuItemInfo(int iconRes, CharSequence text, Runnable onClick, Runnable onLongClick) {
            this.iconRes = iconRes;
            this.text = text;
            this.onClick = onClick;
            this.onLongClick = onLongClick;
        }
    }

    private MainMenuHelper() {
    }

    public static MenuItemInfo resolve(int id, BaseFragment fragment, int currentAccount) {
        MainMenuItem item = MainMenuItem.getById(id);
        if (item == null || fragment == null) {
            return null;
        }
        switch (item) {
            case ARCHIVE:
                return new MenuItemInfo(R.drawable.msg_archive, LocaleController.getString(R.string.ArchivedChats), () -> {
                    Bundle args = new Bundle();
                    args.putInt("folderId", 1);
                    fragment.presentFragment(new DialogsActivity(args));
                }, null);
            case NEW_GROUP:
                return new MenuItemInfo(R.drawable.outline_groups_24, LocaleController.getString(R.string.NewGroup), () -> {
                    fragment.presentFragment(new GroupCreateActivity(new Bundle()));
                }, null);
            case SAVED:
                return new MenuItemInfo(R.drawable.outline_saved_24, LocaleController.getString(R.string.SavedMessages), () -> {
                    Bundle args = new Bundle();
                    args.putLong("user_id", UserConfig.getInstance(currentAccount).getClientUserId());
                    fragment.presentFragment(new ChatActivity(args));
                }, null);
            case NEW_CHANNEL:
                return new MenuItemInfo(R.drawable.outline_channel_24, LocaleController.getString(R.string.NewChannel), () -> presentChannelCreate(fragment), null);
            case CALLS:
                return new MenuItemInfo(R.drawable.msg_calls, LocaleController.getString(R.string.Calls), () -> fragment.presentFragment(new CallLogActivity(new Bundle())), null);
            case SETTINGS:
                return new MenuItemInfo(R.drawable.msg_settings_old, LocaleController.getString(R.string.Settings), () -> fragment.presentFragment(new SettingsActivity()), null);
            case QR:
                return new MenuItemInfo(R.drawable.msg_qrcode, LocaleController.getString(R.string.AuthAnotherClient), () -> {
                    CameraScanActivity.showAsSheet(fragment, false, CameraScanActivity.TYPE_QR_LOGIN, null);
                }, null);
            case GHOST_MODE:
                if (!NekoConfig.showGhostInDrawer.Bool()) {
                    return null;
                }
                return new MenuItemInfo(R.drawable.ayu_ghost, LocaleController.getString(NekoConfig.isGhostModeActive()
                        ? R.string.DisableGhostMode
                        : R.string.EnableGhostMode), () -> {
                    boolean wasActive = NekoConfig.isGhostModeActive();
                    NekoConfig.toggleGhostMode();
                    BulletinFactory.of(fragment).createSuccessBulletin(LocaleController.getString(
                            wasActive ? R.string.GhostModeDisabled : R.string.GhostModeEnabled)).show();
                }, () -> fragment.presentFragment(new GhostModeActivity()));
            case CONTACTS:
                return new MenuItemInfo(R.drawable.msg_contacts, LocaleController.getString(R.string.Contacts), () -> {
                    Bundle args = new Bundle();
                    args.putBoolean("needPhonebook", true);
                    fragment.presentFragment(new ContactsActivity(args));
                }, null);
            case PROFILE:
                return new MenuItemInfo(R.drawable.left_status_profile, LocaleController.getString(R.string.MyProfile), () -> {
                    Bundle args = new Bundle();
                    args.putLong("user_id", UserConfig.getInstance(currentAccount).getClientUserId());
                    args.putBoolean("my_profile", true);
                    fragment.presentFragment(new ProfileActivity(args));
                }, null);
            case RECENT_CHATS:
                return new MenuItemInfo(R.drawable.menu_recent, LocaleController.getString(R.string.RecentChats), () -> {
                    View anchor = fragment.getActionBar() != null ? fragment.getActionBar().getBackButton() : fragment.getFragmentView();
                    BackButtonMenuRecent.show(currentAccount, fragment, anchor);
                }, null);
            case BOOKMARKS:
                return new MenuItemInfo(R.drawable.msg_fave, LocaleController.getString(R.string.BookmarksManager), () -> fragment.presentFragment(new BookmarkManagerActivity()), null);
            case NEW_CONTACT:
                return new MenuItemInfo(R.drawable.msg_contact_add, LocaleController.getString(R.string.NewContact), () -> {
                    if (fragment.getContext() != null) {
                        new NewContactBottomSheet(fragment, fragment.getContext()).show();
                    }
                }, null);
            case BOTS:
                return null;
            default:
                return null;
        }
    }

    public static boolean hasSideMenuBots() {
        TLRPC.TL_attachMenuBots menuBots = MediaDataController.getInstance(UserConfig.selectedAccount).getAttachMenuBots();
        if (menuBots == null || menuBots.bots == null) {
            return false;
        }
        for (TLRPC.TL_attachMenuBot bot : menuBots.bots) {
            if (bot.show_in_side_menu) {
                return true;
            }
        }
        return false;
    }

    private static void presentChannelCreate(BaseFragment fragment) {
        android.content.SharedPreferences prefs = MessagesController.getGlobalMainSettings();
        if (BuildVars.DEBUG_VERSION || !prefs.getBoolean("channel_intro", false)) {
            fragment.presentFragment(new ActionIntroActivity(ActionIntroActivity.ACTION_TYPE_CHANNEL_CREATE));
            prefs.edit().putBoolean("channel_intro", true).apply();
        } else {
            Bundle args = new Bundle();
            args.putInt("step", 0);
            fragment.presentFragment(new ChannelCreateActivity(args));
        }
    }
}
