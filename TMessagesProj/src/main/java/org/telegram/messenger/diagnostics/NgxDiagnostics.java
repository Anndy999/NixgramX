package org.telegram.messenger.diagnostics;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import androidx.core.content.FileProvider;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.BuildConfig;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.Utilities;
import java.io.*;
import java.security.SecureRandom;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Local, allowlisted decision diagnostics.  This is deliberately a facade over
 * FileLog, not a second logging backend: FileLog owns the queue and log directory.
 */
public final class NgxDiagnostics {
    public enum Level { OFF, DIAGNOSTIC, TRACE }
    public enum Category { APP, UI, NAVIGATION, GESTURE, CHAT, LAYOUT, MESSAGE, EMOJI, MEDIA, PLAYER, DOWNLOAD, UPLOAD, TRANSLATION, NETWORK, MTPROTO, STORAGE, DATABASE, NOTIFICATION, PUSH, BACKGROUND, GHOST, NIXGRAMX, PERFORMANCE, ERROR }
    /** Only these keys can enter an NGX line; there is intentionally no String-key API. */
    public enum Field { EVENT, RESULT, REASON, STATE_OLD, STATE_NEW, TRIGGER, DIRECTION, INDEX, TARGET_INDEX, COUNT, DX, DY, VELOCITY, WIDTH, HEIGHT, LINE_COUNT, MEDIA_TYPE, PROVIDER, SOURCE_LANGUAGE, TARGET_LANGUAGE, REQUEST_TYPE, REQUEST_TOKEN, DURATION_MS, ERROR_CATEGORY, RULE, THREAD, BUILD, VALUE }
    public static final class Value { final Field key; final String value; private Value(Field k, String v) { key=k; value=v; }
        public static Value of(Field k, boolean v) { return new Value(k, Boolean.toString(v)); }
        public static Value of(Field k, int v) { return new Value(k, Integer.toString(v)); }
        public static Value of(Field k, long v) { return new Value(k, Long.toString(v)); }
        public static Value of(Field k, Enum<?> v) { return new Value(k, v.name()); }
        /** Constants only (event names, enum labels); rejects whitespace and delimiters. */
        public static Value label(Field k, String v) { if (v == null || !v.matches("[A-Za-z0-9_.-]{1,80}")) throw new IllegalArgumentException("unsafe diagnostic label"); return new Value(k, v); }
    }
    private static final String PREF = "ngx_diagnostics";
    private static final int RING_LIMIT = 100, CAPTURE_AFTER = 50;
    private static final Object LOCK = new Object();
    private static final ArrayDeque<String> ring = new ArrayDeque<>(RING_LIMIT);
    private static final HashMap<String, Long> recent = new HashMap<>();
    private static final SecureRandom RANDOM = new SecureRandom();
    private static volatile String sessionId;
    private static volatile int captureRemaining;
    private static volatile Level captureRestore;
    private NgxDiagnostics() { }

