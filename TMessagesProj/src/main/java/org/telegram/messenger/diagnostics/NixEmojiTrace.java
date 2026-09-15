package org.telegram.messenger.diagnostics;

import android.os.SystemClock;
import android.util.Log;
import android.view.View;
import android.view.ViewParent;

import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.BuildConfig;
import org.telegram.messenger.BuildVars;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.MessageObject;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * DIAGNOSTIC ONLY — DO NOT MERGE.
 *
 * Targeted translation / custom-emoji layout lifecycle trace.
 * Production behavior is unchanged: every public method is a no-op on stable/release.
 * Never logs message text, usernames, ids, document ids, URLs, or paths.
 */
public final class NixEmojiTrace {
    public static final String TAG = "NixEmojiTrace";

    private static final boolean ENABLED =
            BuildConfig.DEBUG || !"stable".equals(BuildConfig.NIXGRAMX_CHANNEL);

    private static final AtomicLong SEQ = new AtomicLong();
    private static final ThreadLocal<DrawCtx> CTX = new ThreadLocal<>();
    private static final ThreadLocal<Integer> REBIND_DEPTH = new ThreadLocal<>();
    private static final HashMap<Integer, RebindSnap> LAST_REBIND = new HashMap<>();
    private static final HashMap<String, Integer> LAST_KEY = new HashMap<>();
    private static final HashMap<Integer, Integer> LAST_SPAN_LAYOUT = new HashMap<>();
    private static final HashMap<Integer, Long> LAST_SPAN_XY = new HashMap<>();
    private static final ThreadPoolExecutor IO = new ThreadPoolExecutor(
            1, 1, 30, TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(64),
            r -> {
                Thread t = new Thread(r, "NixEmojiTrace");
                t.setDaemon(true);
                return t;
            },
            (task, executor) -> { /* drop rather than block the UI thread */ });

    private NixEmojiTrace() {}

    public static boolean enabled() {
        return ENABLED;
    }

    public static int id(Object o) {
        return o == null ? 0 : System.identityHashCode(o);
    }

    public static int firstLayoutId(ArrayList<MessageObject.TextLayoutBlock> blocks) {
        if (blocks == null || blocks.isEmpty() || blocks.get(0) == null) {
            return 0;
        }
        return id(blocks.get(0).textLayout);
    }

    public static int blockCount(ArrayList<MessageObject.TextLayoutBlock> blocks) {
        return blocks == null ? 0 : blocks.size();
    }

    public static boolean inRebind() {
        Integer depth = REBIND_DEPTH.get();
        return depth != null && depth > 0;
    }

    public static void enterRebind() {
        if (!ENABLED) {
            return;
        }
        Integer depth = REBIND_DEPTH.get();
        REBIND_DEPTH.set(depth == null ? 1 : depth + 1);
    }

    public static void exitRebind() {
        if (!ENABLED) {
            return;
        }
        Integer depth = REBIND_DEPTH.get();
        if (depth == null || depth <= 1) {
            REBIND_DEPTH.remove();
        } else {
            REBIND_DEPTH.set(depth - 1);
        }
    }

    public static void enterRole(String role, View cell, Object layout, Object stack, boolean translated,
                                boolean animateChangeRunning, float animateChangeProgress, boolean moveAnimatorActive) {
        if (!ENABLED) {
            return;
        }
        DrawCtx ctx = new DrawCtx();
        ctx.role = role == null ? "UNKNOWN" : role;
        ctx.cell = id(cell);
        ctx.layout = id(layout);
        ctx.stack = id(stack);
        ctx.translated = translated ? 1 : 0;
        ctx.animateChangeRunning = animateChangeRunning ? 1 : 0;
        ctx.animateChangeProgress = animateChangeProgress;
        ctx.moveAnimatorActive = moveAnimatorActive ? 1 : 0;
        CTX.set(ctx);
    }

    public static void exitRole() {
        if (!ENABLED) {
            return;
        }
        CTX.remove();
    }

    public static DrawCtx ctx() {
        return ENABLED ? CTX.get() : null;
    }

