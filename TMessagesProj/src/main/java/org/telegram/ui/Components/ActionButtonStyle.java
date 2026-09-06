package org.telegram.ui.Components;

import android.graphics.Color;

import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.blur3.drawable.color.BlurredBackgroundColorProviderThemed;

import xyz.nextalone.nagram.NaConfig;

public final class ActionButtonStyle {

    public static final int ACCENT = 0;
    public static final int NEUTRAL = 1;
    public static final int WHITE = 2;

    private ActionButtonStyle() {
    }

    public static int getCurrentStyle() {
        return NaConfig.INSTANCE.getActionButtonStyle().Int();
    }

    public static int resolveBackgroundColor(Theme.ResourcesProvider resourcesProvider) {
        int style = getCurrentStyle();
        if (style == NEUTRAL) {
            return Theme.getColor(Theme.key_chat_messagePanelBackground, resourcesProvider);
        } else if (style == WHITE) {
            return Color.WHITE;
        }
        return Theme.getColor(Theme.key_chat_messagePanelSend, resourcesProvider);
    }

    /**
     * Icon/plane color that contrasts with {@link #resolveBackgroundColor}.
     * Light fills (WHITE / NEUTRAL on light message-panel bg) must not use a white plane —
     * that combination is invisible in light theme.
     */
    public static int resolveOnFillIconColor(Theme.ResourcesProvider resourcesProvider) {
        int style = getCurrentStyle();
        if (style == NEUTRAL) {
            return Theme.getColor(Theme.key_glass_defaultIcon, resourcesProvider);
        } else if (style == WHITE) {
            return Theme.getColor(Theme.key_chat_messagePanelSend, resourcesProvider);
        }
        // ACCENT fill → white plane
        return Color.WHITE;
    }

    public static int resolveIconColor(Theme.ResourcesProvider resourcesProvider) {
        return resolveOnFillIconColor(resourcesProvider);
    }

    public static BlurredBackgroundColorProviderThemed resolveBubbleColorProvider(
            BlurredBackgroundColorProviderThemed whiteColorProvider,
            BlurredBackgroundColorProviderThemed neutralColorProvider,
            BlurredBackgroundColorProviderThemed accentColorProvider) {
        int style = getCurrentStyle();
        if (style == WHITE && whiteColorProvider != null) {
            return whiteColorProvider;
        } else if (style == NEUTRAL) {
            return neutralColorProvider;
        }
        return accentColorProvider != null ? accentColorProvider : neutralColorProvider;
    }
}
