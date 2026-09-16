package tw.nekomimi.nekogram.drawer;

import android.content.Context;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ContactsController;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesStorage;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.AvatarDrawable;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.LaunchActivity;
import org.telegram.ui.LoginActivity;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;

/**
 * Account picker adapted from Exteraless without its server preview/badge APIs.
 * Account order remains Telegram's loginTime order; a long press exposes local
 * up/down controls that swap the two saved loginTime values.
 */
public class DrawerAccountPickerView extends FrameLayout {
    private final LinearLayout list;
    private final ArrayList<Integer> accounts = new ArrayList<>();
    private boolean expanded;
    private boolean reorderMode;
    private Runnable onAccountSelected;

    public DrawerAccountPickerView(Context context) {
        super(context);
        list = new LinearLayout(context);
        list.setOrientation(LinearLayout.VERTICAL);
        addView(list, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        expanded = org.telegram.messenger.MessagesController.getGlobalMainSettings().getBoolean("accountsShown", false);
        rebuild();
    }

    public boolean isExpanded() { return expanded; }
    public void setOnAccountSelected(Runnable value) { onAccountSelected = value; }
    public void toggleExpand() { setExpanded(!expanded); }
    public void setExpanded(boolean value) {
        if (expanded == value) return;
        expanded = value;
        org.telegram.messenger.MessagesController.getGlobalMainSettings().edit().putBoolean("accountsShown", value).apply();
        rebuild();
    }
    public void updateUnreadCounters() { rebuild(); }
    public void updateColors() { rebuild(); }
    public void dispose() { }

    public void rebuild() {
        list.removeAllViews();
        accounts.clear();
        for (int account = 0; account < UserConfig.MAX_ACCOUNT_COUNT; account++) {
            if (UserConfig.getInstance(account).isClientActivated()) accounts.add(account);
        }
        Collections.sort(accounts, Comparator.comparingLong(a -> UserConfig.getInstance(a).loginTime));
        if (!expanded) {
            setVisibility(GONE);
            return;
        }
        setVisibility(VISIBLE);
        for (int i = 0; i < accounts.size(); i++) list.addView(createAccountRow(accounts.get(i), i));
        Integer available = availableAccount();
        if (available != null) list.addView(createAddAccountRow(available));
    }

    private View createAccountRow(final int account, final int position) {
        FrameLayout row = new FrameLayout(getContext());
        boolean selected = account == UserConfig.selectedAccount;
        row.setBackground(selected
                ? Theme.createSimpleSelectorRoundRectDrawable(dp(12), Theme.getColor(Theme.key_windowBackgroundGray), Theme.getColor(Theme.key_listSelector))
                : Theme.createSelectorDrawable(Theme.getColor(Theme.key_listSelector), 2));
        AvatarDrawable avatar = new AvatarDrawable();
        BackupImageView avatarView = new BackupImageView(getContext());
        avatarView.setRoundRadius(dp(17));
        TLRPC.User user = UserConfig.getInstance(account).getCurrentUser();
        if (user != null) {
            avatar.setInfo(account, user);
            avatarView.getImageReceiver().setCurrentAccount(account);
            avatarView.setForUserOrChat(user, avatar);
        }
        row.addView(avatarView, LayoutHelper.createFrame(34, 34, Gravity.START | Gravity.CENTER_VERTICAL, 8, 0, 0, 0));
        TextView name = new TextView(getContext());
        name.setTextSize(15);
        name.setTypeface(AndroidUtilities.bold());
        name.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        name.setGravity(Gravity.CENTER_VERTICAL);
        name.setSingleLine(true);
        name.setEllipsize(android.text.TextUtils.TruncateAt.END);
        name.setText(user == null ? "" : ContactsController.formatName(user.first_name, user.last_name));
        row.addView(name, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.START, 54, 0, reorderMode ? 72 : 58, 0));
        int unread = MessagesStorage.getInstance(account).getMainUnreadCount();
        if (unread > 0 && UserConfig.getVisibleAccountsCount() > 1) {
            TextView badge = new TextView(getContext());
            badge.setText(Integer.toString(unread));
            badge.setTextSize(12);
            badge.setTypeface(Typeface.DEFAULT_BOLD);
            badge.setTextColor(Theme.getColor(Theme.key_chats_unreadCounterText));
            badge.setGravity(Gravity.CENTER);
            badge.setBackground(Theme.createRoundRectDrawable(dp(12), Theme.getColor(Theme.key_chats_unreadCounter)));
            row.addView(badge, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, 24, Gravity.END | Gravity.CENTER_VERTICAL, 0, 0, reorderMode ? 62 : 12, 0));
            badge.setMinWidth(dp(24));
            badge.setPadding(dp(7), 0, dp(7), 0);
        }
        if (selected) {
            ImageView check = new ImageView(getContext());
            check.setImageResource(R.drawable.msg_check);
            check.setColorFilter(Theme.getColor(Theme.key_windowBackgroundWhiteBlueIcon));
            row.addView(check, LayoutHelper.createFrame(18, 18, Gravity.END | Gravity.CENTER_VERTICAL, 0, 0, 12, 0));
        }
        if (reorderMode) addReorderControls(row, position);
        row.setOnClickListener(v -> {
            if (account == UserConfig.selectedAccount) return;
            if (onAccountSelected != null) onAccountSelected.run();
            if (getContext() instanceof LaunchActivity) ((LaunchActivity) getContext()).switchToAccount(account, true);
        });
        row.setOnLongClickListener(v -> { reorderMode = !reorderMode; rebuild(); return true; });
        row.setLayoutParams(new LinearLayout.LayoutParams(LayoutHelper.MATCH_PARENT, dp(44)));
        return row;
    }

    private void addReorderControls(FrameLayout row, final int position) {
        TextView up = reorderButton("↑");
        up.setEnabled(position > 0);
        up.setOnClickListener(v -> swap(position, position - 1));
        row.addView(up, LayoutHelper.createFrame(28, 44, Gravity.END | Gravity.CENTER_VERTICAL, 28, 0, 0, 0));
        TextView down = reorderButton("↓");
        down.setEnabled(position < accounts.size() - 1);
        down.setOnClickListener(v -> swap(position, position + 1));
        row.addView(down, LayoutHelper.createFrame(28, 44, Gravity.END | Gravity.CENTER_VERTICAL));
    }

    private TextView reorderButton(String text) {
        TextView result = new TextView(getContext());
        result.setText(text);
        result.setTextSize(18);
        result.setGravity(Gravity.CENTER);
        result.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlueIcon));
        return result;
    }

    private void swap(int firstIndex, int secondIndex) {
        if (firstIndex < 0 || secondIndex < 0 || firstIndex >= accounts.size() || secondIndex >= accounts.size()) return;
        UserConfig first = UserConfig.getInstance(accounts.get(firstIndex));
        UserConfig second = UserConfig.getInstance(accounts.get(secondIndex));
        int loginTime = first.loginTime;
        first.loginTime = second.loginTime;
        second.loginTime = loginTime;
        first.saveConfig(false);
        second.saveConfig(false);
        rebuild();
    }

    private View createAddAccountRow(final int account) {
        TextView add = new TextView(getContext());
        add.setText(LocaleController.getString(R.string.AddAccount));
        add.setTextSize(15);
        add.setTypeface(AndroidUtilities.bold());
        add.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlueText4));
        add.setGravity(Gravity.CENTER_VERTICAL);
        add.setCompoundDrawablesWithIntrinsicBounds(R.drawable.poll_add_circle, 0, 0, 0);
        add.setCompoundDrawablePadding(dp(16));
        add.setPadding(dp(14), 0, dp(12), 0);
        add.setBackground(Theme.createSelectorDrawable(Theme.getColor(Theme.key_listSelector), 2));
        add.setOnClickListener(v -> {
            if (onAccountSelected != null) onAccountSelected.run();
            if (getContext() instanceof LaunchActivity) ((LaunchActivity) getContext()).presentFragment(new LoginActivity(account));
        });
        add.setLayoutParams(new LinearLayout.LayoutParams(LayoutHelper.MATCH_PARENT, dp(44)));
        return add;
    }

    private Integer availableAccount() {
        for (int account = UserConfig.MAX_ACCOUNT_COUNT - 1; account >= 0; account--) if (!UserConfig.getInstance(account).isClientActivated()) return account;
        return null;
    }
    private static int dp(float value) { return AndroidUtilities.dp(value); }
}
