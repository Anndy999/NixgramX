package org.telegram.ui.Components;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.core.view.ViewCompat;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;
import androidx.customview.widget.ExploreByTouchHelper;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.Utilities;

import java.util.List;

public class FilledTabsView extends View {

    private final Paint backgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint selectedPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path selectedPath = new Path();

    private int textColor = 0xFFFFFFFF;
    private int selectedTextColor = 0xFFFFFFFF;

    private Text[] tabs;
    private RectF[] bounds;
    private final ExploreByTouchHelper accessibilityHelper = new TabsAccessibilityHelper();

    public FilledTabsView(Context context) {
        super(context);
        ViewCompat.setAccessibilityDelegate(this, accessibilityHelper);
    }

    public void setTabs(CharSequence ...texts) {
        tabs = new Text[texts.length];
        bounds = new RectF[texts.length];

        for (int i = 0; i < texts.length; ++i) {
            tabs[i] = new Text(texts[i], 14, AndroidUtilities.bold());
            bounds[i] = new RectF();
        }

        updateTabBounds();
        invalidate();
    }

    private float selectedTabIndex;

    public void setSelected(float tabIndex) {
        if (Math.abs(tabIndex - selectedTabIndex) > 0.001f) {
            invalidate();
        }
        int previousIndex = Math.round(selectedTabIndex);
        selectedTabIndex = tabIndex;
        if (previousIndex != Math.round(tabIndex)) {
            accessibilityHelper.invalidateRoot();
        }
    }

    private Utilities.Callback<Integer> onTabClick;
    public FilledTabsView onTabSelected(Utilities.Callback<Integer> onTabClick) {
        this.onTabClick = onTabClick;
        return this;
    }

    @Override
    public void setBackgroundColor(int color) {
        backgroundPaint.setColor(color);
        invalidate();
    }

    public void setSelectedColor(int color) {
        selectedPaint.setColor(color);
        invalidate();
    }

    public void setTextColor(int color) {
        textColor = color;
        invalidate();
    }

    public void setSelectedTextColor(int color) {
        selectedTextColor = color;
        invalidate();
    }

    public void setColors(int backgroundColor, int selectedColor, int textColor, int selectedTextColor) {
        backgroundPaint.setColor(backgroundColor);
        selectedPaint.setColor(selectedColor);
        this.textColor = textColor;
        this.selectedTextColor = selectedTextColor;
        invalidate();
    }

