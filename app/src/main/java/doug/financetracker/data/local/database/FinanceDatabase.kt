package doug.financetracker.data.local.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import doug.financetracker.data.local.dao.AccountDao
import doug.financetracker.data.local.dao.PendingReviewDao
import doug.financetracker.data.local.dao.SourceEventDao
import doug.financetracker.data.local.dao.SyncTombstoneDao
import doug.financetracker.data.local.dao.TagDao
import doug.financetracker.data.local.dao.TransactionCandidateDao
import doug.financetracker.data.local.dao.TransactionDao
import doug.financetracker.data.local.entity.AccountEntity
import doug.financetracker.data.local.entity.PendingReviewEntity
import doug.financetracker.data.local.entity.SourceEventEntity
import doug.financetracker.data.local.entity.SyncTombstoneEntity
import doug.financetracker.data.local.entity.TagEntity
import doug.financetracker.data.local.entity.TransactionCandidateEntity
import doug.financetracker.data.local.entity.TransactionEntity
import doug.financetracker.data.local.entity.TransactionTagCrossRef
import java.util.concurrent.Executors

@Database(
    entities = [
        AccountEntity::class,
        TransactionEntity::class,
        TagEntity::class,
        TransactionTagCrossRef::class,
        SourceEventEntity::class,
        PendingReviewEntity::class,
        TransactionCandidateEntity::class,
        SyncTombstoneEntity::class
    ],
    version = 5,
    exportSchema = false
)
abstract class FinanceDatabase : RoomDatabase() {
    abstract fun accountDao(): AccountDao
    abstract fun transactionDao(): TransactionDao
    abstract fun tagDao(): TagDao
    abstract fun sourceEventDao(): SourceEventDao
    abstract fun pendingReviewDao(): PendingReviewDao
    abstract fun transactionCandidateDao(): TransactionCandidateDao
    abstract fun syncTombstoneDao(): SyncTombstoneDao

    companion object {
        @Volatile
        private var instance: FinanceDatabase? = null

        fun get(context: Context): FinanceDatabase =
            instance ?: synchronized(this) {
                instance ?: build(context).also { instance = it }
            }

        private fun build(context: Context): FinanceDatabase =
            Room.databaseBuilder(
                context.applicationContext,
                FinanceDatabase::class.java,
                "finance_tracker.db"
            )
                .addCallback(SeedCallback())
                // Pre-release: destructive migration is acceptable (single-user dev
                // installs). Phase 8 hardening adds exported schemas + tested
                // migrations before any wider distribution.
                .fallbackToDestructiveMigration()
                .build()

        /** Seeds the five default user-owned accounts on first creation. */
        private class SeedCallback : Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                super.onCreate(db)
                // Room executes this on the main thread by default; push seeding off-thread
                // and insert directly via SQL to avoid needing DAO instances here.
                Executors.newSingleThreadExecutor().execute {
                    val now = System.currentTimeMillis()
                    val defaults = listOf(
                        Triple("Bancolombia", "Bancolombia", ""),
                        Triple("Nequi", "Nequi", ""),
                        Triple("DAVIbank", "DAVIbank", ""),
                        Triple("BBVA", "BBVA", ""),
                        Triple("Cash", "Cash", "CASH")
                    )
                    for ((name, institution, type) in defaults) {
                        db.execSQL(
                            "INSERT INTO accounts (name, institution, accountType, identifierSuffix, isOwnedByUser, createdAt, syncStatus, updatedAt) VALUES (?, ?, ?, '', 1, ?, 'PENDING_UPLOAD', ?)",
                            arrayOf(name, institution, type, now, now)
                        )
                    }
                }
            }
        }
    }
}
