package org.telegram.messenger;

import android.os.SystemClock;
import android.text.TextUtils;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.firebase.FirebaseApp;
import com.google.firebase.messaging.FirebaseMessaging;

public class GooglePushListenerServiceProvider implements PushListenerController.IPushListenerServiceProvider {

    private Boolean hasServices;
    private int lastPlayServicesStatusCode = ConnectionResult.SUCCESS;

    public GooglePushListenerServiceProvider() {}

    @Override
    public String getLogTitle() {
        return "Google Play Services";
    }

    @Override
    public int getPushType() {
        return PushListenerController.PUSH_TYPE_FIREBASE;
    }

    /** Clear cached Play Services availability so the next hasServices() re-queries GMS. */
    public void reset() {
        hasServices = null;
    }

    /**
     * Fresh GoogleApiAvailability check (does not use the cached {@link #hasServices} flag).
     * @return ConnectionResult code (SUCCESS == 0 when Play Services are usable)
     */
    public static int checkPlayServicesStatusCode() {
        try {
            return GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(ApplicationLoader.applicationContext);
        } catch (Exception e) {
            FileLog.e(e);
            return ConnectionResult.SERVICE_MISSING;
        }
    }

    public int getLastPlayServicesStatusCode() {
        if (hasServices == null) {
            hasServices();
        }
        return lastPlayServicesStatusCode;
    }

    @Override
    public void onRequestPushToken() {
        org.telegram.messenger.diagnostics.Diagnostics.event(org.telegram.messenger.diagnostics.Diagnostics.Event.TOKEN_REQUEST, 0);
        String currentPushString = SharedConfig.pushString;
        if (!TextUtils.isEmpty(currentPushString)) {
            if (BuildVars.DEBUG_PRIVATE_VERSION) {
                FileLog.d("FCM token present (value omitted)");
            }
        } else {
            FileLog.d("FCM Registration not found.");
        }
        Utilities.globalQueue.postRunnable(() -> {
            try {
                SharedConfig.pushStringGetTimeStart = SystemClock.elapsedRealtime();
                FirebaseApp.initializeApp(ApplicationLoader.applicationContext);
                FirebaseMessaging.getInstance().getToken()
                        .addOnCompleteListener(task -> {
                            SharedConfig.pushStringGetTimeEnd = SystemClock.elapsedRealtime();
                            if (!task.isSuccessful()) {
                                Exception exception = task.getException();
                                if (exception != null) {
                                    FileLog.e("FCM token request failed");
                                    SharedConfig.pushStringLastError = exception.getClass().getSimpleName();
                                } else {
                                    FileLog.e("Failed to get FCM regid (no exception)");
                                    SharedConfig.pushStringLastError = "getToken failed (no exception)";
                                }
                                org.telegram.messenger.diagnostics.Diagnostics.event(org.telegram.messenger.diagnostics.Diagnostics.Event.TOKEN_FAILED, 0);
                                SharedConfig.pushStringStatus = "__FIREBASE_FAILED__";
                                PushListenerController.sendRegistrationToServer(getPushType(), null);
                                return;
                            }
                            SharedConfig.pushStringLastError = "";
                            // Clear stale __FIREBASE_GENERATING_SINCE_*__ / prior status on success.
                            SharedConfig.pushStringStatus = "";
                            String token = task.getResult();
                            if (!TextUtils.isEmpty(token)) {
                                org.telegram.messenger.diagnostics.Diagnostics.event(org.telegram.messenger.diagnostics.Diagnostics.Event.TOKEN_OK, token.length());
                                PushListenerController.sendRegistrationToServer(getPushType(), token);
                            }
                        });
            } catch (Throwable e) {
                FileLog.e("FCM token request failed (details omitted)");
                SharedConfig.pushStringLastError = e.getClass().getSimpleName();
                org.telegram.messenger.diagnostics.Diagnostics.event(org.telegram.messenger.diagnostics.Diagnostics.Event.TOKEN_FAILED, 0);
                SharedConfig.pushStringStatus = "__FIREBASE_FAILED__";
            }
        });
    }

    @Override
    public boolean hasServices() {
        if (hasServices == null) {
            try {
                lastPlayServicesStatusCode = GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(ApplicationLoader.applicationContext);
                hasServices = lastPlayServicesStatusCode == ConnectionResult.SUCCESS;
            } catch (Exception e) {
                FileLog.e(e);
                lastPlayServicesStatusCode = ConnectionResult.SERVICE_MISSING;
                hasServices = false;
            }
        }
        return hasServices;
    }
}
