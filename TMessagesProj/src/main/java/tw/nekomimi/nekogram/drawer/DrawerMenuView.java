package tw.nekomimi.nekogram.drawer;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.content.Context;
import android.view.View;
import android.widget.LinearLayout;

import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;

import java.util.List;

public class DrawerMenuView extends LinearLayout {

    public DrawerMenuView(Context context) {
        super(context);
        setOrientation(VERTICAL);
    }

    public void rebuild(BaseFragment fragment, int currentAccount, Runnable afterClick) {
        removeAllViews();
        if (fragment == null) {
            return;
        }
        List<Integer> visible = MainMenuLayout.getVisibleIds();
        for (Integer id : visible) {
            MainMenuHelper.MenuItemInfo info = MainMenuHelper.resolve(id, fragment, currentAccount);
            if (info == null) {
                continue;
            }
            DrawerMenuItemView row = new DrawerMenuItemView(getContext());
            row.setItem(info.iconRes, info.text);
            row.setBackground(Theme.createSelectorDrawable(Theme.getColor(Theme.key_listSelector), 2));
            row.setOnClickListener(v -> {
                if (info.onClick != null) {
                    info.onClick.run();
                }
                if (afterClick != null) {
                    afterClick.run();
                }
            });
            if (info.onLongClick != null) {
                row.setOnLongClickListener(v -> {
                    info.onLongClick.run();
                    if (afterClick != null) {
                        afterClick.run();
                    }
                    return true;
                });
            }
            addView(row, new LayoutParams(LayoutParams.MATCH_PARENT, dp(48)));
        }
        if (getChildCount() == 0) {
            setVisibility(View.GONE);
        } else {
            setVisibility(View.VISIBLE);
        }
    }
}
