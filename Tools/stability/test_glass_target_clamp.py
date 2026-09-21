"""Verify the dark-theme glass target clamp and the provider seam that reaches raw readers.

The clamp is exercised by copying the production members verbatim into a Java harness rather than
reimplementing the policy in Python, so a change to the threshold or the fallback chain shows up
here. The source assertions cover the part that cannot be executed without Android: that
RichEditorToolbar routes its raw Theme.key_glass_targetMain* reads through the wrapper, and that
the upstream RichEditor class is left alone.
"""
import subprocess
import tempfile
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
UI = ROOT / 'TMessagesProj/src/main/java/org/telegram/ui'
PROVIDER = UI / 'Components/blur3/drawable/color/impl/BlurredBackgroundProviderImpl.java'
WRAPPER = UI / 'Components/glass/GlassClampedResourceProvider.java'
TOOLBAR = UI / 'iv/RichEditorToolbar.java'
RICH_EDITOR = UI / 'iv/RichEditor.java'
ANDROID_UTILITIES = ROOT / 'TMessagesProj/src/main/java/org/telegram/messenger/AndroidUtilities.java'

CLAMP_START = '    private static final float BRIGHT_GLASS_TARGET_THRESHOLD'
CLAMP_END = '    public static BlurredBackgroundProvider mainTabs('

