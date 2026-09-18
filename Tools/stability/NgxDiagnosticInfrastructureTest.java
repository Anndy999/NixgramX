import org.telegram.messenger.diagnostics.NgxDiagnosticCore;
import org.telegram.messenger.diagnostics.NgxDiagnosticCore.Category;
import org.telegram.messenger.diagnostics.NgxDiagnosticCore.Field;
import org.telegram.messenger.diagnostics.NgxDiagnosticCore.Level;
import org.telegram.messenger.diagnostics.NgxDiagnosticCore.Value;
import org.telegram.messenger.diagnostics.NgxDiagnosticExport;
import org.telegram.messenger.diagnostics.NgxDiagnosticSelfTest;
import org.telegram.messenger.diagnostics.NgxDiagnosticStore;
import org.telegram.messenger.diagnostics.NgxDiagnosticWriter;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.lang.reflect.Method;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class NgxDiagnosticInfrastructureTest {
    static final String[] SECRETS = {
            "SecretTokenABC123",
            "password123",
            "username_test",
            "+15551234567",
            "https://private.example/path",
            "/data/user/0/private"
    };

    static void ok(boolean b, String n) {
        if (!b) throw new AssertionError(n);
    }

    static boolean enqueue(NgxDiagnosticWriter writer, String line) {
        try {
            Method method = NgxDiagnosticWriter.class.getDeclaredMethod("enqueue", String.class);
            method.setAccessible(true);
            return (Boolean) method.invoke(writer, line);
        } catch (Exception e) {
            throw new AssertionError("enqueue", e);
        }
    }

    static String unzip(File zip, String name) throws Exception {
        try (ZipInputStream in = new ZipInputStream(new FileInputStream(zip))) {
            ZipEntry entry;
            while ((entry = in.getNextEntry()) != null) {
                if (name.equals(entry.getName())) {
                    ByteArrayOutputStream out = new ByteArrayOutputStream();
                    byte[] buf = new byte[4096];
                    int n;
                    while ((n = in.read(buf)) >= 0) out.write(buf, 0, n);
                    return out.toString(StandardCharsets.UTF_8.name());
                }
            }
        }
        return null;
    }

    static void privacyScan(String body, String where) {
        ok(body != null, "missing " + where);
        for (String secret : SECRETS) {
            ok(!body.contains(secret), where + " leaked " + secret);
        }
    }

    public static void main(String[] args) throws Exception {
        File root = Files.createTempDirectory("ngx-infra").toFile();
        NgxDiagnosticStore store = new NgxDiagnosticStore(new File(root, "logs"));
        NgxDiagnosticCore core = new NgxDiagnosticCore();

        ok(core.event(Category.APP, "OFF") == null && store.totalBytes() == 0, "off no persist");

        core.setLevel(Level.DIAGNOSTIC);
        String live = core.event(Category.APP, "LIVE", Value.integer(Field.INDEX, 1));
        store.append(live);
        ok(store.readAll().contains("LIVE"), "diagnostic persist");

        core.setLevel(Level.TRACE);
        String trace = core.event(Category.APP, "TRACE_EVENT", Value.integer(Field.INDEX, 2));
        store.append(trace);
        ok(store.readAll().contains("TRACE_EVENT"), "trace persist");

        core.event(Category.APP, "TOKEN", Value.integer(Field.INDEX, 3));
        for (String secret : SECRETS) {
            ok(core.event(Category.APP, "LEAK", Value.integer(Field.INDEX, secret.hashCode())) == null
                    || !String.valueOf(secret.hashCode()).equals("SecretTokenABC123"), "typed only");
        }
        ok(core.event(Category.APP, "PASSWORD") == null, "password name dropped");
        ok(core.event(Category.APP, "USERNAME") == null, "username name dropped");

        store.clear();
        core.clear();
        core.setLevel(Level.OFF);
        ok(core.capture(), "e2e capture");
        List<String> chain = new ArrayList<>();
        String start = core.snapshot().get(core.snapshot().size() - 1);
        store.append(start);
        chain.add(start);
        NgxDiagnosticSelfTest.run(core, line -> {
            try {
                store.append(line);
                chain.add(line);
            } catch (Exception e) {
                throw new AssertionError(e);
            }
        });
        ok(core.cancelCapture(), "e2e cancel");
        String cancel = core.snapshot().get(core.snapshot().size() - 1);
        store.append(cancel);

        NgxDiagnosticExport.Meta meta = new NgxDiagnosticExport.Meta();
        meta.versionName = "test";
        meta.versionCode = "1";
        meta.buildType = "debug";
        meta.commit = "deadbeef";
        meta.androidVersion = "14";
        meta.sdk = "34";
        meta.manufacturer = "TestMaker";
        meta.model = "TestDevice";
        meta.diagnosticLevel = core.level().name();
        meta.captureActive = "FALSE";
        meta.eventCount = Integer.toString(chain.size());
        meta.droppedCount = Long.toString(core.dropped());
        meta.exportTimestamp = "1";
        File zipDir = new File(root, "zips");
        File zip = NgxDiagnosticExport.writeZip(new File(zipDir, "NixgramX-diagnostics-20260101-000000.zip"), store.readAll(), meta);
        ok(zip.isFile() && zip.length() > 0, "zip exists");
        String log = unzip(zip, "ngx_diagnostics.log");
        String metadata = unzip(zip, "metadata.txt");
        privacyScan(log, "log");
        privacyScan(metadata, "metadata");
        int pos = -1;
        for (String event : NgxDiagnosticSelfTest.EVENTS) {
            int next = log.indexOf("event=" + event);
            ok(next > pos, "order " + event);
            pos = next;
        }
        ok(log.contains("CAPTURE_START"), "capture start in log");
        ok(metadata.contains("nixgramx_version=test"), "metadata allowlist");
        ok(!metadata.contains("phone="), "no phone field");
        System.out.println("SYNTHETIC_E2E");
        for (String line : chain) System.out.println(line);

        core.clear();
        store.clear();
        ok(store.totalBytes() == 0 && store.fileCount() == 0, "clear files");

        for (int i = 0; i < 20000; i++) {
            store.append("[NGX]|APP|sid=NONE|event=STEP|index=" + i);
        }
        ok(store.totalBytes() <= NgxDiagnosticStore.MAX_TOTAL_BYTES, "disk bound " + store.totalBytes());
        ok(store.fileCount() <= NgxDiagnosticStore.MAX_FILES, "file bound");
        String rolled = store.readAll();
        ok(rolled.contains("index=9999"), "latest kept");

        File zip2 = NgxDiagnosticExport.writeZip(new File(zipDir, "NixgramX-diagnostics-20260101-000001.zip"), "ok\n", meta);
        File zip3 = NgxDiagnosticExport.writeZip(new File(zipDir, "NixgramX-diagnostics-20260101-000002.zip"), "ok\n", meta);
        File zip4 = NgxDiagnosticExport.writeZip(new File(zipDir, "NixgramX-diagnostics-20260101-000003.zip"), "ok\n", meta);
        File zip5 = NgxDiagnosticExport.writeZip(new File(zipDir, "NixgramX-diagnostics-20260101-000004.zip"), "ok\n", meta);
        File zip6 = NgxDiagnosticExport.writeZip(new File(zipDir, "NixgramX-diagnostics-20260101-000005.zip"), "ok\n", meta);
        File zip7 = NgxDiagnosticExport.writeZip(new File(zipDir, "NixgramX-diagnostics-20260101-000006.zip"), "ok\n", meta);
        NgxDiagnosticExport.pruneZips(zipDir);
        File[] left = zipDir.listFiles((d, n) -> n.endsWith(".zip"));
        ok(left != null && left.length <= NgxDiagnosticExport.MAX_ZIPS, "zip retention");

        NgxDiagnosticCore cc = new NgxDiagnosticCore();
        cc.setLevel(Level.DIAGNOSTIC);
        NgxDiagnosticStore cs = new NgxDiagnosticStore(new File(root, "concurrent"));
        Thread[] ts = new Thread[4];
        AtomicReference<Throwable> err = new AtomicReference<>();
        for (int t = 0; t < 4; t++) {
            ts[t] = new Thread(() -> {
                try {
                    for (int j = 0; j < 80; j++) {
                        String line = cc.event(Category.APP, "STEP", Value.integer(Field.INDEX, j));
                        if (line != null) cs.append(line);
                        cc.snapshot();
                        if ((j & 31) == 0) cc.capture();
                        if ((j & 31) == 16) cc.cancelCapture();
                    }
                } catch (Throwable e) {
                    err.compareAndSet(null, e);
                }
            });
            ts[t].start();
        }
        for (Thread t : ts) t.join();
        ok(err.get() == null, "concurrent " + err.get());
        ok(cc.snapshot().size() <= NgxDiagnosticCore.RING_LIMIT, "concurrent ring");
        ok(cs.totalBytes() <= NgxDiagnosticStore.MAX_TOTAL_BYTES, "concurrent disk");

        core.setLevel(Level.DIAGNOSTIC);
        ok(core.event(null, "X") == null, "bridge null category");
        ok(Value.enumValue(null, Level.OFF) == null, "null enum");

        List<String> mem = Collections.synchronizedList(new ArrayList<>());
        NgxDiagnosticWriter idle = new NgxDiagnosticWriter(new NgxDiagnosticWriter.Sink() {
            public void append(String line) { mem.add(line); }
            public void clear() { mem.clear(); }
        });
        ok(!idle.isStarted(), "writer lazy until persist");
        idle.persist("old");
        ok(idle.flush(2000), "writer flush old");
        ok(mem.contains("old"), "writer wrote old");
        idle.clear();
        ok(!mem.contains("old"), "clear drops disk");
        ok(idle.dropped() == 0, "clear resets dropped");
        idle.persist("new");
        ok(idle.flush(2000), "writer flush new");
        ok(mem.contains("new") && !mem.contains("old"), "clear does not revive old");

        NgxDiagnosticWriter pending = new NgxDiagnosticWriter(new NgxDiagnosticWriter.Sink() {
            public void append(String line) { mem.add(line); }
            public void clear() { mem.clear(); }
        });
        mem.clear();
        ok(enqueue(pending, "pending-old"), "pending line queued");
        ok(!pending.isStarted(), "pending does not start writer");
        pending.clear();
        ok(pending.dropped() == 0, "pending clear resets dropped");
        pending.persist("pending-new");
        ok(pending.flush(2000), "pending flush");
        ok(mem.contains("pending-new") && !mem.contains("pending-old"), "pending LineJob lost on clear");

        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch hold = new CountDownLatch(1);
        List<String> blocked = Collections.synchronizedList(new ArrayList<>());
        NgxDiagnosticWriter stuck = new NgxDiagnosticWriter(new NgxDiagnosticWriter.Sink() {
            public void append(String line) throws Exception {
                entered.countDown();
                if (!hold.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("hold");
                blocked.add(line);
            }
            public void clear() { blocked.clear(); }
        });
        stuck.persist("first");
        ok(entered.await(2, TimeUnit.SECONDS), "writer entered append");
        for (int i = 0; i < 200; i++) stuck.persist("queued-" + i);
        ok(stuck.dropped() > 0, "overflow increments dropped");
        ok(!stuck.flush(300), "flush fails when queue cannot take flush token");
        hold.countDown();
        ok(stuck.flush(2000), "flush succeeds after writer unblocks");
        stuck.clear();
        ok(stuck.dropped() == 0, "clear zeros dropped after overflow");

        System.out.println("NGX infrastructure PASS");
    }
}
