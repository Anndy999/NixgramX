package tw.nekomimi.nekogram.drawer;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewParent;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.INavigationLayout;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.DialogsActivity;
import org.telegram.ui.LaunchActivity;
import org.telegram.ui.MainTabsActivity;
import org.telegram.ui.ProxyListActivity;
import org.telegram.ui.ThemeActivity;

import tw.nekomimi.nekogram.helpers.NixNavigationConfig;

/**
 * Overlay drawer. Edge swipe uses candidate → slop confirmation → takeover.
 * Drawer OFF must leave DrawerLayoutContainer equivalent to the pre-port stub.
 */
public class DrawerContainer extends FrameLayout {

    private static final float OPEN_SLOP_CM = 0.2f;
    private static final float VERTICAL_DOMINANCE = 1.0f;

    private final FrameLayout drawerPanel;
    private final DrawerHeaderView headerView;
    private final DrawerAccountPickerView accountPickerView;
    private final DrawerMenuView menuView;
    private final Paint scrimPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path panelClipPath = new Path();
    private final float[] panelRadii = new float[8];

    private int drawerWidth;
    private float progress;
    private boolean isOpen;
    private boolean tracking;
    private boolean edgeGestureCandidate;
    private boolean drawerTakenOver;
    private float downX;
    private float downY;
    private float startProgress;
    private VelocityTracker velocityTracker;

    public DrawerContainer(Context context) {
        super(context);
        setVisibility(GONE);
        setTag("drawer_container");
        setWillNotDraw(false);

        drawerWidth = calculateDrawerWidth();
        drawerPanel = new FrameLayout(context);
        drawerPanel.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        boolean rtl = NixDrawerEdgeHelper.isRtl(this);
        addView(drawerPanel, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT,
                rtl ? Gravity.RIGHT : Gravity.LEFT));
        FrameLayout.LayoutParams panelParams = (FrameLayout.LayoutParams) drawerPanel.getLayoutParams();
        panelParams.width = drawerWidth;
        drawerPanel.setLayoutParams(panelParams);
        drawerPanel.setTranslationX(closedTranslation());

