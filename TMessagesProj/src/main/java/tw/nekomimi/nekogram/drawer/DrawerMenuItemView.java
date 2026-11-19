package tw.nekomimi.nekogram.drawer;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.content.Context;
import android.graphics.PorterDuff;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ActionBar.ThemeBind;
import org.telegram.ui.Components.LayoutHelper;

public class DrawerMenuItemView extends FrameLayout {

    private final ImageView iconView;
    private final TextView textView;
    private int iconResId;

    public DrawerMenuItemView(Context context) {
        super(context);
        setMinimumHeight(dp(48));
        ThemeBind.selectorBackground(this, Theme.createSelectorDrawable(Theme.getColor(Theme.key_listSelector), 2),
                null, ThemeBind.of(Theme.key_listSelector));

        iconView = new ImageView(context);
        iconView.setScaleType(ImageView.ScaleType.CENTER);
        ThemeBind.colorFilter(iconView, Theme.key_windowBackgroundWhiteGrayIcon, PorterDuff.Mode.MULTIPLY);
        addView(iconView, LayoutHelper.createFrameRelatively(24, 24, Gravity.START | Gravity.CENTER_VERTICAL, 18, 0, 0, 0));

        textView = new TextView(context);
        textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        textView.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        textView.setLines(1);
        textView.setEllipsize(android.text.TextUtils.TruncateAt.END);
        ThemeBind.textColor(textView, Theme.key_windowBackgroundWhiteBlackText);
        textView.setGravity(LayoutHelper.getAbsoluteGravityStart());
        addView(textView, LayoutHelper.createFrameRelatively(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.START | Gravity.CENTER_VERTICAL, 58, 4, 16, 4));
    }

    public void setItem(int iconRes, CharSequence text) {
        if (iconResId != iconRes) {
            iconResId = iconRes;
            iconView.setImageResource(iconRes);
        }
        if (!TextUtils.equals(textView.getText(), text)) {
            textView.setText(text);
        }
    }
}
