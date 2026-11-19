/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.ui.Cells;

import android.animation.Animator;
import android.animation.ObjectAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.BuildVars;
import org.telegram.messenger.LocaleController;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ActionBar.ThemeBind;
import org.telegram.ui.Components.AnimatedTextView;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RLottieImageView;

import java.util.ArrayList;

public class TextSettingsCell extends FrameLayout {

    private Theme.ResourcesProvider resourcesProvider;
    private TextView textView;
    private AnimatedTextView valueTextView;
    private ImageView imageView;
    private boolean imageViewIsColorful;
    private BackupImageView valueBackupImageView;
    private ImageView valueImageView;
    public boolean needDivider;
    private boolean canDisable;
    private boolean drawLoading;
    private int padding;
    private boolean isRTL = LocaleController.isRTL;
    private boolean multilineTitle;

    private boolean incrementLoadingProgress;
    private float loadingProgress;
    private float drawLoadingProgress;
    private int loadingSize;
    private boolean measureDelay;
    private int changeProgressStartDelay;

    Paint paint;

    public TextSettingsCell(Context context) {
        this(context, 21);
    }

    public TextSettingsCell(Context context, Theme.ResourcesProvider resourcesProvider) {
        this(context, 21, resourcesProvider);
    }

    public TextSettingsCell(Context context, int padding) {
        this(context, padding, null);
    }

    public TextSettingsCell(Context context, int padding, Theme.ResourcesProvider resourcesProvider) {
        super(context);
        this.resourcesProvider = resourcesProvider;
        this.padding = padding;

        textView = new TextView(context);
        textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        textView.setLines(1);
        textView.setMaxLines(1);
        textView.setSingleLine(true);
        textView.setEllipsize(TextUtils.TruncateAt.END);
        textView.setGravity((LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.CENTER_VERTICAL);
        ThemeBind.textColor(textView, Theme.key_windowBackgroundWhiteBlackText, resourcesProvider);
        addView(textView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP, padding, 0, padding, 0));

        valueTextView = new AnimatedTextView(context, true, true, !LocaleController.isRTL);
        valueTextView.setAnimationProperties(.55f, 0, 320, CubicBezierInterpolator.EASE_OUT_QUINT);
        valueTextView.setTextSize(AndroidUtilities.dp(16));
        valueTextView.setGravity((LocaleController.isRTL ? Gravity.LEFT : Gravity.RIGHT) | Gravity.CENTER_VERTICAL);
        ThemeBind.textColor(valueTextView, Theme.key_windowBackgroundWhiteValueText, resourcesProvider);
        addView(valueTextView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.MATCH_PARENT, (LocaleController.isRTL ? Gravity.LEFT : Gravity.RIGHT) | Gravity.CENTER_VERTICAL, padding - 4, 0, padding - 4, 0));

