/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.messenger;


import static androidx.media3.common.util.Assertions.checkNotNull;
import static androidx.media3.common.util.Util.castNonNull;

import android.net.Uri;

import androidx.annotation.Nullable;

import androidx.annotation.OptIn;
import androidx.media3.common.C;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.common.util.Util;
import androidx.media3.datasource.DataSource;
import androidx.media3.datasource.DataSpec;
import androidx.media3.datasource.TransferListener;

import org.telegram.tgnet.TLRPC;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

@OptIn(markerClass = UnstableApi.class)
public class FileStreamLoadOperation implements DataSource, FileLoadOperationStream {

    public static final ConcurrentHashMap<Long, FileStreamLoadOperation> allStreams = new ConcurrentHashMap<>();

    private volatile FileLoadOperation loadOperation;

    private Uri uri;
    private long bytesRemaining;
    private long bytesTransferred;
    private long requestedLength;
    private volatile boolean opened;
    private long currentOffset;
    private final AtomicReference<CountDownLatch> dataWaiter = new AtomicReference<>();
    private RandomAccessFile file;
    private TLRPC.Document document;
    private Object parentObject;
    private int currentAccount;
    File currentFile;

    private static final ConcurrentHashMap<Long, Integer> priorityMap = new ConcurrentHashMap<>();

    public FileStreamLoadOperation() {
        this.isNetwork = true;
        this.listeners = new ArrayList<>(/* initialCapacity= */ 1);
    }

    @Deprecated
    public FileStreamLoadOperation(@Nullable TransferListener listener) {
        this();
        if (listener != null) {
            addTransferListener(listener);
        }
    }

    public static int getStreamPrioriy(TLRPC.Document document) {
        if (document == null) {
            return FileLoader.PRIORITY_HIGH;
        }
        Integer integer = priorityMap.get(document.id);
        if (integer == null) {
            return FileLoader.PRIORITY_HIGH;
        }
        return integer;
    }

    @Override
    public long open(DataSpec dataSpec) throws IOException {
        uri = dataSpec.uri;
        transferInitializing(dataSpec);
        currentAccount = Utilities.parseInt(uri.getQueryParameter("account"));
        parentObject = FileLoader.getInstance(currentAccount).getParentObject(Utilities.parseInt(uri.getQueryParameter("rid")));
        document = new TLRPC.TL_document();
        document.access_hash = Utilities.parseLong(uri.getQueryParameter("hash"));
        document.id = Utilities.parseLong(uri.getQueryParameter("id"));
        document.size = Utilities.parseLong(uri.getQueryParameter("size"));
        document.dc_id = Utilities.parseInt(uri.getQueryParameter("dc"));
        document.mime_type = uri.getQueryParameter("mime");
        document.file_reference = Utilities.hexToBytes(uri.getQueryParameter("reference"));
        TLRPC.TL_documentAttributeFilename filename = new TLRPC.TL_documentAttributeFilename();
        filename.file_name = uri.getQueryParameter("name");
        document.attributes.add(filename);
        if (document.mime_type.startsWith("video")) {
            document.attributes.add(new TLRPC.TL_documentAttributeVideo());
        } else if (document.mime_type.startsWith("audio")) {
            document.attributes.add(new TLRPC.TL_documentAttributeAudio());
        }
        currentOffset = dataSpec.position;
        requestedLength = dataSpec.length;
        loadOperation = FileLoader.getInstance(currentAccount).loadStreamFile(this, document, null, parentObject,
                currentOffset, true, getCurrentPriority());
        if (loadOperation == null) throw new IOException("Unable to open the video download");
        bytesTransferred = 0;
        isNetwork = !loadOperation.isStreamDownloadComplete();
        File completed = isNetwork ? null : loadOperation.getCurrentFileFast();
        bytesRemaining = Math.max(0, (completed == null ? document.size : completed.length()) - currentOffset);
        if (requestedLength != C.LENGTH_UNSET) {
            bytesRemaining = Math.min(bytesRemaining, requestedLength);
        }
        allStreams.put(document.id, this);
        opened = true;
        transferStarted(dataSpec);
        return bytesRemaining;
    }