    public static long nextSeq() {
        return SEQ.incrementAndGet();
    }

    public static int[] animatorState(View view) {
        int present = 0;
        int running = 0;
        if (view != null) {
            ViewParent parent = view.getParent();
            if (parent instanceof RecyclerView) {
                RecyclerView.ItemAnimator animator = ((RecyclerView) parent).getItemAnimator();
                if (animator != null) {
                    present = 1;
                    running = animator.isRunning() ? 1 : 0;
                }
            }
        }
        return new int[]{present, running};
    }

    public static void rebindBegin(View cell, Object oldMsg, Object newMsg, boolean translated,
                                   Object textBlocks, Object replyLayout, Object bodyStack, Object replyStack,
                                   int animatorPresent) {
        if (!ENABLED) {
            return;
        }
        long seq = nextSeq();
        int cellId = id(cell);
        int oldLayout = firstLayoutId(asBlocks(textBlocks));
        RebindSnap snap = LAST_REBIND.get(cellId);
        if (snap == null) {
            snap = new RebindSnap();
            if (LAST_REBIND.size() > 64) {
                LAST_REBIND.clear();
            }
            LAST_REBIND.put(cellId, snap);
        }
        snap.seq = seq;
        snap.oldLayout = oldLayout;
        snap.oldReplyLayout = id(replyLayout);
        snap.oldStack = id(bodyStack);
        snap.oldReplyStack = id(replyStack);
        snap.oldMsg = id(oldMsg);
        snap.newMsg = id(newMsg);
        snap.translated = translated ? 1 : 0;
        snap.time = SystemClock.elapsedRealtime();
        log("TRANSLATION_REBIND_BEGIN seq=" + seq
                + " cell=" + hex(cellId)
                + " oldMsg=" + hex(id(oldMsg))
                + " newMsg=" + hex(id(newMsg))
                + " tr=" + (translated ? 1 : 0)
                + " bodyL=" + hex(oldLayout)
                + " replyL=" + hex(id(replyLayout))
                + " bodyS=" + hex(id(bodyStack))
                + " replyS=" + hex(id(replyStack))
                + " anim=" + animatorPresent
                + " blocks=" + blockCount(asBlocks(textBlocks)));
    }

    public static void rebindEnd(View cell, Object newMsg, boolean translated,
                                 Object textBlocks, Object replyLayout, Object bodyStack, Object replyStack,
                                 boolean animateChangeRunning, float animateChangeProgress, int animatorPresent) {
        if (!ENABLED) {
            return;
        }
        int cellId = id(cell);
        RebindSnap snap = LAST_REBIND.get(cellId);
        long seq = snap != null ? snap.seq : nextSeq();
        int newLayout = firstLayoutId(asBlocks(textBlocks));
        int newReply = id(replyLayout);
        int newStack = id(bodyStack);
        int newReplyStack = id(replyStack);
        if (snap != null) {
            snap.newLayout = newLayout;
            snap.newReplyLayout = newReply;
            snap.newStack = newStack;
            snap.newReplyStack = newReplyStack;
            snap.translated = translated ? 1 : 0;
            snap.time = SystemClock.elapsedRealtime();
        }
        log("TRANSLATION_REBIND_END seq=" + seq
                + " cell=" + hex(cellId)
                + " msg=" + hex(id(newMsg))
                + " tr=" + (translated ? 1 : 0)
                + " bodyL=" + hex(newLayout)
                + " replyL=" + hex(newReply)
                + " bodyS=" + hex(newStack)
                + " replyS=" + hex(newReplyStack)
                + " chg=" + (animateChangeRunning ? 1 : 0)
                + " p=" + fmt(animateChangeProgress)
                + " anim=" + animatorPresent
                + " blocks=" + blockCount(asBlocks(textBlocks)));
        if (snap != null) {
            if (snap.oldLayout != 0 && newLayout != 0 && snap.oldLayout != newLayout
                    && newStack != 0 && newStack == snap.oldStack) {
                alert("STACK_NOT_REBOUND", "seq=" + seq + " cell=" + hex(cellId)
                        + " oldL=" + hex(snap.oldLayout) + " newL=" + hex(newLayout)
                        + " stack=" + hex(newStack) + " role=BODY");
            }
            if (snap.oldReplyLayout != 0 && newReply != 0 && snap.oldReplyLayout != newReply
                    && newReplyStack != 0 && newReplyStack == snap.oldReplyStack) {
                alert("STACK_NOT_REBOUND", "seq=" + seq + " cell=" + hex(cellId)
                        + " oldL=" + hex(snap.oldReplyLayout) + " newL=" + hex(newReply)
                        + " stack=" + hex(newReplyStack) + " role=REPLY");
            }
        }
    }

