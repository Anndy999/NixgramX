package tw.nekomimi.nekogram.ui.cells;

import static org.telegram.messenger.AndroidUtilities.dp;

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
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.UserObject;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ActionBar.ThemeBind;
import org.telegram.ui.Components.AvatarDrawable;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.CombinedDrawable;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.messenger.Emoji;

import tw.nekomimi.nekogram.ui.icons.IconsResources;
import xyz.nextalone.nagram.NaConfig;

public class BookmarksChatCell extends FrameLayout {

    private final TextView titleView;
    private final TextView subtitleView;
    private final TextView countView;
    private final BackupImageView imageView;
    private final LinearLayout labels;
    private boolean needDivider;

    public BookmarksChatCell(Context context) {
        super(context);
        setMinimumHeight(dp(56));
        labels = new LinearLayout(context);
        labels.setOrientation(LinearLayout.VERTICAL);
        addView(labels, LayoutHelper.createFrameRelatively(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT,
                Gravity.START | Gravity.CENTER_VERTICAL, 68, 4, 16, 4));

        imageView = new BackupImageView(context);
        imageView.setRoundRadius(dp(20));
        addView(imageView, LayoutHelper.createFrameRelatively(40, 40, Gravity.START | Gravity.CENTER_VERTICAL, 16, 0, 0, 0));

        countView = new TextView(context);
        countView.setLines(1);
        countView.setTextSize(15);
        countView.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        ThemeBind.textColor(countView, Theme.key_windowBackgroundWhiteValueText);
        countView.setSingleLine(true);
        countView.setEllipsize(TextUtils.TruncateAt.END);
        countView.setGravity(LayoutHelper.getAbsoluteGravityEnd() | Gravity.CENTER_VERTICAL);
        addView(countView, LayoutHelper.createFrameRelatively(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.END | Gravity.CENTER_VERTICAL, 0, 0, 16, 0));

        titleView = new TextView(context);
        titleView.setLines(1);
        titleView.setTextSize(16);
        titleView.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        titleView.setMaxLines(1);
        titleView.setSingleLine(true);
        titleView.setEllipsize(TextUtils.TruncateAt.END);
        ThemeBind.textColor(titleView, Theme.key_windowBackgroundWhiteBlackText);
        titleView.setGravity(LayoutHelper.getAbsoluteGravityStart());
        labels.addView(titleView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

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

    public void setData(TLObject peer, CharSequence title, CharSequence subtitle, int bookmarkCount, boolean divider) {
        if (needDivider != divider) {
            needDivider = divider;
            setMinimumHeight(dp(56) + (divider ? 1 : 0));
        }
        boolean useSolar = NaConfig.INSTANCE.getIconReplacements().Int() == IconsResources.ICON_REPLACE_SOLAR;

        if (peer instanceof TLRPC.User user) {
            if (UserObject.isUserSelf(user)) {
                CombinedDrawable combinedDrawable = Theme.createCircleDrawableWithIcon(dp(40), useSolar ? R.drawable.chats_saved_solar : R.drawable.chats_saved);
                combinedDrawable.setIconSize(dp(20), dp(20));
                Theme.setCombinedDrawableColor(combinedDrawable, Theme.getColor(Theme.key_avatar_backgroundSaved), false);
                Theme.setCombinedDrawableColor(combinedDrawable, Theme.getColor(Theme.key_avatar_text), true);
                imageView.setImageDrawable(combinedDrawable);
            } else {
                AvatarDrawable avatarDrawable = new AvatarDrawable();
                avatarDrawable.setInfo(user);
                imageView.setForUserOrChat(user, avatarDrawable);
            }
            imageView.setRoundRadius(dp(20));
        } else if (peer instanceof TLRPC.Chat chat) {
            AvatarDrawable avatarDrawable = new AvatarDrawable();
            avatarDrawable.setInfo(chat);
            imageView.setRoundRadius(ChatObject.isForum(chat) ? dp(16) : dp(20));
            imageView.setForUserOrChat(chat, avatarDrawable);
        } else {
            CombinedDrawable combinedDrawable = Theme.createCircleDrawableWithIcon(dp(40), useSolar ? R.drawable.ghost_solar : R.drawable.ghost);
            combinedDrawable.setIconSize(dp(20), dp(20));
            Theme.setCombinedDrawableColor(combinedDrawable, Theme.getColor(Theme.keys_avatar_background[AvatarDrawable.getColorIndex(0)]), false);
            Theme.setCombinedDrawableColor(combinedDrawable, Theme.getColor(Theme.key_avatar_text), true);
            imageView.setImageDrawable(combinedDrawable);
            imageView.setRoundRadius(dp(20));
        }

        CharSequence name = !TextUtils.isEmpty(title) ? title : LocaleController.getString(R.string.HiddenName);
        titleView.setText(Emoji.replaceEmoji(name, titleView.getPaint().getFontMetricsInt(), false));
        subtitleView.setText(subtitle);
        countView.setText(String.valueOf(Math.max(0, bookmarkCount)));

        setWillNotDraw(!needDivider);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int maxCountWidth = MeasureSpec.getSize(widthMeasureSpec) / 3;
        if (countView.getMaxWidth() != maxCountWidth) {
            countView.setMaxWidth(maxCountWidth);
        }
        measureChildWithMargins(countView, widthMeasureSpec, 0, heightMeasureSpec, 0);
        int endMargin = dp(24) + countView.getMeasuredWidth();
        FrameLayout.LayoutParams labelParams = (FrameLayout.LayoutParams) labels.getLayoutParams();
        if (LocaleController.isRTL) {
            labelParams.leftMargin = endMargin;
        } else {
            labelParams.rightMargin = endMargin;
        }
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }
}
