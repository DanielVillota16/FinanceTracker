package doug.financetracker.data.local.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Explicit upgrade chain (Phase 8 hardening). Every schema change since v1 is
 * represented here and covered by [MigrationTest]; the database builder no
 * longer falls back to destructive migration, so a mismatch fails loudly in
 * development instead of silently wiping financial records.
 *
 * History:
 * - v1: accounts, transactions, tags, transaction_tags (manual CRUD era)
 * - v2: source_events + pending_reviews (review pipeline)
 * - v3: transaction_candidates + reviews.candidateId (purchase correlation)
 * - v4: candidates.suggestedKind (transfer matching)
 * - v5: accounts/tags sync metadata (remoteId/syncStatus/updatedAt) + tombstones
 */
object Migrations {

    val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `source_events` (" +
                    "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`sourceType` TEXT NOT NULL, " +
                    "`sourceIdentifier` TEXT NOT NULL, " +
                    "`receivedAt` INTEGER NOT NULL, " +
                    "`eventTime` INTEGER, " +
                    "`rawContent` TEXT NOT NULL, " +
                    "`fingerprint` TEXT NOT NULL, " +
                    "`parsedStatus` TEXT NOT NULL)"
            )
            db.execSQL(
                "CREATE UNIQUE INDEX IF NOT EXISTS `index_source_events_fingerprint` " +
                    "ON `source_events` (`fingerprint`)"
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_source_events_receivedAt` " +
                    "ON `source_events` (`receivedAt`)"
            )
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `pending_reviews` (" +
                    "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`sourceEventId` INTEGER NOT NULL, " +
                    "`amount` INTEGER, " +
                    "`direction` TEXT, " +
                    "`kind` TEXT NOT NULL, " +
                    "`possibleKinds` TEXT NOT NULL, " +
                    "`institution` TEXT NOT NULL, " +
                    "`sourceHintDigits` TEXT, " +
                    "`sourceHintLabel` TEXT, " +
                    "`sourceHintCash` INTEGER NOT NULL, " +
                    "`destHintDigits` TEXT, " +
                    "`destHintLabel` TEXT, " +
                    "`destHintCash` INTEGER NOT NULL, " +
                    "`counterparty` TEXT, " +
                    "`eventTime` INTEGER, " +
                    "`reference` TEXT, " +
                    "`confidence` TEXT NOT NULL, " +
                    "`warnings` TEXT NOT NULL, " +
                    "`status` TEXT NOT NULL, " +
                    "`linkedTransactionId` INTEGER, " +
                    "`createdAt` INTEGER NOT NULL, " +
                    "`updatedAt` INTEGER NOT NULL, " +
                    "FOREIGN KEY(`sourceEventId`) REFERENCES `source_events`(`id`) " +
                    "ON UPDATE NO ACTION ON DELETE CASCADE)"
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_pending_reviews_sourceEventId` " +
                    "ON `pending_reviews` (`sourceEventId`)"
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_pending_reviews_status` " +
                    "ON `pending_reviews` (`status`)"
            )
        }
    }

    val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `transaction_candidates` (" +
                    "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`status` TEXT NOT NULL, " +
                    "`linkedTransactionId` INTEGER, " +
                    "`createdAt` INTEGER NOT NULL, " +
                    "`updatedAt` INTEGER NOT NULL)"
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_transaction_candidates_status` " +
                    "ON `transaction_candidates` (`status`)"
            )
            db.execSQL("ALTER TABLE `pending_reviews` ADD COLUMN `candidateId` INTEGER")
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_pending_reviews_candidateId` " +
                    "ON `pending_reviews` (`candidateId`)"
            )
        }
    }

    val MIGRATION_3_4 = object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `transaction_candidates` ADD COLUMN `suggestedKind` TEXT")
        }
    }

    val MIGRATION_4_5 = object : Migration(4, 5) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `accounts` ADD COLUMN `remoteId` TEXT")
            db.execSQL(
                "ALTER TABLE `accounts` ADD COLUMN `syncStatus` TEXT NOT NULL " +
                    "DEFAULT 'PENDING_UPLOAD'"
            )
            db.execSQL(
                "ALTER TABLE `accounts` ADD COLUMN `updatedAt` INTEGER NOT NULL DEFAULT 0"
            )
            db.execSQL("UPDATE `accounts` SET `updatedAt` = `createdAt`")
            db.execSQL("ALTER TABLE `tags` ADD COLUMN `remoteId` TEXT")
            db.execSQL(
                "ALTER TABLE `tags` ADD COLUMN `syncStatus` TEXT NOT NULL " +
                    "DEFAULT 'PENDING_UPLOAD'"
            )
            db.execSQL(
                "ALTER TABLE `tags` ADD COLUMN `updatedAt` INTEGER NOT NULL DEFAULT 0"
            )
            db.execSQL("UPDATE `tags` SET `updatedAt` = `createdAt`")
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `sync_tombstones` (" +
                    "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`tableName` TEXT NOT NULL, " +
                    "`remoteId` TEXT NOT NULL, " +
                    "`createdAt` INTEGER NOT NULL)"
            )
        }
    }

    val ALL: Array<Migration> = arrayOf(
        MIGRATION_1_2,
        MIGRATION_2_3,
        MIGRATION_3_4,
        MIGRATION_4_5
    )
}