    public static void stackAction(String action, View view, Object stack, Object prevStack, Object requestedLayout,
                                   Object span, int spanStart, int spanEnd, int textLength, Object holderLayout) {
        if (!ENABLED) {
            return;
        }
        if (!interesting(view)) {
            return;
        }
        DrawCtx ctx = CTX.get();
        String role = ctx != null ? ctx.role : roleForView(view);
        int req = id(requestedLayout);
        int holderL = id(holderLayout);
        String key = action + ":" + id(span) + ":" + req + ":" + id(stack);
        if ("REUSE".equals(action) && !inRebind() && req == holderL) {
            return;
        }
        if (!changed(key, req ^ id(stack) ^ spanStart ^ spanEnd)) {
            return;
        }
        log("EMOJI_STACK_UPDATE role=" + role
                + " action=" + action
                + " stack=" + hex(id(stack))
                + " prevS=" + hex(id(prevStack))
                + " reqL=" + hex(req)
                + " holdL=" + hex(holderL)
                + " span=" + hex(id(span))
                + " start=" + spanStart
                + " end=" + spanEnd
                + " len=" + textLength
                + " view=" + hex(id(view)));
        if (spanStart < 0 || (textLength >= 0 && spanEnd > textLength) || spanStart >= spanEnd) {
            alert("SPAN_RANGE_INVALID", "role=" + role + " span=" + hex(id(span))
                    + " start=" + spanStart + " end=" + spanEnd + " len=" + textLength);
        }
        if (req != 0 && holderL != 0 && req != holderL) {
            alert("MISMATCH_LAYOUT", "role=" + role + " action=" + action
                    + " reqL=" + hex(req) + " holdL=" + hex(holderL) + " span=" + hex(id(span)));
        }
    }

    public static void stackReleased(View view, Object prevStack) {
        if (!ENABLED || prevStack == null || !interesting(view)) {
            return;
        }
        DrawCtx ctx = CTX.get();
        String role = ctx != null ? ctx.role : roleForView(view);
        log("EMOJI_STACK_UPDATE role=" + role
                + " action=REMOVE"
                + " stack=0"
                + " prevS=" + hex(id(prevStack))
                + " reqL=0 holdL=0 span=0 start=-1 end=-1 len=0"
                + " view=" + hex(id(view)));
    }

