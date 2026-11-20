"""Host regressions for persisted download ranges and foreground scheduling."""
import unittest

from test_beta_ui_performance import JAVA, run_java, section

MESSENGER = JAVA / 'org/telegram/messenger'


class DownloadPipelineTest(unittest.TestCase):
    def test_checkpoint_requires_valid_ranges_and_groups_writes(self):
        source = (MESSENGER / 'FileLoadOperation.java').read_text()
        ranges = section(source, '    public static class Range {', '    private static final Object lockObject')
        checkpoint = section(source, '    private static long addPart(', '    private void flushCheckpointOrFail()')
        fields = section(source, '    private static final int CHECKPOINT_BYTES', '    private boolean ungzip;')
        run_java('''
import java.io.*;
import java.nio.file.*;
import java.util.*;
class Probe {
    static final int PART = 128 * 1024;
    static final class Queue {
        Runnable callback;
        void postRunnable(Runnable task, long delay) {
            assert callback == null && delay == 250;
            callback = task;
        }
        void cancelRunnable(Runnable task) { if (callback == task) callback = null; }
        void runTimer() { Runnable task = callback; assert task != null; task.run(); }
    }
    static final class Utilities { static final Queue stageQueue = new Queue(); }
    File cacheFileParts;
    RandomAccessFile fileOutputStream;
    ArrayList<Range> notLoadedBytesRanges;
    void flushCheckpointOrFail() {
        try { flushCheckpoint(); } catch (IOException e) { throw new AssertionError(e); }
    }
''' + fields + ranges + checkpoint + '''
    public static void main(String[] args) throws Exception {
        Path dir = Files.createTempDirectory("download-checkpoint");
        File data = dir.resolve("download.temp").toFile();
        File checkpoint = dir.resolve("download.pt").toFile();
        long total = 16L * PART;

        try (RandomAccessFile out = new RandomAccessFile(data, "rw")) {
            Probe p = new Probe();
            p.fileOutputStream = out;
            p.cacheFileParts = checkpoint;
            p.bufferedFileWrites = true;
            p.notLoadedBytesRanges = new ArrayList<>(List.of(new Range(0, total)));
            byte[] block = new byte[PART];
            Arrays.fill(block, (byte) 25);
            out.seek(total - PART);
            out.write(block);
            assert readResumeRanges(checkpoint, out.length(), total, PART) == null;
            assert addPart(p.notLoadedBytesRanges, total - PART, total) == PART;
            assert addPart(p.notLoadedBytesRanges, total - PART, total) == 0;
            p.noteCheckpointProgress(PART);
            assert !checkpoint.exists();
            Utilities.stageQueue.runTimer();
            var gap = readResumeRanges(checkpoint, out.length(), total, PART);
            assert gap.size() == 1 && gap.get(0).start == 0 && gap.get(0).end == total - PART;
            assert readResumeRanges(checkpoint, out.length(), total, 4 * PART).get(0).end == total;

            var snapshot = copyRanges(p.notLoadedBytesRanges);
            for (int i = 0; i < 8; i++) {
                out.seek((long) i * PART);
                out.write(block);
                assert addPart(p.notLoadedBytesRanges, (long) i * PART, (long) (i + 1) * PART) == PART;
                p.noteCheckpointProgress(PART);
                var persisted = readResumeRanges(checkpoint, out.length(), total, PART);
                assert persisted.get(0).start == (i == 7 ? 8L * PART : 0);
            }
            assert snapshot.get(0).start == 0 && Utilities.stageQueue.callback == null;

            out.seek(8L * PART); out.write(block);
            addPart(p.notLoadedBytesRanges, 8L * PART, 9L * PART);
            p.noteCheckpointProgress(PART);
            p.flushCheckpoint(); // pause or finalization
            assert readResumeRanges(checkpoint, out.length(), total, PART).get(0).start == 9L * PART;

            try (RandomAccessFile damaged = new RandomAccessFile(checkpoint, "rw")) {
                damaged.seek(0);
                damaged.writeInt(2); // header promises more entries than are present
            }
            assert readResumeRanges(checkpoint, out.length(), total, PART) == null;
            writeResumeRanges(checkpoint, new ArrayList<>(List.of(new Range(0, 4L * PART), new Range(PART, total))));
            assert readResumeRanges(checkpoint, out.length(), total, PART) == null;
            writeResumeRanges(checkpoint, new ArrayList<>());
            assert readResumeRanges(checkpoint, total - 1, total, PART) == null;
            assert readResumeRanges(checkpoint, total, total, PART).isEmpty();
            assert !dir.resolve("download.pt.new").toFile().exists();

            var holes = new ArrayList<>(List.of(new Range(0, 100), new Range(200, 300)));
            assert addPart(holes, 80, 260) == 80 && holes.size() == 2;
            assert holes.get(0).end == 80 && holes.get(1).start == 260;
        } finally {
            Files.deleteIfExists(checkpoint.toPath());
            Files.deleteIfExists(data.toPath());
            Files.deleteIfExists(dir);
        }
    }
}
''')

    def test_manual_download_keeps_priority_and_story_reservation_expires(self):
        source = (MESSENGER / 'FileLoaderPriorityQueue.java').read_text()
        queue = source[source.index('public class FileLoaderPriorityQueue'):].replace(
            'public class FileLoaderPriorityQueue', 'class FileLoaderPriorityQueue', 1)
        operation = (MESSENGER / 'FileLoadOperation.java').read_text()
        priority = section(operation, '    private int userDownloadPriority;', '    private FilePathDatabase.FileMeta')
        run_java('''
import java.util.*;
class SystemClock {
    static long now = 100;
    static long elapsedRealtime() { return now; }
}
class DispatchQueue2 {
    final Map<Runnable,Long> pending = new HashMap<>();
    void cancelRunnable(Runnable task) { pending.remove(task); }
    void postRunnable(Runnable task, long delay) { pending.put(task, delay); }
}
class MessagesController {
    final int largeQueueMaxActiveOperations = 2, smallQueueMaxActiveOperations = 5;
    static MessagesController getInstance(int account) { return new MessagesController(); }
}
class DownloadSpeedEstimator { static boolean isFast() { return true; } }
class FileLoadOperation {
    int priority;
    boolean isStory, preFinished, started, running, hasStartData;
    Object parentObject = new Object();
    FileLoadOperation(int priority) { this.priority = priority; }
    boolean hasStoryStartData() { return hasStartData; }
    int getPriority() { return priority; }
    boolean wasStarted() { return started; }
    void start() { started = running = true; }
    void pause() { running = false; }
    void cancel() { running = false; }
''' + priority + '''
}
''' + queue + '''
class Probe {
    public static void main(String[] args) {
        var worker = new DispatchQueue2();
        var q = new FileLoaderPriorityQueue(0, "test", FileLoaderPriorityQueue.TYPE_LARGE, worker);
        int manual = FileLoaderPriorityQueue.PRIORITY_VALUE_USER_DOWNLOAD;
        int high = FileLoaderPriorityQueue.PRIORITY_VALUE_MAX;
        var background = new FileLoadOperation(0);
        var clicked = new FileLoadOperation(0);
        clicked.priority = clicked.resolvePriority(manual + 1, true);
        assert clicked.resolvePriority(0, false) == manual + 1;
        assert clicked.resolvePriority(high, false) == high;
        assert clicked.resolvePriority(0, false) == manual + 1;
        clicked.priority = clicked.resolvePriority(manual + 2, true);
        assert clicked.priority == manual + 2;
        q.add(background); q.add(clicked);
        q.checkLoadingOperations(true); assert clicked.running && !background.running;

        var story = new FileLoadOperation(high + 2);
        story.isStory = true;
        var sibling = new FileLoadOperation(FileLoaderPriorityQueue.PRIORITY_VALUE_NORMAL);
        sibling.parentObject = story.parentObject;
        q.add(story); q.add(sibling);
        q.checkLoadingOperations(true);
        assert story.running && sibling.running && !clicked.running;
        SystemClock.now += 1000;
        var nextStory = new FileLoadOperation(high + 3);
        nextStory.isStory = true;
        q.remove(story); q.add(nextStory);
        q.checkLoadingOperations(true);
        assert nextStory.running && !clicked.running;
        SystemClock.now += 500;
        q.checkLoadingOperations(true);
        assert nextStory.running && clicked.running && !background.running;
    }
}
''')

    def test_old_route_samples_cannot_warm_new_connection(self):
        source = (MESSENGER / 'DownloadSpeedEstimator.java').read_text()
        production = source[source.index('public class DownloadSpeedEstimator'):].replace(
            'public class DownloadSpeedEstimator', 'class DownloadSpeedEstimator', 1)
        run_java('''
import java.util.Objects;
record Network(int id) {}
class SystemClock { static long now = 1000; static long elapsedRealtime() { return now; } }
class ApplicationLoader { static Object applicationContext; }
class DefaultBandwidthMeter {
    static DefaultBandwidthMeter getSingletonInstance(Object context) { return new DefaultBandwidthMeter(); }
    long getBitrateEstimate() { return 12_000_000; }
}
''' + production + '''
class Probe {
    public static void main(String[] args) {
        var wifi = new Network(1);
        var mobile = new Network(2);
        DownloadSpeedEstimator.onNetworkChanged(wifi, 0);
        long wifiGeneration = DownloadSpeedEstimator.getRouteGeneration();
        DownloadSpeedEstimator.onSample(12_000_000, 1000, wifiGeneration);
        DownloadSpeedEstimator.onNetworkChanged(new Network(1), 0);
        assert DownloadSpeedEstimator.getBitrate() == 12_000_000;
        DownloadSpeedEstimator.onNetworkChanged(mobile, 0);
        long mobileGeneration = DownloadSpeedEstimator.getRouteGeneration();
        assert !DownloadSpeedEstimator.isFast();
        DownloadSpeedEstimator.onSample(12_000_000, 1000, wifiGeneration);
        assert !DownloadSpeedEstimator.isFast();
        DownloadSpeedEstimator.onSample(500_000, 1000, mobileGeneration);
        assert DownloadSpeedEstimator.getBitrate() == 500_000;
        DownloadSpeedEstimator.onRouteChanged(); // a proxy change on the same network
        assert DownloadSpeedEstimator.getBitrate() == 1_000_000;
        SystemClock.now += 10_000;
        assert DownloadSpeedEstimator.isFast();
    }
}
''')
