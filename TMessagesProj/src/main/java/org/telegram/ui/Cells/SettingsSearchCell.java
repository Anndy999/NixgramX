package org.telegram.ui.Cells;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.text.SpannableStringBuilder;
import android.text.TextUtils;
import android.text.style.ImageSpan;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ActionBar.ThemeBind;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalRecyclerView;
import org.telegram.ui.ProfileActivity;

public class SettingsSearchCell extends FrameLayout {

    private final TextView textView;
    private final TextView valueTextView;
    private final LinearLayout labels;
    private final ImageView imageView;
    private final Theme.ResourcesProvider resourcesProvider;
    private Drawable titleArrow;
    private Drawable valueArrow;
    private boolean needDivider;
    private int left;

    public static class VerticalImageSpan extends ImageSpan {

        private final Paint.FontMetricsInt metrics = new Paint.FontMetricsInt();

        public VerticalImageSpan(Drawable drawable) {
            super(drawable);
        }

        @Override
        public int getSize(Paint paint, CharSequence text, int start, int end, Paint.FontMetricsInt fontMetricsInt) {
            Drawable drawable = getDrawable();
            Rect rect = drawable.getBounds();
            if (fontMetricsInt != null) {
                paint.getFontMetricsInt(metrics);
                int fontHeight = metrics.descent - metrics.ascent;
                int drHeight = rect.bottom - rect.top;
                int centerY = metrics.ascent + fontHeight / 2;

                fontMetricsInt.ascent = centerY - drHeight / 2;
                fontMetricsInt.top = fontMetricsInt.ascent;
                fontMetricsInt.bottom = centerY + drHeight / 2;
                fontMetricsInt.descent = fontMetricsInt.bottom;
            }
            return rect.right;
        }

        @Override
        public void draw(Canvas canvas, CharSequence text, int start, int end, float x, int top, int y, int bottom, Paint paint) {
            Drawable drawable = getDrawable();
            canvas.save();
            paint.getFontMetricsInt(metrics);
            int fontHeight = metrics.descent - metrics.ascent;
            int centerY = y + metrics.descent - fontHeight / 2;
            int transY = centerY - (drawable.getBounds().bottom - drawable.getBounds().top) / 2;
            canvas.translate(x, transY);
            if (LocaleController.isRTL) {
                canvas.scale(-1, 1, drawable.getIntrinsicWidth() / 2, drawable.getIntrinsicHeight() / 2);
            }
            drawable.draw(canvas);
            canvas.restore();
        }
    }

    public SettingsSearchCell(Context context) {
        this(context, null);
    }

