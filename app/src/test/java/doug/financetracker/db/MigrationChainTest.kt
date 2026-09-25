package doug.financetracker.db

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import doug.financetracker.data.local.database.FinanceDatabase
import doug.financetracker.data.local.database.Migrations
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Validates the hand-written [Migrations] chain against a realistic v1
 * database: Room itself must accept the migrated schema (its TableInfo check
 * runs on open) and pre-existing rows must survive with sane backfills.
 *
 * The v1 shape is recreated with raw DDL plus Room's identity-hash row so the
 * upgrade path (not onCreate) executes exactly as on a real device.
 */
@RunWith(RobolectricTestRunner::class)
// SDK 35: highest sandbox Robolectric 4.17 runs reliably in this
// environment (the 36 sandbox hits untamed framework internals).
@Config(sdk = [35])
class MigrationChainTest {

    private val dbName = "migration-chain-test.db"
    private var db: FinanceDatabase? = null

    @After
    fun close() {
        db?.close()
        ApplicationProvider.getApplicationContext<Context>()
            .getDatabasePath(dbName).delete()
    }

    @Test
    fun `migrates v1 to v5 preserving data`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val identityHash = readV5IdentityHash(context)
        createV1Database(context, identityHash)

        db = Room.databaseBuilder(context, FinanceDatabase::class.java, dbName)
            .addMigrations(*Migrations.ALL)
            .allowMainThreadQueries()
            .build()
        // Force open → runs 1→2→3→4→5, then Room validates the final schema.
        val dao = db!!.accountDao()
        assertTrue(dao.getAll().any { it.name == "Bancolombia" })

        db!!.openHelper.readableDatabase.query(
            "SELECT name, syncStatus, updatedAt, createdAt, remoteId FROM accounts WHERE name = 'Bancolombia'"
        ).use { cursor ->
            cursor.moveToFirst()
            assertEquals("PENDING_UPLOAD", cursor.getString(1))
            // Backfilled from createdAt (unsynced rows upload on next sync).
            assertEquals(cursor.getLong(3), cursor.getLong(2))
            assertTrue(cursor.isNull(4))
        }

        // v2–v5 structures exist and accept writes.
        db!!.openHelper.readableDatabase.query(
            "SELECT name FROM sqlite_master WHERE type = 'table' AND " +
                "name IN ('source_events','pending_reviews','transaction_candidates','sync_tombstones')"
        ).use { cursor ->
            val tables = buildSet {
                while (cursor.moveToNext()) add(cursor.getString(0))
            }
            assertEquals(
                setOf("source_events", "pending_reviews", "transaction_candidates", "sync_tombstones"),
                tables
            )
        }
        db!!.syncTombstoneDao().insert(
            doug.financetracker.data.local.entity.SyncTombstoneEntity(
                tableName = "transactions", remoteId = "r1"
            )
        )
        assertEquals(1, db!!.syncTombstoneDao().getAll().size)

