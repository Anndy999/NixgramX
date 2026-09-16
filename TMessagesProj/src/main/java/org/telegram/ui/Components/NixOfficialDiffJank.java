package org.telegram.ui.Components;

import android.os.SystemClock;
import android.util.Log;

import org.telegram.messenger.ChatObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ChatActivity;

/**
 * Private diagnostic telemetry for comparing the attach-preview path by chat kind.
 * It deliberately stores no peer or media identifiers and emits only transition-level logs.
 */
public final class NixOfficialDiffJank {
    public static final String TAG = "NixOfficialDiffJank";

    private static boolean active;
    private static String chatType = "UNKNOWN";
    private static long attachOpenStartedAt;
    private static long viewerOpenStartedAt;
    private static long morphStartedAt;
    private static boolean firstDrawLogged;
    private static boolean glassLogged;
    private static int glassInvalidateCount;
    private static long glassInvalidateTotalMs;
    private static long maxGlassInvalidateMs;
    private static int headerInvalidateCount;
    private static int actionBarInvalidateCount;
    private static int chatAvatarInvalidateCount;
    private static int hdrUpdateCount;
    private static long hdrUpdateTotalMs;
    private static long windowAddMs;
    private static long viewerMorphMs;
    private static int animationInProgress;

    private NixOfficialDiffJank() {
    }

    public static void beginAttachOpen(ChatActivity activity) {
        active = true;
        chatType = classify(activity);
        attachOpenStartedAt = now();
        viewerOpenStartedAt = 0;
        morphStartedAt = 0;
        firstDrawLogged = false;
        glassLogged = false;
        glassInvalidateCount = 0;
        glassInvalidateTotalMs = 0;
        maxGlassInvalidateMs = 0;
        headerInvalidateCount = 0;
        actionBarInvalidateCount = 0;
        chatAvatarInvalidateCount = 0;
        hdrUpdateCount = 0;
        hdrUpdateTotalMs = 0;
        windowAddMs = 0;
        viewerMorphMs = 0;
        animationInProgress = 0;
        log("ATTACH_OPEN_BEGIN", 0);
    }

    public static void markAttachFirstDraw() {
        if (active && !firstDrawLogged) {
            firstDrawLogged = true;
            log("ATTACH_FIRST_DRAW", elapsed(attachOpenStartedAt));
        }
    }

    public static void markAttachReady() {
        if (active) {
            log("ATTACH_READY", elapsed(attachOpenStartedAt));
        }
    }

    public static void viewerOpenBegin() {
        if (active) {
            viewerOpenStartedAt = now();
            log("VIEWER_OPEN_BEGIN", 0);
        }
    }

    public static long windowAddBegin() {
        if (!active) return 0;
        log("WINDOW_ADD_BEGIN", 0);
        return now();
    }

    public static void windowAddEnd(long startedAt) {
        if (active && startedAt != 0) {
            windowAddMs += elapsed(startedAt);
            log("WINDOW_ADD_END", elapsed(startedAt));
        }
    }

    public static void morphBegin() {
        if (active) {
            morphStartedAt = now();
            log("MORPH_BEGIN", 0);
        }
    }

    public static void setAnimationInProgress(int value) {
        if (active) animationInProgress = value;
    }

    public static void morphEnd() {
        if (active && morphStartedAt != 0) {
            viewerMorphMs += elapsed(morphStartedAt);
            log("MORPH_END", elapsed(morphStartedAt));
            morphStartedAt = 0;
        }
    }

    public static long glassInvalidateBegin() {
        return active ? now() : 0;
    }

    public static void glassInvalidateEnd(long startedAt) {
        if (!active || startedAt == 0) return;
        long duration = elapsed(startedAt);
        glassInvalidateCount++;
        glassInvalidateTotalMs += duration;
        maxGlassInvalidateMs = Math.max(maxGlassInvalidateMs, duration);
        if (!glassLogged) {
            glassLogged = true;
            log("GLASS_INVALIDATE_BEGIN", 0);
            log("GLASS_INVALIDATE_END", duration);
        }
    }

    public static void actionBarInvalidated() {
        if (active) actionBarInvalidateCount++;
    }

    public static void chatAvatarInvalidated() {
        if (active) chatAvatarInvalidateCount++;
    }

    public static void headerInvalidated() {
        if (active) headerInvalidateCount++;
    }

    public static long hdrUpdateBegin() {
        return active ? now() : 0;
    }

    public static void hdrUpdateEnd(long startedAt) {
        if (active && startedAt != 0) {
            hdrUpdateCount++;
            long duration = elapsed(startedAt);
            hdrUpdateTotalMs += duration;
            log("HDR_UPDATE", duration);
        }
    }

    public static void viewerOpenEnd() {
        if (active) log("VIEWER_OPEN_END", elapsed(viewerOpenStartedAt));
    }

    public static void end() {
        if (!active) return;
        log("TRANSITION_SUMMARY", elapsed(viewerOpenStartedAt),
                "attachOpenMs=" + elapsed(attachOpenStartedAt)
                        + " glassInvalidateCount=" + glassInvalidateCount
                        + " glassInvalidateTotalMs=" + glassInvalidateTotalMs
                        + " maxGlassInvalidateMs=" + maxGlassInvalidateMs
                        + " headerInvalidateCount=" + headerInvalidateCount
                        + " actionBarInvalidateCount=" + actionBarInvalidateCount
                        + " chatAvatarInvalidateCount=" + chatAvatarInvalidateCount
                        + " viewerMorphMs=" + viewerMorphMs
                        + " windowAddMs=" + windowAddMs
                        + " hdrUpdateCount=" + hdrUpdateCount
                        + " hdrUpdateMs=" + hdrUpdateTotalMs);
        active = false;
    }

    private static String classify(ChatActivity activity) {
        if (activity == null) return "UNKNOWN";
        if (activity.getCurrentUser() != null || activity.getDialogId() > 0) return "PRIVATE";
        TLRPC.Chat chat = activity.getCurrentChat();
        if (chat == null) return "UNKNOWN";
        return ChatObject.isChannel(chat) && !chat.megagroup ? "CHANNEL" : "GROUP";
    }

    private static long now() {
        return SystemClock.elapsedRealtime();
    }

    private static long elapsed(long startedAt) {
        return startedAt == 0 ? 0 : now() - startedAt;
    }

    private static void log(String event, long durationMs) {
        log(event, durationMs, "");
    }

    private static void log(String event, long durationMs, String extra) {
        Log.i(TAG, "event=" + event + " chatType=" + chatType + " elapsedRealtime=" + now()
                + " durationMs=" + durationMs + " animationInProgress=" + animationInProgress
                + (extra.isEmpty() ? "" : " " + extra));
    }
}
