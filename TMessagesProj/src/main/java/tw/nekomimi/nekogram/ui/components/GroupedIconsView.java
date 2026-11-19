package tw.nekomimi.nekogram.ui.components;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.LocaleController.getString;

import android.content.Context;
import android.graphics.PorterDuff;
import android.os.Build;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;

import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.ActionBarMenuItem;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ActionBar.ThemeBind;
import org.telegram.ui.ChatActivity;
import org.telegram.ui.Components.ScaleStateListAnimator;

import java.util.ArrayList;
import java.util.List;

import xyz.nextalone.nagram.NaConfig;

public class GroupedIconsView extends LinearLayout {

    private static final Option DELETE = new Option(1, R.drawable.msg_delete, R.string.Delete);
    private static final Option FORWARD = new Option(2, R.drawable.msg_forward_noquote, R.string.Forward);
    private static final Option FORWARD_NOQUOTE = new Option(2011, R.drawable.msg_forward_noquote, R.string.NoQuoteForward);
    private static final Option COPY = new Option(3, R.drawable.msg_copy, R.string.Copy);
    private static final Option COPY_PHOTO = new Option(150, R.drawable.msg_copy_photo, R.string.CopyPhoto);
    private static final Option COPY_PHOTO_AS_STICKER = new Option(151, R.drawable.msg_copy_photo, R.string.CopyPhotoAsSticker);
    private static final Option COPY_LINK = new Option(22, R.drawable.msg_link, R.string.CopyLink);
    private static final Option COPY_LINK_PM = new Option(2025, R.drawable.msg_link, R.string.CopyLink);
    private static final Option REPLY = new Option(8, R.drawable.menu_reply, R.string.Reply);
    private static final Option REPLY_PM = new Option(2033, R.drawable.menu_reply, R.string.ReplyInPrivate);
    private static final Option EDIT = new Option(12, R.drawable.msg_edit, R.string.Edit);

    private final List<Option> secondaryOptions = new ArrayList<>();
    private final ChatActivity chatActivity;
    private final Theme.ResourcesProvider resourcesProvider;

    public GroupedIconsView(Context context, ChatActivity chatActivity, Theme.ResourcesProvider resourcesProvider,
                            boolean allowReply, boolean allowReplyPm, boolean allowEdit, boolean allowDelete,
                            boolean allowForward, boolean allowCopy, boolean allowCopyPhoto, boolean allowCopyAsSticker,
                            boolean allowCopyLink, boolean allowCopyLinkPm, boolean removeFromSaved) {
        super(context);
        this.chatActivity = chatActivity;
        this.resourcesProvider = resourcesProvider;
        setOrientation(HORIZONTAL);
        setGravity(Gravity.CENTER);

        if (allowReply) addOption(REPLY);
        if (allowReplyPm) secondaryOptions.add(REPLY_PM);

        Option link = allowCopyLink ? COPY_LINK : allowCopyLinkPm ? COPY_LINK_PM : null;
        if (allowCopy) {
            addOption(COPY);
            if (allowCopyPhoto && !allowDelete) addOption(COPY_PHOTO);
            else if (allowCopyPhoto) secondaryOptions.add(COPY_PHOTO);
            if (link != null) secondaryOptions.add(link);
        } else if (allowCopyPhoto) {
            addOption(COPY_PHOTO);
            if (link != null) secondaryOptions.add(link);
        } else if (link != null) {
            addOption(link);
        }
        if (allowCopyAsSticker) secondaryOptions.add(COPY_PHOTO_AS_STICKER);
        if (allowDelete) {
            addOption(removeFromSaved ? new Option(DELETE.id(), DELETE.icon(), R.string.Remove) : DELETE);
        }
        if (allowEdit) {
            addOption(EDIT);
            if (allowForward) secondaryOptions.add(FORWARD);
        } else if (allowForward) {
            addOption(FORWARD);
        }
        if (allowForward) secondaryOptions.add(FORWARD_NOQUOTE);
    }

    public static boolean useGroupedIcons() {
        return NaConfig.INSTANCE.getGroupedMessageMenu().Bool();
    }

    public void addSecondaryOptions(ViewGroup popup, List<Integer> existingOptions) {
        for (Option option : secondaryOptions) {
            if (existingOptions.contains(option.id())) continue;
            var item = ActionBarMenuItem.addItem(popup, option.icon(), getString(option.label()), false, resourcesProvider);
            item.setOnClickListener(v -> chatActivity.processSelectedOption(option.id()));
        }
    }

    private void addOption(Option option) {
        ImageView button = new ImageView(getContext());
        button.setScaleType(ImageView.ScaleType.CENTER);
        button.setImageResource(option.icon());
        CharSequence label = getString(option.label());
        button.setContentDescription(label);
        if (Build.VERSION.SDK_INT >= 26) button.setTooltipText(label);
        ThemeBind.selectorBackground(button,
                Theme.createSelectorDrawable(Theme.getColor(Theme.key_listSelector, resourcesProvider), Theme.RIPPLE_MASK_CIRCLE_20DP),
                null, ThemeBind.of(Theme.key_listSelector), resourcesProvider);
        ThemeBind.colorFilter(button, Theme.key_actionBarDefaultSubmenuItemIcon, resourcesProvider, PorterDuff.Mode.MULTIPLY);
        ScaleStateListAnimator.apply(button, .1f, 1.5f);
        button.setOnClickListener(v -> chatActivity.processSelectedOption(option.id()));
        addView(button, new LayoutParams(dp(48), dp(48)));
    }

    private record Option(int id, int icon, int label) {}
}
