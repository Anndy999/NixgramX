package org.telegram.messenger.diagnostics;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.BuildConfig;
import org.telegram.messenger.diagnostics.NgxDiagnosticCore.Category;
import org.telegram.messenger.diagnostics.NgxDiagnosticCore.Level;
import org.telegram.messenger.diagnostics.NgxDiagnosticCore.Value;
import org.telegram.messenger.diagnostics.NgxDiagnosticExport.Meta;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Android facade over {@link NgxDiagnosticCore}. Diagnostics may drop events
 * and must never crash Telegram. Persistence uses a bounded local writer
 * instead of FileLog: FileLog's queue is private, unbounded, and mixed with
 * MTProto/network dumps.
 */
public final class NgxDiagnostics {
    public interface Listener {
        void onDiagnosticsChanged();
    }

    private static final Object LOCK = new Object();
    private static final NgxDiagnosticCore CORE = new NgxDiagnosticCore();
    private static final ArrayList<Listener> listeners = new ArrayList<>();

    private static volatile boolean inited;
    private static SharedPreferences prefs;
    private static NgxDiagnosticStore store;
    private static NgxDiagnosticWriter writer;
    private static File zipDir;
    private static volatile boolean lastCapturing;

    private NgxDiagnostics() {}

    public static boolean enabled(Level required) {
        return CORE.enabled(required);
    }

    public static Level level() {
        return CORE.level();
    }

    public static boolean isCapturing() {
        return CORE.isCapturing();
    }

    public static int remaining() {
        return CORE.remaining();
    }

    public static long dropped() {
        NgxDiagnosticWriter current = writer;
        return CORE.dropped() + (current == null ? 0 : current.dropped());
    }

    public static List<String> snapshot() {
        return CORE.snapshot();
    }

    public static void init(Context context) {
        try {
            if (context == null) return;
            Context app = context.getApplicationContext();
            if (app == null) app = context;
            synchronized (LOCK) {
                if (inited) return;
                prefs = app.getSharedPreferences("ngx_diagnostics", Context.MODE_PRIVATE);
                store = new NgxDiagnosticStore(new File(app.getNoBackupFilesDir(), "ngx_diagnostics"));
                writer = new NgxDiagnosticWriter(new NgxDiagnosticWriter.Sink() {
                    @Override
                    public void append(String line) throws Exception {
                        store.append(line);
                    }

                    @Override
                    public void clear() throws Exception {
                        store.clear();
                    }
                });
                zipDir = new File(app.getCacheDir(), "diagnostics");
                CORE.setLevel(parseLevel(prefs.getString("level", "OFF")));
                inited = true;
                lastCapturing = CORE.isCapturing();
            }
        } catch (Throwable ignored) {
        }
    }

