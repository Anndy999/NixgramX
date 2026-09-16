package tw.nekomimi.nekogram.drawer;

import android.view.View;

import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;

/**
 * Dynamic Drawer edge strip. Gesture insets first; one fallback when 3-button nav
 * reports 0. Do not scatter magic dp values elsewhere.
 */
public final class NixDrawerEdgeHelper {

    /** Used only when system gesture insets are 0 (typically 3-button navigation). */
    private static final float FALLBACK_EDGE_CM = 0.4f;

    private NixDrawerEdgeHelper() {
    }

    public static boolean isRtl(View view) {
        if (view != null && view.getLayoutDirection() == View.LAYOUT_DIRECTION_RTL) {
            return true;
        }
        return LocaleController.isRTL;
    }

    public static float getEdgeWidthPx(View view) {
        float fallback = AndroidUtilities.getPixelsInCM(FALLBACK_EDGE_CM, true);
        if (view == null) {
            return fallback;
        }
        WindowInsetsCompat insets = ViewCompat.getRootWindowInsets(view);
        if (insets == null) {
            return fallback;
        }
        boolean rtl = isRtl(view);
        Insets gestures = insets.getInsets(WindowInsetsCompat.Type.systemGestures());
        Insets mandatory = insets.getInsets(WindowInsetsCompat.Type.mandatorySystemGestures());
        int edge = Math.max(rtl ? gestures.right : gestures.left, rtl ? mandatory.right : mandatory.left);
        if (edge <= 0) {
            return fallback;
        }
        return edge;
    }

    public static boolean isInStartEdge(View view, float x) {
        if (view == null) {
            return false;
        }
        float edge = getEdgeWidthPx(view);
        if (isRtl(view)) {
            return x >= view.getWidth() - edge;
        }
        return x <= edge;
    }

    public static boolean isOpeningHorizontal(View view, float dx) {
        if (isRtl(view)) {
            return dx < 0f;
        }
        return dx > 0f;
    }
}