    @Override
    protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
        super.onSizeChanged(width, height, oldWidth, oldHeight);
        updateTabBounds();
    }

    private void updateTabBounds() {
        if (tabs == null || tabs.length == 0) return;
        float textBudget = Math.max(0, getWidth() - dp(8) - tabs.length * dp(24));
        float naturalTextWidth = 0;
        for (Text tab : tabs) {
            tab.ellipsize(-1);
            naturalTextWidth += tab.getWidth();
        }
        float scale = naturalTextWidth > textBudget ? textBudget / naturalTextWidth : 1f;
        float width = tabs.length * dp(24) + Math.min(naturalTextWidth, textBudget);
        float x = (getWidth() - width) / 2f;
        for (int i = 0; i < tabs.length; i++) {
            if (scale < 1f) tabs[i].ellipsize(tabs[i].getWidth() * scale);
            float tabWidth = tabs[i].getWidth() + dp(24);
            bounds[i].set(x, 0, x + tabWidth, getHeight());
            x += tabWidth;
        }
        accessibilityHelper.invalidateRoot();
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        if (tabs == null || tabs.length == 0) return;

        final int height = getHeight();
        final float textLeft = bounds[0].left + dp(12);
        AndroidUtilities.rectTmp.set(bounds[0].left - dp(4), (height - dp(36)) / 2f,
                bounds[tabs.length - 1].right + dp(4), (height + dp(36)) / 2f);
        canvas.drawRoundRect(AndroidUtilities.rectTmp, dp(18), dp(18), backgroundPaint);

        float top = (height - dp(28)) / 2f, bottom = (height + dp(28)) / 2f;
        final int l = Utilities.clamp((int) Math.floor(selectedTabIndex), tabs.length - 1, 0);
        final int r = Utilities.clamp((int) Math.ceil(selectedTabIndex), tabs.length - 1, 0);
        float left = AndroidUtilities.lerp(bounds[l].left, bounds[r].left, (float) (selectedTabIndex - Math.floor(selectedTabIndex)));
        float right = AndroidUtilities.lerp(bounds[l].right, bounds[r].right, (float) (selectedTabIndex - Math.floor(selectedTabIndex)));

        AndroidUtilities.rectTmp.set(left, top, right, bottom);
        selectedPath.rewind();
        selectedPath.addRoundRect(AndroidUtilities.rectTmp, dp(15), dp(15), Path.Direction.CW);
        canvas.drawRoundRect(AndroidUtilities.rectTmp, dp(15), dp(15), selectedPaint);

        drawTabsText(canvas, textLeft, height, textColor);

        canvas.save();
        canvas.clipPath(selectedPath);
        drawTabsText(canvas, textLeft, height, selectedTextColor);
        canvas.restore();
    }

    private void drawTabsText(Canvas canvas, float x, int height, int color) {
        for (Text tab : tabs) {
            tab.draw(canvas, x, height / 2f, color, 1f);
            x += tab.getWidth() + dp(12 + 12);
        }
    }

    private int tabAt(float x, float y) {
        if (bounds != null) {
            for (int i = 0; i < bounds.length; i++) {
                if (bounds[i].contains(x, y)) return i;
            }
        }
        return ExploreByTouchHelper.INVALID_ID;
    }

    private int lastPressedIndex = ExploreByTouchHelper.INVALID_ID;
    private boolean trackingTouch;

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        int index = tabAt(event.getX(), event.getY());
        int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN) {
            trackingTouch = index != ExploreByTouchHelper.INVALID_ID;
        }
        if (!trackingTouch) return super.onTouchEvent(event);

        if (action != MotionEvent.ACTION_CANCEL && index != ExploreByTouchHelper.INVALID_ID
                && index != lastPressedIndex && onTabClick != null) {
            lastPressedIndex = index;
            onTabClick.run(index);
        }
        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            trackingTouch = false;
            lastPressedIndex = ExploreByTouchHelper.INVALID_ID;
        }
        return true;
    }

    @Override
    public boolean dispatchHoverEvent(MotionEvent event) {
        return accessibilityHelper.dispatchHoverEvent(event) || super.dispatchHoverEvent(event);
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        return accessibilityHelper.dispatchKeyEvent(event) || super.dispatchKeyEvent(event);
    }

    @Override
    protected void onFocusChanged(boolean gainFocus, int direction, Rect previouslyFocusedRect) {
        super.onFocusChanged(gainFocus, direction, previouslyFocusedRect);
        accessibilityHelper.onFocusChanged(gainFocus, direction, previouslyFocusedRect);
    }

    private class TabsAccessibilityHelper extends ExploreByTouchHelper {
        TabsAccessibilityHelper() {
            super(FilledTabsView.this);
        }

        @Override
        protected int getVirtualViewAt(float x, float y) {
            return tabAt(x, y);
        }

        @Override
        protected void getVisibleVirtualViews(List<Integer> ids) {
            if (bounds == null) return;
            for (int i = 0; i < bounds.length; i++) {
                if (!bounds[i].isEmpty()) ids.add(i);
            }
        }

        @Override
        protected void onPopulateNodeForVirtualView(int id, @NonNull AccessibilityNodeInfoCompat node) {
            Rect rect = new Rect();
            bounds[id].roundOut(rect);
            node.setBoundsInParent(rect);
            node.setText(tabs[id].getText());
            node.setClassName("android.widget.Button");
            node.setSelected(id == Math.round(selectedTabIndex));
            node.setClickable(true);
            node.addAction(AccessibilityNodeInfoCompat.ACTION_CLICK);
        }

        @Override
        protected boolean onPerformActionForVirtualView(int id, int action, Bundle arguments) {
            if (action != AccessibilityNodeInfoCompat.ACTION_CLICK || onTabClick == null) return false;
            onTabClick.run(id);
            return true;
        }
    }
}