    public static void spanWrite(Object span, float previousCx, float previousCy,
                                 float candidateCx, float candidateCy, float finalCx, float finalCy,
                                 int measuredSize, boolean lockPositionChanging, boolean animateChanges,
                                 boolean positionChanged, boolean moveAnimatorActive, String reason) {
        if (!ENABLED) {
            return;
        }
        DrawCtx ctx = CTX.get();
        if (ctx == null && !inRebind()) {
            return;
        }
        int spanId = id(span);
        int layoutId = ctx != null ? ctx.layout : 0;
        int qx = Math.round(finalCx);
        int qy = Math.round(finalCy);
        long packed = (((long) qx) << 32) ^ (qy & 0xffffffffL);
        Long prevXy = LAST_SPAN_XY.get(spanId);
        Integer prevLayout = LAST_SPAN_LAYOUT.get(spanId);
        boolean layoutChanged = prevLayout != null && layoutId != 0 && prevLayout != layoutId;
        if (prevXy != null && prevXy == packed && !layoutChanged && !"ANIMATE_INTERCEPT".equals(reason) && !"LOCK_SKIP".equals(reason)) {
            return;
        }
        LAST_SPAN_XY.put(spanId, packed);
        if (layoutId != 0) {
            LAST_SPAN_LAYOUT.put(spanId, layoutId);
        }
        log("SPAN_POSITION_WRITE span=" + hex(spanId)
                + " role=" + (ctx != null ? ctx.role : "UNKNOWN")
                + " prodL=" + hex(layoutId)
                + " stack=" + hex(ctx != null ? ctx.stack : 0)
                + " cell=" + hex(ctx != null ? ctx.cell : 0)
                + " prev=" + fmt(previousCx) + "," + fmt(previousCy)
                + " cand=" + fmt(candidateCx) + "," + fmt(candidateCy)
                + " fin=" + fmt(finalCx) + "," + fmt(finalCy)
                + " sz=" + measuredSize
                + " lock=" + (lockPositionChanging ? 1 : 0)
                + " ac=" + (animateChanges ? 1 : 0)
                + " pc=" + (positionChanged ? 1 : 0)
                + " mv=" + (moveAnimatorActive ? 1 : 0)
                + " why=" + reason
                + " p=" + fmt(ctx != null ? ctx.animateChangeProgress : -1f));
        if (layoutChanged && prevLayout != null && prevLayout != 0) {
            RebindSnap snap = ctx != null ? LAST_REBIND.get(ctx.cell) : null;
            if (snap != null && snap.oldLayout != 0 && prevLayout == snap.oldLayout && layoutId != snap.oldLayout) {
                // First write on the new layout after rebind: expected, not stale.
            } else if (snap != null && layoutId == snap.oldLayout && snap.newLayout != 0 && snap.newLayout != snap.oldLayout) {
                alert("STALE_POSITION", "span=" + hex(spanId) + " prodL=" + hex(layoutId)
                        + " oldL=" + hex(snap.oldLayout) + " newL=" + hex(snap.newLayout)
                        + " fin=" + fmt(finalCx) + "," + fmt(finalCy));
            }
        }
        prune(LAST_SPAN_XY, 256);
        pruneInt(LAST_SPAN_LAYOUT, 256);
    }

    public static void emojiDraw(String role, Object requestedLayout, Object stack, Object holder, Object holderLayout,
                                 Object span, float lastCx, float lastCy, int left, int top, int right, int bottom,
                                 int measuredSize) {
        if (!ENABLED) {
            return;
        }
        DrawCtx ctx = CTX.get();
        if (ctx == null && !inRebind()) {
            return;
        }
        int req = id(requestedLayout);
        int holdL = id(holderLayout);
        int spanId = id(span);
        int stackId = id(stack);
        if (stackId == 0 && ctx != null) {
            stackId = ctx.stack;
        }
        String key = "ED:" + role + ":" + spanId + ":" + req + ":" + holdL;
        int token = Math.round(lastCx) ^ (Math.round(lastCy) << 16) ^ left ^ top;
        if (!changed(key, token) && req == holdL) {
            return;
        }
        log("EMOJI_DRAW role=" + role
                + " reqL=" + hex(req)
                + " stack=" + hex(stackId)
                + " hold=" + hex(id(holder))
                + " holdL=" + hex(holdL)
                + " span=" + hex(spanId)
                + " xy=" + fmt(lastCx) + "," + fmt(lastCy)
                + " box=" + left + "," + top + "," + right + "," + bottom
                + " sz=" + measuredSize
                + " tr=" + (ctx != null ? ctx.translated : -1));
        if (req != 0 && holdL != 0 && req != holdL) {
            alert("MISMATCH_LAYOUT", "role=" + role + " reqL=" + hex(req) + " holdL=" + hex(holdL)
                    + " span=" + hex(spanId) + " stack=" + hex(id(stack)));
        }
        RebindSnap snap = ctx != null ? LAST_REBIND.get(ctx.cell) : null;
        if (snap != null && snap.newLayout != 0 && req == snap.newLayout && holdL != 0 && holdL == snap.oldLayout
                && snap.oldLayout != snap.newLayout
                && SystemClock.elapsedRealtime() - snap.time < 5000) {
            alert("STALE_POSITION", "role=" + role + " reqL=" + hex(req) + " holdL=" + hex(holdL)
                    + " oldL=" + hex(snap.oldLayout) + " span=" + hex(spanId));
            alert("STACK_NOT_REBOUND", "role=" + role + " stack=" + hex(id(stack))
                    + " reqL=" + hex(req) + " holdL=" + hex(holdL));
        }
    }

