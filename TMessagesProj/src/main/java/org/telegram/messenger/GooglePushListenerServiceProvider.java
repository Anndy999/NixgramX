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
        String currentPushString = SharedConfig.pushString;
        if (!TextUtils.isEmpty(currentPushString)) {
            if (BuildVars.DEBUG_PRIVATE_VERSION) {
                FileLog.d("FCM regId = " + currentPushString);
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
                                    FileLog.e(exception);
                                    String message = exception.getMessage();
                                    SharedConfig.pushStringLastError = !TextUtils.isEmpty(message) ? message : exception.toString();
                                } else {
                                    FileLog.e("Failed to get FCM regid (no exception)");
                                    SharedConfig.pushStringLastError = "getToken failed (no exception)";
                                }
                                SharedConfig.pushStringStatus = "__FIREBASE_FAILED__";
                                PushListenerController.sendRegistrationToServer(getPushType(), null);
                                return;
                            }
                            SharedConfig.pushStringLastError = "";
                            String token = task.getResult();
                            if (!TextUtils.isEmpty(token)) {
                                PushListenerController.sendRegistrationToServer(getPushType(), token);
                            }
                        });
            } catch (Throwable e) {
                FileLog.e(e);
                String message = e.getMessage();
                SharedConfig.pushStringLastError = !TextUtils.isEmpty(message) ? message : e.toString();
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
