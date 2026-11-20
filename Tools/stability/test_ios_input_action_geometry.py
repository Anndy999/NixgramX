"""Regression coverage for iOS-style chat-input action geometry."""

from pathlib import Path
import re
import unittest

from test_beta_ui_performance import run_java, section


ROOT = Path(__file__).resolve().parents[2]
CHAT_ENTER_VIEW = ROOT / "TMessagesProj/src/main/java/org/telegram/ui/Components/ChatActivityEnterView.java"
GLASS_DRAWABLE = ROOT / "TMessagesProj/src/main/java/org/telegram/ui/Components/blur3/drawable/BlurredBackgroundDrawable.java"


class IosInputActionGeometryTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.source = CHAT_ENTER_VIEW.read_text(encoding="utf-8")

    def test_ios_action_visual_is_40dp_inside_the_existing_touch_target(self):
        self.assertIn("private static final int IOS_ACTION_BUBBLE_SIZE_DP = 40;", self.source)
        self.assertIn(
            "private static final int IOS_ACTION_BUBBLE_RADIUS_DP = IOS_ACTION_BUBBLE_SIZE_DP / 2;",
            self.source,
        )

        start = self.source.index("    private void setIosActionBubbleBounds(BlurredBackgroundDrawable bubble, View view)")
        end = self.source.index("\n    public float getVisualHeight()", start)
        helpers = self.source[start:end]
        self.assertEqual(helpers.count("final int targetSize = dp(DEFAULT_HEIGHT);"), 2)
        self.assertEqual(helpers.count("final int visualSize = dp(IOS_ACTION_BUBBLE_SIZE_DP);"), 2)
        self.assertEqual(helpers.count("final int inset = (targetSize - visualSize) / 2;"), 2)
        self.assertIn("final int actionLeft = view.getRight() - targetSize;", helpers)
        self.assertIn("final int actionLeft = width - targetSize;", helpers)

    def test_all_right_hand_ios_action_states_share_the_same_bounds(self):
        for call in (
            "setIosActionBubbleBounds(sendBubbleDrawable, child);",
            "setIosActionBubbleBounds(voiceBubbleDrawable, getMeasuredWidth(), getMeasuredHeight());",
            "setIosActionBubbleBounds(bubble, view);",
        ):
            self.assertIn(call, self.source)

        self.assertNotIn(
            "sendBubbleDrawable.setBounds(child.getRight() - dp(DEFAULT_HEIGHT)",
            self.source,
        )
        self.assertNotIn(
            "voiceBubbleDrawable.setBounds(getMeasuredWidth() - dp(DEFAULT_HEIGHT)",
            self.source,
        )

    def test_action_bubble_radius_matches_the_40dp_visual(self):
        for name in (
            "sendBubbleDrawable",
            "voiceBubbleDrawable",
            "expandStickersBubbleDrawable",
            "cancelBotBubbleDrawable",
        ):
            self.assertIn(
                f"{name}.setRadius(dp(IOS_ACTION_BUBBLE_RADIUS_DP));",
                self.source,
            )

    def test_channel_editor_reserves_the_entire_right_button_group(self):
        calendar = section(self.source, '    private void updateScheduledButtonLayoutParams()',
                           '    private ValueAnimator animateScheduledTranslationX(')
        margins = section(self.source, '    private int getRightAttachmentExtent()',
                          '    public void startMessageTransition()')
        input_measure = section(self.source, 'FrameLayout frameLayout = messageEditTextContainer =',
                                '                super.onMeasure(widthMeasureSpec, heightMeasureSpec);')
        self.assertIn('updateFieldRight(lastAttachVisible);', input_measure)
        constants = '\n'.join(re.search(rf'static final int {name} = \d+;', self.source).group()
                              for name in ('DEFAULT_HEIGHT', 'RIGHT_CLUSTER_GAP_DP', 'CAPSULE_INSET_DP', 'NO_ICON_TEXT_INSET_DP'))
        run_java('''
import java.util.*;
class MarginLayoutParams { int width, leftMargin, rightMargin; }
class FrameLayout { static class LayoutParams extends MarginLayoutParams {} }
class View {
    static final int VISIBLE = 0, GONE = 8;
    int visibility;
    FrameLayout.LayoutParams params = new FrameLayout.LayoutParams();
    View(int width, int right) { params.width = width; params.rightMargin = right; }
    int getVisibility() { return visibility; }
    float getAlpha() { return 1; }
    MarginLayoutParams getLayoutParams() { return params; }
    void setLayoutParams(MarginLayoutParams p) { params = (FrameLayout.LayoutParams) p; }
    int width() { return params.width; }
}
class Probe {
    static final int VISIBLE = View.VISIBLE, GONE = View.GONE;
''' + constants + '''
    static class Group extends View {
        List<View> children = new ArrayList<>();
        Group() { super(0, DEFAULT_HEIGHT); }
        int getChildCount() { return children.size(); }
        View getChildAt(int i) { return children.get(i); }
    }
    static class Message { boolean needResendWhenEdit() { return false; } }
    Message editingMessageObject;
    boolean isStories, isLiveComment, suggestButtonVisible;
    View messageEditText = new View(0, 0), attachButton = new View(DEFAULT_HEIGHT, 8);
    View sendButton = new View(DEFAULT_HEIGHT, 0), emojiButton, recordedAudioPanel, doneButton;
    View scheduledButton = new View(DEFAULT_HEIGHT, 0);
    Group attachLayout = new Group();
    int lastAttachVisible, fieldRightDp, emojiRightDp, iosGapDp = 8;
    boolean isIosButtonPlacement() { return false; }
    boolean isIosInputAppearance() { return true; }
    int dp(int value) { return value; }
''' + calendar + margins + '''
    public static void main(String[] args) {
        Probe p = new Probe();
        View bell = new View(44, 0);
        p.attachLayout.children.add(bell);
        p.scheduledButton.visibility = GONE;
        p.updateFieldRight(1);
        assert p.messageEditText.params.rightMargin == 92;

        bell.visibility = GONE;
        p.scheduledButton.visibility = VISIBLE;
        p.updateFieldRight(1);
        assert p.scheduledButton.params.rightMargin == 52;
        assert p.messageEditText.params.rightMargin == 100;

        View bot = new View(36, 0);
        p.attachLayout.children.add(bot);
        p.updateFieldRight(1);
        assert p.scheduledButton.params.rightMargin == 80;
        assert p.messageEditText.params.rightMargin == 128;

        p.attachLayout.visibility = GONE;
        p.scheduledButton.visibility = GONE;
        p.updateFieldRight(1);
        assert p.messageEditText.params.rightMargin == 56;
        p.attachButton.visibility = GONE;
        p.updateFieldRight(0);
        assert p.messageEditText.params.rightMargin == 20;
    }
}
''')

    def test_input_glass_switch_picks_solid_colors_and_restores_the_global_glass_mode(self):
        source = GLASS_DRAWABLE.read_text(encoding='utf-8')
        mode = section(source, '    private boolean isLiquidGlassEffectEnabled()',
                       '    @Override\n    protected void onGlassSettingsChanged(')
        fill = section(source, '        int themeColor = colorProvider.getBackgroundColor();',
                       '        shadowColor = colorProvider.getShadowColor();')
        run_java('''
class Build { static class VERSION { static int SDK_INT = 33; } }
class ColorUtils { static int setAlphaComponent(int color, int alpha) { return (color & 0x00ffffff) | (alpha << 24); } }
class Probe {
    static class GlassSource {}
    static class GlassSettings { boolean isBlurEnabled = true, isLiquidGlassEnabled = true; }
    static class Provider {
        int color = 0xc2ffffff;
        int getBackgroundColor() { return color; }
    }
    static final GlassSource SOLID_COLOR_SOURCE = new GlassSource();
    final GlassSource glassSource = new GlassSource(), frostedSource = new GlassSource(), staticSource = new GlassSource();
    final GlassSettings settings = new GlassSettings();
    final Provider colorProvider = new Provider();
    int backgroundColor, invalidations;
    boolean solidBackground, implInvalidated, liquidGlassEffectAllowed = true;
    GlassSettings getGlassSettings() { return settings; }
    void invalidateSelf() { invalidations++; }
    void updateColors() {
''' + fill + '''
    }
''' + mode + '''
    public static void main(String[] args) {
        Probe p = new Probe();
        assert p.pickSource() == p.glassSource;
        p.setSolidBackground(true);
        assert p.implInvalidated && p.invalidations == 1;
        assert p.pickSource() == SOLID_COLOR_SOURCE && !p.isLiquidGlassEffectEnabled();
        assert p.backgroundColor == 0xffffffff;
        p.setSolidBackground(true);
        assert p.invalidations == 1;
        p.colorProvider.color = 0xc21c2230;
        p.updateColors();
        assert p.backgroundColor == 0xff1c2230;

        p.setSolidBackground(false);
        assert p.backgroundColor == 0xc21c2230 && p.pickSource() == p.glassSource;
        Build.VERSION.SDK_INT = 31;
        assert p.pickSource() == p.frostedSource;
        p.settings.isBlurEnabled = false;
        assert p.pickSource() == p.staticSource;
    }
}
''')


if __name__ == "__main__":
    unittest.main()