    private int getCurrentPriority() {
        Integer priority = priorityMap.getOrDefault(document.id, null);
        if (priority != null) {
            return priority;
        }
        return FileLoader.PRIORITY_HIGH;
    }

    @Override
    public int read(byte[] buffer, int offset, int readLength) throws IOException {
        if (readLength == 0) return 0;
        if (bytesRemaining <= 0) return C.RESULT_END_OF_INPUT;
        final int length = (int) Math.min(bytesRemaining, readLength);
        while (opened) {
            final FileLoadOperation operation = loadOperation;
            if (operation == null || operation.hasStreamDownloadFailed()) {
                throw new IOException("Video download failed");
            }
            final int available = (int) operation.getReadableLength(currentOffset, length);
            if (available > 0) return readBuffered(operation, buffer, offset, available);
            if (operation.isStreamDownloadComplete()) return C.RESULT_END_OF_INPUT;
            awaitVideoData(operation);
        }
        return C.RESULT_END_OF_INPUT;
    }

    private synchronized int readBuffered(FileLoadOperation operation, byte[] buffer, int offset, int length) throws IOException {
        if (!opened) return C.RESULT_END_OF_INPUT;
        if (file == null) {
            currentFile = operation.getCurrentFileFast();
            if (currentFile == null) throw new FileNotFoundException("Video file is unavailable");
            // A rename can race the first open. A descriptor already open on the
            // temporary file stays valid after publication, even across a copy.
            try {
                file = new RandomAccessFile(currentFile, "r");
            } catch (FileNotFoundException e) {
                File published = operation.getPublishedFileForStream();
                if (published == null || published.equals(currentFile)) throw e;
                currentFile = published;
                file = new RandomAccessFile(currentFile, "r");
            }
            file.seek(currentOffset);
        }
        int bytesRead = file.read(buffer, offset, length);
        if (bytesRead < 0 && !operation.isStreamDownloadComplete()) {
            throw new IOException("The video cache ended inside a downloaded range");
        }
        if (bytesRead > 0) {
            currentOffset += bytesRead;
            bytesRemaining -= bytesRead;
            bytesTransferred += bytesRead;
            bytesTransferred(bytesRead);
        }
        return bytesRead;
    }

