package org.telegram.messenger;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

final class DownloadedFileStore {
    private DownloadedFileStore() {}

    static void publish(File source, File destination) throws IOException {
        if (!source.isFile()) throw new IOException("Downloaded file is missing");
        if (source.equals(destination) || source.renameTo(destination)) return;

        // Cross-volume copies go to a private name in the destination directory.
        // A failed copy cannot expose a partial file under the final name.
        File staging = File.createTempFile(".nix-download-", ".part", destination.getParentFile());
        try {
            long expectedSize = source.length();
            long copied = 0;
            try (FileInputStream input = new FileInputStream(source);
                 FileOutputStream output = new FileOutputStream(staging)) {
                byte[] buffer = new byte[256 * 1024];
                int count;
                while ((count = input.read(buffer)) != -1) {
                    output.write(buffer, 0, count);
                    copied += count;
                }
                if (copied != expectedSize) throw new IOException("Downloaded file changed while copying");
                output.getFD().sync();
            }
            try {
                Files.move(staging.toPath(), destination.toPath(), StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                if (!staging.renameTo(destination)) throw e;
            }
            source.delete();
        } finally {
            staging.delete();
        }
    }
}
