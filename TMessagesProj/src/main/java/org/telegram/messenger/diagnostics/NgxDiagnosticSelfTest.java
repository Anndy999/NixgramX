package org.telegram.messenger.diagnostics;

import org.telegram.messenger.diagnostics.NgxDiagnosticCore.Category;
import org.telegram.messenger.diagnostics.NgxDiagnosticCore.Field;
import org.telegram.messenger.diagnostics.NgxDiagnosticCore.Value;

/**
 * Developer-only synthetic event chain. No personal data, no production behavior.
 */
public final class NgxDiagnosticSelfTest {
    public static final String[] EVENTS = {
            "SELF_TEST_START",
            "STATE_A",
            "DECISION_A",
            "STATE_B",
            "DECISION_B",
            "SELF_TEST_END"
    };

    private NgxDiagnosticSelfTest() {}

    public interface Sink {
        void accept(String line);
    }

    public static int run(NgxDiagnosticCore core) {
        return run(core, null);
    }

    public static int run(NgxDiagnosticCore core, Sink sink) {
        if (core == null) return 0;
        int n = 0;
        if (emit(core, sink, Category.APP, "SELF_TEST_START") != null) n++;
        if (emit(core, sink, Category.UI, "STATE_A", Value.bool(Field.RESULT, true)) != null) n++;
        if (emit(core, sink, Category.NAVIGATION, "DECISION_A", Value.bool(Field.RESULT, true)) != null) n++;
        if (emit(core, sink, Category.UI, "STATE_B", Value.bool(Field.RESULT, true)) != null) n++;
        if (emit(core, sink, Category.NAVIGATION, "DECISION_B", Value.bool(Field.RESULT, true)) != null) n++;
        if (emit(core, sink, Category.APP, "SELF_TEST_END") != null) n++;
        return n;
    }

    private static String emit(NgxDiagnosticCore core, Sink sink, Category category, String name, Value... values) {
        String line = core.event(category, name, values);
        if (line != null && sink != null) sink.accept(line);
        return line;
    }
}
