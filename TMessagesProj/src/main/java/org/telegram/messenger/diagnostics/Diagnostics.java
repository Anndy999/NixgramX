package org.telegram.messenger.diagnostics;

import android.app.Activity;
import android.content.Context;
import android.os.Build;
import android.os.PowerManager;
import androidx.core.app.NotificationManagerCompat;
import org.telegram.messenger.*;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.ui.ActionBar.AlertDialog;
import java.io.File;
import java.lang.ref.WeakReference;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** Local-only allowlisted events. Never pass payloads, paths, URLs or exception messages. */
public final class Diagnostics {
    public enum Event {
        PUSH_RECEIVED, PUSH_PARSE_FAILED, TOKEN_REQUEST, TOKEN_OK, TOKEN_FAILED,
        PUSH_REGISTERED, PUSH_REGISTRATION_FAILED, SERVICE_START, SERVICE_STOP, SERVICE_FAILED, KEEP_ALIVE_CONFLICT,
        CONNECTION_STATE, PUSH_CONNECTION, UPDATE_CHECK, UPDATE_VERSION, UPDATE_PARSE_FAILED, UPDATE_FAILED,
        TRANSLATION_REQUEST, TRANSLATION_OK, TRANSLATION_FAILED, TRANSLATION_TIMEOUT, MEDIA_DOWNLOAD_FAILED, PROXY_CHANGE
    }
    private static final java.util.concurrent.atomic.AtomicLong dropped = new java.util.concurrent.atomic.AtomicLong();
    private static final ThreadPoolExecutor IO = new ThreadPoolExecutor(1, 1, 30, TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(128), r -> { Thread t = new Thread(r, "NixgramX-diagnostics"); t.setDaemon(true); return t; },
            (task, executor) -> dropped.incrementAndGet());
    private static final AtomicBoolean crashPrompted = new AtomicBoolean();
    private static volatile boolean ioFailed;
    public static volatile boolean keepAliveRunning;
    public static volatile int lastPushError;
    private static volatile int tokenFetchResult;
    private static final java.util.concurrent.atomic.AtomicIntegerArray pushConnections =
            new java.util.concurrent.atomic.AtomicIntegerArray(UserConfig.MAX_ACCOUNT_COUNT);

    private static DiagnosticStore store() {
        return new DiagnosticStore(new File(ApplicationLoader.applicationContext.getNoBackupFilesDir(), "diagnostics"));
    }

    public static void event(Event event, int value) {
        try {
            if (event == Event.TOKEN_REQUEST) tokenFetchResult = 1;
            if (event == Event.TOKEN_OK) tokenFetchResult = 2;
            if (event == Event.TOKEN_FAILED) { tokenFetchResult = 3; lastPushError = 2; }
            if (event == Event.PUSH_REGISTRATION_FAILED) lastPushError = 3;
            if ("stable".equals(BuildConfig.NIXGRAMX_CHANNEL) && !BuildConfig.DEBUG &&
                    (event == Event.TOKEN_REQUEST || event == Event.TRANSLATION_REQUEST || event == Event.TRANSLATION_OK)) return;
            final long time = System.currentTimeMillis();
            IO.execute(() -> {
                try { store().append(time + " " + (event.name().contains("FAILED") || event == Event.TRANSLATION_TIMEOUT ? "WARN " : "STATE ") + event.name() + " " + value + "\n"); }
                catch (Throwable failure) { ioFailed = true; }
            });
        } catch (Throwable failure) { ioFailed = true; }
    }

    public static void received(int provider) {
        final long time = System.currentTimeMillis();
        SharedConfig.pushLastReceivedTime = time;
        try {
            IO.execute(() -> {
                try {
                    ApplicationLoader.applicationContext.getSharedPreferences("nixgramx_diagnostics", Context.MODE_PRIVATE)
                            .edit().putLong("pushLastReceivedTime", time).apply();
                } catch (Throwable failure) { ioFailed = true; }
            });
        } catch (Throwable failure) { ioFailed = true; }
        // Timestamp is enough for normal delivery; no per-message disk log in Stable.
        if (BuildConfig.DEBUG || !"stable".equals(BuildConfig.NIXGRAMX_CHANNEL)) event(Event.PUSH_RECEIVED, provider);
    }

    public static void pushConnection(int account, boolean enabled) {
        int next = enabled ? 2 : 1;
        if (pushConnections.getAndSet(account, next) != next) event(Event.PUSH_CONNECTION, enabled ? 1 : 0);
    }

    private static String header() {
        return "NixgramX " + BuildConfig.VERSION_NAME + " (" + BuildConfig.VERSION_CODE + ")\ncommit "
                + BuildConfig.DIAGNOSTIC_COMMIT + "\nAndroid " + Build.VERSION.RELEASE + " API " + Build.VERSION.SDK_INT
                + "\n" + Build.MANUFACTURER + " " + Build.MODEL + "\n";
    }

    public static void crash(Thread thread, Throwable error) {
        try {
            store().crash(header() + "time " + System.currentTimeMillis() + "\nthread id " + thread.getId()
                    + "\n" + DiagnosticStore.safeStack(error));
        } catch (Throwable failure) { /* Never prevent the original crash handler from running. */ }
    }

