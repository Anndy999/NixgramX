package org.telegram.messenger.diagnostics;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Bounded, drop-on-overflow diagnostic writer. Lazy-started so OFF does no
 * thread work. Epoch skips stale line jobs after clear().
 */
public final class NgxDiagnosticWriter {
    public interface Sink {
        void append(String line) throws Exception;
        void clear() throws Exception;
    }

    private static final int QUEUE_LIMIT = 128;

    private final Object lock = new Object();
    private final Sink sink;
    private final ArrayBlockingQueue<Job> queue = new ArrayBlockingQueue<>(QUEUE_LIMIT);
    private final AtomicInteger epoch = new AtomicInteger();
    private final AtomicLong dropped = new AtomicLong();
    private volatile boolean started;

    private interface Job {}

    private static final class LineJob implements Job {
        final String line;
        final int epoch;
        LineJob(String line, int epoch) {
            this.line = line;
            this.epoch = epoch;
        }
    }

    private static final class FlushJob implements Job {
        final CountDownLatch latch;
        FlushJob(CountDownLatch latch) {
            this.latch = latch;
        }
    }

    public NgxDiagnosticWriter(Sink sink) {
        this.sink = sink;
    }

    public long dropped() {
        return dropped.get();
    }

    public boolean isStarted() {
        return started;
    }

    public void persist(String line) {
        if (line == null || line.isEmpty()) return;
        if (!enqueue(line)) dropped.incrementAndGet();
        else start();
    }

    boolean enqueue(String line) {
        if (line == null || line.isEmpty()) return false;
        return queue.offer(new LineJob(line, epoch.get()));
    }

    /**
     * Waits until previously offered lines are handled. Returns false if the
     * flush token cannot be queued or the writer does not catch up in time.
     * Callers must not export on false: the on-disk log may be truncated.
     */
    public boolean flush(long timeoutMs) {
        if (!started) return true;
        if (timeoutMs < 0) timeoutMs = 0;
        CountDownLatch latch = new CountDownLatch(1);
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(Math.max(1L, timeoutMs));
        try {
            if (!queue.offer(new FlushJob(latch), timeoutMs, TimeUnit.MILLISECONDS)) {
                dropped.incrementAndGet();
                return false;
            }
            long left = deadline - System.nanoTime();
            if (left <= 0) return false;
            return latch.await(left, TimeUnit.NANOSECONDS);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return false;
        } catch (Throwable ignored) {
            return false;
        }
    }

    public void clear() {
        synchronized (lock) {
            epoch.incrementAndGet();
            queue.removeIf(job -> job instanceof LineJob);
            dropped.set(0);
            try {
                if (sink != null) sink.clear();
            } catch (Throwable ignored) {
            }
        }
    }

    void start() {
        if (started) return;
        synchronized (lock) {
            if (started) return;
            Thread thread = new Thread(this::loop, "ngx-diag");
            thread.setDaemon(true);
            thread.start();
            started = true;
        }
    }

    private void loop() {
        while (true) {
            try {
                handle(queue.take());
                Job extra;
                while ((extra = queue.poll()) != null) handle(extra);
            } catch (InterruptedException ignored) {
                return;
            } catch (Throwable ignored) {
                dropped.incrementAndGet();
            }
        }
    }

    private void handle(Job job) {
        if (job instanceof FlushJob) {
            ((FlushJob) job).latch.countDown();
            return;
        }
        if (!(job instanceof LineJob)) return;
        LineJob line = (LineJob) job;
        synchronized (lock) {
            if (line.epoch != epoch.get()) return;
            try {
                if (sink != null) sink.append(line.line);
            } catch (Throwable ignored) {
                dropped.incrementAndGet();
            }
        }
    }
}