        // Pre-existing v1 transaction survived with its fields intact.
        val tx = db!!.transactionDao().getAllWithTags().single()
        assertEquals(29_000L, tx.transaction.amount)
        assertEquals("BOLD SA", tx.transaction.counterparty)
        assertEquals(listOf("comida"), tx.tags.map { it.name })
    }

    private fun readV5IdentityHash(context: Context): String {
        val fresh = Room.inMemoryDatabaseBuilder(context, FinanceDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            fresh.openHelper.readableDatabase.query(
                "SELECT identity_hash FROM room_master_table WHERE id = 42"
            ).use { cursor ->
                cursor.moveToFirst()
                return cursor.getString(0)
            }
        } finally {
            fresh.close()
        }
    }

    private fun createV1Database(context: Context, identityHash: String) {
        context.getDatabasePath(dbName).delete()
        val path = context.getDatabasePath(dbName).absolutePath
        val sqlite = SQLiteDatabase.openOrCreateDatabase(path, null)
        try {
            sqlite.execSQL(
                "CREATE TABLE accounts (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "name TEXT NOT NULL, institution TEXT NOT NULL, accountType TEXT NOT NULL, " +
                    "identifierSuffix TEXT NOT NULL, isOwnedByUser INTEGER NOT NULL, " +
                    "createdAt INTEGER NOT NULL)"
            )
            sqlite.execSQL(
                "CREATE TABLE transactions (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "type TEXT NOT NULL, amount INTEGER NOT NULL, dateTime INTEGER NOT NULL, " +
                    "description TEXT NOT NULL, counterparty TEXT NOT NULL, " +
                    "sourceAccountId INTEGER, destinationAccountId INTEGER, " +
                    "syncStatus TEXT NOT NULL, remoteId TEXT, " +
                    "createdAt INTEGER NOT NULL, updatedAt INTEGER NOT NULL, " +
                    "FOREIGN KEY(sourceAccountId) REFERENCES accounts(id) " +
                    "ON UPDATE NO ACTION ON DELETE SET NULL, " +
                    "FOREIGN KEY(destinationAccountId) REFERENCES accounts(id) " +
                    "ON UPDATE NO ACTION ON DELETE SET NULL)"
            )
            sqlite.execSQL("CREATE INDEX index_transactions_dateTime ON transactions (dateTime)")
            sqlite.execSQL("CREATE INDEX index_transactions_type ON transactions (type)")
            sqlite.execSQL(
                "CREATE INDEX index_transactions_sourceAccountId ON transactions (sourceAccountId)"
            )
            sqlite.execSQL(
                "CREATE INDEX index_transactions_destinationAccountId " +
                    "ON transactions (destinationAccountId)"
            )
            sqlite.execSQL(
                "CREATE TABLE tags (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "name TEXT NOT NULL, createdAt INTEGER NOT NULL)"
            )
            sqlite.execSQL("CREATE UNIQUE INDEX index_tags_name ON tags (name)")
            sqlite.execSQL(
                "CREATE TABLE transaction_tags (transactionId INTEGER NOT NULL, " +
                    "tagId INTEGER NOT NULL, PRIMARY KEY(transactionId, tagId), " +
                    "FOREIGN KEY(transactionId) REFERENCES transactions(id) " +
                    "ON UPDATE NO ACTION ON DELETE CASCADE, " +
                    "FOREIGN KEY(tagId) REFERENCES tags(id) " +
                    "ON UPDATE NO ACTION ON DELETE CASCADE)"
            )
            sqlite.execSQL(
                "CREATE INDEX index_transaction_tags_tagId ON transaction_tags (tagId)"
            )
            sqlite.execSQL(
                "INSERT INTO accounts (name, institution, accountType, identifierSuffix, isOwnedByUser, createdAt) " +
                    "VALUES ('Bancolombia', 'Bancolombia', '', '8494', 1, 1000)"
            )
            sqlite.execSQL(
                "INSERT INTO transactions (type, amount, dateTime, description, counterparty, sourceAccountId, syncStatus, createdAt, updatedAt) " +
                    "VALUES ('EXPENSE', 29000, 2000, '', 'BOLD SA', 1, 'PENDING_UPLOAD', 1000, 1000)"
            )
            sqlite.execSQL("INSERT INTO tags (name, createdAt) VALUES ('comida', 1000)")
            sqlite.execSQL("INSERT INTO transaction_tags (transactionId, tagId) VALUES (1, 1)")
            sqlite.execSQL(
                "CREATE TABLE room_master_table (id INTEGER PRIMARY KEY, identity_hash TEXT)"
            )
            sqlite.execSQL(
                "INSERT INTO room_master_table (id, identity_hash) VALUES (42, '$identityHash')"
            )
            sqlite.execSQL("PRAGMA user_version = 1")
        } finally {
            sqlite.close()
        }
    }
}
