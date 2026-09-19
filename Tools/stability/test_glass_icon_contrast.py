"""Verify the glass-design glyph contrast guard used by the emoji panel.

The guard is exercised by copying the production members verbatim into a Java harness rather than
reimplementing the rule in Python, so a change to the light/night glyph constants or to the
brightness cutoff shows up here. The source assertions cover what cannot be executed without
Android: that EmojiView routes every glass glyph through the guard, and that the guard compares
against the surface those glyphs are really painted on.
"""
import subprocess
import tempfile
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
UI = ROOT / 'TMessagesProj/src/main/java/org/telegram/ui'
PROVIDER = UI / 'Components/blur3/drawable/color/impl/BlurredBackgroundProviderImpl.java'
EMOJI_VIEW = UI / 'Components/EmojiView.java'
THEME_COLORS = UI / 'ActionBar/ThemeColors.java'
ANDROID_UTILITIES = ROOT / 'TMessagesProj/src/main/java/org/telegram/messenger/AndroidUtilities.java'

GUARD_START = '    private static final int GLASS_ICON_ON_LIGHT_SURFACE'
GUARD_END = '    public static BlurredBackgroundProvider mainTabs('

HARNESS = '''class Color {
    static int red(int c) { return (c >> 16) & 0xFF; }
    static int green(int c) { return (c >> 8) & 0xFF; }
    static int blue(int c) { return c & 0xFF; }
}

class AndroidUtilities {
BRIGHTNESS
}

class BlurredBackgroundProviderImpl {
GUARD
}

public class GlassIconContrastTest {
    static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    // The light palette paints glass on a white surface, the night palette on a near-black one.
    static final int LIGHT_SURFACE = 0xFFFFFFFF;
    static final int DARK_SURFACE = 0xFF232324;

    // ThemeColors: the light palette's glass_defaultIcon / the night palette's glyph colour.
    static final int LIGHT_GLASS_ICON = 0x991B2227;
    static final int NIGHT_GLASS_ICON = 0xFFFFFFFF;

    // ThemeColors: key_chat_messagePanelIcons, the colour the glass key falls back to in every
    // palette that does not override it. This is what the emoji panel used to paint with.
    static final int PANEL_ICONS_FALLBACK = 0xFF8E959B;

    public static void main(String[] args) {
        check(AndroidUtilities.computePerceivedBrightness(PANEL_ICONS_FALLBACK) > 0.5f,
            "the fallback glyph colour is light, which is why it vanishes on a white surface");
        check(AndroidUtilities.computePerceivedBrightness(LIGHT_GLASS_ICON) <= 0.5f,
            "the light palette's glass glyph must read as dark");
        check(AndroidUtilities.computePerceivedBrightness(NIGHT_GLASS_ICON) > 0.5f,
            "the night palette's glass glyph must read as light");

        check((BlurredBackgroundProviderImpl.GLASS_ICON_ON_LIGHT_SURFACE & 0xFFFFFF)
                == (LIGHT_GLASS_ICON & 0xFFFFFF),
            "the light-surface glyph colour must mirror ThemeColors' light glass_defaultIcon");

        // Healthy palettes keep their exact current appearance.
        check(BlurredBackgroundProviderImpl.clampGlassIconColor(LIGHT_GLASS_ICON, LIGHT_SURFACE)
                == LIGHT_GLASS_ICON, "a dark glyph on a light surface must survive untouched");
        check(BlurredBackgroundProviderImpl.clampGlassIconColor(NIGHT_GLASS_ICON, DARK_SURFACE)
                == NIGHT_GLASS_ICON, "a light glyph on a dark surface must survive untouched");

        // The regression: the fallback colour paints white-on-white inside the light theme.
        check(BlurredBackgroundProviderImpl.clampGlassIconColor(PANEL_ICONS_FALLBACK, LIGHT_SURFACE)
                == BlurredBackgroundProviderImpl.GLASS_ICON_ON_LIGHT_SURFACE,
            "a light glyph on a light surface must be rewritten to the light-surface glyph colour");
        check(BlurredBackgroundProviderImpl.clampGlassIconColor(LIGHT_GLASS_ICON, DARK_SURFACE)
                == BlurredBackgroundProviderImpl.GLASS_ICON_ON_DARK_SURFACE,
            "a dark glyph on a dark surface must be rewritten to the dark-surface glyph colour");

        // An accent-tinted glyph that already contrasts is left alone.
        final int darkAccent = 0xFF0D7FCF;
        check(AndroidUtilities.computePerceivedBrightness(darkAccent) <= 0.5f,
            "the accent used below must read as dark");
        check(BlurredBackgroundProviderImpl.clampGlassIconColor(darkAccent, LIGHT_SURFACE) == darkAccent,
            "a dark accent glyph on a light surface must not be rewritten");

        // A light glyph on a light surface is unreadable no matter where the colour came from.
        final int lightAccent = 0xFFFFD700;
        check(BlurredBackgroundProviderImpl.clampGlassIconColor(lightAccent, LIGHT_SURFACE)
                == BlurredBackgroundProviderImpl.GLASS_ICON_ON_LIGHT_SURFACE,
            "any light glyph on a light surface must be rewritten");

        final int once = BlurredBackgroundProviderImpl.clampGlassIconColor(PANEL_ICONS_FALLBACK, LIGHT_SURFACE);
        check(BlurredBackgroundProviderImpl.clampGlassIconColor(once, LIGHT_SURFACE) == once,
            "clamping must be idempotent");

        System.out.println("PASS: glass glyph contrast guard");
    }
}
'''


