"""Verify the emoji panel re-colours its tabs when the palette changes.

The glass pill background is a live BlurredBackgroundDrawable, so it follows the theme.
The labels on it (表情符号 / GIF / 贴纸) and the category-row icons above the search field
bake getGlassIconColor() into TextView.setTextColor / ImageView colour filters and only
re-read that colour when the selected tab changes. Switching light↔dark with the panel
open therefore left the previous palette sitting on the new surface: dark-on-dark after
light→dark, white-on-white after dark→light.

EmojiView has to implement Theme.Colorable so ActionBarLayout.globallyUpdateColors()
calls updateColors() on a theme switch (the same path ChatActivityEnterView uses), and
that method has to push the refresh into PagerSlidingTabStrip and every EmojiTabButton.
"""
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
UI = ROOT / 'TMessagesProj/src/main/java/org/telegram/ui'
EMOJI_VIEW = UI / 'Components/EmojiView.java'
ENTER_VIEW = UI / 'Components/ChatActivityEnterView.java'
REFRESH_HOST = UI / 'ActionBar/ActionBarLayout.java'
TAB_STRIP = UI / 'Components/PagerSlidingTabStrip.java'
CATEGORY_STRIP = UI / 'Components/EmojiTabsStrip.java'


def body(source, signature, terminator='\n    }\n'):
    """Members of the class under test, anchored at the start of a line so identically named
    overrides inside inner classes cannot be picked up by mistake."""
    start = source.index('\n' + signature) + 1
    end = source.index(terminator, start) + len(terminator)
    return source[start:end]


def declarations(source, marker):
    start = source.index(marker)
    return source[start:source.index('{', start)]


class EmojiPanelThemeRefreshTest(unittest.TestCase):

    def test_panel_is_colour_aware(self):
        source = EMOJI_VIEW.read_text(encoding='utf-8')
        declared = declarations(source, 'public class EmojiView extends FrameLayout implements')
        self.assertIn('Theme.Colorable', declared,
                      'EmojiView bakes glass colours into child views, so it has to implement '
                      'Theme.Colorable or globallyUpdateColors never reaches it')
        self.assertIn('@Override\n    public void updateColors()', source,
                      'updateColors() must override Theme.Colorable.updateColors()')

    def test_the_sibling_component_still_uses_the_same_mechanism(self):
        source = ENTER_VIEW.read_text(encoding='utf-8')
        declared = declarations(source, 'public class ChatActivityEnterView extends FrameLayout implements')
        self.assertIn('Theme.Colorable', declared,
                      'ChatActivityEnterView is why the input row keeps its colours; if it is '
                      'no longer Colorable, EmojiView cannot rely on the same host walk')

    def test_the_refresh_host_walks_the_view_tree(self):
        source = REFRESH_HOST.read_text(encoding='utf-8')
        walk = body(source, '    private void globallyUpdateColors(View view) {')
        self.assertIn('instanceof Theme.Colorable', walk,
                      'globallyUpdateColors must keep dispatching to Colorable views')
        self.assertIn('updateColors();', walk,
                      'globallyUpdateColors must keep calling updateColors()')
        self.assertIn('getChildAt(i)', walk,
                      'globallyUpdateColors must keep recursing: EmojiView is a descendant of '
                      'the fragment root, not the root itself')

    def test_update_colors_pushes_refresh_into_both_tab_strips(self):
        source = EMOJI_VIEW.read_text(encoding='utf-8')
        update = body(source, '    public void updateColors() {')
        self.assertIn('typeTabs.updateColors()', update,
                      'bottom 表情符号/GIF/贴纸 labels live in typeTabs, not in tabIcons')
        self.assertIn('emojiTabs.updateColors()', update,
                      'the category-row icons live in emojiTabs')


class BottomTabsAndCategoryStripRefreshTest(unittest.TestCase):

    def test_bottom_tabs_recolour_themselves(self):
        source = TAB_STRIP.read_text(encoding='utf-8')
        self.assertIn('\n    public void updateColors()', source,
                      'PagerSlidingTabStrip must expose updateColors() so EmojiView can refresh '
                      'the 表情符号/GIF/贴纸 labels when the palette changes')
        update = body(source, '    public void updateColors() {')
        self.assertIn('setSelected(', update,
                      're-running setSelected() is what reapplies TextTab.setTextColor')
        self.assertIn('child.invalidate()', update,
                      'icon-tab ripples are software-layer drawables; the parent invalidate '
                      'does not guarantee each ImageView redraws')
        self.assertIn('Math.min(currentPosition', update,
                      'currentPosition can outlive tabsContainer after notifyDataSetChanged')

    def test_bottom_tabs_distinguish_glass_from_themed_palette(self):
        source = TAB_STRIP.read_text(encoding='utf-8')
        glyph = body(source, '    private int resolveGlyphColor(float selectedProgress) {')
        self.assertIn('if (glassDesign)', glyph,
                      'PagerSlidingTabStrip used to always paint glass_defaultIcon, which '
                      'falls back to chat_messagePanelIcons on non-glass themes')
        self.assertIn('getGlassIconColor(', glyph)
        self.assertIn('key_glass_defaultIcon', source,
                      'glass branch must still read Theme.key_glass_defaultIcon')
        self.assertIn('key_chat_emojiBottomPanelIcon', glyph,
                      'non-glass bottom labels must use the emoji bottom-panel key, not the '
                      'message-input fallback')
        self.assertIn('key_chat_emojiPanelIconSelected', glyph)
        ctor = source[source.index('public PagerSlidingTabStrip(Context context, Theme.ResourcesProvider resourcesProvider, boolean glassDesign)'):]
        self.assertIn('this.glassDesign = glassDesign', ctor.split('{', 1)[1][:400])

    def test_emoji_view_passes_glass_flag_into_bottom_tabs(self):
        source = EMOJI_VIEW.read_text(encoding='utf-8')
        self.assertIn('new PagerSlidingTabStrip(context, resourcesProvider, glassDesign)', source,
                      'typeTabs is created for both glass and non-glass; the flag has to '
                      'travel into PagerSlidingTabStrip or the branch is dead')

    def test_category_strip_recolours_every_tab_button(self):
        source = CATEGORY_STRIP.read_text(encoding='utf-8')
        update = body(source, '    public void updateColors() {')
        self.assertIn('contentView.getChildCount()', update,
                      'EmojiTabsStrip.updateColors() used to refresh only recentTab, so the '
                      'clock/smile/cat row stayed on the previous palette across a theme switch')
        self.assertIn('EmojiTabButton', update,
                      'every EmojiTabButton (not just recentTab) has to get updateColor()')
        self.assertIn('updateColor()', update)


if __name__ == '__main__':
    unittest.main()
