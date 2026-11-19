package tw.nekomimi.nekogram.drawer;

import android.content.Context;
import android.util.SparseArray;
import android.view.View;
import android.widget.LinearLayout;

import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.ThemeBind;

import java.util.HashSet;

public class DrawerMenuView extends LinearLayout {
    private final SparseArray<DrawerMenuItemView> rows = new SparseArray<>();

    public DrawerMenuView(Context context) {
        super(context);
        setOrientation(VERTICAL);
    }

    public void rebuild(BaseFragment fragment, int currentAccount, Runnable afterClick) {
        if (fragment == null) {
            removeAllViews();
            rows.clear();
            setVisibility(GONE);
            return;
        }
        var retained = new HashSet<Integer>();
        int position = 0;
        for (int id : MainMenuLayout.getVisibleIds()) {
            MainMenuHelper.MenuItemInfo info = MainMenuHelper.resolve(id, fragment, currentAccount);
            if (info == null || !retained.add(id)) {
                continue;
            }
            DrawerMenuItemView row = rows.get(id);
            if (row == null) {
                row = new DrawerMenuItemView(getContext());
                rows.put(id, row);
                addView(row, position, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
            } else if (indexOfChild(row) != position) {
                removeView(row);
                addView(row, position);
            }
            row.setItem(info.iconRes, info.text);
            row.setOnClickListener(v -> {
                if (info.onClick != null) {
                    info.onClick.run();
                }
                if (afterClick != null) {
                    afterClick.run();
                }
            });
            row.setOnLongClickListener(info.onLongClick == null ? null : v -> {
                info.onLongClick.run();
                if (afterClick != null) {
                    afterClick.run();
                }
                return true;
            });
            row.setLongClickable(info.onLongClick != null);
            position++;
        }
        for (int i = rows.size() - 1; i >= 0; i--) {
            if (!retained.contains(rows.keyAt(i))) {
                removeView(rows.valueAt(i));
                rows.removeAt(i);
            }
        }
        setVisibility(getChildCount() == 0 ? View.GONE : View.VISIBLE);
    }

    void updateColors() {
        ThemeBind.updateTree(this, false);
    }
}