def member(source, signature):
    """Return a method body verbatim, from its signature to its closing brace."""
    start = source.index(signature)
    end = source.index('\n    }\n', start) + len('\n    }\n')
    return source[start:end]


def strip_comments(source):
    """Drop javadoc/line comments so assertions can talk about code, not prose."""
    out = []
    in_block = False
    for line in source.splitlines():
        if in_block:
            if '*/' in line:
                in_block = False
            continue
        if line.strip().startswith('/*'):
            if '*/' not in line:
                in_block = True
            continue
        if line.strip().startswith('//') or line.strip().startswith('*'):
            continue
        out.append(line.split('//')[0])
    return '\n'.join(out)


class GlassIconContrastBehaviourTest(unittest.TestCase):
    def test_guard_keeps_contrast_on_every_palette(self):
        provider = PROVIDER.read_text(encoding='utf-8')
        utilities = ANDROID_UTILITIES.read_text(encoding='utf-8')

        guard = provider[provider.index(GUARD_START):provider.index(GUARD_END)]
        self.assertNotIn('key_', strip_comments(guard),
                         'the guard must be a pure colour policy, not a theme-key reader')
        self.assertNotIn('Theme.', strip_comments(guard),
                         'the guard must not read the theme, it only compares two colours')
        # The harness asserts on the constants, so they have to be visible outside the class.
        guard = guard.replace('private static final int GLASS_ICON', 'static final int GLASS_ICON')

        harness = (HARNESS
                   .replace('BRIGHTNESS', member(utilities, '    public static float computePerceivedBrightness(int color) {'))
                   .replace('GUARD', guard))

        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            path = root / 'GlassIconContrastTest.java'
            path.write_text(harness, encoding='utf-8')
            subprocess.run(['javac', '-encoding', 'UTF-8', '-d', str(root), str(path)], check=True)
            subprocess.run(['java', '-cp', str(root), 'GlassIconContrastTest'], check=True)


class GlassIconContrastSourceTest(unittest.TestCase):
    """Failures print a verdict, not the whole file, so CI logs stay readable."""

    def test_emoji_view_routes_glass_glyphs_through_the_guard(self):
        source = EMOJI_VIEW.read_text(encoding='utf-8')
        body = member(source, '    private int getGlassIconColor(float alpha) {')
        missing = [t for t in ('BlurredBackgroundProviderImpl.clampGlassIconColor(',
                               'Theme.key_glass_defaultIcon',
                               'Theme.key_glass_targetMainTopPanel',
                               'ColorUtils.setAlphaComponent(')
                   if t not in body]
        self.assertEqual(missing, [], 'EmojiView.getGlassIconColor is missing: ' + ', '.join(missing))
        self.assertNotIn('getGlassIconColor(null', body,
                         'the guard must never be called with a null surface colour')

    def test_guard_compares_against_the_surface_the_pill_is_painted_on(self):
        source = PROVIDER.read_text(encoding='utf-8')
        pill = member(source, '    public static BlurredBackgroundProvider emojiViewButton(')
        self.assertIn('Theme.key_glass_targetMainTopPanel', pill,
                      'the emoji pill must keep being painted from key_glass_targetMainTopPanel, '
                      'which is the surface the glyph guard compares against')

    def test_guard_constants_mirror_the_light_palette(self):
        colors = THEME_COLORS.read_text(encoding='utf-8')
        self.assertIn('defaultColors[key_glass_defaultIcon] = 0x991B2227;', colors,
                      'ThemeColors must keep declaring a dark glass glyph for the light palette, '
                      'otherwise the guard and the palette disagree')


if __name__ == '__main__':
    unittest.main()
