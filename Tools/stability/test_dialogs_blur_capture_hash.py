"""Home-list glass capture must hash so idle dispatchDraw can skip a full list recapture."""
import unittest
from pathlib import Path

from test_beta_ui_performance import run_java, section

ROOT = Path(__file__).resolve().parents[2]
DIALOGS = ROOT / 'TMessagesProj/src/main/java/org/telegram/ui/DialogsActivity.java'
CAPTURE = ROOT / 'TMessagesProj/src/main/java/org/telegram/ui/Components/blur3/capture/IBlur3Capture.java'
SUPPRESSOR = ROOT / 'TMessagesProj/src/main/java/org/telegram/ui/Components/blur3/DownscaleScrollableNoiseSuppressor.java'
SEARCH = ROOT / 'TMessagesProj/src/main/java/org/telegram/ui/Components/SearchViewPager.java'


class DialogsBlurCaptureHashTest(unittest.TestCase):
    def test_default_capture_hash_is_unsupported(self):
        source = CAPTURE.read_text(encoding='utf-8')
        self.assertIn('default void captureCalculateHash', source)
        self.assertIn('builder.unsupported()', source)

    def test_suppressor_skips_when_hash_matches(self):
        source = SUPPRESSOR.read_text(encoding='utf-8')
        self.assertIn('if (!builder.isUnsupported() && sourcePart.lastHash == hash', source)

    def test_dialogs_capture_implements_hash(self):
        source = DIALOGS.read_text(encoding='utf-8')
        self.assertIn('iBlur3Capture = new IBlur3Capture()', source)
        self.assertIn('public void captureCalculateHash(IBlur3Hash builder, RectF position)', source)
        self.assertIn('hashDialogsBlurCapture(builder, position)', source)
        self.assertIn('list.computeVerticalScrollOffset()', source)
        self.assertNotIn('iBlur3Capture = (canvas, position) ->', source)

    def test_on_scrolled_does_not_recapture_on_zero_delta(self):
        # 13.2.2: the list is registered with GlassEngine.addScrolledView; onScrolled no longer
        # invalidates the blur itself, so a zero-delta scroll cannot force a recapture
        source = DIALOGS.read_text(encoding='utf-8')
        self.assertIn('glassEngine.addScrolledView(viewPage.listView)', source)
        self.assertNotIn('blur3_InvalidateBlur', source)
        self.assertIn('private void invalidateBlurCaptures(int flags)', source)
        self.assertIn('findFirstVisibleItemPosition()', source)

    def test_search_keeps_keyboard_offset_and_hashes_the_visible_page(self):
        dialogs = DIALOGS.read_text(encoding='utf-8')
        search = SEARCH.read_text(encoding='utf-8')
        translate = section(dialogs, '    private void applySearchPagerTranslation(', '\n    @Override\n    public void finishFragment()')
        alpha = section(dialogs, '    private int getSearchCaptureAlpha()', '    private void hashDialogsBlurCapture(')
        self.assertIn('} else if (child == searchViewPager) {\n                    applySearchPagerTranslation(searchViewPager);', dialogs)
        self.assertIn('canvas.clipRect(0, getSearchPagesTopPadding(), getWidth(), getHeight());', dialogs)
        self.assertIn('searchViewPager.setPagesPadding(getSearchPagesTopPadding(),', dialogs)
        self.assertIn('ThemeBind.backgroundColor(searchViewPager, Theme.key_windowBackgroundWhite', dialogs)
        self.assertNotIn('searchViewPager.setBackground(null)', dialogs)
        self.assertIn('Blur3Utils.hashRelativeParent(searchViewPager, builder, position', dialogs)
        layers = section(search, '    @Override\n    public void capture(Canvas canvas, RectF position)', '    private class ViewPagerAdapter').replace('@Nullable ', '')
        run_java('''
import java.util.*;
class RectF { int y; void set(RectF other) { y = other.y; } }
class Canvas { final List<String> layers = new ArrayList<>(); }
class IBlur3Hash { final List<String> layers = new ArrayList<>(); }
class View {
    static final int VISIBLE = 0, GONE = 8;
    int visibility = VISIBLE;
    float alpha = 1, y;
    float getAlpha() { return alpha; }
    int getVisibility() { return visibility; }
    void setTranslationY(float y) { this.y = y; }
}
class RecyclerListView extends View { String name; RecyclerListView(String name) { this.name = name; } }
class Blur3Utils {
    static RectF sharedCaptureRect = new RectF();
    static void captureRelativeParent(RecyclerListView list, Canvas canvas, RectF position, View view, Object parent, int alpha) {
        if (alpha > 0) canvas.layers.add(list.name + ":" + alpha + ":" + position.y);
        sharedCaptureRect.y -= 20;
    }
    static void hashRelativeParent(RecyclerListView list, IBlur3Hash builder, RectF position, View view, Object parent, int alpha) {
        if (alpha > 0) builder.layers.add(list.name + ":" + alpha + ":" + position.y);
    }
}
class Probe {
    static final int VISIBLE = View.VISIBLE;
    static class Comment { boolean popup; boolean isPopupShowing() { return popup; } }
    static class Filtered extends View { RecyclerListView recyclerListView = new RecyclerListView("filtered"); }
    Comment commentView = new Comment();
    View searchViewPager = new View(), searchContainer = new View();
    Filtered noMediaFiltersSearchView = new Filtered();
    RecyclerListView chats = new RecyclerListView("chats");
    float panTranslationY = -120, searchViewPagerTranslationY = 5;
    View[] getViewPages() { return new View[]{searchContainer, null}; }
    RecyclerListView getRecyclerViewFromPage(View page) { return chats; }
''' + translate + alpha + layers.replace('    @Override\n', '') + '''
    public static void main(String[] args) {
        Probe p = new Probe();
        p.applySearchPagerTranslation(p.searchViewPager);
        assert p.searchViewPager.y == -115;
        p.commentView.popup = true;
        p.applySearchPagerTranslation(p.searchViewPager);
        assert p.searchViewPager.y == 5;
        assert p.getSearchCaptureAlpha() == 255;
        p.searchViewPager.visibility = View.GONE;
        assert p.getSearchCaptureAlpha() == 0;

        p.noMediaFiltersSearchView.alpha = .5f;
        RectF shared = Blur3Utils.sharedCaptureRect;
        shared.y = 90;
        Canvas first = new Canvas();
        p.capture(first, shared);
        assert first.layers.equals(List.of("chats:128:90", "filtered:128:90"));
        shared.y = 90;
        IBlur3Hash hash = new IBlur3Hash();
        p.captureCalculateHash(hash, shared);
        assert hash.layers.equals(first.layers);
        p.noMediaFiltersSearchView.alpha = 1;
        Canvas filtered = new Canvas();
        p.capture(filtered, shared);
        assert filtered.layers.equals(List.of("filtered:255:90"));
    }
}
''')
