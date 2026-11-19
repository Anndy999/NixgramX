"""Host JVM regression for the production bulk-delete method; no Android/Room runtime."""
import subprocess
import tempfile
import unittest
from pathlib import Path

CONTROLLER = Path('TMessagesProj/src/main/java/com/radolyn/ayugram/messages/AyuMessagesController.java')

HARNESS = r'''
import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;

public class BulkDeleteMediaTest {
    static File attachmentsPath;
    record Key(long userId, long dialogId, int messageId) {}
    static class Message { String mediaPath; Message(String path) { mediaPath = path; } }
    static class Full { Message message; Full(String path) { message = new Message(path); } }
    static class TextUtils {
        static boolean isEmpty(String value) { return value == null || value.isEmpty(); }
    }
    static class FileLog {
        static void e(Exception e) { throw new AssertionError(e); }
        static void e(String text) { throw new AssertionError(text); }
    }
    static class Dao {
        Map<Key, Full> rows = new HashMap<>();
        int deletes;
        int pathQueries;
        boolean failDelete;
        List<String> getMediaPaths(long userId, long dialogId, List<Integer> ids) {
            check(ids.size() <= 900, "SQLite batch is too large");
            pathQueries++;
            Set<String> paths = new LinkedHashSet<>();
            for (int id : ids) {
                Full row = rows.get(new Key(userId, dialogId, id));
                if (row != null && !TextUtils.isEmpty(row.message.mediaPath)) paths.add(row.message.mediaPath);
            }
            return new ArrayList<>(paths);
        }
        void deleteMessages(long userId, long dialogId, List<Integer> ids) {
            deletes++;
            for (int id : ids) rows.remove(new Key(userId, dialogId, id));
        }
        void deleteByDialogIdAndMessageIds(long userId, long dialogId, List<Integer> ids) {
            deleteMessages(userId, dialogId, ids);
            if (failDelete) throw new IllegalStateException("edited DAO failure");
        }
    }
    static class DeletedMessageDao extends Dao {
        List<String> getReferencedAttachmentPaths(List<String> paths) {
            check(paths.size() <= 200, "reference query exceeds the SQLite bind budget");
            Set<String> used = new HashSet<>();
            for (Dao dao : List.of(this, AyuData.database.edited)) {
                for (Full row : dao.rows.values()) {
                    if (paths.contains(row.message.mediaPath)) used.add(row.message.mediaPath);
                }
            }
            return new ArrayList<>(used);
        }
    }
    static class EditedMessageDao extends Dao {}
    static class AyuDatabase {
        final DeletedMessageDao deleted = new DeletedMessageDao();
        final EditedMessageDao edited = new EditedMessageDao();
        int transactions;
        DeletedMessageDao deletedMessageDao() { return deleted; }
        EditedMessageDao editedMessageDao() { return edited; }
        void runInTransaction(Runnable action) {
            transactions++;
            var deletedBefore = new HashMap<>(deleted.rows);
            var editedBefore = new HashMap<>(edited.rows);
            try {
                action.run();
            } catch (RuntimeException e) {
                deleted.rows.clear();
                deleted.rows.putAll(deletedBefore);
                edited.rows.clear();
                edited.rows.putAll(editedBefore);
                throw e;
            }
        }
    }
    static class AyuData {
        static final AyuDatabase database = new AyuDatabase();
        static AyuDatabase getDatabase() { return database; }
    }
    // PRODUCTION_METHOD
    static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
    public static void main(String[] args) throws Exception {
        var controller = new BulkDeleteMediaTest();
        var database = AyuData.getDatabase();
        controller.deleteMessages(1, -99, null);
        controller.deleteMessages(1, -99, List.of());
        check(database.transactions == 0, "null/empty selection must be a no-op");
        Path dir = Path.of(args[0]);
        Path managed = Files.createDirectory(dir.resolve("attachments"));
        attachmentsPath = managed.toFile();
        Path first = Files.createFile(managed.resolve("first"));
        Path second = Files.createFile(managed.resolve("second"));
        Path otherAccount = Files.createFile(managed.resolve("other-account"));
        Path unselected = Files.createFile(managed.resolve("unselected"));
        Path outside = Files.createFile(dir.resolve("outside"));
        Path linked = Files.createSymbolicLink(managed.resolve("linked"), outside);
        var rows = database.deleted.rows;
        rows.put(new Key(1, -99, 7), new Full(first.toString()));
        rows.put(new Key(1, -99, 8), new Full(second.toString()));
        rows.put(new Key(1, -99, 9), new Full(null));
        rows.put(new Key(1, -99, 10), new Full(""));
        rows.put(new Key(1, -99, 11), new Full(managed.resolve("absent").toString()));
        rows.put(new Key(2, -99, 7), new Full(otherAccount.toString()));
        rows.put(new Key(1, -98, 7), new Full(second.toString()));
        rows.put(new Key(1, -99, 12), new Full(unselected.toString()));
        rows.put(new Key(1, -99, 13), new Full(outside.toString()));
        rows.put(new Key(1, -99, 14), new Full(linked.toString()));
        database.edited.rows.putAll(rows);
        var ids = new ArrayList<>(List.of(7, 7, 8, 9, 10, 11, 13, 14, 404));
        for (int id = 1000; id < 1900; id++) ids.add(id);
        controller.deleteMessages(1, -99, ids);
        check(database.transactions == 2, "one record transaction and one reference check transaction");
        check(database.deleted.pathQueries == 2, "only one file-path projection per batch");
        check(!Files.exists(first), "unreferenced media must be cleaned after bulk delete");
        check(Files.exists(otherAccount) && Files.exists(second) && Files.exists(unselected),
              "unselected media must survive");
        check(Files.exists(outside) && Files.isSymbolicLink(linked), "external and symlink paths must survive");
        var remaining = Set.of(new Key(2, -99, 7), new Key(1, -98, 7), new Key(1, -99, 12));
        check(rows.keySet().equals(remaining), "selected deleted rows must be removed");
        check(database.edited.rows.keySet().equals(remaining), "selected edit history must be removed");

        database.edited.failDelete = true;
        try {
            controller.deleteMessages(1, -99, List.of(12));
            throw new AssertionError("expected the edited DAO to fail");
        } catch (IllegalStateException expected) {
            check(rows.keySet().equals(remaining) && database.edited.rows.keySet().equals(remaining),
                  "failure must roll back both deletes");
            check(Files.exists(unselected), "transaction failure must leave the media file intact");
        }
    }
}
'''


class BulkDeleteMediaTest(unittest.TestCase):
    def test_production_bulk_delete_cleans_selected_media(self):
        source = CONTROLLER.read_text()
        start = source.index('    public void deleteMessages(long')
        end = source.index('    public void deleteRevision(', start)
        cleanup_start = source.index('    private void deleteUnreferencedAttachments(')
        cleanup_end = source.index('    public boolean isAyuDeletedMessageId(', cleanup_start)
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            java = root / 'BulkDeleteMediaTest.java'
            java.write_text(HARNESS.replace('    // PRODUCTION_METHOD', source[start:end] + source[cleanup_start:cleanup_end]))
            subprocess.run(['javac', '-d', directory, str(java)], check=True, capture_output=True, text=True)
            subprocess.run(['java', '-cp', directory, 'BulkDeleteMediaTest', directory],
                           check=True, capture_output=True, text=True)