    public static void emojiDrawNoChunk(String role, Object requestedLayout, Object stack, int holderCount) {
        if (!ENABLED) {
            return;
        }
        if (requestedLayout == null || stack == null || holderCount <= 0) {
            return;
        }
        String key = "EDMISS:" + role + ":" + id(requestedLayout) + ":" + id(stack);
        if (!changed(key, holderCount)) {
            return;
        }
        alert("MISMATCH_LAYOUT", "role=" + role + " reqL=" + hex(id(requestedLayout))
                + " stack=" + hex(id(stack)) + " holders=" + holderCount + " chunk=0");
    }

    public static void bodyLayoutDraw(View cell, Object layout, float originX, float originY,
                                      int width, int height, int lineCount, Object stack, boolean translated) {
        if (!ENABLED) {
            return;
        }
        int layoutId = id(layout);
        String key = "BODY:" + id(cell) + ":" + layoutId + ":" + (translated ? 1 : 0);
        int token = Math.round(originX) ^ (Math.round(originY) << 10) ^ width ^ (lineCount << 16);
        if (!changed(key, token)) {
            return;
        }
        log("BODY_LAYOUT_DRAW role=BODY"
                + " cell=" + hex(id(cell))
                + " layout=" + hex(layoutId)
                + " origin=" + fmt(originX) + "," + fmt(originY)
                + " w=" + width + " h=" + height
                + " lines=" + lineCount
                + " stack=" + hex(id(stack))
                + " tr=" + (translated ? 1 : 0));
    }

    public static void replyLayoutDraw(View cell, Object layout, Object stack, int replyTextOffset,
                                       float translateX, float translateY, int width, int height, boolean translated) {
        if (!ENABLED) {
            return;
        }
        int layoutId = id(layout);
        String key = "REPLY:" + id(cell) + ":" + layoutId + ":" + (translated ? 1 : 0);
        int token = replyTextOffset ^ Math.round(translateX) ^ (Math.round(translateY) << 10) ^ width;
        if (!changed(key, token)) {
            return;
        }
        log("REPLY_LAYOUT_DRAW role=REPLY"
                + " cell=" + hex(id(cell))
                + " layout=" + hex(layoutId)
                + " stack=" + hex(id(stack))
                + " off=" + replyTextOffset
                + " origin=" + fmt(translateX) + "," + fmt(translateY)
                + " w=" + width + " h=" + height
                + " tr=" + (translated ? 1 : 0));
    }

    public static void animateChange(View cell, boolean translated, boolean animateMessageText,
                                     Object outBlocks, Object bodyStack, Object replyLayout, Object replyStack,
                                     boolean animateChangeRunning, float progress, boolean moveAnimatorActive) {
        if (!ENABLED) {
            return;
        }
        String key = "AC:" + id(cell) + ":" + firstLayoutId(asBlocks(outBlocks)) + ":" + id(bodyStack);
        int token = (animateMessageText ? 1 : 0) ^ (Math.round(progress * 100) << 1) ^ (moveAnimatorActive ? 4 : 0);
        if (!inRebind() && !changed(key, token)) {
            return;
        }
        log("ANIMATE_CHANGE cell=" + hex(id(cell))
                + " tr=" + (translated ? 1 : 0)
                + " msgText=" + (animateMessageText ? 1 : 0)
                + " outB=" + hex(id(outBlocks))
                + " bodyS=" + hex(id(bodyStack))
                + " replyL=" + hex(id(replyLayout))
                + " replyS=" + hex(id(replyStack))
                + " chg=" + (animateChangeRunning ? 1 : 0)
                + " p=" + fmt(progress)
                + " mv=" + (moveAnimatorActive ? 1 : 0));
    }