HARNESS = '''import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

class Color {
    static int red(int c) { return (c >> 16) & 0xFF; }
    static int green(int c) { return (c >> 8) & 0xFF; }
    static int blue(int c) { return c & 0xFF; }
}

class AndroidUtilities {
BRIGHTNESS
}

class Theme {
    static final int key_dialogBackground = 0;
    static final int key_windowBackgroundWhite = 1;
    static final int key_glass_targetMainTabs = 2;
    static final int key_glass_targetMainTopPanel = 3;
    static final int key_glass_tabSelected = 4;
    static final HashMap<Integer, Integer> colors = new HashMap<>();
    static boolean globalDark = true;

    static int getColor(int key) {
        final Integer color = colors.get(key);
        return color == null ? 0xFFFFFFFF : color;
    }

    static int getColor(int key, ResourcesProvider provider) {
        return provider != null ? provider.getColor(key) : getColor(key);
    }

    static boolean isCurrentThemeDark() { return globalDark; }

    interface ResourcesProvider {
        int getColor(int key);
        default boolean isDark() { return isCurrentThemeDark(); }
    }
}

class WrappedResourceProvider implements Theme.ResourcesProvider {
    Theme.ResourcesProvider resourcesProvider;

    WrappedResourceProvider(Theme.ResourcesProvider resourcesProvider) {
        this.resourcesProvider = resourcesProvider;
    }

    public int getColor(int key) {
        if (resourcesProvider == null) {
            return Theme.getColor(key);
        }
        return resourcesProvider.getColor(key);
    }
}

class BlurredBackgroundProviderImpl {
CLAMP
}

WRAPPER

public class GlassTargetClampTest {
    static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    static final int WHITE = 0xFFFFFFFF;
    static final int NIGHT_TARGET = 0xFF232324;
    static final int BOUNDARY_LOW = 0xFFB7B7B7;
    static final int BOUNDARY_HIGH = 0xFFB8B8B8;
    static final int DIALOG_DARK = 0xFF1F1F1F;
    static final int DIALOG_BRIGHT = 0xFFF0F0F0;
    static final int WINDOW_DARK = 0xFF121212;
    static final int WINDOW_BRIGHT = 0xFFEAEAEA;
    // Homepage folder-strip sample when Monet 取色 wrote the accent into glass_target*.
    static final int ACCENT_GREEN = 0xFF34C635;

    public static void main(String[] args) {
        check(AndroidUtilities.computePerceivedBrightness(BOUNDARY_HIGH) > 0.721f,
            "0xFFB8B8B8 must sit above the 0.721 cutoff");
        check(AndroidUtilities.computePerceivedBrightness(BOUNDARY_LOW) <= 0.721f,
            "0xFFB7B7B7 must sit at or below the 0.721 cutoff");
        check(AndroidUtilities.computePerceivedBrightness(ACCENT_GREEN) < 0.721f,
            "the homepage accent green must sit below the light-glass cutoff");

        Theme.colors.clear();
        Theme.colors.put(Theme.key_dialogBackground, DIALOG_DARK);
        Theme.colors.put(Theme.key_windowBackgroundWhite, WINDOW_DARK);

        check(BlurredBackgroundProviderImpl.clampGlassTargetColor(WHITE, null, true) == DIALOG_DARK,
            "bright target in a dark UI must clamp to dialogBackground");
        check(BlurredBackgroundProviderImpl.clampGlassTargetColor(NIGHT_TARGET, null, true) == NIGHT_TARGET,
            "an already dark target must survive untouched");
        check(BlurredBackgroundProviderImpl.clampGlassTargetColor(WHITE, null, false) == WHITE,
            "a light surface in a light UI stays unclamped");
        check(BlurredBackgroundProviderImpl.clampGlassTargetColor(BOUNDARY_LOW, null, true) == BOUNDARY_LOW,
            "the cutoff is strict: a colour below it stays unclamped");
        check(BlurredBackgroundProviderImpl.clampGlassTargetColor(BOUNDARY_HIGH, null, true) != BOUNDARY_HIGH,
            "a colour just above the cutoff must be clamped");

        Theme.colors.put(Theme.key_windowBackgroundWhite, WHITE);
        Theme.colors.put(Theme.key_dialogBackground, WINDOW_BRIGHT);
        check(BlurredBackgroundProviderImpl.clampGlassTargetColor(ACCENT_GREEN, null, false) == WHITE,
            "accent fill in a light UI must clamp to windowBackgroundWhite");
        check(BlurredBackgroundProviderImpl.clampGlassTargetColor(WINDOW_BRIGHT, null, false) == WINDOW_BRIGHT,
            "a bright-gray surface in a light UI stays unclamped");
        check(BlurredBackgroundProviderImpl.clampGlassTargetColor(ACCENT_GREEN, null, false)
                == BlurredBackgroundProviderImpl.clampGlassTargetColor(
                    BlurredBackgroundProviderImpl.clampGlassTargetColor(ACCENT_GREEN, null, false), null, false),
            "light accent clamping must be idempotent");

        Theme.colors.put(Theme.key_windowBackgroundWhite, WINDOW_DARK);
        Theme.colors.put(Theme.key_dialogBackground, DIALOG_BRIGHT);
        check(BlurredBackgroundProviderImpl.clampGlassTargetColor(WHITE, null, true) == WINDOW_DARK,
            "a bright dialogBackground must fall through to windowBackgroundWhite");

        Theme.colors.put(Theme.key_windowBackgroundWhite, WHITE);
        check(BlurredBackgroundProviderImpl.clampGlassTargetColor(WHITE, null, true) == NIGHT_TARGET,
            "when every fallback is bright the night glass target must win");

        final int once = BlurredBackgroundProviderImpl.clampGlassTargetColor(WHITE, null, true);
        check(BlurredBackgroundProviderImpl.clampGlassTargetColor(once, null, true) == once,
            "clamping must be idempotent");

        Theme.colors.put(Theme.key_glass_targetMainTabs, WHITE);
        Theme.colors.put(Theme.key_glass_targetMainTopPanel, WHITE);
        Theme.colors.put(Theme.key_dialogBackground, DIALOG_DARK);
        check(BlurredBackgroundProviderImpl.resolveGlassTargetColor(null, true, Theme.key_glass_targetMainTabs)
                == BlurredBackgroundProviderImpl.clampGlassTargetColor(WHITE, null, true),
            "resolveGlassTargetColor must be the raw clamp applied to the theme key");

        Theme.globalDark = true;
        Theme.colors.put(Theme.key_windowBackgroundWhite, WINDOW_BRIGHT);
        final GlassClampedResourceProvider wrapped = new GlassClampedResourceProvider(null);
        check(wrapped.getColor(Theme.key_glass_targetMainTabs) == DIALOG_DARK,
            "the wrapper must clamp glass_targetMainTabs when no delegate is present");
        check(wrapped.getColor(Theme.key_glass_targetMainTopPanel) == DIALOG_DARK,
            "the wrapper must clamp glass_targetMainTopPanel when no delegate is present");
        check(wrapped.getColor(Theme.key_windowBackgroundWhite) == WINDOW_BRIGHT,
            "the wrapper must pass unrelated keys through untouched");
        check(wrapped.getColor(Theme.key_glass_tabSelected) == WHITE,
            "the wrapper must not intercept other glass keys");

        Theme.globalDark = false;
        Theme.colors.put(Theme.key_windowBackgroundWhite, WHITE);
        check(new GlassClampedResourceProvider(null).getColor(Theme.key_glass_targetMainTabs) == WHITE,
            "with no delegate a light surface in a light UI stays unclamped");
        Theme.colors.put(Theme.key_glass_targetMainTabs, ACCENT_GREEN);
        check(new GlassClampedResourceProvider(null).getColor(Theme.key_glass_targetMainTabs) == WHITE,
            "light chrome must clamp an accent glass_target to the surface");
        Theme.colors.put(Theme.key_glass_targetMainTabs, WHITE);

        Theme.globalDark = true;
        check(new GlassClampedResourceProvider(new FixedProvider(WHITE, false))
                .getColor(Theme.key_glass_targetMainTabs) == WHITE,
            "a delegate reporting a light surface must suppress the clamp even under a dark global theme");
        Theme.globalDark = false;
        check(new GlassClampedResourceProvider(new FixedProvider(WHITE, true))
                .getColor(Theme.key_glass_targetMainTabs) == DIALOG_DARK,
            "a delegate reporting a dark surface must clamp even under a light global theme");
        Theme.globalDark = true;
        check(new GlassClampedResourceProvider(new FixedProvider(WHITE, true))
                .getColor(Theme.key_glass_targetMainTabs) == DIALOG_DARK,
            "a delegate overriding the glass key must still be clamped");

        final int delegateFallback = 0xFF333333;
        final RecordingProvider recording = new RecordingProvider(WHITE, true, delegateFallback);
        check(new GlassClampedResourceProvider(recording).getColor(Theme.key_glass_targetMainTabs) == delegateFallback,
            "the fallback chain must resolve through the delegate, not the global theme");
        check(recording.requested.contains(Theme.key_dialogBackground),
            "the clamp must read dialogBackground from the delegate");

        System.out.println("PASS: glass clamp policy, cutoff, idempotence, provider seam");
    }

    static class FixedProvider implements Theme.ResourcesProvider {
        final int glass;
        final boolean dark;

        FixedProvider(int glass, boolean dark) {
            this.glass = glass;
            this.dark = dark;
        }

        public int getColor(int key) {
            if (key == Theme.key_glass_targetMainTabs || key == Theme.key_glass_targetMainTopPanel) {
                return glass;
            }
            return Theme.getColor(key);
        }

        public boolean isDark() { return dark; }
    }

    static class RecordingProvider extends FixedProvider {
        final List<Integer> requested = new ArrayList<>();
        final int dialog;

        RecordingProvider(int glass, boolean dark, int dialog) {
            super(glass, dark);
            this.dialog = dialog;
        }

        public int getColor(int key) {
            requested.add(key);
            if (key == Theme.key_dialogBackground) return dialog;
            return super.getColor(key);
        }
    }
}
'''


