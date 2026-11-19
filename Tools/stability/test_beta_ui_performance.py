"""Exercise production cache and asynchronous diff code with deterministic host queues."""
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
