package tw.nekomimi.nekogram.ui.cells;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.LocaleController.getString;

import android.content.Context;
import android.graphics.Canvas;
import android.text.TextUtils;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ChatObject;
import org.telegram.messenger.ContactsController;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ActionBar.ThemeBind;
import org.telegram.ui.Components.AvatarDrawable;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.LayoutHelper;

public class FiltersChatCell extends FrameLayout {

    private final TextView textView;
    private final TextView subtitleView;
    private final BackupImageView imageView;
    private final int currentAccount;
    private long dialogId;
    private final LinearLayout labels;
    private boolean needDivider;

    public FiltersChatCell(Context context, int currentAccount) {
        super(context);
        this.currentAccount = currentAccount;
        setMinimumHeight(dp(56));
        labels = new LinearLayout(context);
        labels.setOrientation(LinearLayout.VERTICAL);
        addView(labels, LayoutHelper.createFrameRelatively(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT,
                Gravity.START | Gravity.CENTER_VERTICAL, 68, 4, 16, 4));

        imageView = new BackupImageView(context);
        imageView.setRoundRadius(dp(20));
        imageView.getImageReceiver().setCurrentAccount(currentAccount);
        addView(imageView, LayoutHelper.createFrameRelatively(40, 40, Gravity.START | Gravity.CENTER_VERTICAL, 16, 0, 0, 0));

        textView = new TextView(context);
        textView.setLines(1);
        textView.setTextSize(16);
        textView.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        textView.setMaxLines(1);
        textView.setSingleLine(true);
        textView.setEllipsize(TextUtils.TruncateAt.END);
        ThemeBind.textColor(textView, Theme.key_windowBackgroundWhiteBlackText);
        textView.setGravity(LayoutHelper.getAbsoluteGravityStart());
        labels.addView(textView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        subtitleView = new TextView(context);
        subtitleView.setLines(1);
        subtitleView.setTextSize(13);
        ThemeBind.textColor(subtitleView, Theme.key_windowBackgroundWhiteGrayText);
        subtitleView.setMaxLines(1);
        subtitleView.setSingleLine(true);
        subtitleView.setEllipsize(TextUtils.TruncateAt.END);
        subtitleView.setGravity(LayoutHelper.getAbsoluteGravityStart());
        labels.addView(subtitleView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        setWillNotDraw(false);
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        super.onDraw(canvas);
        if (needDivider) {
            canvas.drawLine(LocaleController.isRTL ? 0 : dp(68), getHeight() - 1,
                    LocaleController.isRTL ? getWidth() - dp(68) : getWidth(), getHeight() - 1, Theme.dividerPaint);
        }
    }

    public long getDialogId() {
        return dialogId;
    }

    public void setDialog(long dialogId, int filterCount) {
        this.dialogId = dialogId;
        if (!needDivider) {
            needDivider = true;
            setMinimumHeight(dp(56) + 1);
        }
        String title = "";
        String subtitle = filterCount + " " + getString(R.string.RegexFiltersHeader);
        if (filterCount == 1) {
            subtitle = subtitle.replace("s", "");
        }
        if (dialogId > 0) {
            TLRPC.User user = MessagesController.getInstance(currentAccount).getUser(dialogId);
            if (user != null) {
                title = ContactsController.formatName(user.first_name, user.last_name);
                AvatarDrawable avatar = new AvatarDrawable();
                avatar.setInfo(user);
                imageView.setRoundRadius(dp(20));
                imageView.setForUserOrChat(user, avatar);
            } else {
                imageView.setImageDrawable(null);
            }
        } else {
            TLRPC.Chat chat = MessagesController.getInstance(currentAccount).getChat(-dialogId);
            if (chat != null) {
                title = chat.title;
                AvatarDrawable avatar = new AvatarDrawable();
                avatar.setInfo(chat);
                imageView.setRoundRadius(ChatObject.isForum(chat) ? dp(16) : dp(21));
                imageView.setForUserOrChat(chat, avatar);
            } else {
                imageView.setImageDrawable(null);
            }
        }

        textView.setText(title);
        subtitleView.setVisibility(VISIBLE);
        subtitleView.setText(subtitle);
        setWillNotDraw(!needDivider);
    }

}
