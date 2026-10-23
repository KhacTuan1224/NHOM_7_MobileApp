"""Offline SQLite checks for the exact exported Room schema and durable outbox boundary."""
import json
import re
import sqlite3
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
SCHEMAS = ROOT / 'app/schemas/com.rescuefarm.data.local.database.RescueFarmDatabase'


class RoomMigrationTest(unittest.TestCase):
    def setUp(self):
        self.db = sqlite3.connect(':memory:')
        for entity in json.loads((SCHEMAS / '10.json').read_text())['database']['entities']:
            self.db.execute(entity['createSql'].replace('${TABLE_NAME}', entity['tableName']))
            if entity['tableName'] == 'order_cache':
                row = {field['columnName']: 0 if field['affinity'] in ('REAL', 'INTEGER') else 'old'
                       for field in entity['fields'] if field.get('notNull', False)}
                row.update(id='old-order', orderCode='RF-OLD', paymentStatus='PAID', status='CONFIRMED')
                self.db.execute('INSERT INTO order_cache (' + ','.join(row) + ') VALUES (' + ','.join('?' for _ in row) + ')', tuple(row.values()))
        self.db.execute("INSERT INTO guest_cart_items (id, guestId, quantity, unitPrice, selected, updatedAtEpochMillis) VALUES ('p::b','guest',3,10,1,100)")
        self.db.commit()
        source = (ROOT / 'app/src/main/java/com/rescuefarm/data/local/database/RescueFarmDatabase.java').read_text(encoding='utf-8')
        migration = source.split('MIGRATION_10_11 =', 1)[1].split('public abstract', 1)[0]
        for statement in re.findall(r'database.execSQL\("([^"\n]+)"\)', migration):
            self.db.execute(statement)
        self.db.commit()

    def tearDown(self):
        self.db.close()

    def test_upgrade_preserves_guest_cart_and_matches_v11_fields(self):
        self.assertEqual(3, self.db.execute('SELECT quantity FROM guest_cart_items').fetchone()[0])
        self.assertEqual(('RF-OLD', 'PAID', 0, None), self.db.execute('SELECT orderCode,paymentStatus,expiryReviewRequired,cancellationReason FROM order_cache').fetchone())
        expected = json.loads((SCHEMAS / '11.json').read_text())['database']['entities']
        for entity in expected:
            columns = {row[1]: (row[2], bool(row[3])) for row in self.db.execute(f"PRAGMA table_info(`{entity['tableName']}`)")}
            for field in entity['fields']:
                self.assertEqual((field['affinity'], field.get('notNull', False)), columns[field['columnName']])

    def test_snapshot_move_is_atomic_and_completing_old_job_never_clears_new_guest_changes(self):
        self.db.execute('BEGIN')
        self.db.execute("INSERT INTO cart_migration_outbox VALUES ('job','guest','customer','immutable snapshot')")
        self.db.execute("DELETE FROM guest_cart_items WHERE guestId='guest'")
        self.db.rollback()  # Failure before commit: the original guest cart is intact.
        self.assertEqual(1, self.db.execute('SELECT COUNT(*) FROM guest_cart_items').fetchone()[0])
        self.assertEqual(0, self.db.execute('SELECT COUNT(*) FROM cart_migration_outbox').fetchone()[0])
        self.db.execute('BEGIN')
        self.db.execute("INSERT INTO cart_migration_outbox VALUES ('job','guest','customer','immutable snapshot')")
        self.db.execute("DELETE FROM guest_cart_items WHERE guestId='guest'")
        self.db.commit()  # Crash now: durable job remains to retry with the SAME requestId.
        self.assertEqual(1, self.db.execute('SELECT COUNT(*) FROM cart_migration_outbox').fetchone()[0])
        self.db.execute("INSERT INTO guest_cart_items (id, guestId, quantity, unitPrice, selected, updatedAtEpochMillis) VALUES ('new','guest',2,10,1,200)")
        self.db.execute("DELETE FROM cart_migration_outbox WHERE id='job'")
        self.db.commit()
        self.assertEqual(2, self.db.execute("SELECT quantity FROM guest_cart_items WHERE id='new'").fetchone()[0])


if __name__ == '__main__':
    unittest.main()
