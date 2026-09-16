package tw.nekomimi.nekogram.drawer;

import android.content.Context;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.Drawable;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;

import org.telegram.PhoneFormat.PhoneFormat;
import org.telegram.messenger.ContactsController;
import org.telegram.messenger.DialogObject;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.SimpleTextView;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.AnimatedEmojiDrawable;
import org.telegram.ui.Components.AnimatedTextView;
import org.telegram.ui.Components.AvatarDrawable;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.Premium.PremiumGradient;

import tw.nekomimi.nekogram.NekoConfig;

/** NixgramX adaptation of the Exteraless drawer header, without server-only badges. */
public class DrawerHeaderView extends FrameLayout {
    private final AvatarDrawable avatarDrawable = new AvatarDrawable();
    private final BackupImageView avatarView;
    private final SimpleTextView nameView;
    private final SimpleTextView subtitleView;
    private final ImageView chevronView;
    private final ImageView themeIcon;
    private final FrameLayout themeButton;
    private final FrameLayout proxyButton;
    private final ImageView proxyIcon;
    private final AnimatedTextView proxyText;
    private final AnimatedEmojiDrawable.SwapAnimatedEmojiDrawable statusDrawable;
    private boolean expanded;
    private Runnable onProfile, onAccounts, onTheme, onThemeLongPress, onProxy;

