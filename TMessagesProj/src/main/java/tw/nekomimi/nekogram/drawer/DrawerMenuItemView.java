package tw.nekomimi.nekogram.drawer;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.content.Context;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;

public class DrawerMenuItemView extends FrameLayout {

    private final ImageView iconView;
    private final TextView textView;

    public DrawerMenuItemView(Context context) {
        super(context);
        setMinimumHeight(dp(48));

        iconView = new ImageView(context);
        iconView.setScaleType(ImageView.ScaleType.CENTER);
        addView(iconView, LayoutHelper.createFrame(24, 24, Gravity.START | Gravity.CENTER_VERTICAL, 18, 0, 0, 0));

        textView = new TextView(context);
        textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        textView.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        textView.setLines(1);
        textView.setEllipsize(android.text.TextUtils.TruncateAt.END);
        addView(textView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.START | Gravity.CENTER_VERTICAL, 58, 0, 16, 0));
    }

    public void setItem(int iconRes, CharSequence text) {
        iconView.setImageResource(iconRes);
        iconView.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_windowBackgroundWhiteGrayIcon), PorterDuff.Mode.MULTIPLY));
        textView.setText(text);
        textView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
    }
}
