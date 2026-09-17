package tw.nekomimi.nekogram.drawer;

import android.content.Context;
import android.graphics.Typeface;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ContactsController;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.MessagesStorage;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.diagnostics.NgxDiagnosticCore.Category;
import org.telegram.messenger.diagnostics.NgxDiagnosticCore.Field;
import org.telegram.messenger.diagnostics.NgxDiagnosticCore.Value;
import org.telegram.messenger.diagnostics.NgxDiagnostics;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.AvatarDrawable;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.LaunchActivity;
import org.telegram.ui.LoginActivity;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;

import tw.nekomimi.nekogram.helpers.PasscodeHelper;

/** Account picker adapted from Exteraless without its server badge services. */
public class DrawerAccountPickerView extends FrameLayout {
    private final ArrayList<Integer> accounts = new ArrayList<>();
    private final AccountAdapter adapter;
    private final RecyclerView recyclerView;
    private final ItemTouchHelper itemTouchHelper;
    private View draggingItemView;
    private int dragStartIndex = RecyclerView.NO_POSITION;
    private int dragCurrentIndex = RecyclerView.NO_POSITION;
    private boolean expanded;
    private Runnable onAccountSelected;

    public DrawerAccountPickerView(Context context) {
        super(context);
        expanded = MessagesController.getGlobalMainSettings().getBoolean("accountsShown", false);

        adapter = new AccountAdapter();
        recyclerView = new RecyclerView(context);
        recyclerView.setLayoutManager(new LinearLayoutManager(context));
        recyclerView.setAdapter(adapter);
        recyclerView.setOverScrollMode(OVER_SCROLL_NEVER);
        recyclerView.setVerticalScrollBarEnabled(false);
        addView(recyclerView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        RecyclerView.ChildDrawingOrderCallback drawingOrderCallback = this::resolveDragDrawingOrder;
        itemTouchHelper = new ItemTouchHelper(new ItemTouchHelper.Callback() {
            @Override
            public int getMovementFlags(RecyclerView rv, RecyclerView.ViewHolder holder) {
                int position = holder.getAdapterPosition();
                if (position == RecyclerView.NO_POSITION || position >= accounts.size()) {
                    return 0;
                }
                return makeMovementFlags(ItemTouchHelper.UP | ItemTouchHelper.DOWN, 0);
            }

            @Override
            public boolean isLongPressDragEnabled() {
                return false;
            }

            @Override
            public boolean onMove(RecyclerView rv, RecyclerView.ViewHolder from, RecyclerView.ViewHolder to) {
                int fromPosition = from.getAdapterPosition();
                int toPosition = to.getAdapterPosition();
                if (fromPosition == RecyclerView.NO_POSITION || toPosition == RecyclerView.NO_POSITION
                        || fromPosition >= accounts.size() || toPosition >= accounts.size()) {
                    return false;
                }
                diagnostic("ACCOUNT_REORDER_MOVE",
                        Value.integer(Field.INDEX, fromPosition),
                        Value.integer(Field.TARGET_INDEX, toPosition));
                adapter.swapElements(fromPosition, toPosition);
                dragCurrentIndex = toPosition;
                return true;
            }

            @Override
            public void onSwiped(RecyclerView.ViewHolder holder, int direction) {
            }

            @Override
            public void onSelectedChanged(RecyclerView.ViewHolder holder, int actionState) {
                if (actionState == ItemTouchHelper.ACTION_STATE_DRAG && holder != null) {
                    dragStartIndex = holder.getAdapterPosition();
                    dragCurrentIndex = dragStartIndex;
                    draggingItemView = holder.itemView;
                    draggingItemView.setPressed(false);
                    draggingItemView.jumpDrawablesToCurrentState();
                    draggingItemView.setElevation(dp(4));
                    recyclerView.setChildDrawingOrderCallback(drawingOrderCallback);
                    recyclerView.invalidate();
                    diagnostic("ACCOUNT_REORDER_START", Value.integer(Field.INDEX, dragStartIndex));
                }
                super.onSelectedChanged(holder, actionState);
            }

            @Override
            public void clearView(RecyclerView rv, RecyclerView.ViewHolder holder) {
                super.clearView(rv, holder);
                holder.itemView.setTranslationX(0f);
                holder.itemView.setTranslationY(0f);
                holder.itemView.setElevation(0f);
                holder.itemView.setPressed(false);
                if (draggingItemView == holder.itemView) {
                    draggingItemView = null;
                }
                rv.setChildDrawingOrderCallback(null);
                rv.invalidate();
                int finalIndex = dragCurrentIndex != RecyclerView.NO_POSITION
                        ? dragCurrentIndex : holder.getAdapterPosition();
                if (dragStartIndex != RecyclerView.NO_POSITION && finalIndex != RecyclerView.NO_POSITION) {
                    diagnostic("ACCOUNT_REORDER_END", Value.integer(Field.INDEX, finalIndex));
                }
                dragStartIndex = RecyclerView.NO_POSITION;
                dragCurrentIndex = RecyclerView.NO_POSITION;
            }
        });
        itemTouchHelper.attachToRecyclerView(recyclerView);
        rebuild();
    }

    public boolean isExpanded() {
        return expanded;
    }

    public void setOnAccountSelected(Runnable value) {
        onAccountSelected = value;
    }

    public void toggleExpand() {
        setExpanded(!expanded);
    }

    public void setExpanded(boolean value) {
        if (expanded == value) {
            return;
        }
        expanded = value;
        MessagesController.getGlobalMainSettings().edit().putBoolean("accountsShown", value).apply();
        rebuild();
    }

    public void updateUnreadCounters() {
        adapter.notifyItemRangeChanged(0, accounts.size());
    }

    public void updateColors() {
        adapter.notifyDataSetChanged();
        invalidate();
    }

    public void dispose() {
        draggingItemView = null;
        recyclerView.setChildDrawingOrderCallback(null);
        recyclerView.stopScroll();
    }

    public void rebuild() {
        accounts.clear();
        for (int account = 0; account < UserConfig.MAX_ACCOUNT_COUNT; account++) {
            if (UserConfig.getInstance(account).isClientActivated() && !PasscodeHelper.isAccountHidden(account)) {
                accounts.add(account);
            }
        }
        accounts.sort(Comparator.comparingLong(account -> UserConfig.getInstance(account).loginTime));
        adapter.notifyDataSetChanged();
        setVisibility(expanded ? VISIBLE : GONE);
    }

    private int resolveDragDrawingOrder(int childCount, int drawingPosition) {
        if (draggingItemView != null) {
            int draggingIndex = recyclerView.indexOfChild(draggingItemView);
            if (draggingIndex >= 0) {
                if (drawingPosition == childCount - 1) {
                    return draggingIndex;
                }
                if (drawingPosition >= draggingIndex) {
                    return drawingPosition + 1;
                }
            }
        }
        return drawingPosition;
    }

    private Integer availableAccount() {
        for (int account = UserConfig.MAX_ACCOUNT_COUNT - 1; account >= 0; account--) {
            if (!UserConfig.getInstance(account).isClientActivated()) {
                return account;
            }
        }
        return null;
    }

    private void diagnostic(String event, Value... values) {
        NgxDiagnostics.event(Category.NAVIGATION, event, values);
    }

    private static int dp(float value) {
        return AndroidUtilities.dp(value);
    }

    private class AccountAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
        private static final int VIEW_TYPE_ACCOUNT = 0;
        private static final int VIEW_TYPE_ADD = 1;

        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View view = viewType == VIEW_TYPE_ADD
                    ? new AddAccountView(parent.getContext())
                    : new AccountRowView(parent.getContext());
            return new RecyclerView.ViewHolder(view) {
            };
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
            if (getItemViewType(position) == VIEW_TYPE_ACCOUNT) {
                int account = accounts.get(position);
                AccountRowView row = (AccountRowView) holder.itemView;
                row.bind(account, accounts.size() > 1);
                row.setOnClickListener(v -> {
                    if (account == UserConfig.selectedAccount) {
                        return;
                    }
                    if (onAccountSelected != null) {
                        onAccountSelected.run();
                    }
                    if (getContext() instanceof LaunchActivity) {
                        ((LaunchActivity) getContext()).switchToAccount(account, true);
                    }
                });
                row.setOnLongClickListener(v -> {
                    itemTouchHelper.startDrag(holder);
                    return true;
                });
            } else {
                AddAccountView add = (AddAccountView) holder.itemView;
                add.updateColors();
                add.setOnClickListener(v -> {
                    Integer account = availableAccount();
                    if (account == null) {
                        return;
                    }
                    if (onAccountSelected != null) {
                        onAccountSelected.run();
                    }
                    if (getContext() instanceof LaunchActivity) {
                        ((LaunchActivity) getContext()).presentFragment(new LoginActivity(account));
                    }
                });
            }
        }

