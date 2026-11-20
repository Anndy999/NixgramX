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
        priority = section(operation, '    int resolvePriority(', '    private FilePathDatabase.FileMeta')
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
    int userDownloadPriority;
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

    def test_cached_stream_reads_and_publishes_without_partial_destination(self):
        source = (MESSENGER / 'FileStreamLoadOperation.java').read_text()
        read = section(source, '    public int read(byte[] buffer', '    @Override\n    public Uri getUri()')
        close = section(source, '    private void closeStreamFile()', '    public static void setPriorityForDocument')
        store = (MESSENGER / 'DownloadedFileStore.java').read_text()
        store = store[store.index('final class DownloadedFileStore'):]
        run_java('''
import java.io.*;
import java.net.SocketTimeoutException;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
class C { static final int RESULT_END_OF_INPUT = -1; }
class FileLoadOperation {
    long received = 4;
    File path;
    boolean failed, finished;
    long getReadableLength(long offset, long limit) { return Math.min(limit, Math.max(0, received - offset)); }
    boolean hasStreamDownloadFailed() { return failed; }
    boolean isStreamDownloadComplete() { return finished; }
    File getCurrentFileFast() { return path; }
    void removeStreamListener(Probe player) {}
}
class FileLoader {
    static final FileLoader INSTANCE = new FileLoader();
    int loads;
    static FileLoader getInstance(int id) { return INSTANCE; }
    FileLoadOperation loadStreamFile(Probe player, Object document, Object location, Object parent, long offset, boolean priority, int p) {
        assert priority;
        loads++;
        player.loadOperation.received = 8;
        player.newDataAvailable(); // arrival races the wait registration
        return player.loadOperation;
    }
}
class FileLog { static void e(IOException e) { throw new AssertionError(e); } }
''' + store + '''
class Probe {
    FileLoadOperation loadOperation = new FileLoadOperation();
    boolean opened = true;
    RandomAccessFile file;
    File currentFile;
    long bytesRemaining = 8, currentOffset, bytesTransferred;
    int currentAccount;
    Object parentObject, document;
    AtomicReference<CountDownLatch> dataWaiter = new AtomicReference<>();
    int getCurrentPriority() { return 3; }
    void bytesTransferred(int bytes) {}
    static class CrossVolumeFile extends File {
        CrossVolumeFile(Path path) { super(path.toString()); }
        @Override public boolean renameTo(File destination) { return false; }
    }
''' + read + close.replace('    @Override\n','') + '''
    public static void main(String[] args) throws Exception {
        Path dir = Files.createTempDirectory("media-publish");
        byte[] contents = {0, 1, 2, 3, 4, 5, 6, 7};
        Probe p = new Probe();
        try {
            Path cache = dir.resolve("data.tmp"), target = dir.resolve("final.bin");
            Files.write(cache, contents);
            p.loadOperation.path = cache.toFile();
            byte[] read = new byte[8];
            assert p.read(read, 0, 2) == 2;
            assert p.read(read, 2, 2) == 2;
            assert FileLoader.INSTANCE.loads == 0;
            DownloadedFileStore.publish(new CrossVolumeFile(cache), target.toFile());
            assert !Files.exists(cache) && Arrays.equals(contents, Files.readAllBytes(target));
            p.loadOperation.path = target.toFile();
            assert p.read(read, 4, 4) == 4 && FileLoader.INSTANCE.loads == 1;
            assert Arrays.equals(read, contents);
            assert p.read(read, 0, 1) == C.RESULT_END_OF_INPUT;
            p.closeStreamFile();

            Files.write(cache, contents);
            Path occupied = Files.createDirectory(dir.resolve("occupied"));
            try {
                DownloadedFileStore.publish(new CrossVolumeFile(cache), occupied.toFile());
                throw new AssertionError("expected publish failure");
            } catch (IOException expected) {
                assert Files.isDirectory(occupied) && Files.isRegularFile(cache);
                try (var paths = Files.list(dir)) {
                    assert paths.noneMatch(path -> path.getFileName().toString().endsWith(".part"));
                }
            }
        } finally {
            p.closeStreamFile();
            try (var paths = Files.list(dir)) {
                for (Path path : paths.toList()) Files.delete(path);
            }
            Files.delete(dir);
        }
    }
}
''')

    def test_proxy_rotation_requires_a_recent_faster_route_and_sustained_slowness(self):
        source = (MESSENGER / 'ProxyRotationController.java').read_text()
        controller = source[source.index('public class ProxyRotationController'):].replace(
            'public class ProxyRotationController', 'class ProxyRotationController', 1)
        run_java('''
import java.util.*;
import java.util.function.LongConsumer;
class SystemClock { static long time = 10_000; static long elapsedRealtime() { return time; } }
class SharedPreferences {
    static class Editor {
        void putBoolean(String key, boolean value) {}
        void apply() {}
    }
    Editor edit() { return new Editor(); }
}
class SharedConfig {
    static boolean proxyRotationEnabled = true;
    static boolean isProxyEnabled() { return true; }
    static int proxyRotationTimeout;
    static class ProxyInfo {
        boolean checking, available = true;
        long availableCheckTime, ping;
        ProxyInfo(long ping) { this.ping = ping; }
        final Settings settings = new Settings();
    }
    static class Settings { void toSharedPreferences(SharedPreferences.Editor editor) {} }
    static ProxyInfo currentProxy;
    static List<ProxyInfo> proxyList = new ArrayList<>();
}
class UserConfig { static int selectedAccount; static final int MAX_ACCOUNT_COUNT = 1; }
class DownloadSpeedEstimator {
    static long route = 1, network = 1;
    static long getRouteGeneration() { return route; }
    static long getNetworkGeneration() { return network; }
}
class MessagesController {
    static SharedPreferences getGlobalMainSettings() { return new SharedPreferences(); }
}
class ConnectionsManager {
    static final int ConnectionStateConnectingToProxy = 2, ConnectionStateConnected = 3;
    static int switches;
    static ConnectionsManager getInstance(int account) { return new ConnectionsManager(); }
    int getConnectionState() { return ConnectionStateConnected; }
    void checkProxy(SharedConfig.Settings settings, LongConsumer callback) {}
    static void setProxySettings(boolean enabled, SharedConfig.Settings settings) { switches++; DownloadSpeedEstimator.route++; }
}
class AndroidUtilities {
    static void runOnUIThread(Runnable r) { r.run(); }
    static void runOnUIThread(Runnable r, long delay) {}
    static void cancelRunOnUIThread(Runnable r) {}
}
class NotificationCenter {
    interface NotificationCenterDelegate { void didReceivedNotification(int id, int account, Object... args); }
    static final int didUpdateConnectionState = 1, proxyCheckDone = 2, proxySettingsChanged = 3, proxyChangedByRotation = 4;
    static NotificationCenter getInstance(int account) { return new NotificationCenter(); }
    static NotificationCenter getGlobalInstance() { return new NotificationCenter(); }
    void addObserver(NotificationCenterDelegate observer, int id) {}
    void postNotificationName(int id, Object... args) {}
}
''' + controller + '''
class Probe {
    static void sample(long advance, long rate) {
        SystemClock.time += advance;
        ProxyRotationController.onForegroundDownloadSample(0, DownloadSpeedEstimator.route, rate);
    }
    public static void main(String[] args) {
        var quickPing = new SharedConfig.ProxyInfo(5);
        var fastDownload = new SharedConfig.ProxyInfo(100);
        var slow = new SharedConfig.ProxyInfo(15);
        SharedConfig.proxyList = List.of(quickPing, fastDownload, slow);
        SharedConfig.currentProxy = fastDownload;
        sample(0, 6_000_000);

        SharedConfig.currentProxy = slow;
        DownloadSpeedEstimator.route++;
        sample(0, 200_000);
        sample(4000, 250_000);
        assert ConnectionsManager.switches == 0;
        sample(4000, 180_000);
        assert ConnectionsManager.switches == 1 && SharedConfig.currentProxy == fastDownload;

        // Even a known better alternative does not trigger an immediate second switch.
        SharedConfig.currentProxy = slow;
        DownloadSpeedEstimator.route++;
        sample(0, 200_000); sample(4000, 250_000); sample(4000, 180_000);
        assert ConnectionsManager.switches == 1 && SharedConfig.currentProxy == slow;
        DownloadSpeedEstimator.network++;
        DownloadSpeedEstimator.route++;
        sample(61_000, 200_000); sample(4000, 250_000); sample(4000, 180_000);
        assert ConnectionsManager.switches == 1 && SharedConfig.currentProxy == slow;
        SharedConfig.proxyRotationEnabled = false;
        sample(1000, 100_000);
        assert ConnectionsManager.switches == 1;
    }
}
''')

    def test_native_foreground_request_gets_next_slot_without_expanding_budget(self):
        from pathlib import Path
        import subprocess
        import tempfile

        root = Path(__file__).resolve().parents[2]
        with tempfile.TemporaryDirectory() as scratch:
            probe = Path(scratch) / 'probe.cpp'
            output = Path(scratch) / 'probe'
            probe.write_text('''
#include "TMessagesProj/jni/tgnet/DownloadRequestBudget.h"
#include <cassert>
#include <vector>
struct Request { int id; bool download; bool foreground; };
int main() {
    std::list<Request> queue{{0, false, false}, {1, true, false}, {2, false, false},
                             {3, true, true}, {4, true, false}, {5, true, true}};
    prioritizeForegroundDownloads(queue, [](auto &r) { return r.download; }, [](auto &r) { return r.foreground; });
    std::vector<int> result;
    for (const auto &r : queue) result.push_back(r.id);
    assert((result == std::vector<int>{0, 3, 5, 1, 2, 4}));
    constexpr uint32_t block = 128 * 1024;
    DownloadRequestBudget budget(false);
    for (int i = 0; i < 15; ++i) budget.account(1, false, block);
    assert(queue.begin()->id == 0 && std::next(queue.begin())->foreground);
    assert(budget.acquire(1, false, block));
    assert(!budget.acquire(1, false, block)); // The same 2 MiB cap applies after reordering.
    assert(budget.acquire(1, true, block) && budget.acquire(2, false, block));
}
''')
            subprocess.run(['c++', '-std=c++17', '-I', str(root), str(probe), '-o', str(output)], check=True)
            subprocess.run([str(output)], check=True)
