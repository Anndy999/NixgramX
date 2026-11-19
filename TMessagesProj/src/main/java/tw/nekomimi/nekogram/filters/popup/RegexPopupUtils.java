package tw.nekomimi.nekogram.filters.popup;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.graphics.Rect;
import android.view.Gravity;
import android.view.View;

import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.ActionBarMenuSubItem;
import org.telegram.ui.ActionBar.ActionBarPopupWindow;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;

final class RegexPopupUtils {

    private RegexPopupUtils() {
    }

    static ActionBarPopupWindow.ActionBarPopupWindowLayout createLayout(BaseFragment fragment) {
        return new ActionBarPopupWindow.ActionBarPopupWindowLayout(fragment.getContext(), R.drawable.popup_fixed_alert4, fragment.getResourceProvider(), 0);
    }

    static ActionBarPopupWindow createPopupWindow(ActionBarPopupWindow.ActionBarPopupWindowLayout layout) {
        ActionBarPopupWindow popupWindow = new ActionBarPopupWindow(layout, LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT);
        popupWindow.setPauseNotifications(true);
        popupWindow.setDismissAnimationDuration(220);
        popupWindow.setOutsideTouchable(true);
        popupWindow.setClippingEnabled(true);
        popupWindow.setAnimationStyle(R.style.PopupContextAnimation);
        popupWindow.setFocusable(true);
        popupWindow.setInputMethodMode(ActionBarPopupWindow.INPUT_METHOD_NOT_NEEDED);
        popupWindow.getContentView().setFocusableInTouchMode(true);
        return popupWindow;
    }

    static void showPopupAtTouch(BaseFragment fragment, View anchorView, float touchedX, float touchedY,
                                 ActionBarPopupWindow popupWindow, View windowLayout) {
        View root = fragment.getFragmentView();
        if (root == null || !anchorView.isAttachedToWindow()) return;

        Rect visible = new Rect();
        root.getWindowVisibleDisplayFrame(visible);
        int[] windowOrigin = new int[2];
        int[] rootLocation = new int[2];
        root.getLocationOnScreen(windowOrigin);
        root.getLocationInWindow(rootLocation);
        visible.offset(rootLocation[0] - windowOrigin[0], rootLocation[1] - windowOrigin[1]);
        if (!visible.intersect(rootLocation[0], rootLocation[1],
                rootLocation[0] + root.getWidth(), rootLocation[1] + root.getHeight())) return;
        visible.inset(dp(6), dp(6));
        if (visible.isEmpty()) return;

        windowLayout.measure(
                View.MeasureSpec.makeMeasureSpec(visible.width(), View.MeasureSpec.AT_MOST),
                View.MeasureSpec.makeMeasureSpec(visible.height(), View.MeasureSpec.AT_MOST)
        );
        final int width = windowLayout.getMeasuredWidth();
        final int height = windowLayout.getMeasuredHeight();
        int[] anchor = new int[2];
        anchorView.getLocationInWindow(anchor);
        int x = anchor[0] + Math.round(touchedX);
        int y = anchor[1] + Math.round(touchedY);
        if (y + height > visible.bottom && y - height >= visible.top) {
            y -= height;
        }
        x = Math.max(visible.left, Math.min(x, visible.right - width));
        y = Math.max(visible.top, Math.min(y, visible.bottom - height));
        popupWindow.setWidth(Math.min(width, visible.width()));
        popupWindow.setHeight(Math.min(height, visible.height()));
        popupWindow.showAtLocation(root, Gravity.TOP | Gravity.LEFT, x, y);
        popupWindow.dimBehind();
    }

    static void applyDeleteItemColor(ActionBarMenuSubItem item) {
        int deleteBtnColor = Theme.getColor(Theme.key_text_RedBold);
        item.setColors(deleteBtnColor, deleteBtnColor);
    }
}