    public SettingsSearchCell(Context context, Theme.ResourcesProvider resourcesProvider) {
        super(context);
        this.resourcesProvider = resourcesProvider;
        setMinimumHeight(AndroidUtilities.dp(64));

        labels = new LinearLayout(context);
        labels.setOrientation(LinearLayout.VERTICAL);
        labels.setGravity(Gravity.CENTER_VERTICAL);
        addView(labels, LayoutHelper.createFrameRelatively(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT,
                Gravity.START | Gravity.CENTER_VERTICAL, 71, 6, 16, 6));
        textView = createLabel(context, 16, Theme.key_windowBackgroundWhiteBlackText);
        valueTextView = createLabel(context, 13, Theme.key_windowBackgroundWhiteGrayText2);
        labels.addView(textView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        labels.addView(valueTextView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        imageView = new ImageView(context);
        imageView.setScaleType(ImageView.ScaleType.CENTER);
        ThemeBind.colorFilter(imageView, Theme.key_windowBackgroundWhiteGrayIcon, resourcesProvider, PorterDuff.Mode.MULTIPLY);
        addView(imageView, LayoutHelper.createFrameRelatively(48, 48, Gravity.START | Gravity.CENTER_VERTICAL, 10, 0, 10, 0));
    }

    private TextView createLabel(Context context, int size, int colorKey) {
        TextView label = new TextView(context);
        ThemeBind.textColor(label, colorKey, resourcesProvider);
        label.setTextSize(TypedValue.COMPLEX_UNIT_DIP, size);
        label.setGravity(LayoutHelper.getAbsoluteGravityStart());
        label.setSingleLine(true);
        label.setEllipsize(TextUtils.TruncateAt.END);
        return label;
    }

    private CharSequence formatPath(String[] path, boolean title) {
        if (path == null || path.length == 0) return "";
        if (path.length == 1) return path[0];
        Drawable arrow = title ? titleArrow : valueArrow;
        if (arrow == null) {
            arrow = getContext().getDrawable(R.drawable.settings_arrow).mutate();
            arrow.setBounds(0, 0, arrow.getIntrinsicWidth(), arrow.getIntrinsicHeight());
            ThemeBind.drawableColorFilter(this, arrow, title ? Theme.key_windowBackgroundWhiteBlackText
                    : Theme.key_windowBackgroundWhiteGrayText2, resourcesProvider, PorterDuff.Mode.MULTIPLY);
            if (title) titleArrow = arrow;
            else valueArrow = arrow;
        }
        SpannableStringBuilder builder = new SpannableStringBuilder();
        for (int i = 0; i < path.length; i++) {
            if (i > 0) {
                builder.append(" > ");
                builder.setSpan(new VerticalImageSpan(arrow), builder.length() - 2, builder.length() - 1,
                        SpannableStringBuilder.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            builder.append(path[i]);
        }
        return builder;
    }

    public void setTextAndValueAndIcon(CharSequence text, String[] value, int icon, boolean divider) {
        bind(text, value == null ? null : formatPath(value, false), icon, true, divider);
    }

    public void setTextAndValue(CharSequence text, String[] value, boolean faq, boolean divider) {
        if (faq && value != null && value.length > 0) {
            bind(formatPath(value, true), text, 0, false, divider);
        } else {
            bind(text, value == null ? null : formatPath(value, false), 0, false, divider);
        }
    }

    private void bind(CharSequence title, CharSequence value, int icon, boolean reserveIconSpace, boolean divider) {
        textView.setText(title);
        valueTextView.setText(value);
        valueTextView.setVisibility(TextUtils.isEmpty(value) ? GONE : VISIBLE);
        if (icon != 0) imageView.setImageResource(icon);
        imageView.setVisibility(icon == 0 ? GONE : VISIBLE);
        int inset = AndroidUtilities.dp(reserveIconSpace ? 71 : 16);
        int end = AndroidUtilities.dp(16);
        LayoutParams params = (LayoutParams) labels.getLayoutParams();
        int l = LocaleController.isRTL ? end : inset;
        int r = LocaleController.isRTL ? inset : end;
        if (params.leftMargin != l || params.rightMargin != r) {
            params.leftMargin = l;
            params.rightMargin = r;
            labels.setLayoutParams(params);
        }
        left = reserveIconSpace ? 69 : 16;
        needDivider = divider;
        int minHeight = AndroidUtilities.dp(64) + (divider ? 1 : 0);
        if (getMinimumHeight() != minHeight) setMinimumHeight(minHeight);
        setWillNotDraw(!divider);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (needDivider) {
            canvas.drawLine(LocaleController.isRTL ? 0 : AndroidUtilities.dp(left), getMeasuredHeight() - 1, getMeasuredWidth() - (LocaleController.isRTL ? AndroidUtilities.dp(left) : 0), getMeasuredHeight() - 1, Theme.dividerPaint);
        }
    }

    public static class Factory extends UItem.UItemFactory<SettingsSearchCell> {
        static { setup(new Factory()); }

        @Override
        public SettingsSearchCell createView(Context context, RecyclerListView listView, int currentAccount, int classGuid, Theme.ResourcesProvider resourcesProvider) {
            return new SettingsSearchCell(context, resourcesProvider);
        }

        @Override
        public void bindView(View view, UItem item, boolean divider, UniversalAdapter adapter, UniversalRecyclerView listView) {
            if (item.object instanceof ProfileActivity.SearchAdapter.SearchResult) {
                final ProfileActivity.SearchAdapter.SearchResult r = (ProfileActivity.SearchAdapter.SearchResult) item.object;
                ((SettingsSearchCell) view).setTextAndValueAndIcon(item.text, r.path, r.iconResId, divider);
            } else if (item.object instanceof MessagesController.FaqSearchResult) {
                final MessagesController.FaqSearchResult r = (MessagesController.FaqSearchResult) item.object;
                ((SettingsSearchCell) view).setTextAndValue(item.text, r.path, true, divider);
            }
        }

        public static UItem of(CharSequence text, Object obj) {
            UItem item = UItem.ofFactory(Factory.class);
            item.text = text;
            item.object = obj;
            return item;
        }

        public static UItem of(CharSequence text, ProfileActivity.SearchAdapter.SearchResult r) {
            UItem item = UItem.ofFactory(Factory.class);
            item.text = text;
            item.object = r;
            return item;
        }

        public static UItem of(CharSequence text, MessagesController.FaqSearchResult faq) {
            UItem item = UItem.ofFactory(Factory.class);
            item.text = text;
            item.object = faq;
            return item;
        }
    }

}
