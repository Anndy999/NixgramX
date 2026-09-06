"""Execute the actual Room SQL against two accounts sharing a channel ID."""
import re
import sqlite3
import unittest
from pathlib import Path

DAO = Path('TMessagesProj/src/main/java/com/radolyn/ayugram/database/dao')

def query(file, method):
    source = (DAO / file).read_text()
    matches = re.findall(r'@Query\("([^"\n]+)"\)\s+[\w<>]+ ' + method + r'\(([^)]*)\)', source)
    return next(sql for sql, signature in matches if method != 'delete' or signature.count(',') == 1)

class AccountIsolationTest(unittest.TestCase):
    def test_clear_and_bulk_delete_preserve_other_account(self):
        for file, table, method in (
            ('DeletedMessageDao.java', 'deletedmessage', 'delete'),
            ('EditedMessageDao.java', 'editedmessage', 'delete'),
            ('EditedMessageDao.java', 'editedmessage', 'deleteByDialogIdAndMessageIds'),
        ):
            with self.subTest(method=method, table=table), sqlite3.connect(':memory:') as db:
                db.execute(f'CREATE TABLE {table}(userId INTEGER, dialogId INTEGER, messageId INTEGER)')
                db.executemany(f'INSERT INTO {table} VALUES(?,?,?)', [(1, -99, 7), (1, -99, 8), (2, -99, 7), (2, -99, 8), (1, -98, 7)])
                db.execute(query(file, method).replace(':messageIds', ':first, :second'), {'userId': 1, 'dialogId': -99, 'first': 7, 'second': 8})
                self.assertEqual(db.execute(f'SELECT * FROM {table} ORDER BY userId').fetchall(), [(1, -98, 7), (2, -99, 7), (2, -99, 8)])

    def test_media_cleanup_select_is_account_scoped(self):
        with sqlite3.connect(':memory:') as db:
            db.execute('CREATE TABLE deletedmessage(userId INTEGER, dialogId INTEGER, messageId INTEGER)')
            db.executemany('INSERT INTO deletedmessage VALUES(?,?,?)', [(1, -99, 7), (2, -99, 7)])
            self.assertEqual(db.execute(query('DeletedMessageDao.java', 'getMessagesByDialog'),
                                       {'userId': 1, 'dialogId': -99}).fetchall(), [(1, -99, 7)])
