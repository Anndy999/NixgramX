package org.telegram.messenger.diagnostics;

import android.app.Activity;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.AlertDialog;

/** Compatibility facade for existing NixgramX callers. FileLog remains the only IO backend. */
public final class Diagnostics {
    public enum Event { PUSH_RECEIVED, PUSH_PARSE_FAILED, TOKEN_REQUEST, TOKEN_OK, TOKEN_FAILED, PUSH_REGISTERED, PUSH_REGISTRATION_FAILED, SERVICE_START, SERVICE_STOP, SERVICE_FAILED, KEEP_ALIVE_CONFLICT, CONNECTION_STATE, PUSH_CONNECTION, UPDATE_CHECK, UPDATE_VERSION, UPDATE_PARSE_FAILED, UPDATE_FAILED, TRANSLATION_REQUEST, TRANSLATION_OK, TRANSLATION_FAILED, TRANSLATION_TIMEOUT, MEDIA_DOWNLOAD_FAILED, PROXY_CHANGE }
    public static volatile boolean keepAliveRunning;
    public static volatile int lastPushError;
    private Diagnostics() { }
    public static void event(Event event, int value) {
        NgxDiagnostics.Category category = event.name().startsWith("PUSH") || event.name().startsWith("TOKEN") || event.name().startsWith("SERVICE") ? NgxDiagnostics.Category.PUSH : event.name().startsWith("TRANSLATION") ? NgxDiagnostics.Category.TRANSLATION : event.name().contains("MEDIA") ? NgxDiagnostics.Category.MEDIA : event.name().contains("PROXY") || event.name().contains("CONNECTION") ? NgxDiagnostics.Category.NETWORK : NgxDiagnostics.Category.NIXGRAMX;
        if (event.name().contains("FAILED")) lastPushError = value;
        NgxDiagnostics.event(category, event.name(), NgxDiagnostics.Value.of(NgxDiagnostics.Field.VALUE, value));
    }
    public static void received(int provider) { event(Event.PUSH_RECEIVED, provider); }
    public static void pushConnection(int account, boolean enabled) { event(Event.PUSH_CONNECTION, enabled ? 1 : 0); }
    public static void crash(Thread thread, Throwable error) { NgxDiagnostics.event(NgxDiagnostics.Category.ERROR, "CRASH", NgxDiagnostics.Value.label(NgxDiagnostics.Field.THREAD, "UNKNOWN"), NgxDiagnostics.Value.label(NgxDiagnostics.Field.ERROR_CATEGORY, error == null ? "UNKNOWN" : error.getClass().getSimpleName())); }
    public static void show(Activity activity) {
        String message = "NGX Diagnostics: " + NgxDiagnostics.level() + "\nLocal-only, allowlisted structured events.\nCapture records 100 prior and 50 following safe events.";
        new AlertDialog.Builder(activity).setTitle(LocaleController.getString(R.string.NixDiagnostics)).setMessage(message)
            .setPositiveButton("Capture next action", (d, w) -> NgxDiagnostics.captureNextAction())
            .setNeutralButton("Export diagnostics", (d, w) -> NgxDiagnostics.export(activity))
            .setNegativeButton(LocaleController.getString(R.string.Close), null).show();
    }
    public static void promptLastCrash(Activity activity) { }
}