    public DrawerHeaderView(Context context) {
        super(context);
        avatarView = new BackupImageView(context);
        avatarView.setRoundRadius(dp(36));
        avatarView.setOnClickListener(v -> run(onProfile));
        addView(avatarView, LayoutHelper.createFrame(72, 72, Gravity.START | Gravity.TOP, 16, 16, 0, 0));

        themeButton = roundButton(context);
        themeIcon = new ImageView(context);
        themeIcon.setImageResource(R.drawable.msg_theme);
        themeIcon.setColorFilter(iconFilter());
        themeButton.addView(themeIcon, LayoutHelper.createFrame(24, 24, Gravity.CENTER));
        themeButton.setOnClickListener(v -> {
            themeIcon.animate().rotationBy(180).setDuration(220).setInterpolator(CubicBezierInterpolator.DEFAULT).start();
            run(onTheme);
        });
        themeButton.setOnLongClickListener(v -> { run(onThemeLongPress); return onThemeLongPress != null; });
        addView(themeButton, LayoutHelper.createFrame(36, 36, Gravity.END | Gravity.TOP, 0, 16, 16, 0));

        proxyButton = roundButton(context);
        LinearLayout proxyContent = new LinearLayout(context);
        proxyContent.setGravity(Gravity.CENTER);
        proxyContent.setPadding(dp(6), 0, dp(6), 0);
        proxyIcon = new ImageView(context);
        proxyContent.addView(proxyIcon, LayoutHelper.createLinear(24, 24));
        proxyText = new AnimatedTextView(context, true, true, true);
        proxyText.setTextSize(dp(13));
        proxyText.setTypeface(org.telegram.messenger.AndroidUtilities.bold());
        proxyText.setVisibility(GONE);
        proxyContent.addView(proxyText, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_VERTICAL, 2, 0, 2, 0));
        proxyButton.addView(proxyContent, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.MATCH_PARENT));
        proxyButton.setOnClickListener(v -> run(onProxy));
        addView(proxyButton, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, 36, Gravity.END | Gravity.TOP, 0, 16, 60, 0));

        FrameLayout accountBlock = new FrameLayout(context);
        accountBlock.setOnClickListener(v -> run(onAccounts));
        addView(accountBlock, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 50, Gravity.START | Gravity.TOP, 0, 100, 0, 0));
        nameView = new SimpleTextView(context);
        nameView.setTextSize(15);
        nameView.setTypeface(org.telegram.messenger.AndroidUtilities.bold());
        nameView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        nameView.setEllipsizeByGradient(true);
        nameView.setCanHideRightDrawable(false);
        nameView.setRightDrawableOutside(true);
        accountBlock.addView(nameView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 24, Gravity.START | Gravity.TOP, 16, 0, 64, 0));
        statusDrawable = new AnimatedEmojiDrawable.SwapAnimatedEmojiDrawable(nameView, dp(22));
        subtitleView = new SimpleTextView(context);
        subtitleView.setTextSize(12);
        subtitleView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText2));
        accountBlock.addView(subtitleView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 20, Gravity.START | Gravity.TOP, 16, 26, 64, 0));
        chevronView = new ImageView(context);
        chevronView.setImageResource(R.drawable.msg_expand);
        chevronView.setColorFilter(iconFilter());
        accountBlock.addView(chevronView, LayoutHelper.createFrame(24, 24, Gravity.END | Gravity.CENTER_VERTICAL, 0, 0, 22, 0));
        updateColors();
    }

    public void setOnProfile(Runnable value) { onProfile = value; }
    public void setOnAccounts(Runnable value) { onAccounts = value; }
    public void setOnTheme(Runnable value) { onTheme = value; }
    public void setOnThemeLongPress(Runnable value) { onThemeLongPress = value; }
    public void setOnProxy(Runnable value) { onProxy = value; }

    public void setChevronExpanded(boolean value) {
        if (expanded == value) return;
        expanded = value;
        chevronView.animate().rotation(value ? 180 : 0).setDuration(250).setInterpolator(CubicBezierInterpolator.DEFAULT).start();
    }

    public void updateColors() {
        nameView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        subtitleView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText2));
        chevronView.setColorFilter(iconFilter());
        themeIcon.setColorFilter(iconFilter());
        updateUserInfo();
    }

    public void updateUserInfo() {
        int account = UserConfig.selectedAccount;
        TLRPC.User user = UserConfig.getInstance(account).getCurrentUser();
        if (user == null) return;
        avatarDrawable.setInfo(account, user);
        avatarView.getImageReceiver().setCurrentAccount(account);
        avatarView.setForUserOrChat(user, avatarDrawable);
        nameView.setText(ContactsController.formatName(user.first_name, user.last_name));
        statusDrawable.setCurrentAccount(account);
        long emoji = DialogObject.getEmojiStatusDocumentId(user.emoji_status);
        boolean premium = MessagesController.getInstance(account).isPremiumUser(user);
        if (emoji != 0) statusDrawable.set(emoji, true);
        else if (premium) statusDrawable.set(PremiumGradient.getInstance().premiumStarDrawableMini, true);
        else statusDrawable.set((Drawable) null, true);
        statusDrawable.setParticles(DialogObject.isEmojiStatusCollectible(user.emoji_status), true);
        statusDrawable.setColor(Theme.getColor(Theme.key_profile_verifiedBackground));
        nameView.setRightDrawable(emoji != 0 || premium ? statusDrawable : null);
        String username = DialogObject.getPublicUsername(user);
        if (username != null && !username.isEmpty()) subtitleView.setText("@" + username);
        else if (user.phone == null || user.phone.isEmpty()) subtitleView.setText(LocaleController.getString(R.string.NumberUnknown));
        else if (NekoConfig.hidePhone.Bool()) subtitleView.setText(LocaleController.getString(R.string.MobileHidden));
        else subtitleView.setText(PhoneFormat.getInstance().format("+" + user.phone));
        updateProxyStatus();
    }

    public void updateProxyStatus() {
        boolean enabled = SharedConfig.isProxyEnabled();
        int state = ConnectionsManager.getInstance(UserConfig.selectedAccount).getConnectionState();
        boolean connected = state == ConnectionsManager.ConnectionStateConnected || state == ConnectionsManager.ConnectionStateUpdating;
        if (SharedConfig.proxyList.isEmpty()) { proxyButton.setVisibility(GONE); return; }
        proxyButton.setVisibility(VISIBLE);
        long ping = SharedConfig.currentProxy == null ? 0 : Utilities.clamp(SharedConfig.currentProxy.ping, 9999L, 0L);
        proxyText.setVisibility(enabled && connected && ping > 0 ? VISIBLE : GONE);
        if (proxyText.getVisibility() == VISIBLE) proxyText.setText(ping + " ms", true);
        int color = Theme.getColor(enabled && connected ? Theme.key_windowBackgroundWhiteGreenText : Theme.key_windowBackgroundWhiteGrayIcon);
        proxyButton.setBackground(Theme.createRoundRectDrawable(dp(18), Theme.multAlpha(color, .075f)));
        proxyIcon.setColorFilter(new PorterDuffColorFilter(color, PorterDuff.Mode.SRC_IN));
        proxyText.setTextColor(color);
        proxyIcon.setImageResource(enabled && connected ? R.drawable.proxy_on_solar : R.drawable.proxy_off_solar);
    }

    @Override protected void onAttachedToWindow() { super.onAttachedToWindow(); statusDrawable.attach(); }
    @Override protected void onDetachedFromWindow() { super.onDetachedFromWindow(); statusDrawable.detach(); }
    private FrameLayout roundButton(Context context) { FrameLayout v = new FrameLayout(context); v.setBackground(Theme.createRoundRectDrawable(dp(18), Theme.multAlpha(Theme.getColor(Theme.key_windowBackgroundWhiteGrayIcon), .075f))); return v; }
    private PorterDuffColorFilter iconFilter() { return new PorterDuffColorFilter(Theme.getColor(Theme.key_windowBackgroundWhiteGrayIcon), PorterDuff.Mode.SRC_IN); }
    private static void run(Runnable value) { if (value != null) value.run(); }
    private static int dp(float value) { return org.telegram.messenger.AndroidUtilities.dp(value); }
}