    private static String snapshot() {
        StringBuilder out = new StringBuilder(header());
        out.append("Push type: ").append(xyz.nextalone.nagram.NaConfig.INSTANCE.getPushServiceType().Int());
        out.append("\nPlay Services status (0=available): ").append(GooglePushListenerServiceProvider.checkPlayServicesStatusCode());
        String token = SharedConfig.pushString;
        out.append("\nToken: ").append(token == null || token.isEmpty() ? "missing" : "present")
                .append("; length ").append(token == null ? 0 : token.length());
        out.append("\nToken fetch: ").append(tokenFetchResult == 1 ? "pending" : tokenFetchResult == 2 ? "PASS (token fetch only)" :
                tokenFetchResult == 3 ? "FAIL" : "NOT TESTED this process");
        long received = SharedConfig.pushLastReceivedTime;
        if (received == 0) received = ApplicationLoader.applicationContext.getSharedPreferences("nixgramx_diagnostics", 0)
                .getLong("pushLastReceivedTime", 0);
        out.append("\nLast received Push (device UTC epoch ms; not delivery proof): ").append(received == 0 ? "NOT TESTED" : received);
        out.append("\nLast Push error code (0=none observed,1=parse,2=token,3=registration): ").append(lastPushError);
        out.append("\nKeep Alive service observed: ").append(keepAliveRunning);
        for (int a = 0; a < UserConfig.MAX_ACCOUNT_COUNT; a++) {
            if (!UserConfig.getInstance(a).isClientActivated()) continue;
            out.append("\nAccount slot ").append(a).append(" registeredForPush: ").append(UserConfig.getInstance(a).registeredForPush);
            out.append("; network state: ").append(ConnectionsManager.getInstance(a).getConnectionState());
            out.append("; DC: ").append(ConnectionsManager.getInstance(a).getCurrentDatacenterId());
            int state = pushConnections.get(a);
            out.append("; push connection requested: ").append(state == 0 ? "UNKNOWN" : state == 2 ? "enabled" : "disabled");
        }
        Context context = ApplicationLoader.applicationContext;
        out.append("\nOS notifications enabled: ").append(NotificationManagerCompat.from(context).areNotificationsEnabled());
        PowerManager power = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        out.append("\nBattery optimization exempt: ").append(power != null && power.isIgnoringBatteryOptimizations(context.getPackageName()));
        out.append("\nDiagnostics I/O failure: ").append(ioFailed).append("; dropped tasks: ").append(dropped.get());
        out.append("\nUpdater status: see UPDATE events below; absent = NOT TESTED");
        out.append("\nTranslation / media: see fixed events; absent = NOT TESTED");
        return out.toString();
    }

    public static void show(Activity activity) {
        WeakReference<Activity> target = new WeakReference<>(activity);
        IO.execute(() -> {
            String report;
            try { report = snapshot() + "\n\nRecent events / errors\n" + store().read("events.1", 8192)
                    + store().read("events.0", 16384) + "\nLast crash\n" + store().read("last-crash.txt", DiagnosticStore.CRASH_BYTES); }
            catch (Throwable failure) { report = "Diagnostics unavailable (local I/O or status query failed)"; }
            final String text = report;
            AndroidUtilities.runOnUIThread(() -> {
                Activity current = target.get();
                if (current == null || current.isFinishing() || current.isDestroyed()) return;
                new AlertDialog.Builder(current).setTitle(LocaleController.getString(R.string.NixDiagnostics))
                    .setMessage(text).setPositiveButton(LocaleController.getString(R.string.Copy), (d, w) -> AndroidUtilities.addToClipboard(text))
                    .setNeutralButton(LocaleController.getString(R.string.Clear), (d, w) -> IO.execute(() -> {
                        try { store().clear(); ioFailed = false; } catch (Throwable failure) { ioFailed = true; }
                    })).setNegativeButton(LocaleController.getString(R.string.Close), null).show();
            });
        });
    }

    public static void promptLastCrash(Activity activity) {
        if (!crashPrompted.compareAndSet(false, true)) return;
        WeakReference<Activity> target = new WeakReference<>(activity);
        IO.execute(() -> {
            try {
                String report = store().read("last-crash.txt", DiagnosticStore.CRASH_BYTES);
                if (report.isEmpty()) return;
                AndroidUtilities.runOnUIThread(() -> {
                    Activity current = target.get();
                    if (current == null || current.isFinishing() || current.isDestroyed()) { crashPrompted.set(false); return; }
                    new AlertDialog.Builder(current).setTitle(LocaleController.getString(R.string.NixLastCrash))
                            .setMessage(LocaleController.getString(R.string.NixCrashPrivacy))
                            .setPositiveButton(LocaleController.getString(R.string.Copy), (d, w) -> AndroidUtilities.addToClipboard(report))
                            .setNegativeButton(LocaleController.getString(R.string.Close), null).show();
                });
            } catch (Throwable failure) { ioFailed = true; }
        });
    }
}
