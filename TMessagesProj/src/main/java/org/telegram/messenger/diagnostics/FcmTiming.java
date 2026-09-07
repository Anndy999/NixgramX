package org.telegram.messenger.diagnostics;

import android.os.SystemClock;

import org.telegram.messenger.BuildVars;
import org.telegram.messenger.FileLog;

/** Per-push, local-only timings. Accepts no push content or account identifiers. */
public final class FcmTiming {
    private final long receiver = SystemClock.elapsedRealtime();
    private final long receiverEpoch = System.currentTimeMillis();
    private long process;
    private long initStart;
    private long initEnd;
    private long queued;
    private long stage;
    private boolean finished;

    private FcmTiming() {}

    public static FcmTiming start() {
        return BuildVars.LOGS_ENABLED ? new FcmTiming() : null;
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
        FileLog.d("FCM_TIMING receiver_epoch_ms=" + receiverEpoch
                + " receiver_to_process_ms=" + (process - receiver)
                + " ui_queue_wait_ms=" + (initStart - process)
                + " post_init_ms=" + (initEnd - initStart)
                + " post_init_to_stage_enqueue_ms=" + (queued - initEnd)
                + " stage_queue_wait_ms=" + (stage - queued)
                + " push_parse_process_ms=" + (end - stage)
                + " total_client_processing_ms=" + (end - receiver)
                + " endpoint=" + (handoff ? "T5" : "stage_exit")
                + " notification_handoff_count=" + (handoff ? 1 : 0));
    }
}