    public static boolean setLevel(Level next) {
        try {
            if (CORE.isCapturing()) return false;
            CORE.setLevel(next);
            persistLevel(CORE.level());
            notifyChanged();
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static boolean capture() {
        try {
            boolean started = CORE.capture();
            if (started) {
                lastCapturing = true;
                persistLine(lastLine());
                notifyChanged();
            }
            return started;
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static boolean cancelCapture() {
        try {
            boolean cancelled = CORE.cancelCapture();
            if (cancelled) {
                lastCapturing = false;
                persistLine(lastLine());
                persistLevel(CORE.level());
                notifyChanged();
            }
            return cancelled;
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static void event(Category category, String name, Value... values) {
        try {
            if (!CORE.enabled(Level.DIAGNOSTIC) && !CORE.isCapturing()) return;
            String line = CORE.event(category, name, values);
            if (line != null) persistLine(line);
            boolean capturing = CORE.isCapturing();
            if (lastCapturing && !capturing) {
                lastCapturing = false;
                persistLevel(CORE.level());
                notifyChanged();
            } else {
                lastCapturing = capturing;
            }
        } catch (Throwable ignored) {
        }
    }

    public static int runSelfTest() {
        try {
            int n = NgxDiagnosticSelfTest.run(CORE, NgxDiagnostics::persistLine);
            boolean capturing = CORE.isCapturing();
            if (lastCapturing && !capturing) {
                lastCapturing = false;
                persistLevel(CORE.level());
            }
            lastCapturing = capturing;
            notifyChanged();
            return n;
        } catch (Throwable ignored) {
            return 0;
        }
    }

    public static void clear() {
        try {
            CORE.clear();
            lastCapturing = false;
            if (writer != null) writer.clear();
            persistLevel(CORE.level());
            notifyChanged();
        } catch (Throwable ignored) {
        }
    }

    public static File exportZip() {
        try {
            NgxDiagnosticWriter current = writer;
            if (current != null && !current.flush(2000)) return null;
            String log = "";
            if (store != null) log = store.readAll();
            if (log.isEmpty()) {
                StringBuilder ring = new StringBuilder();
                for (String line : CORE.snapshot()) {
                    ring.append(line).append('\n');
                }
                log = ring.toString();
            }
            Meta meta = metadata(log);
            File dir = zipDir != null ? zipDir : new File("diagnostics");
            dir.mkdirs();
            String name = "NixgramX-diagnostics-"
                    + new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(new Date())
                    + ".zip";
            File zip = NgxDiagnosticExport.writeZip(new File(dir, name), log, meta);
            NgxDiagnosticExport.pruneZips(dir);
            return zip;
        } catch (Throwable ignored) {
            return null;
        }
    }

    public static Meta metadata() {
        return metadata("");
    }

    public static void addListener(Listener listener) {
        if (listener == null) return;
        synchronized (listeners) {
            if (!listeners.contains(listener)) listeners.add(listener);
        }
    }

    public static void removeListener(Listener listener) {
        synchronized (listeners) {
            listeners.remove(listener);
        }
    }

    public static int storedEstimate() {
        try {
            return CORE.snapshot().size();
        } catch (Throwable ignored) {
            return 0;
        }
    }

    static NgxDiagnosticCore core() {
        return CORE;
    }

    static NgxDiagnosticStore store() {
        return store;
    }

    private static Meta metadata(String log) {
        Meta meta = new Meta();
        try {
            meta.versionName = BuildConfig.VERSION_NAME;
            meta.versionCode = Integer.toString(BuildConfig.VERSION_CODE);
            meta.buildType = BuildConfig.NIXGRAMX_CHANNEL;
            meta.commit = BuildConfig.DIAGNOSTIC_COMMIT;
            meta.androidVersion = Build.VERSION.RELEASE;
            meta.sdk = Integer.toString(Build.VERSION.SDK_INT);
            meta.manufacturer = Build.MANUFACTURER;
            meta.model = Build.MODEL;
            meta.diagnosticLevel = CORE.level().name();
            meta.captureActive = CORE.isCapturing() ? "TRUE" : "FALSE";
            int events = 0;
            if (log != null && !log.isEmpty()) {
                for (int i = 0; i < log.length(); i++) if (log.charAt(i) == '\n') events++;
            } else {
                events = CORE.snapshot().size();
            }
            meta.eventCount = Integer.toString(events);
            meta.droppedCount = Long.toString(dropped());
            meta.exportTimestamp = Long.toString(System.currentTimeMillis());
        } catch (Throwable ignored) {
        }
        return meta;
    }

    private static void persistLevel(Level level) {
        try {
            if (prefs == null) return;
            prefs.edit().putString("level", level == null ? "OFF" : level.name()).apply();
        } catch (Throwable ignored) {
        }
    }

    private static Level parseLevel(String name) {
        if ("DIAGNOSTIC".equals(name)) return Level.DIAGNOSTIC;
        if ("TRACE".equals(name)) return Level.TRACE;
        return Level.OFF;
    }

    private static String lastLine() {
        List<String> snap = CORE.snapshot();
        return snap.isEmpty() ? null : snap.get(snap.size() - 1);
    }

    private static void persistLine(String line) {
        NgxDiagnosticWriter current = writer;
        if (line == null || current == null) return;
        current.persist(line);
    }

    private static void notifyChanged() {
        try {
            Listener[] copy;
            synchronized (listeners) {
                copy = listeners.toArray(new Listener[0]);
            }
            Runnable run = () -> {
                for (Listener listener : copy) {
                    try {
                        listener.onDiagnosticsChanged();
                    } catch (Throwable ignored) {
                    }
                }
            };
            AndroidUtilities.runOnUIThread(run);
        } catch (Throwable ignored) {
        }
    }
}