def member(source, signature):
    """Return a method body verbatim, from its signature to its closing brace."""
    start = source.index(signature)
    end = source.index('\n    }\n', start) + len('\n    }\n')
    return source[start:end]


class GlassTargetClampBehaviourTest(unittest.TestCase):
    def test_clamp_and_provider_seam(self):
        provider = PROVIDER.read_text(encoding='utf-8')
        wrapper = WRAPPER.read_text(encoding='utf-8')
        utilities = ANDROID_UTILITIES.read_text(encoding='utf-8')

        clamp = provider[provider.index(CLAMP_START):provider.index(CLAMP_END)]
        self.assertNotIn('mainTabs', clamp)

        wrapper_class = wrapper[wrapper.index('public class GlassClampedResourceProvider'):]
        wrapper_class = wrapper_class.replace('public class GlassClampedResourceProvider', 'class GlassClampedResourceProvider', 1)

        harness = (HARNESS
                   .replace('BRIGHTNESS', member(utilities, '    public static float computePerceivedBrightness(int color) {'))
                   .replace('CLAMP', clamp)
                   .replace('WRAPPER', wrapper_class))

        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            path = root / 'GlassTargetClampTest.java'
            path.write_text(harness, encoding='utf-8')
            subprocess.run(['javac', '-encoding', 'UTF-8', '-d', str(root), str(path)], check=True)
            subprocess.run(['java', '-cp', str(root), 'GlassTargetClampTest'], check=True)


