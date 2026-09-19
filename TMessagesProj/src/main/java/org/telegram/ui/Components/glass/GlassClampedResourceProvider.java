package org.telegram.ui.Components.glass;

import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.blur3.drawable.color.impl.BlurredBackgroundProviderImpl;
import org.telegram.ui.WrappedResourceProvider;

/**
 * A {@link Theme.ResourcesProvider} that applies the dark-theme glass target clamp to reads of
 * {@link Theme#key_glass_targetMainTabs} and {@link Theme#key_glass_targetMainTopPanel}.
 *
 * <p>{@link BlurredBackgroundProviderImpl#resolveGlassTargetColor} guards the glass fills drawn by
 * BlurredBackgroundProvider, ChatAttachAlert and FragmentFloatingButton. Widgets that read the
 * glass target key straight off Theme bypass that guard — RichEditorToolbar paints the rich editor
 * pills, the back/undo/redo buttons and the formatting button row from the raw key — so on a dark
 * theme that still carries the light default 0xFFFFFFFF those widgets come out solid white sitting
 * on the dark key_windowBackgroundWhite pill behind them.
 *
 * <p>Wrapping the provider gives every one of those reads the clamped value without touching the
 * widget code, so a single seam covers all of them.
 */
public class GlassClampedResourceProvider extends WrappedResourceProvider {

    private final Theme.ResourcesProvider delegate;

    public GlassClampedResourceProvider(Theme.ResourcesProvider delegate) {
        super(delegate);
        this.delegate = delegate;
    }

    @Override
    public int getColor(int key) {
        final int color = super.getColor(key);
        if (key != Theme.key_glass_targetMainTabs && key != Theme.key_glass_targetMainTopPanel) {
            return color;
        }
        return BlurredBackgroundProviderImpl.clampGlassTargetColor(color, delegate, isDark());
    }

    /**
     * WrappedResourceProvider leaves isDark() to the interface default, which reports the global
     * theme. Forwarding to the wrapped provider keeps the answer identical to what callers saw
     * before the wrap, and is the more accurate signal for the clamp when the delegate is a
     * deliberately dark provider while the global theme is light.
     */
    @Override
    public boolean isDark() {
        return delegate == null ? Theme.isCurrentThemeDark() : delegate.isDark();
    }
}
