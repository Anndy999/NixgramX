"""Regression checks for the layout and glass work done during keyboard animations."""
import unittest

from test_beta_ui_performance import JAVA, run_java, section

UI = JAVA / 'org/telegram/ui'


class ChatImeRenderingTest(unittest.TestCase):
    def test_identical_platform_insets_do_not_repeat_layout_work(self):
        source = (UI / 'Components/inset/WindowInsetsStateHolder.java').read_text()
        callbacks = section(source, '    public void onAnimatedInsetsChanged(', '    @Override\n    public void onAnimatedInsetsFinished()')
        run_java('''
class View {}
class WindowInsetsCompat {
    static class Type { static int ime() { return 1; } }
    static class Size { int bottom; Size(int bottom) { this.bottom = bottom; } }
    final Size size;
    WindowInsetsCompat(int bottom) { size = new Size(bottom); }
    Size getInsets(int type) { return size; }
}
class Probe {
    int animatedImeInset, updates;
    boolean hasReportedAnimatedImeInset;
    Runnable onUpdateListener = () -> updates++;
    WindowInsetsCompat filterInsets(WindowInsetsCompat i) { return i; }
''' + callbacks.replace('    @Override\n', '') + '''
    public static void main(String[] args) {
        Probe p = new Probe();
        View v = new View();
        p.onAnimatedInsetsStarted();
        p.onAnimatedInsetsChanged(v, new WindowInsetsCompat(0));
        for (int i = 0; i < 10; i++) p.onAnimatedInsetsChanged(v, new WindowInsetsCompat(100));
        p.onAnimatedInsetsChanged(v, new WindowInsetsCompat(250));
        assert p.updates == 3 && p.animatedImeInset == 250;
        p.onAnimatedInsetsStarted();
        p.onAnimatedInsetsChanged(v, new WindowInsetsCompat(250));
        p.onAnimatedInsetsChanged(v, null);
        assert p.updates == 5 && p.animatedImeInset == 0;
    }
}
''')

    def test_hidden_backgrounds_release_their_capture_bounds(self):
        source = (UI / 'Components/chat/ChatInputViewsContainer.java').read_text()
        draw = section(source, '    protected void dispatchDraw(@NonNull Canvas canvas) {\n        // The glass engine',
                       '    @Override\n    protected boolean drawChild(')
        touch = source[source.index('    private boolean captured;'):].rstrip().removesuffix('}')
        self.assertNotIn('underKeyboardPath', source)
        run_java('''
class Canvas {}
class Rect {
    int left, top, right, bottom;
    void set(int l, int t, int r, int b) { left = l; top = t; right = r; bottom = b; }
    void inset(int dx, int dy) { left += dx; top += dy; right -= dx; bottom -= dy; }
    void offset(int dx, int dy) { left += dx; right += dx; top += dy; bottom += dy; }
    boolean isEmpty() { return left >= right || top >= bottom; }
    boolean contains(int x, int y) { return x >= left && x < right && y >= top && y < bottom; }
}
class Background {
    Rect bounds = new Rect();
    int draws;
    void setBounds(Rect rect) { setBounds(rect.left, rect.top, rect.right, rect.bottom); }
    void setBounds(int l, int t, int r, int b) { bounds.set(l, t, r, b); }
    void draw(Canvas canvas) { draws++; }
    int getAlpha() { return 255; }
    Rect getBounds() { return bounds; }
}
class MotionEvent {
    static final int ACTION_DOWN = 0, ACTION_UP = 1, ACTION_CANCEL = 3;
    final int x, y;
    MotionEvent(int x, int y) { this.x = x; this.y = y; }
    int getAction() { return ACTION_DOWN; }
    int getX() { return x; }
    int getY() { return y; }
}
class BaseView {
    void dispatchDraw(Canvas canvas) {}
    public boolean onTouchEvent(MotionEvent event) { return false; }
}
class Probe extends BaseView {
    boolean drawInputBackground = true, needDrawInAppKeyboard;
    Rect tmpRect = new Rect();
    Background blurredBackgroundDrawable = new Background(), underKeyboardBackgroundDrawable = new Background();
    int INPUT_KEYBOARD_RADIUS = 29, inputBubbleHeightRound = 44, currentBlurredHeight = 250;
    float inputBubbleOffsetLeft, inputBubbleOffsetRight, bubbleInputTranlationY, imeBottomInset = 200;
    int dp(int v) { return v; }
    int getMeasuredHeight() { return 700; }
    int getMeasuredWidth() { return 360; }
''' + draw.replace('@NonNull ', '') + touch + '''
    public static void main(String[] args) {
        Probe p = new Probe();
        Canvas c = new Canvas();
        p.dispatchDraw(c);
        assert p.blurredBackgroundDrawable.draws == 1;
        assert p.underKeyboardBackgroundDrawable.bounds.isEmpty();
        p.drawInputBackground = false;
        p.needDrawInAppKeyboard = true;
        p.dispatchDraw(c);
        assert p.blurredBackgroundDrawable.bounds.isEmpty() && p.blurredBackgroundDrawable.draws == 1;
        assert p.underKeyboardBackgroundDrawable.bounds.top == 500 && p.underKeyboardBackgroundDrawable.draws == 1;
        p.needDrawInAppKeyboard = false;
        p.dispatchDraw(c);
        assert p.underKeyboardBackgroundDrawable.bounds.isEmpty() && p.underKeyboardBackgroundDrawable.draws == 1;
        assert p.onTouchEvent(new MotionEvent(100, 460));
        assert !p.onTouchEvent(new MotionEvent(100, 600));
    }
}
''')