    public static Level level() { try { return Level.valueOf(ApplicationLoader.applicationContext.getSharedPreferences(PREF, 0).getString("level", Level.OFF.name())); } catch (Throwable ignored) { return Level.OFF; } }
    public static void setLevel(Level level) { ApplicationLoader.applicationContext.getSharedPreferences(PREF, 0).edit().putString("level", level.name()).apply(); }
    public static String beginSession(String action) { String sid = nextId(); sessionId = sid; event(Category.APP, "SESSION_START", Value.label(Field.TRIGGER, action)); return sid; }
    public static void endSession(String result) { event(Category.APP, "SESSION_END", Value.label(Field.RESULT, result)); sessionId = null; }
    public static void captureNextAction() { synchronized (LOCK) { captureRestore = level(); if (captureRestore == Level.OFF) setLevel(Level.DIAGNOSTIC); sessionId = nextId(); captureRemaining = CAPTURE_AFTER; emitLocked("[NGX]|APP|sid=" + sessionId + "|event=CAPTURE_START|pre_events=" + ring.size(), true); } }
    public static void event(Category category, String event, Value... values) {
        Level level = level(); if (level == Level.OFF && captureRemaining == 0) return;
        if (!event.matches("[A-Z0-9_]{1,80}")) throw new IllegalArgumentException("unsafe event");
        StringBuilder b = new StringBuilder(128).append("[NGX]|").append(category.name()).append("|sid=").append(sessionId == null ? "NONE" : sessionId).append("|event=").append(event);
        for (Value value : values) { if (value != null) b.append('|').append(key(value.key)).append('=').append(value.value); }
        String line = b.toString();
        synchronized (LOCK) { String duplicate = category.name() + '|' + event + '|' + line; long now = android.os.SystemClock.elapsedRealtime(); Long before = recent.get(duplicate); if (before != null && now - before < 1000) return; recent.put(duplicate, now); if (recent.size() > 128) recent.clear(); emitLocked(line, true); }
    }
    private static void emitLocked(String line, boolean persist) { if (ring.size() == RING_LIMIT) ring.removeFirst(); ring.addLast(line); if (persist) FileLog.ngxDiagnostic(line); if (captureRemaining > 0 && --captureRemaining == 0) { FileLog.ngxDiagnostic("[NGX]|APP|sid=" + sessionId + "|event=CAPTURE_END|result=READY"); setLevel(captureRestore); sessionId = null; } }
    private static String key(Field f) { return f.name().toLowerCase(Locale.US); }
    private static String nextId() { return String.format(Locale.US, "%04X", RANDOM.nextInt(0x10000)); }
    public static List<String> ringSnapshot() { synchronized (LOCK) { return new ArrayList<>(ring); } }
    public static void clear() { synchronized (LOCK) { ring.clear(); recent.clear(); sessionId = null; captureRemaining = 0; } }
    public static void export(Activity activity) { final Activity target = activity; Utilities.globalQueue.postRunnable(() -> { try { File out = new File(ApplicationLoader.applicationContext.getCacheDir(), "ngx-diagnostics-" + System.currentTimeMillis() + ".zip"); try (ZipOutputStream zip = new ZipOutputStream(new FileOutputStream(out))) { write(zip, "metadata.txt", metadata()); File log = FileLog.getNgxDiagnosticsFile(); if (log != null && log.isFile()) copy(zip, "ngx_diag.log", log); } android.os.Handler h = new android.os.Handler(android.os.Looper.getMainLooper()); h.post(() -> { try { Intent send = new Intent(Intent.ACTION_SEND).setType("application/zip").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION); send.putExtra(Intent.EXTRA_STREAM, FileProvider.getUriForFile(target, ApplicationLoader.getApplicationId() + ".provider", out)); target.startActivity(Intent.createChooser(send, "Export diagnostics")); } catch (Throwable ignored) { } }); } catch (Throwable ignored) { } }); }
    private static void write(ZipOutputStream zip, String name, String content) throws IOException { zip.putNextEntry(new ZipEntry(name)); zip.write(content.getBytes("UTF-8")); zip.closeEntry(); }
    private static void copy(ZipOutputStream zip, String name, File from) throws IOException { zip.putNextEntry(new ZipEntry(name)); try (InputStream in = new FileInputStream(from)) { byte[] b = new byte[8192]; for (int n; (n=in.read(b)) != -1;) zip.write(b,0,n); } zip.closeEntry(); }
    private static String metadata() { return "nixgramx_version=" + BuildConfig.VERSION_NAME + "\nversion_code=" + BuildConfig.VERSION_CODE + "\ncommit=" + BuildConfig.DIAGNOSTIC_COMMIT + "\nbuild_type=" + BuildConfig.BUILD_TYPE + "\ntelegram_base=" + BuildConfig.OFFICIAL_VERSION + "\nandroid_sdk=" + Build.VERSION.SDK_INT + "\nandroid_version=" + Build.VERSION.RELEASE + "\nmanufacturer=" + Build.MANUFACTURER + "\nmodel=" + Build.MODEL + "\nabi=" + (Build.SUPPORTED_ABIS.length == 0 ? "unknown" : Build.SUPPORTED_ABIS[0]) + "\nlevel=" + level() + "\n"; }
}
