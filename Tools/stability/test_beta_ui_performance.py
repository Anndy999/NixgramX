"""Exercise production UI geometry, bookmark loading, filtering, and list updates on the host."""
from pathlib import Path
import subprocess
import tempfile
import unittest

JAVA = Path(__file__).resolve().parents[2] / 'TMessagesProj/src/main/java'


def section(source, first, following):
    return source[source.index(first):source.index(following, source.index(first))]


def run_java(source):
    with tempfile.TemporaryDirectory() as tmp:
        path = Path(tmp) / 'Probe.java'
        path.write_text(source)
        subprocess.run(['javac', '-d', tmp, str(path)], check=True)
        subprocess.run(['java', '-ea', '-cp', tmp, 'Probe'], check=True)


class BetaUiPerformanceTest(unittest.TestCase):
    def test_effective_filter_input_and_rule_generations(self):
        source = (JAVA / 'tw/nekomimi/nekogram/filters/AyuFilter.java').read_text()
        production = section(source, '    public static void invalidateFilteredCache()',
                             '    public static ArrayList<ChatFilterEntry> getChatFilterEntries()')
        run_java('''
import java.util.*;
import java.util.regex.Pattern;
class Probe {
    static final Object cacheLock = new Object();
    static RuleIndex ruleIndex;
    static long filterRevision;
    static int extractions;
    static boolean excluded;
    static class LruCache<K,V> extends HashMap<K,V> { LruCache(int size) { super(size); } }
    record Flag(boolean value) { boolean Bool() { return value; } }
    static class NaConfig {
        static final NaConfig INSTANCE = new NaConfig();
        boolean privateRules;
        Flag getRegexFiltersEnabled() { return new Flag(true); }
        Flag getRegexFiltersEnableInChats() { return new Flag(privateRules); }
    }
    static class FilterModel {
        boolean enabled = true;
        Pattern pattern;
        FilterModel(String value) { pattern = Pattern.compile(value); }
    }
    static class ChatFilterEntry {
        long dialogId;
        ArrayList<FilterModel> filters = new ArrayList<>();
        ChatFilterEntry(long id, String regex) { dialogId = id; filters.add(new FilterModel(regex)); }
    }
    static final ArrayList<FilterModel> global = new ArrayList<>();
    static final ArrayList<ChatFilterEntry> chats = new ArrayList<>();
    static ArrayList<FilterModel> getRegexFilters() { return global; }
    static ArrayList<ChatFilterEntry> getChatFilterEntries() { return chats; }
    static boolean isDialogExcluded(long id) { return excluded; }
    static class TextUtils { static boolean isEmpty(CharSequence value) { return value == null || value.length() == 0; } }
    static class MessageObject {
        int currentAccount;
        long dialogId = 7;
        String text = "clean";
        long getDialogId() { return dialogId; }
        boolean isOutOwner() { return false; }
        record GroupedMessages(String caption) {}
    }
    static CharSequence getMessageText(MessageObject msg, MessageObject.GroupedMessages group) {
        extractions++;
        return group == null ? msg.text : msg.text + group.caption();
    }
''' + production + '''
    public static void main(String[] args) {
        global.add(new FilterModel("global"));
        chats.add(new ChatFilterEntry(7, "local"));
        chats.add(new ChatFilterEntry(7, "duplicate"));
        MessageObject m = new MessageObject();
        assert !isFiltered(m, null);
        assert isFiltered(m, new MessageObject.GroupedMessages("local"));
        assert !isFiltered(m, null);
        m.currentAccount = 1;
        m.text = "local"; assert isFiltered(m, null);
        m.text = "edited"; assert !isFiltered(m, null);
        m.text = "duplicate"; assert !isFiltered(m, null);
        m.text = "global"; assert !isFiltered(m, null);
        NaConfig.INSTANCE.privateRules = true; assert isFiltered(m, null);
        RuleIndex old = ruleIndex;
        global.clear(); invalidateFilteredCache();
        assert !isFiltered(m, null);
        assert old.isFiltered(m, null, m.dialogId);
        assert !isFiltered(m, null);
        int before = extractions;
        m.dialogId = -1; assert !isFiltered(m, null);
        excluded = true; m.dialogId = 7; assert !isFiltered(m, null);
        assert extractions == before;
    }
}
''')

    def test_late_list_completion_cannot_overwrite_reload(self):
        source = (JAVA / 'org/telegram/ui/Adapters/DialogsAdapter.java').read_text()
        production = section(source, '    private long diffGeneration;', '    @Override\n    public void onViewAttachedToWindow')
        run_java('''
import java.util.*;
class Adapter {
    public void onDetachedFromRecyclerView(Probe.RecyclerView r) {}
    public void onAttachedToRecyclerView(Probe.RecyclerView r) {}
    public void notifyDataSetChanged() {}
}
class Probe extends Adapter {
    static class Queue extends ArrayDeque<Runnable> { void postRunnable(Runnable r) { add(r); } void next() { remove().run(); } }
    static class Utilities { static final Queue searchQueue = new Queue(); }
    static class AndroidUtilities { static final Queue ui = new Queue(); static void runOnUIThread(Runnable r) { ui.add(r); } }
    static class DialogsActivity { static final int DIALOGS_TYPE_DEFAULT = 0; }
    class RecyclerView { Object getAdapter() { return Probe.this; } }
    static class ItemInternal {
        int viewType, id;
        ItemInternal(int id) { this.id = id; }
        boolean compare(ItemInternal other) { return id == other.id; }
    }
    static class DiffUtil {
        abstract static class Callback {
            public abstract int getOldListSize();
            public abstract int getNewListSize();
            public abstract boolean areItemsTheSame(int i, int j);
            public abstract boolean areContentsTheSame(int i, int j);
        }
        static class DiffResult { void dispatchUpdatesTo(Probe p) { p.applied.add(p.itemInternals.get(0).id); } }
        static DiffResult calculateDiff(Callback c) {
            assert c.getOldListSize() == 50 && c.getNewListSize() == 50;
            assert !c.areItemsTheSame(0, 0) && c.areContentsTheSame(0, 0);
            return new DiffResult();
        }
    }
    ArrayList<ItemInternal> itemInternals = new ArrayList<>();
    ArrayList<Integer> applied = new ArrayList<>();
    RecyclerView recyclerListView = new RecyclerView();
    int dialogsCount = 50, dialogsType, communityId, start;
    Object onlineContacts;
    boolean hasHints, hasChatlistHint, isOnlySelect, dialogsListFrozen, isReordering, collapsedView, isTransitionSupport;
    void updateItemList() {
        itemInternals.clear();
        for (int i = 0; i < 50; i++) itemInternals.add(new ItemInternal(start + i));
    }
''' + production + '''
    public static void main(String[] args) {
        Probe p = new Probe(); p.updateItemList();
        Runnable anchor = () -> {};
        p.start = 1; p.updateList(anchor); Utilities.searchQueue.next();
        p.start = 2; p.notifyDataSetChanged();
        p.start = 3; p.updateList(anchor); Utilities.searchQueue.next();
        AndroidUtilities.ui.next(); assert p.itemInternals.get(0).id == 2;
        AndroidUtilities.ui.next(); assert p.applied.equals(List.of(3));
        p.start = 4; p.updateList(anchor); p.start = 5; p.updateList(anchor);
        Utilities.searchQueue.next(); AndroidUtilities.ui.next();
        Utilities.searchQueue.next(); AndroidUtilities.ui.next();
        assert p.applied.equals(List.of(3, 5));
        p.start = 6; p.updateList(anchor); p.onDetachedFromRecyclerView(p.recyclerListView);
        p.start = 7; p.onAttachedToRecyclerView(p.recyclerListView);
        Utilities.searchQueue.next(); AndroidUtilities.ui.next();
        assert p.itemInternals.get(0).id == 7 && p.applied.equals(List.of(3, 5));
    }
}
''')

    def test_compact_comment_budget_handles_rotation_and_album_tiles(self):
        source = (JAVA / 'org/telegram/ui/Cells/ChatMessageCell.java').read_text()
        production = section(source, '        final int rowWidth = isCompactAlbum() ?',
                             '        final float textSize = compactHeaderTextPaint.getTextSize();')
        constants = '\n'.join(line for line in source.splitlines() if any(
            ('int ' + name + ' = ') in line for name in ['COMPACT_HEADER_ROW', 'COMPACT_HEADER_ICON_GAP',
                                                         'COMPACT_HEADER_AVATAR_GAP', 'COMPACT_COMMENT_ARROW_INSET']))
        run_java('''
class Probe {
    boolean album;
    int parentWidth, tileWidth, sideColumn;
    boolean isCompactAlbum() { return album; }
    int getCompactContentWidth() { return parentWidth; }
    int getCompactRight() { return 16 + tileWidth; }
    int getCompactLeft() { return 16; }
    int getCompactSideButtonColumn() { return sideColumn; }
    static int dp(float n) { return (int) Math.ceil(n); }
    record Budget(int width, int avatars, int textWidth) {}
    Budget result;
    static class Rect { void setEmpty() {} }
    static class Paint { void setColor(int color) {} }
    Rect commentButtonRect = new Rect();
    Paint compactHeaderTextPaint = new Paint();
    Budget budget(int avatars, boolean unread) {
        result = new Budget(0, 0, 0);
        measure(avatars, unread);
        return result;
    }
    void measure(int avatars, boolean unread) {
        int textColor = 0;
        int size = dp(COMPACT_HEADER_ROW), avatarStep = dp(11);
''' + production + '''
        result = new Budget(contentWidth, avatars, textWidth);
    }
''' + constants + '''
    public static void main(String[] args) {
        Probe p = new Probe();
        p.parentWidth = 380; p.tileWidth = 150;
        Budget narrow = p.budget(3, true);
        assert narrow.width() == 150 && narrow.avatars() == 3;
        p.album = true;
        assert p.budget(3, true).textWidth() - narrow.textWidth() == 230;
        p.album = false; p.tileWidth = 620; p.parentWidth = 400; p.sideColumn = 40;
        assert p.budget(3, true).width() == 360;
        assert p.budget(3, false).textWidth() > p.budget(3, true).textWidth();
        for (int tile = 0; tile <= 400; tile++) {
            p.tileWidth = tile;
            Budget b = p.budget(3, true);
            assert b.width() == Math.max(0, tile - p.sideColumn);
            assert b.textWidth() >= 0 && b.textWidth() <= b.width();
            assert b.avatars() >= 0 && b.avatars() <= 3;
            if (b.width() < 60) assert b.avatars() == 0;
        }
    }
}
''')

    def test_settings_title_value_and_icon_bounds(self):
        source = (JAVA / 'org/telegram/ui/Cells/TextSettingsCell.java').read_text()
        production = section(source, '    protected void onMeasure(int', '    private void setMultilineTitle(')
        run_java('''
class Probe {
    static final int VISIBLE = 0;
    boolean multilineTitle, isRTL, imageViewIsColorful, betterLayout, needDivider, drawLoading;
    float drawLoadingProgress;
    int loadingSize;
    int padding = 21, height;
    static class AndroidUtilities { static int dp(int n) { return n; } }
    static class MeasureSpec {
        static final int EXACTLY = 1 << 30, AT_MOST = 2 << 30, UNSPECIFIED = 0, MASK = 3 << 30;
        static int getSize(int spec) { return spec & ~MASK; }
        static int makeMeasureSpec(int size, int mode) { assert size >= 0; return size | mode; }
    }
    static int resolveSize(int desired, int spec) {
        int mode = spec & MeasureSpec.MASK, size = MeasureSpec.getSize(spec);
        return mode == MeasureSpec.EXACTLY ? size : mode == MeasureSpec.AT_MOST ? Math.min(size, desired) : desired;
    }
    static class LayoutParams { int width = 24, height = 24, leftMargin, rightMargin; }
    static class View {
        LayoutParams params = new LayoutParams();
        int width, height, natural, visibility;
        View(int natural, int margin) {
            this.natural = natural;
            params.leftMargin = params.rightMargin = margin;
        }
        LayoutParams getLayoutParams() { return params; }
        int getVisibility() { return visibility; }
        int getMeasuredWidth() { return width; }
        int getMeasuredHeight() { return height; }
        void measure(int w, int h) { width = resolveSize(natural, w); height = resolveSize(87, h); }
    }
    View textView = new View(450, 21), imageView = new View(24, 16), valueTextView = new View(240, 17),
         valueImageView = new View(24, 21), valueBackupImageView = new View(24, 17);
    int getPaddingLeft() { return 3; }
    int getPaddingRight() { return 7; }
    void setMeasuredDimension(int w, int h) { height = h; }
    int end(View v) { return isRTL ? v.params.leftMargin : v.params.rightMargin; }
    int start(View v) { return isRTL ? v.params.rightMargin : v.params.leftMargin; }
''' + production + '''
    public static void main(String[] args) {
        for (boolean rtl : new boolean[]{false, true}) {
            for (boolean better : new boolean[]{false, true}) {
                Probe p = new Probe(); p.isRTL = rtl; p.betterLayout = better; p.imageViewIsColorful = true;
                for (int w : new int[]{48, 240, 320, 600}) {
                    p.onMeasure(w, 0);
                    assert p.height == 50;
                    assert p.start(p.textView) >= p.start(p.imageView) + p.imageView.width + 8;
                    assert p.end(p.valueTextView) >= p.end(p.valueImageView) + p.valueImageView.width + 8;
                    assert p.end(p.valueImageView) >= p.end(p.valueBackupImageView) + p.valueBackupImageView.width + 8;
                    if (w >= 240) {
                        int inner = w - p.getPaddingLeft() - p.getPaddingRight();
                        assert p.textView.width > 0 && p.valueTextView.width > 0;
                        assert p.start(p.textView) + p.textView.width + 8 <= inner - p.end(p.valueTextView) - p.valueTextView.width;
                    } else {
                        assert p.textView.width == 0 && p.valueTextView.width == 0;
                    }
                }
                p.multilineTitle = true; p.needDivider = true;
                p.onMeasure(320, 0); assert p.height == 100;
                p.onMeasure(320, MeasureSpec.makeMeasureSpec(80, MeasureSpec.AT_MOST)); assert p.height == 80;
                p.multilineTitle = false;
                p.onMeasure(320, 0); assert p.height == 51;
                p.valueTextView.visibility = p.valueImageView.visibility = p.valueBackupImageView.visibility = 8;
                p.drawLoading = true; p.loadingSize = 45;
                p.onMeasure(240, 0);
                int loadingTitleWidth = p.textView.width;
                assert p.start(p.textView) + loadingTitleWidth + 8 <= 230 - p.padding - p.loadingSize;
                p.drawLoading = false; p.drawLoadingProgress = 0.5f;
                p.onMeasure(240, 0); assert p.textView.width == loadingTitleWidth;
                p.drawLoadingProgress = 0;
                p.onMeasure(240, 0); assert p.textView.width > loadingTitleWidth;
            }
        }
    }
}
''')

    def test_bookmarks_fetch_only_missing_and_preserve_order(self):
        source = (JAVA / 'tw/nekomimi/nekogram/ui/BookmarksActivity.java').read_text()
        production = section(source, '    private ArrayList<MessageObject> loadBookmarkRows(',
                             '    private MessageObject createMissingMessagePlaceholder(')
        run_java('''
import java.util.*;
class Probe {
    long dialogId = -42;
    static class SparseArray<T> extends HashMap<Integer,T> { SparseArray(int n) { super(n); } }
    static class TLRPC {
        static class Media { int ttl_seconds = 3; }
        static class Message { int id; boolean ayuDeleted; Media media = new Media(); }
        static class TL_message extends Message {}
    }
    static class MessageObject {
        TLRPC.Message messageOwner;
        boolean forceAvatar, placeholder;
        MessageObject(int account, TLRPC.Message msg, boolean layout, boolean media) {
            assert account == 2; messageOwner = msg;
        }
        int getId() { return messageOwner.id; }
    }
    record Flag(boolean Bool) {}
    static class NaConfig {
        static NaConfig INSTANCE = new NaConfig();
        boolean history = true;
        Flag getEnableSaveDeletedMessages() { return new Flag(history); }
    }
    static class HistoricalMessage { int messageId = 5; }
    static class Deleted { HistoricalMessage message = new HistoricalMessage(); }
    static boolean hasAyuDeletedContent(Deleted d) { return d != null; }
    static class AyuMessageUtils {
        static void map(HistoricalMessage m, TLRPC.Message tl, int account) { tl.id = m.messageId; }
        static void mapMedia(HistoricalMessage m, TLRPC.Message tl, int account) {}
    }
    static class AyuMessagesController {
        static AyuMessagesController controller = new AyuMessagesController();
        static AyuMessagesController getInstance() { return controller; }
        int queries;
        List<Deleted> getMessagesByIds(long user, long dialog, ArrayList<Integer> ids) {
            assert user == 104 && dialog == -42 && ids.equals(List.of(7, 5));
            queries++;
            return List.of(new Deleted());
        }
    }
    MessageObject createMissingMessagePlaceholder(int account, long user, int id) {
        TLRPC.Message tl = new TLRPC.Message(); tl.id = id;
        MessageObject row = new MessageObject(account, tl, false, true); row.placeholder = true;
        return row;
    }
''' + production + '''
    public static void main(String[] args) {
        Probe p = new Probe();
        var local = new TLRPC.Message(); local.id = 3; local.ayuDeleted = true;
        var stored = new ArrayList<>(List.of(local));
        var ids = new ArrayList<>(List.of(7, 3, 5));
        var rows = p.loadBookmarkRows(2, 104, ids, stored);
        assert rows.stream().map(MessageObject::getId).toList().equals(List.of(3, 5, 7));
        assert rows.stream().allMatch(row -> row.forceAvatar);
        assert rows.get(0).messageOwner == local && local.media.ttl_seconds == 0;
        assert rows.get(1).messageOwner.ayuDeleted && rows.get(1).messageOwner.media.ttl_seconds == 3;
        assert rows.get(2).placeholder;
        p.loadBookmarkRows(2, 104, new ArrayList<>(List.of(3)), stored);
        NaConfig.INSTANCE.history = false;
        rows = p.loadBookmarkRows(2, 104, ids, null);
        assert rows.stream().allMatch(row -> row.placeholder);
        assert AyuMessagesController.controller.queries == 1;
    }
}
''')

    def test_recycled_setting_cannot_restore_a_cleared_value_on_resize(self):
        source = (JAVA / 'org/telegram/ui/Cells/TextCell.java').read_text()
        setter = section(source, '    public void setValue(CharSequence value, boolean animated)',
                         '    public void setTextAndValueAndColorfulIcon(')
        resize = section(source, '        if (lastWidth != 0 && lastWidth != width', '        int titleSpace')
        with_icon = section(source, '        valueText = value;\n        if (value != null)', '        valueTextView.setVisibility(VISIBLE);')
        pill = section(source, '            int loadingWidth = dp(loadingSize);', '            canvas.drawRoundRect(')
        run_java('''
class Probe {
    static final int VISIBLE = 0;
    static class Label {
        CharSequence text;
        void setText(CharSequence s, boolean animate) { text = s; }
        Object getPaint() { return null; }
        int getVisibility() { return VISIBLE; }
        Object getTextPaint() { return null; }
    }
    Label valueTextView = new Label(), textView = new Label();
    static class TextUtils {
        enum TruncateAt { END }
        static CharSequence ellipsize(CharSequence s, Object p, float w, TruncateAt t) {
            return s.subSequence(0, Math.min(s.length(), (int)w));
        }
    }
    static class HintView2 { static float measureCorrectly(CharSequence s, Object p) { return s.length(); } }
    static class Rect {
        int left, right;
        void set(int l, int t, int r, int b) { left = l; right = r; }
    }
    static class AndroidUtilities {
        static class Size { int x = 320; }
        static Size displaySize = new Size();
        static Rect rectTmp = new Rect();
    }
    static class LocaleController { static boolean isRTL; }
    int width = 320, lastWidth = 320, offsetFromImage = 71;
    int getMeasuredWidth() { return width; }
    static int dp(int v) { return v; }
    CharSequence valueText;
''' + setter + '''
    void resize(int width) { this.width = width;
''' + resize + '''
    }
    void bindIcon(CharSequence value) { String text = "title"; boolean animated = false;
''' + with_icon + '''
    }
    void loadingBounds(int loadingSize) { int cy = 25;
''' + pill + '''
    }
    public static void main(String[] args) {
        Probe p = new Probe();
        p.setValue("previous", false); p.setValue(null, false); p.resize(280);
        assert p.valueTextView.text.length() == 0;
        p.bindIcon("previous icon value"); p.bindIcon(null); p.resize(240);
        assert p.valueTextView.text.length() == 0;
        p.setValue("1234567890", false); p.resize(10);
        assert p.valueTextView.text.toString().equals("1234");
        p.resize(240); p.loadingBounds(45);
        assert AndroidUtilities.rectTmp.left == 174 && AndroidUtilities.rectTmp.right == 219;
        LocaleController.isRTL = true; p.loadingBounds(45);
        assert AndroidUtilities.rectTmp.left == 21 && AndroidUtilities.rectTmp.right == 66;
    }
}
''')

    def test_translation_scroll_visits_only_nearby_rows_and_album_members(self):
        source = (JAVA / 'org/telegram/ui/ChatActivity.java').read_text()
        production = section(source, '    private final Runnable checkTranslationRunnable =',
                             '    private void checkSecretMessageForLocation')
        run_java('''
import java.util.*;
class Probe {
    static class SystemClock { static long uptimeMillis() { return 10; } }
    static class RecyclerView { static final int NO_POSITION = -1; }
    record MessageObject(int id, long groupId) {
        long getGroupId() { return groupId; }
        static class GroupedMessages { long groupId; List<MessageObject> messages; }
    }
    static class View { int row; View(int row) { this.row = row; } }
    static class ChatMessageCell extends View { ChatMessageCell(int row) { super(row); } }
    static class ChatList extends ArrayList<View> {
        int getChildCount() { return size(); }
        View getChildAt(int i) { return get(i); }
        int getChildAdapterPosition(View v) { return v.row; }
    }
    static class Adapter extends ArrayList<MessageObject> {
        int messagesStartRow = 5;
        ArrayList<MessageObject> getMessages() { return this; }
    }
    ChatList chatListView = new ChatList();
    Adapter chatAdapter = new Adapter();
    Map<Long, MessageObject.GroupedMessages> groupedMessagesMap = new HashMap<>();
    Map<Integer,MessageObject> pinnedMessageObjects = new HashMap<>();
    List<Integer> checked = new ArrayList<>();
    int currentPinnedMessageId;
    long lastTranslationCheck;
    Probe getMessagesController() { return this; }
    Probe getTranslateController() { return this; }
    void checkTranslation(MessageObject message, boolean onScreen) {
        assert onScreen;
        if (message != null) checked.add(message.id());
    }
    void updateTranslateItemVisibility() {}
''' + production + '''
    public static void main(String[] args) {
        Probe p = new Probe();
        for (int i = 0; i < 2000; i++) {
            long group = (i == 993 || i == 1000 || i == 1001) ? 42 : 0;
            p.chatAdapter.add(new MessageObject(i, group));
        }
        var album = new MessageObject.GroupedMessages();
        album.groupId = 42;
        album.messages = List.of(p.chatAdapter.get(993), p.chatAdapter.get(1000), p.chatAdapter.get(1001));
        p.groupedMessagesMap.put(42L, album);
        p.pinnedMessageObjects.put(10, p.chatAdapter.get(10));
        p.currentPinnedMessageId = 10;
        p.chatListView.add(new ChatMessageCell(1005));
        p.chatListView.add(new ChatMessageCell(1007));
        p.chatListView.add(new View(1500));
        p.chatListView.add(new ChatMessageCell(-1));
        p.checkTranslationRunnable.run();
        assert p.checked.size() == 18 : p.checked;
        assert Collections.frequency(p.checked, 1000) == 1;
        assert p.checked.containsAll(List.of(993, 1002, 1009, 10));
        assert !p.checked.contains(992) && !p.checked.contains(1010);
        p.chatListView.clear();
        p.checked.clear();
        p.checkTranslationRunnable.run();
        assert p.checked.equals(List.of(10));
    }
}
''')

    def test_header_clamp_and_transition_cleanup(self):
        source = (JAVA / 'org/telegram/ui/Components/ChatAvatarContainer.java').read_text()
        bounds = section(source, '    private int getHeaderTextLeft()', '    @Override\n    protected void onMeasure')
        fade = section(source, '    private void fadeHeaderTextCopy(', '    private boolean glassMode;')
        run_java('''
import java.util.concurrent.atomic.AtomicReference;
class Probe {
    static final int VISIBLE = 0;
    int leftPadding, rightAvatarPadding;
    boolean centered, preview, glassMode;
    static int dp(float n) { return (int) Math.ceil(n); }
    boolean isCentered() { return centered; }
    boolean isPreviewMode() { return preview; }
    static class AndroidUtilities { static boolean isTablet() { return false; } }
    static class Avatar { int getVisibility() { return VISIBLE; } }
    Avatar avatarImageView = new Avatar();
    static class CubicBezierInterpolator { static final Object EASE_OUT_QUINT = new Object(); }
    static class SimpleTextView {
        Runnable callback;
        boolean attached, cancelled;
        SimpleTextView animate() { return this; }
        SimpleTextView alpha(int n) { return this; }
        SimpleTextView setDuration(int n) { return this; }
        SimpleTextView setInterpolator(Object n) { return this; }
        SimpleTextView withEndAction(Runnable end) { callback = end; return this; }
        void start() { assert attached; }
        void cancel() { cancelled = true; }
    }
    void addView(SimpleTextView view) { view.attached = true; }
    void removeView(SimpleTextView view) { view.attached = false; }
''' + bounds + fade + '''
    public static void main(String[] args) {
        Probe p = new Probe();
        p.leftPadding = 10;
        assert p.getHeaderTextWidth(32) == 0;
        assert p.getHeaderTextLeft() + p.getHeaderTextWidth(240) <= 228;
        p.centered = true; p.preview = true;
        assert p.getHeaderTextLeft() + p.getHeaderTextWidth(240) <= 186;
        AtomicReference<SimpleTextView> current = new AtomicReference<>();
        SimpleTextView a = new SimpleTextView(), b = new SimpleTextView();
        p.fadeHeaderTextCopy(current, a);
        p.fadeHeaderTextCopy(current, b);
        a.callback.run();
        assert a.cancelled && !a.attached && b.attached && current.get() == b;
        b.callback.run();
        assert !b.attached && current.get() == null;
    }
}
''')

    def test_gallery_cover_sampling_preserves_target_and_bounds_extreme_sources(self):
        source = (JAVA / 'org/telegram/ui/PhotoViewer.java').read_text()
        constant = section(source, '    private static final int GALLERY_COVER_MAX_SIDE',
                           '    private static Bitmap prepareGalleryVideoCover')
        production = section(source, '    private static int galleryCoverSampleSize(',
                             '    public static String getTempFileAbsolutePath')
        run_java('''
class Probe {
''' + constant + production + '''
    public static void main(String[] args) {
        assert galleryCoverSampleSize(6000, 4000, 1920, 1080) == 2;
        assert galleryCoverSampleSize(4000, 6000, 1080, 1920) == 2;
        assert galleryCoverSampleSize(800, 600, 800, 450) == 1;
        assert galleryCoverSampleSize(9000, 9000, 1080, 1080) == 8;
        int sample = galleryCoverSampleSize(32000, 1000, 1600, 900);
        assert 32000 / sample <= 3840;
        sample = galleryCoverSampleSize(Integer.MAX_VALUE, 1, 1, 1);
        assert sample > 0 && Integer.MAX_VALUE / sample <= 3840;
    }
}
''')
