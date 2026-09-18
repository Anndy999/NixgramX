package org.telegram.messenger.diagnostics;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Android-free ZIP builder. Metadata is an allowlist of scalar fields only.
 */
public final class NgxDiagnosticExport {
    public static final int MAX_ZIPS = 5;

    public static final class Meta {
        public String versionName = "UNKNOWN";
        public String versionCode = "UNKNOWN";
        public String buildType = "UNKNOWN";
        public String commit = "UNKNOWN";
        public String androidVersion = "UNKNOWN";
        public String sdk = "UNKNOWN";
        public String manufacturer = "UNKNOWN";
        public String model = "UNKNOWN";
        public String diagnosticLevel = "OFF";
        public String captureActive = "FALSE";
        public String eventCount = "0";
        public String droppedCount = "0";
        public String exportTimestamp = "0";

        public String toText() {
            StringBuilder out = new StringBuilder(256);
            field(out, "nixgramx_version", versionName);
            field(out, "version_code", versionCode);
            field(out, "build_type", buildType);
            field(out, "commit", commit);
            field(out, "android_version", androidVersion);
            field(out, "sdk", sdk);
            field(out, "manufacturer", manufacturer);
            field(out, "model", model);
            field(out, "diagnostic_level", diagnosticLevel);
            field(out, "capture_active", captureActive);
            field(out, "event_count", eventCount);
            field(out, "dropped_count", droppedCount);
            field(out, "export_timestamp", exportTimestamp);
            return out.toString();
        }

        private static void field(StringBuilder out, String key, String value) {
            out.append(key).append('=').append(safe(value)).append('\n');
        }

        static String safe(String value) {
            if (value == null || value.isEmpty()) return "UNKNOWN";
            if (value.length() > 80) value = value.substring(0, 80);
            for (int i = 0; i < value.length(); i++) {
                char c = value.charAt(i);
                if (c < 32 || c == '\n' || c == '\r') return "UNKNOWN";
            }
            String upper = value.toUpperCase(Locale.US);
            if (upper.contains("TOKEN") || upper.contains("PASSWORD") || upper.contains("USERNAME")
                    || upper.contains("SECRET") || upper.contains("PHONE") || upper.contains("@")) {
                return "UNKNOWN";
            }
            return value;
        }
    }

    private NgxDiagnosticExport() {}

    public static File writeZip(File zip, String log, Meta meta) throws IOException {
        if (zip == null) throw new IOException("zip missing");
        File parent = zip.getParentFile();
        if (parent != null) parent.mkdirs();
        File tmp = new File(zip.getPath() + ".tmp");
        try (ZipOutputStream out = new ZipOutputStream(new FileOutputStream(tmp))) {
            put(out, "metadata.txt", meta == null ? new Meta().toText() : meta.toText());
            put(out, "ngx_diagnostics.log", log == null ? "" : log);
        }
        if (zip.exists() && !zip.delete()) throw new IOException("replace failed");
        if (!tmp.renameTo(zip)) {
            copy(tmp, zip);
            if (!tmp.delete()) {
                // leftover tmp is still bounded by pruneZips
            }
        }
        return zip;
    }

    public static void pruneZips(File directory) {
        if (directory == null || !directory.isDirectory()) return;
        File[] files = directory.listFiles((dir, name) -> name != null && name.startsWith("NixgramX-diagnostics-") && name.endsWith(".zip"));
        if (files == null || files.length <= MAX_ZIPS) return;
        java.util.Arrays.sort(files, (a, b) -> Long.compare(a.lastModified(), b.lastModified()));
        int extra = files.length - MAX_ZIPS;
        for (int i = 0; i < extra; i++) {
            files[i].delete();
        }
        File[] tmps = directory.listFiles((dir, name) -> name != null && name.endsWith(".zip.tmp"));
        if (tmps != null) {
            for (File tmp : tmps) tmp.delete();
        }
    }

    private static void put(ZipOutputStream out, String name, String body) throws IOException {
        ZipEntry entry = new ZipEntry(name);
        out.putNextEntry(entry);
        out.write(body.getBytes(StandardCharsets.UTF_8));
        out.closeEntry();
    }

    private static void copy(File from, File to) throws IOException {
        try (FileInputStream in = new FileInputStream(from); FileOutputStream out = new FileOutputStream(to)) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) >= 0) out.write(buf, 0, n);
        }
    }
}