class GlassTargetClampSourceTest(unittest.TestCase):
    """Failures print a verdict, not the whole file, so CI logs stay readable."""

    def test_editor_toolbar_routes_raw_reads_through_the_wrapper(self):
        source = TOOLBAR.read_text(encoding='utf-8')
        self.assertTrue(
            'this.resourcesProvider = new GlassClampedResourceProvider(delegate.getResourcesProvider());' in source,
            'RichEditorToolbar must wrap its provider so the raw glass target reads get clamped')
        self.assertTrue(
            'this.resourcesProvider = delegate.getResourcesProvider();' not in source,
            'RichEditorToolbar still assigns the unwrapped provider')

    def test_upstream_rich_editor_is_left_untouched(self):
        source = RICH_EDITOR.read_text(encoding='utf-8')
        self.assertTrue('GlassClamped' not in source,
                        'RichEditor is an upstream shared class and must stay untouched')
        self.assertTrue('Theme.getColor(backgroundColorKey, resourcesProvider)' in source,
                        'RichEditor.Button is expected to still resolve its background by theme key')

    def test_clamp_policy_has_a_single_source_of_truth(self):
        source = PROVIDER.read_text(encoding='utf-8')
        body = member(source, '    public static int resolveGlassTargetColor(')
        self.assertTrue('clampGlassTargetColor(' in body,
                        'resolveGlassTargetColor must delegate to the shared clamp')
        self.assertTrue('isTooBrightForDarkGlass(' not in body,
                        'resolveGlassTargetColor duplicates the clamp policy instead of delegating')
        clamp = member(source, '    public static int clampGlassTargetColor(')
        self.assertTrue('isTooDarkForLightGlass(' in clamp,
                        'light chrome must clamp accent-dark glass_target fills')
        self.assertTrue('isTooBrightForDarkGlass(' in clamp,
                        'dark chrome must still clamp bright glass_target fills')

    def test_home_wordmark_follows_accent_not_body_text(self):
        theme = (UI / 'ActionBar/Theme.java').read_text(encoding='utf-8')
        self.assertTrue(
            'fallbackKeys.put(key_telegram_color_dialogsLogo, key_windowBackgroundWhiteBlueHeader);' in theme,
            'NixgramX wordmark must fall back to the accent header colour, not body text')
        self.assertTrue(
            'fallbackKeys.put(key_telegram_color_dialogsLogo, key_windowBackgroundWhiteBlackText);' not in theme,
            'wordmark still falls back to body text, so Monet 取色 cannot recolour it')
        self.assertTrue('themeAccentExclusionKeys.add(key_glass_targetMainTabs);' in theme,
                        'glass_targetMainTabs must not be remapped by accent 取色')
        self.assertTrue('themeAccentExclusionKeys.add(key_glass_targetMainTopPanel);' in theme,
                        'glass_targetMainTopPanel must not be remapped by accent 取色')

    def test_monet_light_defines_home_chrome_keys(self):
        theme = (ROOT / 'TMessagesProj/src/main/assets/monet_light.attheme').read_text(encoding='utf-8')
        required = {
            'glass_targetMainTabs=n1_10': 'folder strip / search field surface',
            'glass_targetMainTopPanel=n1_10': 'home top-panel surface',
            'glass_tabSelected=a1_600': 'selected bottom-tab glyph',
            'glass_tabSelectedText=a1_600': 'selected bottom-tab label',
            'glass_tabUnselected=n1_400': 'unselected bottom-tab glyph',
            'telegram_color_dialogsLogo=a1_600': 'NixgramX wordmark',
        }
        missing = [f'{key} ({why})' for key, why in required.items() if key not in theme]
        self.assertEqual(missing, [], 'monet_light.attheme is missing home chrome keys: ' + ', '.join(missing))
        self.assertNotIn('glass_targetMainTabs=a1_', theme,
                         'glass_targetMainTabs must stay a surface token, not an accent')
        self.assertNotIn('glass_targetMainTopPanel=a1_', theme,
                         'glass_targetMainTopPanel must stay a surface token, not an accent')

    def test_wrapper_intercepts_only_the_two_glass_target_keys(self):
        source = WRAPPER.read_text(encoding='utf-8')
        missing = [t for t in ('extends WrappedResourceProvider', 'super.getColor(key)',
                               'BlurredBackgroundProviderImpl.clampGlassTargetColor',
                               'Theme.key_glass_targetMainTabs', 'Theme.key_glass_targetMainTopPanel')
                   if t not in source]
        self.assertEqual(missing, [], 'GlassClampedResourceProvider is missing: ' + ', '.join(missing))
        leaked = [k for k in ('key_glass_tabSelected', 'key_glass_tabUnselected',
                              'key_glass_defaultIcon', 'key_glass_defaultText')
                  if k in source]
        self.assertEqual(leaked, [], 'the wrapper must not intercept unrelated glass keys: ' + ', '.join(leaked))


if __name__ == '__main__':
    unittest.main()
