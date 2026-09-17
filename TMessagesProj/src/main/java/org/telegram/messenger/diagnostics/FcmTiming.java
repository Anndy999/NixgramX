package org.telegram.messenger.diagnostics;

import android.os.SystemClock;

import org.telegram.messenger.BuildVars;
import org.telegram.messenger.FileLog;

/** Per-push, local-only timings. Accepts no push content or account identifiers. */
public final class FcmTiming {
    private final long receiver = SystemClock.elapsedRealtime();
    private final long receiverEpoch = System.currentTimeMillis();
    private final long sentEpoch;
    private final int originalPriority;
    private final int deliveredPriority;
    private long process;
    private long initStart;
    private long initEnd;
    private long queued;
    private long stage;
    private boolean finished;

    private FcmTiming(long sentEpoch, int originalPriority, int deliveredPriority) {
        this.sentEpoch = sentEpoch;
        this.originalPriority = originalPriority;
        this.deliveredPriority = deliveredPriority;
    }

    public static FcmTiming start(long sentEpoch, int originalPriority, int deliveredPriority) {
        return new FcmTiming(sentEpoch, originalPriority, deliveredPriority);
    }

    public void processEntered() { process = SystemClock.elapsedRealtime(); }
    public void initStarted() { initStart = SystemClock.elapsedRealtime(); }
    public void initCompleted() { initEnd = SystemClock.elapsedRealtime(); }
    public void stageQueued() { queued = SystemClock.elapsedRealtime(); }
    public void stageStarted() { stage = SystemClock.elapsedRealtime(); }

    // Called only on stageQueue: T5 before handoff, or finally for paths without T5.
    public void finish(boolean handoff) {
        if (finished) return;
        finished = true;
        long end = SystemClock.elapsedRealtime();
        long transportDelay = sentEpoch > 0 && sentEpoch <= receiverEpoch + 60_000L
                ? Math.max(0L, receiverEpoch - sentEpoch) : -1L;
        long clientProcessing = Math.max(0L, end - receiver);
        Diagnostics.fcmDelivery(transportDelay, clientProcessing, originalPriority, deliveredPriority);
        if (BuildVars.LOGS_ENABLED) {
            FileLog.d("FCM_TIMING receiver_epoch_ms=" + receiverEpoch
                    + " transport_delay_ms=" + transportDelay
                    + " original_priority=" + originalPriority
                    + " delivered_priority=" + deliveredPriority
                    + " receiver_to_process_ms=" + (process - receiver)
                    + " ui_queue_wait_ms=" + (initStart - process)
                    + " post_init_ms=" + (initEnd - initStart)
                    + " post_init_to_stage_enqueue_ms=" + (queued - initEnd)
                    + " stage_queue_wait_ms=" + (stage - queued)
                    + " push_parse_process_ms=" + (end - stage)
                    + " total_client_processing_ms=" + clientProcessing
                    + " endpoint=" + (handoff ? "T5" : "stage_exit")
                    + " notification_handoff_count=" + (handoff ? 1 : 0));
        }
    }
}
