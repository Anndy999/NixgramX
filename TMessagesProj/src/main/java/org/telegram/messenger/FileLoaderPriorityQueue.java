package org.telegram.messenger;

import android.os.SystemClock;

import java.util.ArrayList;

public class FileLoaderPriorityQueue {

    public static final int TYPE_SMALL = 0;
    public static final int TYPE_LARGE = 1;
    String name;
    int type;
    int currentAccount;

    public ArrayList<FileLoadOperation> allOperations = new ArrayList<>();
    public ArrayList<FileLoadOperation> tmpListOperations = new ArrayList<>();

    public final static int PRIORITY_VALUE_MAX = (1 << 20);
    public final static int PRIORITY_VALUE_USER_DOWNLOAD = (1 << 19);
    public final static int PRIORITY_VALUE_NORMAL = (1 << 16);
    public final static int PRIORITY_VALUE_LOW = 0;

    final DispatchQueue2 workerQueue;

    boolean checkOperationsScheduled = false;
    private static final long STORY_START_EXCLUSIVE_MS = 1500;
    private long storyExclusiveUntil;
    private final Runnable checkVisibleStoryRunnable = () -> checkLoadingOperations();

    Runnable checkOperationsRunnable = () -> {
        checkLoadingOperationInternal();
        checkOperationsScheduled = false;
    };

    FileLoaderPriorityQueue(int currentAccount, String name, int type, DispatchQueue2 workerQueue) {
        this.currentAccount = currentAccount;
        this.name = name;
        this.type = type;
        this.workerQueue = workerQueue;
    }

    public void add(FileLoadOperation operation) {
        if (operation == null) {
            return;
        }
        int index = -1;
        allOperations.remove(operation);
        for (int i = 0; i < allOperations.size(); i++) {
            if (operation.getPriority() > allOperations.get(i).getPriority()) {
                index = i;
                break;
            }
        }
        if (index >= 0) {
            allOperations.add(index, operation);
        } else {
            allOperations.add(operation);
        }
    }

    public void cancel(FileLoadOperation operation) {
        if (operation == null) {
            return;
        }
        if (allOperations.remove(operation)) {
            operation.cancel();
        }
    }

    public void checkLoadingOperations() {
        checkLoadingOperations(false);
    }

    public void checkLoadingOperations(boolean immediate) {
        if (immediate) {
            workerQueue.cancelRunnable(checkOperationsRunnable);
            checkOperationsRunnable.run();
            return;
        }
        if (checkOperationsScheduled) {
            return;
        }
        checkOperationsScheduled = true;
        workerQueue.cancelRunnable(checkOperationsRunnable);
        workerQueue.postRunnable(checkOperationsRunnable, 20);
    }

    private void checkLoadingOperationInternal() {
        int activeCount = 0;
        int lastPriority = 0;
        boolean pauseAllNextOperations = false;
        int max = type == TYPE_LARGE ? MessagesController.getInstance(currentAccount).largeQueueMaxActiveOperations : MessagesController.getInstance(currentAccount).smallQueueMaxActiveOperations;
        tmpListOperations.clear();
        // Reserve bandwidth for the first frame for a bounded interval across consecutive stories.
        FileLoadOperation visibleStory = null;
        boolean waitingForStory = false;
        if (type == TYPE_LARGE && !allOperations.isEmpty()) {
            FileLoadOperation first = allOperations.get(0);
            if (first.isStory && first.getPriority() >= PRIORITY_VALUE_MAX && !first.preFinished && !first.hasStoryStartData()) {
                waitingForStory = true;
                long now = SystemClock.elapsedRealtime();
                if (storyExclusiveUntil == 0) storyExclusiveUntil = now + STORY_START_EXCLUSIVE_MS;
                if (now < storyExclusiveUntil) {
                    visibleStory = first;
                    workerQueue.cancelRunnable(checkVisibleStoryRunnable);
                    workerQueue.postRunnable(checkVisibleStoryRunnable, Math.min(250, storyExclusiveUntil - now));
                }
            }
        }
        if (!waitingForStory) {
            storyExclusiveUntil = 0;
            workerQueue.cancelRunnable(checkVisibleStoryRunnable);
        }
        for (int i = 0; i < allOperations.size(); i++) {
            FileLoadOperation prevOperation = i > 0 ? allOperations.get(i - 1) : null;
            FileLoadOperation operation = allOperations.get(i);
            // On a fast connection the visible story's own thumbnail and video load together.
            if (visibleStory != null && operation != visibleStory && !(operation.parentObject == visibleStory.parentObject && DownloadSpeedEstimator.isFast())) {
                if (operation.wasStarted()) {
                    operation.pause();
                }
                continue;
            }
            if (i > 0 && !pauseAllNextOperations) {
                if (type == TYPE_LARGE) {
                    if (prevOperation != null && prevOperation.isStory && prevOperation.getPriority() >= PRIORITY_VALUE_MAX && operation.getPriority() <= PRIORITY_VALUE_LOW) {
                        pauseAllNextOperations = true;
                    }
                }
                if (lastPriority > PRIORITY_VALUE_LOW && operation.getPriority() == PRIORITY_VALUE_LOW) {
                    pauseAllNextOperations = true;
                }
            }
            if (operation.preFinished) {
                //operation will not use connections
                //just skip
//                if (BuildVars.DEBUG_PRIVATE_VERSION)
//                    FileLog.d("{"+name+"}.checkLoadingOperationInternal: #" + i + " "+operation.getFileName()+" priority="+operation.getPriority()+" isStory="+operation.isStory+" preFinished="+ operation.preFinished+" pauseAllNextOperations=" + pauseAllNextOperations + " max=" + max + " => skip");
                continue;
            } else if (!pauseAllNextOperations && activeCount < max) {
//                if (BuildVars.DEBUG_PRIVATE_VERSION)
//                    FileLog.d("{"+name+"}.checkLoadingOperationInternal: #" + i + " " +operation.getFileName()+" priority="+operation.getPriority()+" isStory="+operation.isStory+" preFinished="+ operation.preFinished+" pauseAllNextOperations=" + pauseAllNextOperations + " max=" + max + " => start");
                tmpListOperations.add(operation);
                activeCount++;
            } else {
//                if (BuildVars.DEBUG_PRIVATE_VERSION)
//                    FileLog.d("{"+name+"}.checkLoadingOperationInternal: #" + i + " " +operation.getFileName()+" priority="+operation.getPriority()+" isStory="+operation.isStory+" preFinished="+ operation.preFinished+" pauseAllNextOperations=" + pauseAllNextOperations + " max=" + max + " => pause");
                if (operation.wasStarted()) {
                    operation.pause();
                }
            }
            lastPriority = operation.getPriority();
        }
        for (int i = 0; i < tmpListOperations.size(); i++) {
            tmpListOperations.get(i).start();
        }
    }

    public boolean remove(FileLoadOperation operation) {
        if (operation == null) {
            return false;
        }
        return allOperations.remove(operation);
    }

    public int getCount() {
        return allOperations.size();
    }

    public int getPosition(FileLoadOperation fileLoadOperation) {
        return allOperations.indexOf(fileLoadOperation);
    }
}
