package org.telegram.messenger;

import android.content.SharedPreferences;
import android.os.SystemClock;

import org.telegram.tgnet.ConnectionsManager;

import java.util.Arrays;
import java.util.List;
import java.util.WeakHashMap;

public class ProxyRotationController implements NotificationCenter.NotificationCenterDelegate {
    private final static ProxyRotationController INSTANCE = new ProxyRotationController();

    public final static int DEFAULT_TIMEOUT_INDEX = 1;
    public final static List<Integer> ROTATION_TIMEOUTS = Arrays.asList(
            5, 10, 15, 30, 60
    );

    private static final long CHECK_WINDOW_MS = 2500;
    private static final long QUALITY_MAX_AGE_MS = 5 * 60_000;
    private static final long SWITCH_COOLDOWN_MS = 60_000;
    private static final long SLOW_WINDOW_MS = 8000;
    private static final long SLOW_BITRATE = 750_000;

    private final WeakHashMap<SharedConfig.ProxyInfo, DownloadQuality> quality = new WeakHashMap<>();
    private long qualityNetwork = -1;
    private long lastSwitchAt = -SWITCH_COOLDOWN_MS;
    private long lastSampleRoute = -1;
    private long slowSince;
    private long lastSampleAt;
    private int slowSamples;
    private boolean isCurrentlyChecking;
    private final Runnable finishProxyChecks = () -> {
        if (isCurrentlyChecking) switchToAvailable();
    };

    private static final class DownloadQuality {
        final long time;
        final long bitrate;

        DownloadQuality(long time, long bitrate) {
            this.time = time;
            this.bitrate = bitrate;
        }
    }
    private Runnable checkProxyAndSwitchRunnable = () -> {
        isCurrentlyChecking = true;

        int currentAccount = UserConfig.selectedAccount;
        boolean startedCheck = false;
        for (int i = 0; i < SharedConfig.proxyList.size(); i++) {
            SharedConfig.ProxyInfo proxyInfo = SharedConfig.proxyList.get(i);
            if (proxyInfo.checking || SystemClock.elapsedRealtime() - proxyInfo.availableCheckTime < 2 * 60 * 1000) {
                continue;
            }
            startedCheck = true;
            proxyInfo.checking = true;
            ConnectionsManager.getInstance(currentAccount).checkProxy(proxyInfo.settings, time -> AndroidUtilities.runOnUIThread(() -> {
                proxyInfo.availableCheckTime = SystemClock.elapsedRealtime();
                proxyInfo.checking = false;
                if (time == -1) {
                    proxyInfo.available = false;
                    proxyInfo.ping = 0;
                } else {
                    proxyInfo.ping = time;
                    proxyInfo.available = true;
                }
                NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.proxyCheckDone, proxyInfo);
            }));
        }

