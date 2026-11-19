/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.ui.Cells;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ActionBar.ThemeBind;
import org.telegram.ui.Components.EmojiTextView;
import org.telegram.ui.Components.LayoutHelper;

public class TextDetailSettingsCell extends FrameLayout {

    private final LinearLayout labels;
    private final TextView textView;
    private final TextView valueTextView;
    private final ImageView imageView;
    private boolean needDivider;
    private boolean multiline;

    public TextDetailSettingsCell(Context context) {
        super(context);

        labels = new LinearLayout(context);
        labels.setOrientation(LinearLayout.VERTICAL);
        addView(labels, LayoutHelper.createFrameRelatively(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT,
                Gravity.START | Gravity.TOP, 21, 10, 21, 0));

        textView = new EmojiTextView(context);
        ThemeBind.textColor(textView, Theme.key_windowBackgroundWhiteBlackText);
        textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        textView.setLines(1);
        textView.setMaxLines(1);
        textView.setSingleLine(true);
        textView.setEllipsize(TextUtils.TruncateAt.END);
        textView.setGravity((LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.CENTER_VERTICAL);
        labels.addView(textView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        valueTextView = new EmojiTextView(context);
        ThemeBind.textColor(valueTextView, Theme.key_windowBackgroundWhiteGrayText2);
        valueTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        valueTextView.setGravity(LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT);
        valueTextView.setLines(1);
        valueTextView.setMaxLines(1);
        valueTextView.setSingleLine(true);
        valueTextView.setPadding(0, 0, 0, 0);
        labels.addView(valueTextView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        imageView = new ImageView(context);
        imageView.setScaleType(ImageView.ScaleType.CENTER);
        ThemeBind.colorFilter(imageView, Theme.key_windowBackgroundWhiteGrayIcon, PorterDuff.Mode.MULTIPLY);
        imageView.setVisibility(GONE);
        addView(imageView, LayoutHelper.createFrame(52, 52, (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP, 8, 6, 8, 0));

        setMultilineDetail(true);

    }

    public TextView getTextView() {
        return textView;
    }

    public TextView getValueTextView() {
        return valueTextView;
    }

    public void setMultilineDetail(boolean value) {
        multiline = value;
        valueTextView.setSingleLine(!value);
        valueTextView.setEllipsize(value ? null : TextUtils.TruncateAt.END);
        updateLayout();
    }

    private void updateLayout() {
        boolean iconVisible = imageView.getVisibility() == VISIBLE;
        setMinimumHeight(multiline ? 0 : AndroidUtilities.dp(64) + (needDivider ? 1 : 0));
        textView.setMinHeight(valueTextView.getVisibility() == VISIBLE ? AndroidUtilities.dp(25) : 0);
        int start = iconVisible ? AndroidUtilities.dp(50) : 0;
        labels.setPadding(LocaleController.isRTL ? 0 : start, 0,
                LocaleController.isRTL ? start : 0, multiline ? AndroidUtilities.dp(12) : 0);
    }

    public void setTextAndValue(CharSequence text, CharSequence value, boolean divider) {
        textView.setText(text);
        valueTextView.setVisibility(TextUtils.isEmpty(value) ? GONE : VISIBLE);
        valueTextView.setText(value);
        needDivider = divider;
        imageView.setVisibility(GONE);
        setWillNotDraw(!divider);
        updateLayout();
    }

    public void setTextAndValueAndIcon(String text, CharSequence value, int resId, boolean divider) {
        textView.setText(text);
        valueTextView.setText(value);
        imageView.setImageResource(resId);
        imageView.setVisibility(VISIBLE);
        valueTextView.setVisibility(TextUtils.isEmpty(value) ? GONE : VISIBLE);
        needDivider = divider;
        setWillNotDraw(!divider);
        updateLayout();
    }

    public void setValue(CharSequence value) {
        valueTextView.setText(value);
    }

    @Override
    public void invalidate() {
        super.invalidate();
        textView.invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (needDivider && Theme.dividerPaint != null) {
            canvas.drawLine(LocaleController.isRTL ? 0 : AndroidUtilities.dp(imageView.getVisibility() == VISIBLE ? 71 : 20), getMeasuredHeight() - 1, getMeasuredWidth() - (LocaleController.isRTL ? AndroidUtilities.dp(imageView.getVisibility() == VISIBLE ? 71 : 20) : 0), getMeasuredHeight() - 1, Theme.dividerPaint);
        }
    }
}
