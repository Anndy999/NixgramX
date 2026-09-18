package org.telegram.messenger.diagnostics;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;

/**
 * Android-free bounded NGX log files. Two files, ~1 MB total, local only.
 * current.log rotates into previous.log. Never appends forever.
 */
public final class NgxDiagnosticStore {
    public static final int PART_BYTES = 512 * 1024;
    public static final int MAX_FILES = 2;
    public static final int MAX_TOTAL_BYTES = PART_BYTES * MAX_FILES;
    public static final String CURRENT = "current.log";
    public static final String PREVIOUS = "previous.log";

    private final File directory;

    public NgxDiagnosticStore(File directory) {
        this.directory = directory;
    }

    public File directory() {
        return directory;
    }

    public synchronized void append(String line) throws IOException {
        if (line == null || line.isEmpty()) return;
        directory.mkdirs();
        if (!line.endsWith("\n")) line = line + "\n";
        byte[] bytes = line.getBytes(StandardCharsets.UTF_8);
        if (bytes.length > 2048) throw new IOException("Diagnostic line too large");
        File current = new File(directory, CURRENT);
        if (current.length() + bytes.length > PART_BYTES) {
            File previous = new File(directory, PREVIOUS);
            if (previous.exists() && !previous.delete()) throw new IOException("Rotation failed");
            if (current.exists() && !current.renameTo(previous)) throw new IOException("Rotation failed");
        }
        try (FileOutputStream out = new FileOutputStream(new File(directory, CURRENT), true)) {
            out.write(bytes);
        }
    }

    public synchronized String readAll() throws IOException {
        return read(PREVIOUS) + read(CURRENT);
    }

    public synchronized long totalBytes() {
        return sizeOf(CURRENT) + sizeOf(PREVIOUS);
    }

    public synchronized int fileCount() {
        int n = 0;
        if (new File(directory, CURRENT).isFile()) n++;
        if (new File(directory, PREVIOUS).isFile()) n++;
        return n;
    }

    public synchronized void clear() throws IOException {
        delete(CURRENT);
        delete(PREVIOUS);
    }

    private String read(String name) throws IOException {
        File file = new File(directory, name);
        if (!file.exists()) return "";
        long length = file.length();
        if (length <= 0) return "";
        int size = (int) Math.min(length, PART_BYTES);
        byte[] bytes = new byte[size];
        try (RandomAccessFile in = new RandomAccessFile(file, "r")) {
            if (length > size) in.seek(length - size);
            in.readFully(bytes);
        }
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private long sizeOf(String name) {
        File file = new File(directory, name);
        return file.isFile() ? file.length() : 0;
    }

    private void delete(String name) throws IOException {
        File file = new File(directory, name);
        if (file.exists() && !file.delete()) throw new IOException("Clear failed");
    }
}
