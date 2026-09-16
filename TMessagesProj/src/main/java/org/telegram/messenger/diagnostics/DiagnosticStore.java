package org.telegram.messenger.diagnostics;

import java.io.*;
import java.nio.charset.StandardCharsets;

/** No Android dependencies; callers serialize normal I/O off the UI thread. */
public final class DiagnosticStore {
    public static final int PART_BYTES = 512 * 1024;
    public static final int CRASH_BYTES = 64 * 1024;
    private final File directory;

    public DiagnosticStore(File directory) { this.directory = directory; }

    public void append(String line) throws IOException {
        directory.mkdirs();
        byte[] bytes = line.getBytes(StandardCharsets.UTF_8);
        if (bytes.length > 1024) throw new IOException("Diagnostic event too large");
        File current = new File(directory, "events.0");
        if (current.length() + bytes.length > PART_BYTES) {
            File oldest = new File(directory, "events.3");
            if (oldest.exists() && !oldest.delete()) throw new IOException("Rotation failed");
            for (int i = 2; i >= 0; i--) {
                File source = new File(directory, "events." + i);
                if (source.exists() && !source.renameTo(new File(directory, "events." + (i + 1)))) {
                    throw new IOException("Rotation failed");
                }
            }
        }
        try (FileOutputStream out = new FileOutputStream(current, true)) { out.write(bytes); }
    }

    public void crash(String report) throws IOException {
        directory.mkdirs();
        byte[] bytes = report.getBytes(StandardCharsets.UTF_8);
        try (FileOutputStream out = new FileOutputStream(new File(directory, "last-crash.txt"))) {
            out.write(bytes, 0, Math.min(bytes.length, CRASH_BYTES));
        }
    }

    public String read(String name, int limit) throws IOException {
        File file = new File(directory, name);
        if (!file.exists()) return "";
        try (RandomAccessFile in = new RandomAccessFile(file, "r")) {
            int size = (int) Math.min(in.length(), limit);
            byte[] bytes = new byte[size];
            in.seek(in.length() - size);
            in.readFully(bytes);
            return new String(bytes, StandardCharsets.UTF_8);
        }
    }

    public void clear() throws IOException {
        for (String name : new String[]{"events.0", "events.1", "events.2", "events.3", "last-crash.txt"}) {
            File file = new File(directory, name);
            if (file.exists() && !file.delete()) throw new IOException("Clear failed");
        }
    }

    /** Exception messages and custom thread names can contain secrets: omit them. */
    public static String safeStack(Throwable error) {
        StringBuilder out = new StringBuilder();
        for (int depth = 0; error != null && depth < 8 && out.length() < 48000; depth++) {
            out.append(error.getClass().getName()).append('\n');
            StackTraceElement[] frames = error.getStackTrace();
            for (int i = 0; i < frames.length && i < 128 && out.length() < 48000; i++) {
                StackTraceElement frame = frames[i];
                out.append(" at ").append(frame.getClassName()).append('.').append(frame.getMethodName())
                    .append(':').append(frame.getLineNumber()).append('\n');
            }
            error = error.getCause();
        }
        return out.toString();
    }
}