        imageView = new RLottieImageView(context);
        imageView.setScaleType(ImageView.ScaleType.CENTER);
        ThemeBind.colorFilter(imageView, Theme.key_windowBackgroundWhiteGrayIcon, resourcesProvider, PorterDuff.Mode.MULTIPLY);
        imageView.setVisibility(GONE);
        addView(imageView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.CENTER_VERTICAL, 16, 0, 16, 0));

        valueImageView = new ImageView(context);
        valueImageView.setScaleType(ImageView.ScaleType.CENTER);
        valueImageView.setVisibility(INVISIBLE);
        ThemeBind.colorFilter(valueImageView, Theme.key_windowBackgroundWhiteGrayIcon, resourcesProvider, PorterDuff.Mode.MULTIPLY);
        addView(valueImageView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, (LocaleController.isRTL ? Gravity.LEFT : Gravity.RIGHT) | Gravity.CENTER_VERTICAL, padding, 0, padding, 0));
    }

    public ImageView getValueImageView() {
        return valueImageView;
    }

    private boolean betterLayout = BuildVars.DEBUG_PRIVATE_VERSION;
    public void setBetterLayout(boolean betterLayout) {
        if (this.betterLayout != betterLayout) {
            this.betterLayout = betterLayout;
            requestLayout();
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int rowWidth = MeasureSpec.getSize(widthMeasureSpec);
        int rowHeight = AndroidUtilities.dp(50) + (needDivider ? 1 : 0);
        int heightSpec = MeasureSpec.makeMeasureSpec(rowHeight, MeasureSpec.EXACTLY);
        int innerWidth = Math.max(0, rowWidth - getPaddingLeft() - getPaddingRight());
        LayoutParams titleParams = (LayoutParams) textView.getLayoutParams();
        int start = isRTL ? titleParams.rightMargin : titleParams.leftMargin;
        int titleEnd = isRTL ? titleParams.leftMargin : titleParams.rightMargin;
        int gap = AndroidUtilities.dp(8);

        if (imageView.getVisibility() == VISIBLE) {
            if (imageViewIsColorful) {
                int size = MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(28), MeasureSpec.EXACTLY);
                imageView.measure(size, size);
            } else {
                imageView.measure(MeasureSpec.makeMeasureSpec(innerWidth, MeasureSpec.AT_MOST),
                        MeasureSpec.makeMeasureSpec(rowHeight, MeasureSpec.AT_MOST));
            }
            LayoutParams iconParams = (LayoutParams) imageView.getLayoutParams();
            start = Math.max(start, (isRTL ? iconParams.rightMargin : iconParams.leftMargin) + imageView.getMeasuredWidth() + gap);
            if (isRTL) {
                titleParams.rightMargin = start;
            } else {
                titleParams.leftMargin = start;
            }
        }

        int end = AndroidUtilities.dp(padding - 4);
        if (valueBackupImageView != null && valueBackupImageView.getVisibility() == VISIBLE) {
            LayoutParams params = (LayoutParams) valueBackupImageView.getLayoutParams();
            valueBackupImageView.measure(MeasureSpec.makeMeasureSpec(params.width, MeasureSpec.EXACTLY),
                    MeasureSpec.makeMeasureSpec(params.height, MeasureSpec.EXACTLY));
            end += valueBackupImageView.getMeasuredWidth() + gap;
        }
        if (valueImageView.getVisibility() == VISIBLE) {
            end = Math.max(end, AndroidUtilities.dp(padding));
            setEndMargin(valueImageView, end);
            valueImageView.measure(MeasureSpec.makeMeasureSpec(Math.max(0, innerWidth - start - end), MeasureSpec.AT_MOST), heightSpec);
            end += valueImageView.getMeasuredWidth() + gap;
        }
        if (valueTextView.getVisibility() == VISIBLE) {
            setEndMargin(valueTextView, end);
            int remaining = Math.max(0, innerWidth - start - end - gap);
            int valueLimit = betterLayout ? remaining * 2 / 3 : remaining / 2;
            valueTextView.measure(MeasureSpec.makeMeasureSpec(valueLimit, MeasureSpec.AT_MOST), heightSpec);
            end += valueTextView.getMeasuredWidth() + gap;
        }
        int titleWidth = Math.max(0, innerWidth - start - Math.max(titleEnd, end));
        int titleSpec = MeasureSpec.makeMeasureSpec(titleWidth, MeasureSpec.EXACTLY);
        if (multilineTitle) {
            textView.measure(titleSpec, MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
            rowHeight = Math.max(rowHeight, textView.getMeasuredHeight() + AndroidUtilities.dp(12) + (needDivider ? 1 : 0));
        }
        rowHeight = resolveSize(rowHeight, heightMeasureSpec);
        textView.measure(titleSpec, MeasureSpec.makeMeasureSpec(rowHeight, MeasureSpec.EXACTLY));
        setMeasuredDimension(rowWidth, rowHeight);
    }

    private void setEndMargin(View view, int margin) {
        LayoutParams params = (LayoutParams) view.getLayoutParams();
        if (isRTL) {
            params.leftMargin = margin;
        } else {
            params.rightMargin = margin;
        }
    }

    private void setMultilineTitle(boolean multiline) {
        if (multilineTitle == multiline) return;
        multilineTitle = multiline;
        textView.setSingleLine(!multiline);
        textView.setMaxLines(multiline ? Integer.MAX_VALUE : 1);
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        if (measureDelay && getParent() != null) {
            changeProgressStartDelay = (int) ((getTop() / (float) ((View) getParent()).getMeasuredHeight()) * 150f);
        }
    }

    public TextView getTextView() {
        return textView;
    }

    public void setCanDisable(boolean value) {
        canDisable = value;
    }

    public AnimatedTextView getValueTextView() {
        return valueTextView;
    }

    public void setTextColor(int color) {
        ThemeBind.clear(textView); // custom color from the caller: no longer follows the theme key
        textView.setTextColor(color);
    }

    public void setTextValueColor(int color) {
        ThemeBind.clear(valueTextView); // custom color from the caller: no longer follows the theme key
        valueTextView.setTextColor(color);
    }

    public void setText(CharSequence text, boolean divider) {
        setMultilineTitle(false);
        textView.setText(text);
        valueTextView.setVisibility(INVISIBLE);
        valueImageView.setVisibility(INVISIBLE);
        needDivider = divider;
        setWillNotDraw(!divider);
    }

    public void setTextAndValue(CharSequence text, CharSequence value, boolean divider) {
        setTextAndValue(text, value, false, divider);
    }

    public void setTextAndValue(CharSequence text, CharSequence value, boolean animated, boolean divider) {
        setTextAndValue(text, value, animated, divider, false);
    }

    public void setTextAndValue(CharSequence text, CharSequence value, boolean animated, boolean divider, boolean isNekoCell) {
        setMultilineTitle(isNekoCell);
        textView.setText(text);
        valueImageView.setVisibility(INVISIBLE);
        if (value != null) {
            valueTextView.setText(value, animated);
            valueTextView.setVisibility(VISIBLE);
        } else {
            valueTextView.setVisibility(INVISIBLE);
        }
        needDivider = divider;
        setWillNotDraw(!divider);
        requestLayout();
    }

    public void setTextAndIcon(CharSequence text, int resId, boolean divider) {
        setMultilineTitle(false);
        textView.setText(text);
        valueTextView.setVisibility(INVISIBLE);
        if (resId != 0) {
            valueImageView.setVisibility(VISIBLE);
            valueImageView.setImageResource(resId);
        } else {
            valueImageView.setVisibility(INVISIBLE);
        }
        needDivider = divider;
        setWillNotDraw(!divider);
    }

    public void setValue(CharSequence value, boolean animated) {
        valueTextView.setText(value, animated);
    }

    public void setIcon(int resId) {
        MarginLayoutParams params = (MarginLayoutParams) textView.getLayoutParams();
        imageViewIsColorful = false;
        if (resId == 0) {
            imageView.setVisibility(GONE);
            if (LocaleController.isRTL) {
                params.rightMargin = AndroidUtilities.dp(this.padding);
            } else {
                params.leftMargin = AndroidUtilities.dp(this.padding);
            }
        } else {
            imageView.setImageResource(resId);
            ThemeBind.colorFilter(imageView, Theme.key_windowBackgroundWhiteGrayIcon, resourcesProvider, PorterDuff.Mode.MULTIPLY);
            imageView.setBackground(null);
            imageView.setVisibility(VISIBLE);
            if (LocaleController.isRTL) {
                params.rightMargin = AndroidUtilities.dp(58);
            } else {
                params.leftMargin = AndroidUtilities.dp(58);
            }
        }
    }

    public void setColorfulIcon(int resId, int color) {
        MarginLayoutParams params = (MarginLayoutParams) textView.getLayoutParams();
        imageViewIsColorful = true;
        if (resId == 0) {
            imageView.setVisibility(GONE);
            if (LocaleController.isRTL) {
                params.rightMargin = AndroidUtilities.dp(this.padding);
            } else {
                params.leftMargin = AndroidUtilities.dp(this.padding);
            }
        } else {
            imageView.setBackground(Theme.createRoundRectDrawable(AndroidUtilities.dp(8), color));
            imageView.setImageResource(resId);
            imageView.setColorFilter(new PorterDuffColorFilter(Color.WHITE, PorterDuff.Mode.MULTIPLY));
            imageView.setVisibility(VISIBLE);
            if (LocaleController.isRTL) {
                params.rightMargin = AndroidUtilities.dp(58);
            } else {
                params.leftMargin = AndroidUtilities.dp(58);
            }
        }
    }

    public void setEnabled(boolean value, ArrayList<Animator> animators) {
        setEnabled(value);
        if (animators != null) {
            animators.add(ObjectAnimator.ofFloat(textView, "alpha", value ? 1.0f : 0.5f));
            if (valueTextView.getVisibility() == VISIBLE) {
                animators.add(ObjectAnimator.ofFloat(valueTextView, "alpha", value ? 1.0f : 0.5f));
            }
            if (valueImageView.getVisibility() == VISIBLE) {
                animators.add(ObjectAnimator.ofFloat(valueImageView, "alpha", value ? 1.0f : 0.5f));
            }
        } else {
            textView.setAlpha(value ? 1.0f : 0.5f);
            if (valueTextView.getVisibility() == VISIBLE) {
                valueTextView.setAlpha(value ? 1.0f : 0.5f);
            }
            if (valueImageView.getVisibility() == VISIBLE) {
                valueImageView.setAlpha(value ? 1.0f : 0.5f);
            }
        }
    }

    @Override
    public void setEnabled(boolean value) {
        super.setEnabled(value);
        textView.setAlpha(value || !canDisable ? 1.0f : 0.5f);
        if (valueTextView.getVisibility() == VISIBLE) {
            valueTextView.setAlpha(value || !canDisable ? 1.0f : 0.5f);
        }
        if (valueImageView.getVisibility() == VISIBLE) {
            valueImageView.setAlpha(value || !canDisable ? 1.0f : 0.5f);
        }
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        if (drawLoading || drawLoadingProgress != 0) {
            if (paint == null) {
                paint = new Paint(Paint.ANTI_ALIAS_FLAG);
            }
            if (incrementLoadingProgress) {
                loadingProgress += 16 / 1000f;
                if (loadingProgress > 1f) {
                    loadingProgress = 1f;
                    incrementLoadingProgress = false;
                }
            } else {
                loadingProgress -= 16 / 1000f;
                if (loadingProgress < 0) {
                    loadingProgress = 0;
                    incrementLoadingProgress = true;
                }
            }

            if (changeProgressStartDelay > 0) {
                changeProgressStartDelay -= 15;
            } else if (drawLoading && drawLoadingProgress != 1f) {
                drawLoadingProgress += 16 / 150f;
                if (drawLoadingProgress > 1f) {
                    drawLoadingProgress = 1f;
                }
            } else if (!drawLoading && drawLoadingProgress != 0) {
                drawLoadingProgress -= 16 / 150f;
                if (drawLoadingProgress < 0) {
                    drawLoadingProgress = 0;
                }
            }

            float alpha = (0.6f + 0.4f * loadingProgress) * drawLoadingProgress;
            paint.setColor(Theme.getColor(Theme.key_dialogSearchBackground, resourcesProvider));
            paint.setAlpha((int) (255 * alpha));
            int cy = getMeasuredHeight() >> 1;
            int loadingWidth = AndroidUtilities.dp(loadingSize);
            int loadingLeft = isRTL ? AndroidUtilities.dp(padding) : getMeasuredWidth() - AndroidUtilities.dp(padding) - loadingWidth;
            AndroidUtilities.rectTmp.set(loadingLeft, cy - AndroidUtilities.dp(3), loadingLeft + loadingWidth, cy + AndroidUtilities.dp(3));
            canvas.drawRoundRect(AndroidUtilities.rectTmp, AndroidUtilities.dp(3), AndroidUtilities.dp(3), paint);
            invalidate();
        }
        valueTextView.setAlpha(1f - drawLoadingProgress);
        super.dispatchDraw(canvas);

        if (needDivider) {
            int offset = AndroidUtilities.dp(imageView.getVisibility() == View.VISIBLE ? 58 : 20);
            canvas.drawLine(LocaleController.isRTL ? 0 : offset, getMeasuredHeight() - 1, getMeasuredWidth() - (LocaleController.isRTL ? offset : 0), getMeasuredHeight() - 1, Theme.dividerPaint);
        }
    }


    @Override
    public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
        super.onInitializeAccessibilityNodeInfo(info);
        info.setText(textView.getText() + (valueTextView != null && valueTextView.getVisibility() == View.VISIBLE ? "\n" + valueTextView.getText() : ""));
        info.setEnabled(isEnabled());
    }

    public void setDrawLoading(boolean drawLoading, int size, boolean animated) {
        this.drawLoading = drawLoading;
        this.loadingSize = size;

        if (!animated) {
            drawLoadingProgress = drawLoading ? 1f : 0f;
        } else {
            measureDelay = true;
        }
        invalidate();
    }

    public BackupImageView getValueBackupImageView() {
        if (valueBackupImageView == null) {
            valueBackupImageView = new BackupImageView(getContext());
            addView(valueBackupImageView, LayoutHelper.createFrame(24, 24, (LocaleController.isRTL ? Gravity.LEFT : Gravity.RIGHT) | Gravity.CENTER_VERTICAL, padding - 4, 0, padding - 4, 0));
        }
        valueBackupImageView.setVisibility(VISIBLE);
        return valueBackupImageView;
    }

    public void clearValueImage() {
        if (valueBackupImageView == null || valueBackupImageView.getVisibility() == GONE) return;
        valueBackupImageView.setAnimatedEmojiDrawable(null);
        valueBackupImageView.setImageDrawable(null);
        valueBackupImageView.setVisibility(GONE);
    }

    public void updateRTL() {
        if (isRTL == LocaleController.isRTL) return;
        isRTL = LocaleController.isRTL;
        textView.setGravity(LayoutHelper.getAbsoluteGravityStart() | Gravity.CENTER_VERTICAL);
        valueTextView.setGravity(LayoutHelper.getAbsoluteGravityEnd() | Gravity.CENTER_VERTICAL);
        for (int i = 0; i < getChildCount(); i++) {
            View child = getChildAt(i);
            LayoutParams params = (LayoutParams) child.getLayoutParams();
            int edge = params.gravity & Gravity.HORIZONTAL_GRAVITY_MASK;
            if (edge != Gravity.LEFT && edge != Gravity.RIGHT) continue;
            params.gravity = (params.gravity & ~Gravity.HORIZONTAL_GRAVITY_MASK) | (edge == Gravity.LEFT ? Gravity.RIGHT : Gravity.LEFT);
            int margin = params.leftMargin;
            params.leftMargin = params.rightMargin;
            params.rightMargin = margin;
            child.setLayoutParams(params);
        }
    }
}
