package org.telegram.messenger.diagnostics;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;

/**
 * Android-free, fail-safe, bounded diagnostic state.
 * Diagnostics may drop events. No public API throws.
 *
 * OFF is truly off: no ring, formatting, regex, or session work on the event path.
 * Capture temporarily enables DIAGNOSTIC and records the next {@link #CAPTURE_EVENTS}
 * events after CAPTURE_START. CAPTURE_START itself is not counted.
 */
public final class NgxDiagnosticCore {
    public enum Level { OFF, DIAGNOSTIC, TRACE }

    public enum Category {
        APP, UI, NAVIGATION, GESTURE, CHAT, LAYOUT, MESSAGE, EMOJI, MEDIA, PLAYER,
        DOWNLOAD, UPLOAD, TRANSLATION, NETWORK, MTPROTO, STORAGE, DATABASE,
        NOTIFICATION, PUSH, BACKGROUND, GHOST, NIXGRAMX, PERFORMANCE, ERROR
    }

    /**
     * Allowlisted scalar keys only. REQUEST_ID is a Telegram request correlation
     * integer, never an authentication token.
     */
    public enum Field {
        RESULT, REASON, OLD_STATE, NEW_STATE, TRIGGER, DIRECTION, INDEX, TARGET_INDEX,
        COUNT, DX, DY, VELOCITY, WIDTH, HEIGHT, LINE_COUNT, MEDIA_TYPE, PROVIDER,
        SOURCE_LANGUAGE, TARGET_LANGUAGE, REQUEST_TYPE, REQUEST_ID, DURATION_MS,
        ERROR_CATEGORY, RULE, THREAD
    }

    public static final int RING_LIMIT = 100;
    /** Diagnostic events accepted after CAPTURE_START. CAPTURE_START is not counted. */
    public static final int CAPTURE_EVENTS = 50;

    private static final int DEDUPE_LIMIT = 128;
    private static final long DEDUPE_WINDOW_MS = 1000L;
    private static final AtomicInteger IDS = new AtomicInteger();
    private static final Pattern NAME = Pattern.compile("[A-Z0-9_]+");
    private static final Pattern VALUE = Pattern.compile("[A-Z0-9_]+|-?[0-9]+");

    private final ArrayDeque<String> ring = new ArrayDeque<>(RING_LIMIT);
    private final LinkedHashMap<String, Long> dup = new LinkedHashMap<>();
    private volatile Level level = Level.OFF;
    private String sid;
    private int remaining;
    private Level restore = Level.OFF;
    private long dropped;

    public Level level() {
        Level current = level;
        return current == null ? Level.OFF : current;
    }

    /** Lock-free OFF fast path. A null required level is false and never throws. */
    public boolean enabled(Level required) {
        if (required == null) return false;
        Level current = level;
        if (current == null) return false;
        return current.ordinal() >= required.ordinal();
    }

    public long dropped() {
        return dropped;
    }

    public synchronized void setLevel(Level next) {
        try {
            level = next == null ? Level.OFF : next;
        } catch (Throwable ignored) {
            level = Level.OFF;
        }
    }

    public static final class Value {
        final Field f;
        final String v;

        private Value(Field f, String v) {
            this.f = f;
            this.v = v;
        }

        public static Value bool(Field f, boolean v) {
            return new Value(f, v ? "TRUE" : "FALSE");
        }

        public static Value integer(Field f, int v) {
            return new Value(f, Integer.toString(v));
        }

        public static Value number(Field f, long v) {
            return new Value(f, Long.toString(v));
        }

        public static Value enumValue(Field f, Enum<?> v) {
            if (f == null || v == null) return null;
            return new Value(f, v.name());
        }
    }

    /** Starts a session. A second begin() replaces the current session id. */
    public synchronized String begin() {
        try {
            if (!enabled(Level.DIAGNOSTIC)) return null;
            sid = id();
            return emit(Category.APP, "SESSION_START");
        } catch (Throwable ignored) {
            drop();
            return null;
        }
    }

    /** Emits SESSION_END with the current sid, then always clears sid. */
    public synchronized String end() {
        String line = null;
        try {
            if (enabled(Level.DIAGNOSTIC) || remaining > 0) {
                line = emit(Category.APP, "SESSION_END");
            }
        } catch (Throwable ignored) {
            drop();
        }
        sid = null;
        return line;
    }

