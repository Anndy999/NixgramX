package tw.nekomimi.nekogram.helpers;

import xyz.nextalone.nagram.NaConfig;

/**
 * Single source of truth for NixgramX bottom-navigation mode and drawer flags.
 * Old HideBottomNavigationBar is derived from {@link #MODE_HIDE} only.
 */
public final class NixNavigationConfig {

    public static final int MODE_SHOW = 0;
    public static final int MODE_HIDE = 1;
    public static final int MODE_FLOATING = 2;

    private NixNavigationConfig() {
    }

    public static int getBottomNavigationMode() {
        int mode = NaConfig.INSTANCE.getBottomNavigationMode().Int();
        if (mode < MODE_SHOW || mode > MODE_FLOATING) {
            return MODE_SHOW;
        }
        return mode;
    }

    public static void setBottomNavigationMode(int mode) {
        if (mode < MODE_SHOW || mode > MODE_FLOATING) {
            mode = MODE_SHOW;
        }
        NaConfig.INSTANCE.getBottomNavigationMode().setConfigInt(mode);
        NaConfig.INSTANCE.getHideBottomNavigationBar().setConfigBool(mode == MODE_HIDE);
    }

    public static boolean isBottomNavigationHidden() {
        return getBottomNavigationMode() == MODE_HIDE;
    }

    public static boolean isBottomNavigationFloating() {
        return getBottomNavigationMode() == MODE_FLOATING;
    }

    public static boolean isBottomNavigationVisible() {
        return !isBottomNavigationHidden();
    }

    /** Show occupies dock height. Hide and Floating do not. */
    public static boolean occupiesBottomDock() {
        return getBottomNavigationMode() == MODE_SHOW;
    }

    public static int getFloatingListPaddingDp() {
        if (!isBottomNavigationFloating()) {
            return 0;
        }
        return MainTabsHelper.getMainTabsHeightWithMargins();
    }

    public static boolean isDrawerEnabled() {
        return NaConfig.INSTANCE.getNavigationDrawer().Bool();
    }

    public static void setDrawerEnabled(boolean enabled) {
        NaConfig.INSTANCE.getNavigationDrawer().setConfigBool(enabled);
    }

    public static boolean isImmersiveDrawerEnabled() {
        return isDrawerEnabled() && NaConfig.INSTANCE.getImmersiveDrawerAnimation().Bool();
    }

    public static void setImmersiveDrawerEnabled(boolean enabled) {
        NaConfig.INSTANCE.getImmersiveDrawerAnimation().setConfigBool(enabled);
    }

    public static String getMainMenuLayout() {
        final String value = NaConfig.INSTANCE.getMainMenuLayout().String();
        return value != null ? value : "";
    }

    public static void setMainMenuLayout(String layout) {
        NaConfig.INSTANCE.getMainMenuLayout().setConfigString(layout != null ? layout : "");
    }
}
