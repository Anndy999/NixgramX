package org.telegram.messenger;

import android.net.Network;
import android.os.SystemClock;

import androidx.media3.exoplayer.upstream.DefaultBandwidthMeter;

import java.util.Objects;

// Download speed in bits per second, measured from how fast consecutive chunks of one file arrive.
// Until a fresh measurement exists, falls back to the player's estimate, which starts from the network type.
public class DownloadSpeedEstimator {

    private static final long MAX_AGE_MS = 60_000;
    private static final long ROUTE_WARMUP_MS = 10_000;
    private static final long FAST_THRESHOLD = 3_000_000;

    private static double bitrate;
    private static long updatedAt;
    private static volatile long routeGeneration;
    private static volatile long networkGeneration;
    private static long conservativeUntil;
    private static Network network;
    private static int networkSubtype = -1;

    public static synchronized void onNetworkChanged(Network activeNetwork, int subtype) {
        if (!Objects.equals(network, activeNetwork) || networkSubtype != subtype) {
            network = activeNetwork;
            networkSubtype = subtype;
            networkGeneration++;
            onRouteChanged();
        }
    }

    public static synchronized void onRouteChanged() {
        routeGeneration++;
        updatedAt = 0;
        bitrate = 0;
        conservativeUntil = SystemClock.elapsedRealtime() + ROUTE_WARMUP_MS;
    }

    public static long getRouteGeneration() {
        return routeGeneration;
    }

    static long getNetworkGeneration() {
        return networkGeneration;
    }

    public static synchronized void onSample(long bits, long durationMs, long generation) {
        if (generation != routeGeneration || bits <= 0 || durationMs <= 0) {
            return;
        }
        double sample = bits * 1000.0 / durationMs;
        long now = SystemClock.elapsedRealtime();
        bitrate = updatedAt == 0 || now - updatedAt > MAX_AGE_MS ? sample : bitrate * 0.7 + sample * 0.3;
        updatedAt = now;
    }

    // Fast enough that preloading ahead and waiting for cancelled responses cost nothing noticeable.
    public static boolean isFast() {
        return getBitrate() >= FAST_THRESHOLD;
    }

    public static synchronized long getBitrate() {
        long now = SystemClock.elapsedRealtime();
        if (updatedAt != 0 && now - updatedAt <= MAX_AGE_MS) {
            return (long) bitrate;
        }
        long estimate = DefaultBandwidthMeter.getSingletonInstance(ApplicationLoader.applicationContext).getBitrateEstimate();
        return now < conservativeUntil ? Math.min(1_000_000L, estimate) : estimate;
    }
}
