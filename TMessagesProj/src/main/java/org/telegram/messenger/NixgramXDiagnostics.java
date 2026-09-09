package org.telegram.messenger;

import android.app.Activity;
import android.os.Build;
import android.os.Handler;
import android.util.Log;
import android.view.FrameMetrics;
import android.view.Window;

import java.util.Arrays;

/**
 * Temporary, allocation-free-on-hot-path diagnostics for the 1284 investigation.
 * Keep no content or account identifiers here: callers supply only generic lanes.
 */
public final class NixgramXDiagnostics {
    public static final String HEADER_TAG = "NixgramXHeaderGeometry";
    public static final String PERF_TAG = "NixgramXPerf";
    private static final long HEADER_SETTLE_MS = 500;
    private static final int MAX_FRAMES = 512;

    private NixgramXDiagnostics() {}

    public static final class Header {
        private final android.view.View owner;
        private String lane;
        private boolean pending;
        private boolean emitted;
        private final Runnable emitRunnable = this::emitIfSettled;

        // All state sampled from dispatchDraw is primitive-only.
        public int barW, barH, centerL, centerT, centerR, centerB, centerPL, centerPT, centerPR, centerPB;
        public int sourceL, sourceT, sourceR, sourceB, backL, backT, backR, backB, backPL, backPT, backPR, backPB;
        public int menuL, menuT, menuR, menuB, menuPL, menuPT, menuPR, menuPB, menuWidth, visibleItems, largestItemWidth;
        public int rightOffset, leftDefault, rightDefault, titleL, titleT, titleR, titleB, avatarL, avatarT, avatarR, avatarB;
        public float radius0, radius2, radius4, radius6, shaderRadius0, shaderRadius2, shaderRadius4, shaderRadius6;
        public float avatarFactor, avatarWidthFactor, searchFactor, actionModeFactor;
        public boolean avatarAttached, forum, clipChildren, renderNodeClipToOutline;

        public Header(android.view.View owner) {
            this.owner = owner;
        }

        public void setLane(String lane) {
            if (lane == null || lane.equals(this.lane)) return;
            this.lane = lane;
            pending = false;
            emitted = false;
            owner.removeCallbacks(emitRunnable);
        }

        public boolean isEnabled() {
            return lane != null && !emitted;
        }

        public void changed() {
            if (lane == null || emitted) return;
            pending = true;
            owner.removeCallbacks(emitRunnable);
            owner.postDelayed(emitRunnable, HEADER_SETTLE_MS);
        }

        private void emitIfSettled() {
            if (!pending || emitted || lane == null) return;
            pending = false;
            emitted = true;
            Log.i(HEADER_TAG, "lane=" + lane
                + " bar=" + barW + "x" + barH
                + " centerRaw=[" + centerL + ',' + centerT + ',' + centerR + ',' + centerB + ']'
                + " centerPadded=[" + centerPL + ',' + centerPT + ',' + centerPR + ',' + centerPB + ']'
                + " centerSource=[" + sourceL + ',' + sourceT + ',' + sourceR + ',' + sourceB + ']'
                + " centerRadii=[" + radius0 + ',' + radius2 + ',' + radius4 + ',' + radius6 + ']'
                + " centerShaderRadii=[" + shaderRadius0 + ',' + shaderRadius2 + ',' + shaderRadius4 + ',' + shaderRadius6 + ']'
                + " backRaw=[" + backL + ',' + backT + ',' + backR + ',' + backB + ']'
                + " backPadded=[" + backPL + ',' + backPT + ',' + backPR + ',' + backPB + ']'
                + " menuRaw=[" + menuL + ',' + menuT + ',' + menuR + ',' + menuB + ']'
                + " menuPadded=[" + menuPL + ',' + menuPT + ',' + menuPR + ',' + menuPB + ']'
                + " menuWidth=" + menuWidth + " visibleMenuItems=" + visibleItems + " largestMenuItemWidth=" + largestItemWidth
                + " rightOffset=" + rightOffset + " leftDefault=" + leftDefault + " rightDefault=" + rightDefault
                + " avatarAttached=" + avatarAttached + " avatarFactor=" + avatarFactor + " avatarWidthFactor=" + avatarWidthFactor
                + " titleBounds=[" + titleL + ',' + titleT + ',' + titleR + ',' + titleB + ']'
                + " avatarBounds=[" + avatarL + ',' + avatarT + ',' + avatarR + ',' + avatarB + ']'
                + " searchFactor=" + searchFactor + " actionModeFactor=" + actionModeFactor + " forum=" + forum
                + " clipChildren=" + clipChildren + " renderNodeClipToOutline=" + renderNodeClipToOutline);
        }
    }

    private static boolean transitionActive;
    private static boolean closing;
    private static String peerType;
    private static String theme;
    private static long attachCalls, attachTotalNs, attachMaxNs, attachUpdated;
    private static long suppressorHashNs, suppressorSourceParts, suppressorUnchanged, suppressorChanged, suppressorUnsupported;
    private static long suppressorCaptureCalls, suppressorCaptureNs, suppressorRebuildCalls, suppressorRebuildNs;
    private static long viewerBlurOpenCalls, viewerBlurCloseCalls, viewerBlurTotalNs, viewerBlurMaxNs;
    private static final long[] frameDurations = new long[MAX_FRAMES];
    private static int frameCount;
    private static long slowFrames, maxFrameNs, drawNs, syncNs, gpuNs;
    private static Window metricsWindow;
    private static final Window.OnFrameMetricsAvailableListener frameListener = (window, frameMetrics, dropCountSinceLastInvocation) -> {
        if (!transitionActive) return;
        final long total = frameMetrics.getMetric(FrameMetrics.TOTAL_DURATION);
        if (frameCount < MAX_FRAMES) frameDurations[frameCount++] = total;
        if (total > 16_666_667L) slowFrames++;
        if (total > maxFrameNs) maxFrameNs = total;
        drawNs += frameMetrics.getMetric(FrameMetrics.DRAW_DURATION);
        syncNs += frameMetrics.getMetric(FrameMetrics.SYNC_DURATION);
        gpuNs += frameMetrics.getMetric(FrameMetrics.GPU_DURATION);
    };

