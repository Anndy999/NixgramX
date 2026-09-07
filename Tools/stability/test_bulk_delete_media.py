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
        void deleteMessages(long userId, long dialogId, List<Integer> ids) {
            deletes++;
            for (int id : ids) rows.remove(new Key(userId, dialogId, id));
        }
        void deleteByDialogIdAndMessageIds(long userId, long dialogId, List<Integer> ids) {
            deleteMessages(userId, dialogId, ids);
        }
    }
    Dao deletedMessageDao = new Dao(), editedMessageDao = new Dao();
    Full getMessage(long userId, long dialogId, int id) {
        return deletedMessageDao.rows.get(new Key(userId, dialogId, id));
    }
    // PRODUCTION_METHOD
    static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
    public static void main(String[] args) throws Exception {
        var controller = new BulkDeleteMediaTest();
        controller.deleteMessages(1, -99, null);
        controller.deleteMessages(1, -99, List.of());
        check(controller.deletedMessageDao.deletes == 0 && controller.editedMessageDao.deletes == 0,
              "null/empty selection must be a no-op");
        Path dir = Path.of(args[0]);
        Path first = Files.createFile(dir.resolve("first"));
        Path second = Files.createFile(dir.resolve("second"));
        Path otherAccount = Files.createFile(dir.resolve("other-account"));
        Path otherDialog = Files.createFile(dir.resolve("other-dialog"));
        Path unselected = Files.createFile(dir.resolve("unselected"));
        var rows = controller.deletedMessageDao.rows;
        rows.put(new Key(1, -99, 7), new Full(first.toString()));
        rows.put(new Key(1, -99, 8), new Full(second.toString()));
        rows.put(new Key(1, -99, 9), new Full(null));
        rows.put(new Key(1, -99, 10), new Full(""));
        rows.put(new Key(1, -99, 11), new Full(dir.resolve("absent").toString()));
        rows.put(new Key(2, -99, 7), new Full(otherAccount.toString()));
        rows.put(new Key(1, -98, 7), new Full(otherDialog.toString()));
        rows.put(new Key(1, -99, 12), new Full(unselected.toString()));
        controller.editedMessageDao.rows.putAll(rows);
        controller.deleteMessages(1, -99, List.of(7, 8, 9, 10, 11, 404));
        check(!Files.exists(first) && !Files.exists(second), "selected media must be cleaned after bulk delete");
        check(Files.exists(otherAccount) && Files.exists(otherDialog) && Files.exists(unselected),
              "unselected media must survive");
        var remaining = Set.of(new Key(2, -99, 7), new Key(1, -98, 7), new Key(1, -99, 12));
        check(rows.keySet().equals(remaining), "selected deleted rows must be removed");
        check(controller.editedMessageDao.rows.keySet().equals(remaining), "selected edit history must be removed");
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