    /**
     * Temporarily enables DIAGNOSTIC when OFF, records CAPTURE_START (not counted),
     * then accepts the next {@link #CAPTURE_EVENTS} events.
     * A second capture while one is active is rejected and does not replace restore.
     */
    public synchronized boolean capture() {
        try {
            if (remaining > 0) return false;
            restore = level();
            if (!enabled(Level.DIAGNOSTIC)) {
                level = Level.DIAGNOSTIC;
            }
            sid = id();
            emit(Category.APP, "CAPTURE_START", Value.integer(Field.COUNT, ring.size()));
            remaining = CAPTURE_EVENTS;
            return true;
        } catch (Throwable ignored) {
            drop();
            finishCapture();
            return false;
        }
    }

    public synchronized String event(Category category, String name, Value... values) {
        try {
            if (!enabled(Level.DIAGNOSTIC) && remaining == 0) return null;
            if (category == null || !safeName(name) || values == null) {
                drop();
                return null;
            }
            StringBuilder line = new StringBuilder(96)
                    .append("[NGX]|").append(category)
                    .append("|sid=").append(sid == null ? "NONE" : sid)
                    .append("|event=").append(name);
            for (Value value : values) {
                if (value == null || value.f == null || !safeValue(value.v)) {
                    drop();
                    return null;
                }
                line.append('|')
                        .append(value.f.name().toLowerCase(Locale.US))
                        .append('=')
                        .append(value.v);
            }
            return record(line.toString());
        } catch (Throwable ignored) {
            drop();
            return null;
        }
    }

    public synchronized List<String> snapshot() {
        try {
            return new ArrayList<>(ring);
        } catch (Throwable ignored) {
            return Collections.emptyList();
        }
    }

    /**
     * Clears ring, dedupe, session, and dropped.
     * Active capture is aborted and the pre-capture level is restored.
     * With no active capture the configured level is unchanged.
     */
    public synchronized void clear() {
        try {
            ring.clear();
            dup.clear();
            sid = null;
            dropped = 0;
            if (remaining > 0) {
                finishCapture();
            }
        } catch (Throwable ignored) {
            sid = null;
            remaining = 0;
            dropped = 0;
        }
    }

    boolean isCapturing() {
        return remaining > 0;
    }

    int remaining() {
        return remaining;
    }

    /**
     * Stops capture and restores the original level. The ring is kept.
     * CAPTURE_CANCEL is emitted with the capture sid when possible and does
     * not consume remaining. remaining is cleared before emit so record()
     * cannot call finishCapture() when remaining was 1.
     */
    public synchronized boolean cancelCapture() {
        if (remaining <= 0) return false;
        remaining = 0;
        try {
            emit(Category.APP, "CAPTURE_CANCEL");
        } catch (Throwable ignored) {
            drop();
        }
        finishCapture();
        return true;
    }

    private String emit(Category category, String name, Value... values) {
        return event(category, name, values);
    }

    private String record(String line) {
        long now = System.nanoTime() / 1000000L;
        Long previous = dup.get(line);
        if (previous != null && now - previous < DEDUPE_WINDOW_MS) return null;
        dup.put(line, now);
        if (dup.size() > DEDUPE_LIMIT) {
            dup.remove(dup.keySet().iterator().next());
        }
        if (ring.size() == RING_LIMIT) ring.removeFirst();
        ring.addLast(line);
        if (remaining > 0 && --remaining == 0) {
            finishCapture();
        }
        return line;
    }

    private void finishCapture() {
        remaining = 0;
        level = restore == null ? Level.OFF : restore;
        restore = Level.OFF;
        sid = null;
    }

    private void drop() {
        dropped++;
    }

    private static String id() {
        return String.format(Locale.US, "%04X", IDS.incrementAndGet() & 0xffff);
    }

    private static boolean safeName(String s) {
        return s != null && s.length() <= 80 && NAME.matcher(s).matches() && !sensitive(s);
    }

    private static boolean safeValue(String s) {
        return s != null && s.length() <= 80 && VALUE.matcher(s).matches() && !sensitive(s);
    }

    private static boolean sensitive(String s) {
        return s.contains("TOKEN") || s.contains("PASSWORD") || s.contains("USERNAME") || s.contains("SECRET");
    }
}
