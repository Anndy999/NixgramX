import org.telegram.messenger.diagnostics.DiagnosticStore;
import java.nio.file.*;
import java.io.*;

public class DiagnosticStoreTest {
    public static void main(String[] args) throws Exception {
        Path dir = Files.createTempDirectory("nix-diagnostics-test");
        DiagnosticStore store = new DiagnosticStore(dir.toFile());
        try {
            String privateText = "SECRET_TOKEN phone chat /private/path";
            String stack = DiagnosticStore.safeStack(new RuntimeException(privateText, new IOException(privateText)));
            if (stack.contains(privateText) || !stack.contains("IOException") || !stack.contains("DiagnosticStoreTest.main")) throw new AssertionError("privacy/stack");
            for (int i = 0; i < 3000; i++) store.append("x".repeat(1023) + "\n");
            store.crash("c".repeat(100000));
            long total;
            try (var files = Files.list(dir)) { total = files.mapToLong(p -> p.toFile().length()).sum(); }
            if (total > 4L * DiagnosticStore.PART_BYTES + DiagnosticStore.CRASH_BYTES) throw new AssertionError("rotation limit");
            if (store.read("last-crash.txt", 100000).length() != DiagnosticStore.CRASH_BYTES) throw new AssertionError("crash cap");
            if (store.read("events.0", 100).length() != 100) throw new AssertionError("bounded export");
            try { store.append("x".repeat(1025)); throw new AssertionError("oversize accepted"); } catch (IOException expected) { }
            store.clear();
            if (!store.read("last-crash.txt", 100).isEmpty()) throw new AssertionError("clear");
            System.out.println("DiagnosticStore privacy / rotation / crash / clear PASS");
        } finally {
            store.clear();
            Files.delete(dir);
        }
    }
}
