"""Host JVM regression for the production bulk-delete method; no Android/Room runtime."""
import subprocess
import tempfile
import unittest
from pathlib import Path

CONTROLLER = Path('TMessagesProj/src/main/java/com/radolyn/ayugram/messages/AyuMessagesController.java')

HARNESS = r'''
import java.io.File;
import java.nio.file.*;
import java.util.*;

public class BulkDeleteMediaTest {
    record Key(long userId, long dialogId, int messageId) {}
    static class Message { String mediaPath; Message(String path) { mediaPath = path; } }
    static class Full { Message message; Full(String path) { message = new Message(path); } }
    static class TextUtils {
        static boolean isEmpty(String value) { return value == null || value.isEmpty(); }
    }
    static class FileLog { static void e(Exception e) { throw new AssertionError(e); } }
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
    static class DeletedMessageDao extends Dao {}
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
        Path first = Files.createFile(dir.resolve("first"));
        Path second = Files.createFile(dir.resolve("second"));
        Path otherAccount = Files.createFile(dir.resolve("other-account"));
        Path otherDialog = Files.createFile(dir.resolve("other-dialog"));
        Path unselected = Files.createFile(dir.resolve("unselected"));
        var rows = database.deleted.rows;
        rows.put(new Key(1, -99, 7), new Full(first.toString()));
        rows.put(new Key(1, -99, 8), new Full(second.toString()));
        rows.put(new Key(1, -99, 9), new Full(null));
        rows.put(new Key(1, -99, 10), new Full(""));
        rows.put(new Key(1, -99, 11), new Full(dir.resolve("absent").toString()));
        rows.put(new Key(2, -99, 7), new Full(otherAccount.toString()));
        rows.put(new Key(1, -98, 7), new Full(otherDialog.toString()));
        rows.put(new Key(1, -99, 12), new Full(unselected.toString()));
        database.edited.rows.putAll(rows);
        var ids = new ArrayList<>(List.of(7, 7, 8, 9, 10, 11, 404));
        for (int id = 1000; id < 1900; id++) ids.add(id);
        controller.deleteMessages(1, -99, ids);
        check(database.transactions == 1, "all batches must share one transaction");
        check(database.deleted.pathQueries == 2, "only one file-path projection per batch");
        check(!Files.exists(first) && !Files.exists(second), "selected media must be cleaned after bulk delete");
        check(Files.exists(otherAccount) && Files.exists(otherDialog) && Files.exists(unselected),
              "unselected media must survive");
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
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            java = root / 'BulkDeleteMediaTest.java'
            java.write_text(HARNESS.replace('    // PRODUCTION_METHOD', source[start:end]))
            subprocess.run(['javac', '-d', directory, str(java)], check=True, capture_output=True, text=True)
            subprocess.run(['java', '-cp', directory, 'BulkDeleteMediaTest', directory],
                           check=True, capture_output=True, text=True)