        LinearLayout content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);
        headerView = new DrawerHeaderView(context);
        content.addView(headerView, new LinearLayout.LayoutParams(LayoutHelper.MATCH_PARENT, dp(160)));

        accountPickerView = new DrawerAccountPickerView(context);
        content.addView(accountPickerView, new LinearLayout.LayoutParams(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        ScrollView scrollView = new ScrollView(context);
        menuView = new DrawerMenuView(context);
        scrollView.addView(menuView, new FrameLayout.LayoutParams(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        content.addView(scrollView, new LinearLayout.LayoutParams(LayoutHelper.MATCH_PARENT, 0, 1f));
        drawerPanel.addView(content, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        headerView.setOnProfile(() -> openMenuItem(MainMenuItem.PROFILE.getId()));
        headerView.setOnAccounts(() -> {
            accountPickerView.toggleExpand();
            headerView.setChevronExpanded(accountPickerView.isExpanded());
        });
        accountPickerView.setOnAccountSelected(() -> closeDrawer(true));
        headerView.setChevronExpanded(accountPickerView.isExpanded());
        headerView.setOnTheme(() -> {
            Theme.ThemeInfo target = Theme.isCurrentThemeDark() ? Theme.getCurrentTheme() : Theme.getCurrentNightTheme();
            if (target != null) {
                Theme.applyTheme(target, !Theme.isCurrentThemeDark());
            }
        });
        headerView.setOnThemeLongPress(() -> presentFromDrawer(new ThemeActivity(ThemeActivity.THEME_TYPE_NIGHT)));
        headerView.setOnProxy(() -> presentFromDrawer(new ProxyListActivity()));
    }

    public void dispose() {
        cancelTracking();
        progress = 0f;
        isOpen = false;
        accountPickerView.dispose();
        setVisibility(GONE);
    }

    public boolean isDrawerOpen() {
        return progress > 0.001f || tracking;
    }

    public void openDrawer(boolean animated) {
        if (!NixNavigationConfig.isDrawerEnabled()) {
            closeDrawer(false);
            return;
        }
        refreshContents();
        setVisibility(VISIBLE);
        isOpen = true;
        if (animated) {
            animateProgress(1f);
        } else {
            setProgress(1f);
        }
    }

    public void closeDrawer(boolean animated) {
        isOpen = false;
        if (animated && getVisibility() == VISIBLE) {
            animateProgress(0f);
        } else {
            setProgress(0f);
            setVisibility(GONE);
        }
    }

    public boolean handleBackPressed() {
        if (isDrawerOpen()) {
            closeDrawer(true);
            return true;
        }
        return false;
    }

    public boolean hasEdgeCandidate() {
        return edgeGestureCandidate && !drawerTakenOver;
    }

    public boolean handleEdgeSwipeIntercept(MotionEvent ev) {
        if (!NixNavigationConfig.isDrawerEnabled() || getVisibility() == VISIBLE && progress > 0.001f) {
            return false;
        }
        int action = ev.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN) {
            edgeGestureCandidate = false;
            drawerTakenOver = false;
            tracking = false;
            downX = ev.getX();
            downY = ev.getY();
            startProgress = progress;
            if (canStartEdgeCandidate(ev)) {
                edgeGestureCandidate = true;
                obtainVelocity().addMovement(ev);
            }
            return false;
        }
        if (!edgeGestureCandidate) {
            return false;
        }
        obtainVelocity().addMovement(ev);
        if (action == MotionEvent.ACTION_MOVE) {
            float dx = ev.getX() - downX;
            float dy = ev.getY() - downY;
            if (shouldCancelCandidate(dx, dy)) {
                cancelCandidate();
                return false;
            }
            if (shouldTakeOver(dx, dy)) {
                drawerTakenOver = true;
                tracking = true;
                setVisibility(VISIBLE);
                refreshContents();
                return true;
            }
            return false;
        }
        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            cancelCandidate();
            return false;
        }
        return false;
    }

    public boolean handleEdgeSwipeTouch(MotionEvent ev) {
        if (!NixNavigationConfig.isDrawerEnabled()) {
            return false;
        }
        if (getVisibility() == VISIBLE && progress > 0.001f && !drawerTakenOver && !tracking) {
            return handleOpenDrawerTouch(ev);
        }
        if (!drawerTakenOver && !tracking) {
            return false;
        }
        obtainVelocity().addMovement(ev);
        int action = ev.getActionMasked();
        if (action == MotionEvent.ACTION_MOVE) {
            float dx = ev.getX() - downX;
            float width = Math.max(1, drawerWidth);
            float signed = NixDrawerEdgeHelper.isRtl(this) ? -dx : dx;
            setProgress(Math.max(0f, Math.min(1f, startProgress + signed / width)));
            return true;
        }
        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            finishTracking();
            return true;
        }
        return true;
    }

    private boolean handleOpenDrawerTouch(MotionEvent ev) {
        int action = ev.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN) {
            downX = ev.getX();
            downY = ev.getY();
            startProgress = progress;
            tracking = false;
            return true;
        }
        if (action == MotionEvent.ACTION_MOVE) {
            float dx = ev.getX() - downX;
            if (!tracking && Math.abs(dx) >= AndroidUtilities.getPixelsInCM(OPEN_SLOP_CM, true)) {
                tracking = true;
            }
            if (tracking) {
                float width = Math.max(1, drawerWidth);
                float signed = NixDrawerEdgeHelper.isRtl(this) ? -dx : dx;
                setProgress(Math.max(0f, Math.min(1f, startProgress + signed / width)));
            }
            return true;
        }
        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            if (tracking) {
                finishTracking();
            } else if (action == MotionEvent.ACTION_UP) {
                float panelEdge = NixDrawerEdgeHelper.isRtl(this)
                        ? getWidth() - drawerPanel.getTranslationX()
                        : drawerPanel.getTranslationX() + drawerWidth;
                boolean outside = NixDrawerEdgeHelper.isRtl(this)
                        ? ev.getX() < getWidth() - drawerWidth
                        : ev.getX() > panelEdge;
                if (outside) {
                    closeDrawer(true);
                }
            }
            tracking = false;
            return true;
        }
        return true;
    }

    private boolean canStartEdgeCandidate(MotionEvent ev) {
        if (!NixNavigationConfig.isDrawerEnabled()) {
            return false;
        }
        ViewParent parent = getParent();
        if (!(parent instanceof org.telegram.ui.ActionBar.DrawerLayoutContainer)) {
            return false;
        }
        INavigationLayout navigationLayout = ((org.telegram.ui.ActionBar.DrawerLayoutContainer) parent).getParentActionBarLayout();
        if (navigationLayout == null || navigationLayout.getFragmentStack().size() != 1 || !navigationLayout.allowSwipe()) {
            return false;
        }
        BaseFragment last = getContentFragment();
        if (!(last instanceof DialogsActivity)) {
            return false;
        }
        DialogsActivity dialogs = (DialogsActivity) last;
        if (!dialogs.canOpenDrawer()) {
            return false;
        }
        return NixDrawerEdgeHelper.isInStartEdge(this, ev.getX());
    }

    private boolean shouldCancelCandidate(float dx, float dy) {
        float absDx = Math.abs(dx);
        float absDy = Math.abs(dy);
        float slop = AndroidUtilities.getPixelsInCM(OPEN_SLOP_CM, true);
        return absDy >= slop && absDy > absDx * VERTICAL_DOMINANCE;
    }

    private boolean shouldTakeOver(float dx, float dy) {
        float slop = AndroidUtilities.getPixelsInCM(OPEN_SLOP_CM, true);
        if (Math.abs(dx) < slop) {
            return false;
        }
        if (Math.abs(dx) <= Math.abs(dy)) {
            return false;
        }
        return NixDrawerEdgeHelper.isOpeningHorizontal(this, dx);
    }

    private void cancelCandidate() {
        edgeGestureCandidate = false;
        drawerTakenOver = false;
        tracking = false;
        recycleVelocity();
    }

    private void finishTracking() {
        velocityTracker.computeCurrentVelocity(1000);
        float vx = velocityTracker.getXVelocity();
        if (NixDrawerEdgeHelper.isRtl(this)) {
            vx = -vx;
        }
        boolean open = progress > 0.5f || vx > 400;
        isOpen = open;
        animateProgress(open ? 1f : 0f);
        cancelCandidate();
    }

    private void cancelTracking() {
        cancelCandidate();
    }

    private void animateProgress(float target) {
        ValueAnimator animator = ValueAnimator.ofFloat(progress, target);
        animator.setDuration(220);
        animator.setInterpolator(CubicBezierInterpolator.DEFAULT);
        animator.addUpdateListener(a -> setProgress((float) a.getAnimatedValue()));
        animator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                if (target <= 0.001f) {
                    setVisibility(GONE);
                }
            }
        });
        animator.start();
    }

    private void setProgress(float value) {
        progress = Math.max(0f, Math.min(1f, value));
        drawerPanel.setTranslationX(closedTranslation() * (1f - progress));
        invalidate();
        if (NixNavigationConfig.isImmersiveDrawerEnabled()) {
            translateNavigation(progress);
        } else if (progress <= 0.001f) {
            translateNavigation(0f);
        } else {
            translateNavigation(progress * 0.3f);
        }
    }

    private float closedTranslation() {
        return NixDrawerEdgeHelper.isRtl(this) ? drawerWidth : -drawerWidth;
    }

    private void translateNavigation(float factor) {
        ViewParent parent = getParent();
        if (!(parent instanceof org.telegram.ui.ActionBar.DrawerLayoutContainer)) {
            return;
        }
        INavigationLayout navigationLayout = ((org.telegram.ui.ActionBar.DrawerLayoutContainer) parent).getParentActionBarLayout();
        if (navigationLayout == null || navigationLayout.getView() == null) {
            return;
        }
        float signed = NixDrawerEdgeHelper.isRtl(this) ? -drawerWidth * factor : drawerWidth * factor;
        navigationLayout.getView().setTranslationX(signed);
    }

    private void refreshContents() {
        headerView.updateUserInfo();
        accountPickerView.rebuild();
        BaseFragment fragment = getContentFragment();
        int account = fragment != null ? fragment.getCurrentAccount() : org.telegram.messenger.UserConfig.selectedAccount;
        menuView.rebuild(fragment, account, () -> closeDrawer(true));
    }

    private void openMenuItem(int id) {
        BaseFragment fragment = getContentFragment();
        if (fragment == null) return;
        MainMenuHelper.MenuItemInfo item = MainMenuHelper.resolve(id, fragment, fragment.getCurrentAccount());
        if (item != null && item.onClick != null) {
            item.onClick.run();
            closeDrawer(true);
        }
    }

    private void presentFromDrawer(BaseFragment fragment) {
        BaseFragment current = getContentFragment();
        if (current != null) {
            current.presentFragment(fragment);
            closeDrawer(true);
        }
    }

    private BaseFragment getContentFragment() {
        ViewParent parent = getParent();
        if (!(parent instanceof org.telegram.ui.ActionBar.DrawerLayoutContainer)) {
            return null;
        }
        INavigationLayout navigationLayout = ((org.telegram.ui.ActionBar.DrawerLayoutContainer) parent).getParentActionBarLayout();
        if (navigationLayout == null) {
            return null;
        }
        BaseFragment last = navigationLayout.getLastFragment();
        if (last instanceof MainTabsActivity) {
            return ((MainTabsActivity) last).getCurrentVisibleFragment();
        }
        return last;
    }

    private int calculateDrawerWidth() {
        // Exteraless geometry: min(300dp, screen width - 56dp).  No arbitrary
        // percentage means split-screen and narrow devices retain a usable scrim.
        return Math.min(dp(300), Math.max(0, AndroidUtilities.displaySize.x - dp(56)));
    }

    private VelocityTracker obtainVelocity() {
        if (velocityTracker == null) {
            velocityTracker = VelocityTracker.obtain();
        }
        return velocityTracker;
    }

    private void recycleVelocity() {
        if (velocityTracker != null) {
            velocityTracker.recycle();
            velocityTracker = null;
        }
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        if (progress > 0f) {
            int alpha;
            int color;
            if (NixNavigationConfig.isImmersiveDrawerEnabled()) {
                alpha = (int) (progress * 160);
                color = Theme.getColor(Theme.key_windowBackgroundWhite);
                scrimPaint.setColor(Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color)));
            } else {
                scrimPaint.setColor(Color.argb((int) (progress * 102), 0, 0, 0));
            }
            canvas.drawRect(0, 0, getWidth(), getHeight(), scrimPaint);
        }
        super.dispatchDraw(canvas);
    }

    @Override
    protected boolean drawChild(Canvas canvas, View child, long drawingTime) {
        if (child != drawerPanel || NixNavigationConfig.isImmersiveDrawerEnabled()) {
            return super.drawChild(canvas, child, drawingTime);
        }
        final float radius = dp(24);
        final boolean rtl = NixDrawerEdgeHelper.isRtl(this);
        // Only the edge facing the content is rounded, matching Exteraless' ordinary drawer.
        if (rtl) {
            panelRadii[0] = radius; panelRadii[1] = radius; panelRadii[2] = 0; panelRadii[3] = 0;
            panelRadii[4] = 0; panelRadii[5] = 0; panelRadii[6] = radius; panelRadii[7] = radius;
        } else {
            panelRadii[0] = 0; panelRadii[1] = 0; panelRadii[2] = radius; panelRadii[3] = radius;
            panelRadii[4] = radius; panelRadii[5] = radius; panelRadii[6] = 0; panelRadii[7] = 0;
        }
        RectF bounds = new RectF(child.getX(), child.getY(), child.getX() + child.getWidth(), child.getY() + child.getHeight());
        panelClipPath.rewind();
        panelClipPath.addRoundRect(bounds, panelRadii, Path.Direction.CW);
        int save = canvas.save();
        canvas.clipPath(panelClipPath);
        boolean result = super.drawChild(canvas, child, drawingTime);
        canvas.restoreToCount(save);
        return result;
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        drawerWidth = calculateDrawerWidth();
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) drawerPanel.getLayoutParams();
        lp.width = drawerWidth;
        lp.gravity = NixDrawerEdgeHelper.isRtl(this) ? Gravity.RIGHT : Gravity.LEFT;
        drawerPanel.setLayoutParams(lp);
        setProgress(progress);
    }
}