    private void awaitVideoData(FileLoadOperation operation) throws IOException {
        final CountDownLatch waiter = new CountDownLatch(1);
        dataWaiter.set(waiter);
        try {
            // Register before checking again so arrivals between read and wait are visible.
            if (!opened || operation.getReadableLength(currentOffset, 1) != 0
                    || operation.hasStreamDownloadFailed() || operation.isStreamDownloadComplete()) return;
            final FileLoadOperation resumed = FileLoader.getInstance(currentAccount).loadStreamFile(this, document, null,
                    parentObject, currentOffset, true, getCurrentPriority());
            if (resumed == null) throw new IOException("Unable to continue the video download");
            if (resumed != operation) {
                synchronized (this) {
                    if (!opened) {
                        resumed.removeStreamListener(this);
                        return;
                    }
                    operation.removeStreamListener(this);
                    closeStreamFile();
                    loadOperation = resumed;
                }
            }
            if (opened && resumed.getReadableLength(currentOffset, 1) == 0
                    && !resumed.isStreamDownloadComplete() && !resumed.hasStreamDownloadFailed()
                    && !waiter.await(30, TimeUnit.SECONDS)) {
                throw new SocketTimeoutException("Waiting for video download data");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Video read interrupted", e);
        } finally {
            dataWaiter.compareAndSet(waiter, null);
        }
    }

    @Override
    public Uri getUri() {
        return uri;
    }

    @Override
    public synchronized void close() {
        boolean wasOpened = opened;
        opened = false;
        newDataAvailable();
        if (loadOperation != null) {
            loadOperation.removeStreamListener(this);
            loadOperation = null;
        }
        closeStreamFile();
        uri = null;
        if (document != null) allStreams.remove(document.id, this);
        if (wasOpened) transferEnded();
    }

    private void closeStreamFile() {
        if (file != null) {
            try {
                file.close();
            } catch (IOException e) {
                FileLog.e(e);
            }
            file = null;
        }
        currentFile = null;
    }

    @Override
    public void newDataAvailable() {
        CountDownLatch waiter = dataWaiter.getAndSet(null);
        if (waiter != null) waiter.countDown();
    }

    public static void setPriorityForDocument(TLRPC.Document document, int priority) {
        if (document != null) {
            priorityMap.put(document.id, priority);
        }
    }

    @Nullable
    public static Uri prepareUri(int currentAccount, TLRPC.Document document, Object parent) {
        String attachFileName = FileLoader.getAttachFileName(document);
        File file = FileLoader.getInstance(currentAccount).getPathToAttach(document);

        if (file != null && file.exists()) {
            return Uri.fromFile(file);
        }
        try {
            String params =
                "?account=" + currentAccount +
                "&id=" + document.id +
                "&hash=" + document.access_hash +
                "&dc=" + document.dc_id +
                "&size=" + document.size +
                "&mime=" + URLEncoder.encode(document.mime_type, "UTF-8") +
                "&rid=" + FileLoader.getInstance(currentAccount).getFileReference(parent) +
                "&name=" + URLEncoder.encode(FileLoader.getDocumentFileName(document), "UTF-8") +
                "&reference=" + Utilities.bytesToHex(document.file_reference != null ? document.file_reference : new byte[0]);
            return Uri.parse("tg://" + attachFileName + params);
        } catch (UnsupportedEncodingException e) {
            FileLog.e(e);
        }
        return null;
    }




    protected boolean isNetwork;
    private final ArrayList<TransferListener> listeners;

    private int listenerCount;
    @Nullable private DataSpec dataSpec;

    @Override
    public final void addTransferListener(TransferListener transferListener) {
        checkNotNull(transferListener);
        if (!listeners.contains(transferListener)) {
            listeners.add(transferListener);
            listenerCount++;
        }
    }

    /**
     * Notifies listeners that data transfer for the specified {@link DataSpec} is being initialized.
     *
     * @param dataSpec {@link DataSpec} describing the data for initializing transfer.
     */
    protected final void transferInitializing(DataSpec dataSpec) {
        for (int i = 0; i < listenerCount; i++) {
            listeners.get(i).onTransferInitializing(/* source= */ this, dataSpec, isNetwork);
        }
    }

    /**
     * Notifies listeners that data transfer for the specified {@link DataSpec} started.
     *
     * @param dataSpec {@link DataSpec} describing the data being transferred.
     */
    protected final void transferStarted(DataSpec dataSpec) {
        this.dataSpec = dataSpec;
        for (int i = 0; i < listenerCount; i++) {
            listeners.get(i).onTransferStart(/* source= */ this, dataSpec, isNetwork);
        }
    }

    /**
     * Notifies listeners that bytes were transferred.
     *
     * @param bytesTransferred The number of bytes transferred since the previous call to this method
     *     (or if the first call, since the transfer was started).
     */
    protected final void bytesTransferred(int bytesTransferred) {
        DataSpec dataSpec = castNonNull(this.dataSpec);
        for (int i = 0; i < listenerCount; i++) {
            listeners
                    .get(i)
                    .onBytesTransferred(/* source= */ this, dataSpec, isNetwork, bytesTransferred);
        }
    }

    /** Notifies listeners that a transfer ended. */
    protected final void transferEnded() {
        DataSpec dataSpec = castNonNull(this.dataSpec);
        for (int i = 0; i < listenerCount; i++) {
            listeners.get(i).onTransferEnd(/* source= */ this, dataSpec, isNetwork);
        }
        this.dataSpec = null;
    }
}