    public static void alert(String name, String details) {
        if (!ENABLED) {
            return;
        }
        String key = name + ":" + details;
        if (!changed(key, 1)) {
            return;
        }
        log(name + " " + details);
    }

    /**
     * One-shot diagnostic for reply AnimatedEmojiSpan isolation A/B.
     * Never logs message text, chat ids, document ids, or usernames.
     */
    public static void replySpanIsolation(Object bodySpan, Object replySpanBefore, Object replySpanAfter, boolean sameInstanceBodyReply, boolean sameInstanceBeforeAfter) {
        if (!ENABLED) {
            return;
        }
        log("REPLY_SPAN_ISOLATION body=" + id(bodySpan)
                + " replyBefore=" + id(replySpanBefore)
                + " replyAfter=" + id(replySpanAfter)
                + " sameInstanceBodyReply=" + (sameInstanceBodyReply ? 1 : 0)
                + " sameInstanceBeforeAfter=" + (sameInstanceBeforeAfter ? 1 : 0));
    }

    public static void log(String line) {
        if (!ENABLED || line == null) {
            return;
        }
        if (line.length() > 900) {
            line = line.substring(0, 900);
        }
        Log.d(TAG, line);
        if (BuildVars.LOGS_ENABLED) {
            FileLog.d(TAG + " " + line);
        }
        persist(line);
    }

    private static boolean interesting(View view) {
        if (inRebind()) {
            return true;
        }
        DrawCtx ctx = CTX.get();
        if (ctx != null && !"UNKNOWN".equals(ctx.role)) {
            return true;
        }
        return view instanceof org.telegram.ui.Cells.ChatMessageCell;
    }

    private static String roleForView(View view) {
        DrawCtx ctx = CTX.get();
        if (ctx != null) {
            return ctx.role;
        }
        return view instanceof org.telegram.ui.Cells.ChatMessageCell ? "BODY" : "UNKNOWN";
    }

    @SuppressWarnings("unchecked")
    private static ArrayList<MessageObject.TextLayoutBlock> asBlocks(Object textBlocks) {
        if (textBlocks instanceof ArrayList) {
            return (ArrayList<MessageObject.TextLayoutBlock>) textBlocks;
        }
        return null;
    }

    private static boolean changed(String key, int token) {
        Integer prev = LAST_KEY.get(key);
        if (prev != null && prev == token) {
            return false;
        }
        LAST_KEY.put(key, token);
        pruneInt(LAST_KEY, 384);
        return true;
    }

    private static void prune(HashMap<Integer, Long> map, int max) {
        if (map.size() > max) {
            map.clear();
        }
    }

    private static void pruneInt(HashMap<?, ?> map, int max) {
        if (map.size() > max) {
            map.clear();
        }
    }

    private static String hex(int v) {
        return Integer.toHexString(v);
    }

    private static String fmt(float v) {
        return Integer.toString(Math.round(v));
    }

    private static void persist(String line) {
        try {
            if (ApplicationLoader.applicationContext == null) {
                return;
            }
            final String out = System.currentTimeMillis() + " STATE " + TAG + " " + line + "\n";
            IO.execute(() -> {
                try {
                    DiagnosticStore store = new DiagnosticStore(
                            new File(ApplicationLoader.applicationContext.getNoBackupFilesDir(), "diagnostics"));
                    store.append(out);
                } catch (Throwable ignore) {
                    // Diagnostic persistence must never affect production.
                }
            });
        } catch (Throwable ignore) {
            // Diagnostic persistence must never affect production.
        }
    }

    public static final class DrawCtx {
        public String role = "UNKNOWN";
        public int cell;
        public int layout;
        public int stack;
        public int translated;
        public int animateChangeRunning;
        public float animateChangeProgress;
        public int moveAnimatorActive;
    }

    private static final class RebindSnap {
        long seq;
        int oldLayout;
        int newLayout;
        int oldReplyLayout;
        int newReplyLayout;
        int oldStack;
        int newStack;
        int oldReplyStack;
        int newReplyStack;
        int oldMsg;
        int newMsg;
        int translated;
        long time;
    }
}
