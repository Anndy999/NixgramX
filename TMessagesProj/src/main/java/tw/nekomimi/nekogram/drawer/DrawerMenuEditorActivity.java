package tw.nekomimi.nekogram.drawer;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.LocaleController.getString;

import android.content.Context;
import android.view.View;
import android.widget.FrameLayout;

import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.ActionBarMenuItem;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.HeaderCell;
import org.telegram.ui.Cells.TextCheckCell;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;

import java.util.ArrayList;
import java.util.List;

import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import tw.nekomimi.nekogram.helpers.NixNavigationConfig;

public class DrawerMenuEditorActivity extends BaseFragment {

    private final ArrayList<Integer> visible = new ArrayList<>();
    private final ArrayList<Integer> hidden = new ArrayList<>();
    private ListAdapter adapter;

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setTitle(getString(R.string.FilterReorder));
        actionBar.setAllowOverlayTitle(true);
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                } else if (id == 1) {
                    MainMenuLayout.resetDefault();
                    reload();
                    adapter.notifyDataSetChanged();
                }
            }
        });
        ActionBarMenuItem reset = actionBar.createMenu().addItem(1, R.drawable.msg_reset);
        reset.setContentDescription(getString(R.string.Reset));

        visible.clear();
        hidden.clear();
        visible.addAll(MainMenuLayout.getVisibleIds());
        hidden.addAll(MainMenuLayout.getHiddenIds());

        FrameLayout content = new FrameLayout(context);
        content.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));
        RecyclerListView listView = new RecyclerListView(context);
        listView.setLayoutManager(new LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false));
        adapter = new ListAdapter();
        listView.setAdapter(adapter);
        ItemTouchHelper touchHelper = new ItemTouchHelper(new ItemTouchHelper.Callback() {
            @Override
            public int getMovementFlags(RecyclerView recyclerView, RecyclerView.ViewHolder viewHolder) {
                int type = viewHolder.getItemViewType();
                if (type != 1) {
                    return makeMovementFlags(0, 0);
                }
                return makeMovementFlags(ItemTouchHelper.UP | ItemTouchHelper.DOWN, 0);
            }

            @Override
            public boolean onMove(RecyclerView recyclerView, RecyclerView.ViewHolder from, RecyclerView.ViewHolder to) {
                if (from.getItemViewType() != 1 || to.getItemViewType() != 1) {
                    return false;
                }
                int fromPos = from.getAdapterPosition() - 1;
                int toPos = to.getAdapterPosition() - 1;
                if (fromPos < 0 || toPos < 0 || fromPos >= visible.size() || toPos >= visible.size()) {
                    return false;
                }
                Integer item = visible.remove(fromPos);
                visible.add(toPos, item);
                persist();
                adapter.notifyItemMoved(from.getAdapterPosition(), to.getAdapterPosition());
                return true;
            }

            @Override
            public void onSwiped(RecyclerView.ViewHolder viewHolder, int direction) {
            }
        });
        touchHelper.attachToRecyclerView(listView);
        content.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        fragmentView = content;
        return fragmentView;
    }

    private void reload() {
        visible.clear();
        hidden.clear();
        visible.addAll(MainMenuLayout.getVisibleIds());
        hidden.addAll(MainMenuLayout.getHiddenIds());
    }

    private void persist() {
        MainMenuLayout.save(visible, hidden);
        reload();
    }

    private class ListAdapter extends RecyclerListView.SelectionAdapter {
        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            return holder.getItemViewType() != 0;
        }

        @Override
        public int getItemCount() {
            return 2 + visible.size() + hidden.size();
        }

        @Override
        public int getItemViewType(int position) {
            if (position == 0 || position == visible.size() + 1) {
                return 0;
            }
            return 1;
        }

        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(android.view.ViewGroup parent, int viewType) {
            if (viewType == 0) {
                return new RecyclerListView.Holder(new HeaderCell(parent.getContext()));
            }
            TextCheckCell cell = new TextCheckCell(parent.getContext());
            cell.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
            return new RecyclerListView.Holder(cell);
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
            if (holder.getItemViewType() == 0) {
                HeaderCell cell = (HeaderCell) holder.itemView;
                cell.setText(position == 0 ? getString(R.string.DrawerMenuVisible) : getString(R.string.DrawerMenuHidden));
                return;
            }
            int visibleEnd = visible.size();
            int id;
            boolean checked;
            if (position <= visibleEnd) {
                id = visible.get(position - 1);
                checked = true;
            } else {
                id = hidden.get(position - visibleEnd - 2);
                checked = false;
            }
            MainMenuItem item = MainMenuItem.getById(id);
            TextCheckCell cell = (TextCheckCell) holder.itemView;
            cell.setTextAndCheck(label(item), checked, true);
            cell.setOnClickListener(v -> toggle(id));
        }

        private void toggle(int id) {
            if (id == MainMenuItem.SETTINGS.getId() && MainMenuLayout.settingsMustRemainVisible() && visible.contains(id)) {
                return;
            }
            if (visible.contains(id)) {
                visible.remove((Integer) id);
                if (!hidden.contains(id)) {
                    hidden.add(id);
                }
            } else {
                hidden.remove((Integer) id);
                if (!visible.contains(id)) {
                    visible.add(id);
                }
            }
            persist();
            notifyDataSetChanged();
        }
    }

    private static String label(MainMenuItem item) {
        if (item == null) {
            return "";
        }
        switch (item) {
            case ARCHIVE:
                return getString(R.string.ArchivedChats);
            case NEW_GROUP:
                return getString(R.string.NewGroup);
            case SAVED:
                return getString(R.string.SavedMessages);
            case NEW_CHANNEL:
                return getString(R.string.NewChannel);
            case CALLS:
                return getString(R.string.Calls);
            case SETTINGS:
                return getString(R.string.Settings);
            case QR:
                return getString(R.string.AuthAnotherClient);
            case GHOST_MODE:
                return getString(R.string.GhostMode);
            case CONTACTS:
                return getString(R.string.Contacts);
            case PROFILE:
                return getString(R.string.MyProfile);
            case RECENT_CHATS:
                return getString(R.string.RecentChats);
            case BOOKMARKS:
                return getString(R.string.BookmarksManager);
            case NEW_CONTACT:
                return getString(R.string.NewContact);
            default:
                return item.name();
        }
    }
}