    public static void startPhotoViewerTransition(Activity activity, String peer, String currentTheme) {
        finishPhotoViewerTransition();
        transitionActive = true;
        closing = false;
        peerType = peer;
        theme = currentTheme;
        attachCalls = attachTotalNs = attachMaxNs = attachUpdated = 0;
        suppressorHashNs = suppressorSourceParts = suppressorUnchanged = suppressorChanged = suppressorUnsupported = 0;
        suppressorCaptureCalls = suppressorCaptureNs = suppressorRebuildCalls = suppressorRebuildNs = 0;
        viewerBlurOpenCalls = viewerBlurCloseCalls = viewerBlurTotalNs = viewerBlurMaxNs = 0;
        frameCount = 0;
        slowFrames = maxFrameNs = drawNs = syncNs = gpuNs = 0;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && activity != null) {
            metricsWindow = activity.getWindow();
            metricsWindow.addOnFrameMetricsAvailableListener(frameListener, new Handler(activity.getMainLooper()));
        }
    }

    public static void markPhotoViewerClosing() { if (transitionActive) closing = true; }
    public static void startPhotoViewerCloseTransition(Activity activity) {
        if (!transitionActive && peerType != null) {
            startPhotoViewerTransition(activity, peerType, theme);
        }
        if (transitionActive) closing = true;
    }
    public static boolean isPhotoViewerTransitionActive() { return transitionActive; }
    public static long startTimer() { return transitionActive ? System.nanoTime() : 0L; }
    public static void recordAttachBlur(long startNs, boolean updated) {
        if (startNs == 0L || !transitionActive) return;
        final long elapsed = System.nanoTime() - startNs;
        attachCalls++; attachTotalNs += elapsed; if (elapsed > attachMaxNs) attachMaxNs = elapsed; if (updated) attachUpdated++;
    }
    public static void recordSuppressorHash(long elapsedNs, boolean unchanged, boolean unsupported) {
        if (!transitionActive) return;
        suppressorHashNs += elapsedNs; suppressorSourceParts++;
        if (unchanged) suppressorUnchanged++; else suppressorChanged++;
        if (unsupported) suppressorUnsupported++;
    }
    public static void recordSuppressorCapture(long elapsedNs) { if (transitionActive) { suppressorCaptureCalls++; suppressorCaptureNs += elapsedNs; } }
    public static void recordSuppressorRebuild(long elapsedNs) { if (transitionActive) { suppressorRebuildCalls++; suppressorRebuildNs += elapsedNs; } }
    public static void recordPhotoViewerBlur(long startNs) {
        if (startNs == 0L || !transitionActive) return;
        final long elapsed = System.nanoTime() - startNs;
        if (closing) viewerBlurCloseCalls++; else viewerBlurOpenCalls++;
        viewerBlurTotalNs += elapsed; if (elapsed > viewerBlurMaxNs) viewerBlurMaxNs = elapsed;
    }

    public static void finishPhotoViewerTransition() {
        if (!transitionActive) return;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && metricsWindow != null) metricsWindow.removeOnFrameMetricsAvailableListener(frameListener);
        metricsWindow = null;
        long p50 = 0, p95 = 0;
        if (frameCount > 0) {
            final long[] sorted = Arrays.copyOf(frameDurations, frameCount);
            Arrays.sort(sorted);
            p50 = sorted[(frameCount - 1) / 2];
            p95 = sorted[(int) Math.ceil(frameCount * .95d) - 1];
        }
        Log.i(PERF_TAG, "transition=" + (closing ? "CLOSE" : "OPEN") + " peer=" + peerType + " theme=" + theme
            + " attachBlur[calls=" + attachCalls + ",totalMs=" + ms(attachTotalNs) + ",maxMs=" + ms(attachMaxNs) + ",updated=" + attachUpdated + ']'
            + " suppressor[hashMs=" + ms(suppressorHashNs) + ",sourceParts=" + suppressorSourceParts + ",unchanged=" + suppressorUnchanged + ",changed=" + suppressorChanged + ",unsupported=" + suppressorUnsupported + ",captureCalls=" + suppressorCaptureCalls + ",captureMs=" + ms(suppressorCaptureNs) + ",rebuildCalls=" + suppressorRebuildCalls + ",rebuildMs=" + ms(suppressorRebuildNs) + ']'
            + " viewerBlur[openCalls=" + viewerBlurOpenCalls + ",closeCalls=" + viewerBlurCloseCalls + ",totalMs=" + ms(viewerBlurTotalNs) + ",maxMs=" + ms(viewerBlurMaxNs) + ']'
            + " frames[count=" + frameCount + ",p50Ms=" + ms(p50) + ",p95Ms=" + ms(p95) + ",maxMs=" + ms(maxFrameNs) + ",slow=" + slowFrames + ",drawMs=" + ms(drawNs) + ",syncMs=" + ms(syncNs) + ",gpuMs=" + ms(gpuNs) + ']');
        transitionActive = false;
    }
    private static long ms(long ns) { return ns / 1_000_000L; }
}