        @Override
        public int getItemCount() {
            return accounts.size() + (availableAccount() == null ? 0 : 1);
        }

        @Override
        public int getItemViewType(int position) {
            return position < accounts.size() ? VIEW_TYPE_ACCOUNT : VIEW_TYPE_ADD;
        }

        void swapElements(int from, int to) {
            if (from < 0 || to < 0 || from >= accounts.size() || to >= accounts.size()) {
                return;
            }
            UserConfig first = UserConfig.getInstance(accounts.get(from));
            UserConfig second = UserConfig.getInstance(accounts.get(to));
            int loginTime = first.loginTime;
            first.loginTime = second.loginTime;
            second.loginTime = loginTime;
            first.saveConfig(false);
            second.saveConfig(false);
            Collections.swap(accounts, from, to);
            notifyItemMoved(from, to);
        }
    }

    private static class AccountRowView extends FrameLayout {
        private final AvatarDrawable avatarDrawable = new AvatarDrawable();
        private final BackupImageView avatarView;
        private final TextView nameView;
        private final TextView unreadBadge;
        private final ImageView selectedView;

        AccountRowView(Context context) {
            super(context);
            setLayoutParams(new RecyclerView.LayoutParams(LayoutHelper.MATCH_PARENT, dp(44)));

            avatarView = new BackupImageView(context);
            avatarView.setRoundRadius(dp(17));
            addView(avatarView, LayoutHelper.createFrame(34, 34, Gravity.START | Gravity.CENTER_VERTICAL, 8, 0, 0, 0));

            nameView = new TextView(context);
            nameView.setTextSize(15);
            nameView.setTypeface(AndroidUtilities.bold());
            nameView.setGravity(Gravity.CENTER_VERTICAL);
            nameView.setSingleLine(true);
            nameView.setEllipsize(TextUtils.TruncateAt.END);
            addView(nameView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT,
                    Gravity.START, 54, 0, 70, 0));

            unreadBadge = new TextView(context);
            unreadBadge.setTextSize(12);
            unreadBadge.setTypeface(Typeface.DEFAULT_BOLD);
            unreadBadge.setGravity(Gravity.CENTER);
            unreadBadge.setMinWidth(dp(24));
            unreadBadge.setPadding(dp(7), 0, dp(7), 0);
            addView(unreadBadge, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, 24,
                    Gravity.END | Gravity.CENTER_VERTICAL, 0, 0, 12, 0));

            selectedView = new ImageView(context);
            selectedView.setImageResource(R.drawable.msg_check);
            addView(selectedView, LayoutHelper.createFrame(18, 18,
                    Gravity.END | Gravity.CENTER_VERTICAL, 0, 0, 12, 0));
        }

        void bind(int account, boolean showUnread) {
            boolean selected = account == UserConfig.selectedAccount;
            TLRPC.User user = UserConfig.getInstance(account).getCurrentUser();
            if (user != null) {
                avatarDrawable.setInfo(account, user);
                avatarView.getImageReceiver().setCurrentAccount(account);
                avatarView.setForUserOrChat(user, avatarDrawable);
                nameView.setText(ContactsController.formatName(user.first_name, user.last_name));
            } else {
                nameView.setText("");
            }
            nameView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
            setBackground(selected
                    ? Theme.createSimpleSelectorRoundRectDrawable(dp(12),
                    Theme.getColor(Theme.key_windowBackgroundGray), Theme.getColor(Theme.key_listSelector))
                    : Theme.createSelectorDrawable(Theme.getColor(Theme.key_listSelector), 2));

            int unread = MessagesStorage.getInstance(account).getMainUnreadCount();
            unreadBadge.setVisibility(showUnread && unread > 0 ? VISIBLE : GONE);
            unreadBadge.setText(Integer.toString(unread));
            unreadBadge.setTextColor(Theme.getColor(Theme.key_chats_unreadCounterText));
            unreadBadge.setBackground(Theme.createRoundRectDrawable(dp(12), Theme.getColor(Theme.key_chats_unreadCounter)));
            FrameLayout.LayoutParams badgeParams = (FrameLayout.LayoutParams) unreadBadge.getLayoutParams();
            badgeParams.rightMargin = dp(selected ? 40 : 12);
            unreadBadge.setLayoutParams(badgeParams);

            selectedView.setVisibility(selected ? VISIBLE : GONE);
            selectedView.setColorFilter(Theme.getColor(Theme.key_windowBackgroundWhiteBlueIcon));
        }
    }

    private static class AddAccountView extends TextView {
        AddAccountView(Context context) {
            super(context);
            setLayoutParams(new RecyclerView.LayoutParams(LayoutHelper.MATCH_PARENT, dp(44)));
            setText(LocaleController.getString(R.string.AddAccount));
            setTextSize(15);
            setTypeface(AndroidUtilities.bold());
            setGravity(Gravity.CENTER_VERTICAL);
            setCompoundDrawablesWithIntrinsicBounds(R.drawable.poll_add_circle, 0, 0, 0);
            setCompoundDrawablePadding(dp(16));
            setPadding(dp(14), 0, dp(12), 0);
            updateColors();
        }

        void updateColors() {
            setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlueText4));
            setBackground(Theme.createSelectorDrawable(Theme.getColor(Theme.key_listSelector), 2));
        }
    }
}
