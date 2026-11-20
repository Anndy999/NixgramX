package org.telegram.messenger;

import org.telegram.tgnet.TLRPC;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

public class AnimatedFileDrawableStream implements FileLoadOperationStream {

    private FileLoadOperation loadOperation;
    private final AtomicReference<CountDownLatch> dataWaiter = new AtomicReference<>();
    private TLRPC.Document document;
    private ImageLocation location;
    private Object parentObject;
    private int currentAccount;
    private volatile boolean canceled;
    private final Object sync = new Object();
    private long lastOffset;
    private volatile boolean waitingForLoad;
    private boolean preview;
    private boolean finishedLoadingFile;
    private String finishedFilePath;
    private int loadingPriority;

    private int debugCanceledCount;
    private boolean debugReportSend;

    public AnimatedFileDrawableStream(TLRPC.Document d, ImageLocation l, Object p, int a, boolean prev, int loadingPriority, int cacheType) {
        document = d;
        location = l;
        parentObject = p;
        currentAccount = a;
        preview = prev;
        this.loadingPriority = loadingPriority;
        loadOperation = FileLoader.getInstance(currentAccount).loadStreamFile(this, document, location, parentObject, 0, preview, loadingPriority, cacheType);
    }

    public boolean isFinishedLoadingFile() {
        return finishedLoadingFile;
    }

    public String getFinishedFilePath() {
        return finishedFilePath;
    }

    public int read(int offset, int readLength) {
        synchronized (sync) {
            if (canceled) {
                debugCanceledCount++;
                if (!debugReportSend && debugCanceledCount > 200) {
                    debugReportSend = true;
                    FileLog.e(new RuntimeException("infinity stream reading!!!"));
                }
                return 0;
            }
        }
        if (readLength == 0) {
            return 0;
        } else {
            long availableLength = 0;
            try {
                while (availableLength == 0) {
                    if (loadOperation == null || loadOperation.hasStreamDownloadFailed()) return 0;
                    if (loadOperation.isStreamDownloadComplete()) {
                        if (!finishedLoadingFile) {
                            finishedFilePath = loadOperation.getCacheFileFinal().getAbsolutePath();
                            finishedLoadingFile = true;
                        }
                        // The native reader handles EOF on the published file.
                        availableLength = readLength;
                    } else {
                        availableLength = loadOperation.getReadableLength(offset, readLength);
                    }
                    if (availableLength == 0) {
                        synchronized (sync) {
                            if (canceled) {
                                cancelLoadingInternal();
                                return 0;
                            }
                        }
                        CountDownLatch waiter = new CountDownLatch(1);
                        dataWaiter.set(waiter);
                        try {
                            if (loadOperation.isPaused() || lastOffset != offset || preview) {
                                FileLoadOperation resumed = FileLoader.getInstance(currentAccount).loadStreamFile(this, document, location, parentObject, offset, preview, loadingPriority);
                                if (loadOperation != resumed) {
                                    loadOperation.removeStreamListener(this);
                                    loadOperation = resumed;
                                }
                                lastOffset = offset;
                            }
                            synchronized (sync) {
                                if (canceled) {
                                    cancelLoadingInternal();
                                    return 0;
                                }
                            }
                            if (loadOperation == null || loadOperation.hasStreamDownloadFailed()) return 0;
                            if (loadOperation.isStreamDownloadComplete() || loadOperation.getReadableLength(offset, 1) != 0) continue;
                            if (!preview) {
                                FileLoader.getInstance(currentAccount).setLoadingVideo(document, false, true);
                            }
                            waitingForLoad = true;
                            waiter.await();
                        } finally {
                            waitingForLoad = false;
                            dataWaiter.compareAndSet(waiter, null);
                        }
                    }
                }
                lastOffset = offset + availableLength;
            } catch (Exception e) {
                FileLog.e(e, false);
            }
            return (int) availableLength;
        }
    }

    public void cancel() {
        cancel(true);
    }

    public void cancel(boolean removeLoading) {
        if (canceled) {
            return;
        }
        synchronized (sync) {
            CountDownLatch waiter = dataWaiter.getAndSet(null);
            if (waiter != null) {
                waiter.countDown();
                if (removeLoading && !canceled && !preview) {
                    FileLoader.getInstance(currentAccount).removeLoadingVideo(document, false, true);
                }
            }
            if (parentObject instanceof MessageObject) {
                MessageObject messageObject = (MessageObject) parentObject;
                if (DownloadController.getInstance(messageObject.currentAccount).isDownloading(messageObject.getId()))  {
                    removeLoading = false;
                }
            }
            if (removeLoading) {
                cancelLoadingInternal();
            }
            canceled = true;
        }
    }

    private void cancelLoadingInternal() {
        FileLoader.getInstance(currentAccount).cancelLoadFile(document);
        if (location != null) {
            FileLoader.getInstance(currentAccount).cancelLoadFile(location.location, "mp4");
        }
    }

    public void reset() {
        synchronized (sync) {
            canceled = false;
        }
    }

    public TLRPC.Document getDocument() {
        return document;
    }

    public ImageLocation getLocation() {
        return location;
    }

    public Object getParentObject() {
        return document;
    }

    public boolean isPreview() {
        return preview;
    }

    public int getCurrentAccount() {
        return currentAccount;
    }

    public boolean isWaitingForLoad() {
        return waitingForLoad;
    }

    @Override
    public void newDataAvailable() {
        CountDownLatch waiter = dataWaiter.getAndSet(null);
        if (waiter != null) waiter.countDown();
    }

    public boolean isCanceled() {
        return canceled;
    }
}
