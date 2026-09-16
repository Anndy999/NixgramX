package org.telegram.messenger.diagnostics;

import java.security.SecureRandom;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;

/** Android-free, bounded state for NGX Diagnostics; persistence is supplied by its bridge. */
public final class NgxDiagnosticCore {
    public enum Level { OFF, DIAGNOSTIC, TRACE }
    public enum Category { APP, UI, NAVIGATION, GESTURE, CHAT, LAYOUT, MESSAGE, EMOJI, MEDIA, PLAYER, DOWNLOAD, UPLOAD, TRANSLATION, NETWORK, MTPROTO, STORAGE, DATABASE, NOTIFICATION, PUSH, BACKGROUND, GHOST, NIXGRAMX, PERFORMANCE, ERROR }
    public enum Field { EVENT, RESULT, REASON, OLD_STATE, NEW_STATE, TRIGGER, DIRECTION, INDEX, TARGET_INDEX, COUNT, DX, DY, VELOCITY, WIDTH, HEIGHT, LINE_COUNT, MEDIA_TYPE, PROVIDER, SOURCE_LANGUAGE, TARGET_LANGUAGE, REQUEST_TYPE, REQUEST_TOKEN, DURATION_MS, ERROR_CATEGORY, RULE, THREAD, VALUE }
    public static final int RING_LIMIT = 100, CAPTURE_EVENTS = 50;
    private final ArrayDeque<String> ring = new ArrayDeque<>(RING_LIMIT);
    private final HashMap<String, Long> duplicates = new HashMap<>();
    private final SecureRandom random = new SecureRandom();
    private volatile Level level = Level.OFF;
    private String sessionId;
    private int captureRemaining;
    private Level captureRestore;
    public Level level() { return level; }
    public void setLevel(Level next) { level = next == null ? Level.OFF : next; }
    public boolean enabled(Level required) { return level.ordinal() >= required.ordinal(); }
    public synchronized String begin(String trigger) { sessionId = id(); return line(Category.APP, "SESSION_START", "TRIGGER", label(trigger)); }
    public synchronized String end(String result) { String out = line(Category.APP, "SESSION_END", "RESULT", label(result)); sessionId = null; return out; }
    public synchronized List<String> capture() { captureRestore = level; if (level == Level.OFF) level = Level.DIAGNOSTIC; sessionId = id(); captureRemaining = CAPTURE_EVENTS; ArrayList<String> out = new ArrayList<>(ring); out.add(line(Category.APP, "CAPTURE_START", "COUNT", Integer.toString(ring.size()))); return out; }
    public synchronized String event(Category category, String event, String... pairs) {
        if (level == Level.OFF && captureRemaining == 0) return null;
        StringBuilder out = new StringBuilder("[NGX]|").append(category.name()).append("|sid=").append(sessionId == null ? "NONE" : sessionId).append("|event=").append(label(event));
        for (int i=0; i<pairs.length; i+=2) out.append('|').append(field(pairs[i])).append('=').append(label(pairs[i+1]));
        String line = out.toString(), key = category.name() + '|' + event + '|' + line;
        long now = System.nanoTime() / 1_000_000L; Long previous = duplicates.get(key);
        if (previous != null && now - previous < 1000) return null;
        duplicates.put(key, now); if (duplicates.size() > 128) duplicates.clear();
        if (ring.size() == RING_LIMIT) ring.removeFirst(); ring.addLast(line);
        if (captureRemaining > 0 && --captureRemaining == 0) { level = captureRestore; sessionId = null; }
        return line;
    }
    public synchronized List<String> snapshot() { return new ArrayList<>(ring); }
    public synchronized void clear() { ring.clear(); duplicates.clear(); sessionId=null; captureRemaining=0; }
    private String id() { return String.format(Locale.US, "%04X", random.nextInt(0x10000)); }
    private static String field(String s) { try { return Field.valueOf(s).name().toLowerCase(Locale.US); } catch (Exception e) { throw new IllegalArgumentException("forbidden field"); } }
    private static String label(String s) { if (s == null || !s.matches("[A-Za-z0-9_.-]{1,80}")) throw new IllegalArgumentException("forbidden value"); return s; }
    private String line(Category c, String e, String k, String v) { return event(c,e,k,v); }
}
