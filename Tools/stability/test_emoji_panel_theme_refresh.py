"""Verify the emoji panel re-colours itself when the palette changes.

The glass design bakes its colours into drawables, and EmojiView is a component, not a fragment:
it has no theme descriptions, so nothing re-applies those colours on a theme switch. The panel is
built while the night theme is still active on startup (and on every automatic day/night switch),
which used to leave it with the night glyph colour - white - on the light panel.

These are source assertions plus a data invariant over the bundled themes; there is no Java harness
because the logic under test is the observer plumbing, not a colour policy.
"""
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
UI = ROOT / 'TMessagesProj/src/main/java/org/telegram/ui'
EMOJI_VIEW = UI / 'Components/EmojiView.java'
ASSETS = ROOT / 'TMessagesProj/src/main/assets'

GLASS_GLYPH_KEYS = ('glass_defaultIcon', 'glass_defaultText')


def body(source, signature, terminator='\n    }\n'):
    """Members of the class under test, anchored at the start of a line so the identically named
    overrides inside EmojiView's inner classes cannot be picked up by mistake."""
    start = source.index('\n' + signature) + 1
    end = source.index(terminator, start) + len(terminator)
    return source[start:end]


class EmojiPanelThemeRefreshTest(unittest.TestCase):

    def test_panel_observes_the_theme_change_notification(self):
        source = EMOJI_VIEW.read_text(encoding='utf-8')
        attached = body(source, '    protected void onAttachedToWindow() {')
        self.assertIn('NotificationCenter.didSetNewTheme', attached,
                      'EmojiView must observe didSetNewTheme, the notification Theme posts once the '
                      'palette has been applied')
        self.assertIn('NotificationCenter.getGlobalInstance().addObserver(this,', attached,
                      'didSetNewTheme is posted on the global NotificationCenter, so the observer '
                      'has to be registered there too')

    def test_panel_stops_observing_when_detached(self):
        source = EMOJI_VIEW.read_text(encoding='utf-8')
        detached = body(source, '    protected void onDetachedFromWindow() {')
        self.assertIn('NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.didSetNewTheme);',
                      detached,
                      'the observer must be removed again or every panel that ever opened stays registered')

    def test_theme_change_recolours_the_panel(self):
        source = EMOJI_VIEW.read_text(encoding='utf-8')
        received = body(source, '    public void didReceivedNotification(int id, int account, Object... args) {')
        self.assertIn('NotificationCenter.didSetNewTheme', received,
                      'the panel does not react to the theme-change notification')
        branch = received[received.index('NotificationCenter.didSetNewTheme'):]
        branch = branch[:branch.index('return;') + len('return;')]
        self.assertIn('updateColors();', branch,
                      'the theme-change branch must re-apply the palette through updateColors()')

    def test_update_colors_still_reapplies_every_glass_glyph_colour(self):
        source = EMOJI_VIEW.read_text(encoding='utf-8')
        update = body(source, '    public void updateColors() {')
        missing = [t for t in ('getGlassIconColor(', 'searchField.searchStateDrawable.setColor(',
                               'searchField.searchEditText.setHintTextColor(',
                               'searchField.searchEditText.setTextColor(',
                               'Theme.setEmojiDrawableColor(tabIcons[a]',
                               'Theme.setEmojiDrawableColor(stickerIcons[a]',
                               'Theme.setEmojiDrawableColor(gifIcons[a]')
                   if t not in update]
        self.assertEqual(missing, [],
                         'updateColors() no longer re-applies: ' + ', '.join(missing))


class BundledThemeGlassGlyphTest(unittest.TestCase):
    """The glass glyph colour is painted at 0.06 - 0.8 alpha, so a theme that does not declare it
    inherits key_chat_messagePanelIcons instead - a mid grey picked for the message panel, which
    turns into an invisible tint on the light panel. Every bundled theme ships the key."""

    def test_every_bundled_theme_declares_the_glass_glyph_colour(self):
        themes = sorted(ASSETS.glob('*.attheme'))
        self.assertGreaterEqual(len(themes), 9, 'the bundled theme assets went missing')
        incomplete = []
        for theme in themes:
            declared = set()
            for line in theme.read_text(encoding='utf-8').splitlines():
                line = line.strip()
                if line and not line.startswith('#') and '=' in line:
                    declared.add(line.split('=', 1)[0])
            missing = [k for k in GLASS_GLYPH_KEYS if k not in declared]
            if missing:
                incomplete.append(f'{theme.name}: {", ".join(missing)}')
        self.assertEqual(incomplete, [],
                         'these themes never declare their glass glyph colour: ' + '; '.join(incomplete))

    def test_light_and_night_glyphs_sit_on_opposite_sides(self):
        def declared(theme, key):
            for line in (ASSETS / theme).read_text(encoding='utf-8').splitlines():
                line = line.strip()
                if line.startswith(key + '='):
                    return int(line.split('=', 1)[1])
            raise AssertionError(f'{theme} does not declare {key}')

        def brightness(value):
            value &= 0xFFFFFFFF
            red, green, blue = (value >> 16) & 0xFF, (value >> 8) & 0xFF, value & 0xFF
            return (red * 0.2126 + green * 0.7152 + blue * 0.0722) / 255.0

        for theme in ('bluebubbles.attheme', 'day.attheme', 'arctic.attheme', 'monet_light.attheme'):
            self.assertLess(brightness(declared(theme, 'glass_defaultIcon')), 0.5,
                            f'{theme} paints glass on a light surface, its glyph must stay dark')
        for theme in ('night.attheme', 'darkblue.attheme', 'amoled.attheme', 'monet_dark.attheme'):
            self.assertGreater(brightness(declared(theme, 'glass_defaultIcon')), 0.5,
                               f'{theme} paints glass on a dark surface, its glyph must stay light')


if __name__ == '__main__':
    unittest.main()
