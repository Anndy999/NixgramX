import org.telegram.messenger.diagnostics.NgxDiagnosticCore;
import org.telegram.messenger.diagnostics.NgxDiagnosticCore.Category;
import org.telegram.messenger.diagnostics.NgxDiagnosticCore.Field;
import org.telegram.messenger.diagnostics.NgxDiagnosticCore.Level;
import org.telegram.messenger.diagnostics.NgxDiagnosticCore.Value;

import java.lang.reflect.Method;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

public class NgxDiagnosticCoreTest {
    static void ok(boolean b, String n) {
        if (!b) throw new AssertionError(n);
    }

    static String e(NgxDiagnosticCore c, int n) {
        return c.event(Category.APP, "STEP", Value.integer(Field.INDEX, n));
    }

    static String sidOf(String line) {
        if (line == null) return null;
        int i = line.indexOf("|sid=");
        if (i < 0) return null;
        int j = line.indexOf('|', i + 5);
        return j < 0 ? line.substring(i + 5) : line.substring(i + 5, j);
    }

    static boolean capturing(NgxDiagnosticCore c) {
        try {
            Method m = NgxDiagnosticCore.class.getDeclaredMethod("isCapturing");
            m.setAccessible(true);
            return (Boolean) m.invoke(c);
        } catch (Exception e) {
            throw new AssertionError("isCapturing", e);
        }
    }

    static void captureCount(Level start) {
        NgxDiagnosticCore c = new NgxDiagnosticCore();
        c.setLevel(start);
        ok(c.capture(), "capture from " + start);
        ok(capturing(c), "capturing after start " + start);
        ok(!c.capture(), "double capture rejected " + start);
        for (int i = 0; i < 49; i++) {
            ok(e(c, 4000 + i) != null, "event " + i + " during " + start);
            ok(capturing(c), "still capturing after " + (i + 1) + " " + start);
        }
        ok(e(c, 4049) != null, "50th event " + start);
        ok(!capturing(c), "capture finished " + start);
        ok(c.level() == start, "level restored " + start);
        if (start == Level.OFF) {
            ok(e(c, 4050) == null, "off after capture records nothing");
        } else {
            ok("NONE".equals(sidOf(e(c, 4050))), "sid NONE after capture end " + start);
        }
    }

    public static void main(String[] args) throws Exception {
        NgxDiagnosticCore c = new NgxDiagnosticCore();
        ok(c.level() == Level.OFF, "default off");
        ok(!c.enabled(Level.DIAGNOSTIC), "off not diagnostic");
        ok(c.event(Category.APP, "OFF") == null, "off records nothing");
        ok(c.snapshot().isEmpty(), "off no ring");
        ok(!c.enabled(null), "enabled null");

        c.setLevel(null);
        ok(c.level() == Level.OFF, "setLevel null");

        c.setLevel(Level.DIAGNOSTIC);
        ok(!c.enabled(null), "enabled null while on");
        String start = c.begin();
        String sidA = sidOf(start);
        ok(start != null && start.contains("SESSION_START") && sidA != null && !"NONE".equals(sidA), "session start");
        ok(sidA.equals(sidOf(e(c, 1))), "event uses session A");
        String ended = c.end();
        ok(ended != null && ended.contains("SESSION_END") && sidA.equals(sidOf(ended)), "session end keeps sid");
        ok("NONE".equals(sidOf(e(c, 2))), "event after end has no sid");

        String first = c.begin();
        String replaced = c.begin();
        ok(!sidOf(first).equals(sidOf(replaced)), "second begin replaces session");
        ok(sidOf(replaced).equals(sidOf(e(c, 3))), "event uses replacement sid");
        c.end();

        c.begin();
        c.setLevel(Level.OFF);
        c.end();
        c.setLevel(Level.DIAGNOSTIC);
        ok("NONE".equals(sidOf(e(c, 4))), "end while off still clears sid");

        ok(e(c, 5) != null && e(c, 5) == null && e(c, 6) != null, "same event dropped, different value kept");
        ok(c.event(Category.APP, "FLAG", Value.bool(Field.RESULT, true)).contains("result=TRUE"), "bool");
        ok(c.event(Category.APP, "MOVE", Value.integer(Field.DX, -3)).contains("dx=-3"), "negative int");
        ok(Value.enumValue(Field.REASON, null) == null, "null enum");
        ok(Value.bool(null, true) != null, "null field factory");

        for (int i = 0; i < 1000; i++) e(c, i);
        List<String> ring = c.snapshot();
        ok(ring.size() == 100 && ring.get(99).contains("index=999"), "latest preserved");
        ok(!ring.get(0).contains("index=0") && ring.get(0).contains("index=900"), "oldest removed");

        long dropped = c.dropped();
        ok(c.event(null, "BAD") == null && c.dropped() > dropped, "malformed safe");
        ok(c.event(Category.APP, "TOKEN") == null, "sensitive name");
        ok(c.event(Category.APP, "PASSWORD") == null, "password name");
        for (Field f : Field.values()) {
            ok(!"REQUEST_TOKEN".equals(f.name()), "no request_token field");
        }
        ok(Field.REQUEST_ID != null, "request_id field");

        c.event(Category.APP, "BAD", (Value[]) null);
        ok(c.dropped() > 0, "null values dropped");
        c.clear();
        ok(c.dropped() == 0, "clear resets dropped");
        ok(c.level() == Level.DIAGNOSTIC && c.snapshot().isEmpty(), "clear keeps diagnostic");

        NgxDiagnosticCore clearOff = new NgxDiagnosticCore();
        clearOff.setLevel(Level.OFF);
        ok(clearOff.capture(), "capture from off");
        clearOff.clear();
        ok(clearOff.level() == Level.OFF && !capturing(clearOff), "clear during capture restores off");

        NgxDiagnosticCore clearTrace = new NgxDiagnosticCore();
        clearTrace.setLevel(Level.TRACE);
        ok(clearTrace.capture(), "capture from trace");
        clearTrace.clear();
        ok(clearTrace.level() == Level.TRACE && !capturing(clearTrace), "clear during capture restores trace");

        captureCount(Level.OFF);
        captureCount(Level.DIAGNOSTIC);
        captureCount(Level.TRACE);

        NgxDiagnosticCore cc = new NgxDiagnosticCore();
        cc.setLevel(Level.DIAGNOSTIC);
        Thread[] ts = new Thread[4];
        AtomicReference<Throwable> err = new AtomicReference<>();
        for (int i = 0; i < 4; i++) {
            ts[i] = new Thread(() -> {
                try {
                    for (int j = 0; j < 200; j++) {
                        e(cc, j);
                        cc.snapshot();
                        if ((j & 15) == 0) cc.clear();
                    }
                } catch (Throwable t) {
                    err.compareAndSet(null, t);
                }
            });
            ts[i].start();
        }
        for (Thread t : ts) t.join();
        ok(err.get() == null, "no concurrent throw: " + err.get());
        ok(cc.snapshot().size() <= NgxDiagnosticCore.RING_LIMIT, "concurrent ring bound");
        ok(cc.level() != null, "concurrent level valid");
        ok(cc.dropped() >= 0, "concurrent dropped valid");

        System.out.println("NGX core hardened PASS");
    }
}