        if (startedCheck) {
            AndroidUtilities.runOnUIThread(finishProxyChecks, CHECK_WINDOW_MS);
        } else {
            switchToAvailable();
        }
    };

    public static void init() {
        INSTANCE.initInternal();
    }

    static void onForegroundDownloadSample(int account, long route, long bitrate) {
        if (!SharedConfig.proxyRotationEnabled) return;
        AndroidUtilities.runOnUIThread(() -> INSTANCE.recordDownloadSample(account, route, bitrate));
    }

    private void recordDownloadSample(int account, long route, long bitrate) {
        SharedConfig.ProxyInfo current = SharedConfig.currentProxy;
        if (route != DownloadSpeedEstimator.getRouteGeneration() || account != UserConfig.selectedAccount
                || !SharedConfig.proxyRotationEnabled || !SharedConfig.isProxyEnabled() || current == null
                || ConnectionsManager.getInstance(account).getConnectionState() != ConnectionsManager.ConnectionStateConnected) {
            return;
        }
        refreshNetwork();
        long now = SystemClock.elapsedRealtime();
        long previous = measuredBitrate(current, now);
        long average = previous == 0 ? bitrate : (previous * 7 + bitrate * 3) / 10;
        quality.put(current, new DownloadQuality(now, average));

        if (route != lastSampleRoute || now - lastSampleAt > 6000 || bitrate > SLOW_BITRATE) {
            slowSamples = 0;
        }
        lastSampleRoute = route;
        lastSampleAt = now;
        if (bitrate > SLOW_BITRATE) return;
        if (slowSamples == 0) slowSince = now;
        slowSamples++;
        if (slowSamples < 3 || now - slowSince < SLOW_WINDOW_MS || now - lastSwitchAt < SWITCH_COOLDOWN_MS) return;

        SharedConfig.ProxyInfo alternative = chooseAvailable(now, Math.max(1_500_000, average * 2));
        if (alternative != null) switchProxy(alternative);
    }

    private void refreshNetwork() {
        long network = DownloadSpeedEstimator.getNetworkGeneration();
        if (network != qualityNetwork) {
            qualityNetwork = network;
            quality.clear();
            slowSamples = 0;
        }
    }

    private long measuredBitrate(SharedConfig.ProxyInfo info, long now) {
        DownloadQuality sample = quality.get(info);
        return sample != null && now - sample.time <= QUALITY_MAX_AGE_MS ? sample.bitrate : 0;
    }

    private SharedConfig.ProxyInfo chooseAvailable(long now, long minBitrate) {
        SharedConfig.ProxyInfo best = null;
        long bestRate = 0;
        for (SharedConfig.ProxyInfo candidate : SharedConfig.proxyList) {
            if (candidate == SharedConfig.currentProxy || candidate.checking || !candidate.available) continue;
            long rate = measuredBitrate(candidate, now);
            if (rate < minBitrate) continue;
            if (best == null || rate > bestRate || rate == bestRate && candidate.ping < best.ping) {
                best = candidate;
                bestRate = rate;
            }
        }
        return best;
    }

    private void stopChecks() {
        isCurrentlyChecking = false;
        AndroidUtilities.cancelRunOnUIThread(checkProxyAndSwitchRunnable);
        AndroidUtilities.cancelRunOnUIThread(finishProxyChecks);
    }

    private void switchToAvailable() {
        if (!SharedConfig.isProxyEnabled() || !SharedConfig.proxyRotationEnabled) {
            stopChecks();
            return;
        }
        refreshNetwork();
        SharedConfig.ProxyInfo best = chooseAvailable(SystemClock.elapsedRealtime(), 0);
        if (best != null) {
            switchProxy(best);
            return;
        }
        for (SharedConfig.ProxyInfo info : SharedConfig.proxyList) {
            if (info.checking) return;
        }
        stopChecks();
    }

    private void switchProxy(SharedConfig.ProxyInfo info) {
        stopChecks();
        lastSwitchAt = SystemClock.elapsedRealtime();
        slowSamples = 0;
        SharedPreferences.Editor editor = MessagesController.getGlobalMainSettings().edit();
        editor.putBoolean("proxy_enabled", true);
        info.settings.toSharedPreferences(editor);
        editor.apply();

        SharedConfig.currentProxy = info;
        ConnectionsManager.setProxySettings(true, info.settings);
        NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.proxySettingsChanged);
        NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.proxyChangedByRotation);
    }

    private void initInternal() {
        for (int i = 0; i < UserConfig.MAX_ACCOUNT_COUNT; i++) {
            NotificationCenter.getInstance(i).addObserver(this, NotificationCenter.didUpdateConnectionState);
        }
        NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.proxyCheckDone);
        NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.proxySettingsChanged);
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.proxyCheckDone) {
            if (!SharedConfig.isProxyEnabled() || !SharedConfig.proxyRotationEnabled || SharedConfig.proxyList.size() <= 1 || !isCurrentlyChecking) {
                return;
            }

            for (SharedConfig.ProxyInfo info : SharedConfig.proxyList) {
                if (info.checking) return;
            }
            switchToAvailable();
        } else if (id == NotificationCenter.proxySettingsChanged) {
            stopChecks();
            slowSamples = 0;
        } else if (id == NotificationCenter.didUpdateConnectionState && account == UserConfig.selectedAccount) {
            if (!SharedConfig.isProxyEnabled() || !SharedConfig.proxyRotationEnabled || SharedConfig.proxyList.size() <= 1) {
                stopChecks();
                return;
            }

            int state = ConnectionsManager.getInstance(account).getConnectionState();

            if (state == ConnectionsManager.ConnectionStateConnectingToProxy) {
                if (!isCurrentlyChecking) {
                    AndroidUtilities.runOnUIThread(checkProxyAndSwitchRunnable, ROTATION_TIMEOUTS.get(SharedConfig.proxyRotationTimeout) * 1000L);
                }
            } else {
                stopChecks();
            }
        }
    }
}
