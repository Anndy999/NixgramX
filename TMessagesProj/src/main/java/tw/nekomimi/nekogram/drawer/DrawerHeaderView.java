package tw.nekomimi.nekogram.drawer;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.content.Context;
import android.view.Gravity;
import android.widget.FrameLayout;

import org.telegram.PhoneFormat.PhoneFormat;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.UserObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.SimpleTextView;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.AvatarDrawable;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.LayoutHelper;

public class DrawerHeaderView extends FrameLayout {

    private final AvatarDrawable avatarDrawable = new AvatarDrawable();
    private final BackupImageView avatarView;
    private final SimpleTextView nameView;
    private final SimpleTextView subtitleView;

    public DrawerHeaderView(Context context) {
        super(context);

        avatarView = new BackupImageView(context);
        avatarView.setRoundRadius(dp(36));
        addView(avatarView, LayoutHelper.createFrame(72, 72, Gravity.START | Gravity.TOP, 16, 24, 0, 0));

        nameView = new SimpleTextView(context);
        nameView.setTextSize(18);
        nameView.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        nameView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        addView(nameView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.START | Gravity.TOP, 16, 108, 16, 0));

        subtitleView = new SimpleTextView(context);
        subtitleView.setTextSize(13);
        subtitleView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText2));
        addView(subtitleView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.START | Gravity.TOP, 16, 132, 16, 0));
    }

    public void updateUserInfo() {
        TLRPC.User user = UserConfig.getInstance(UserConfig.selectedAccount).getCurrentUser();
        if (user == null) {
            return;
        }
        avatarDrawable.setInfo(user);
        avatarView.setForUserOrChat(user, avatarDrawable);
        nameView.setText(UserObject.getUserName(user));
        if (user.phone != null && !user.phone.isEmpty()) {
            subtitleView.setText(PhoneFormat.getInstance().format("+" + user.phone));
        } else {
            subtitleView.setText("");
        }
    }
}
