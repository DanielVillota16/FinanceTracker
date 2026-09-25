package doug.financetracker.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Remembers remotely-stored rows the user deleted locally so the sync engine
 * can replay the delete on the next run (offline deletes included).
 * Only official-data tables are ever listed here — source evidence
 * (SourceEvents, reviews, candidates) is local-only and has no tombstones.
 */
@Entity(tableName = "sync_tombstones")
data class SyncTombstoneEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    /** Remote table: "accounts" | "tags" | "transactions" */
    val tableName: String,
    val remoteId: String,
    val createdAt: Long = System.currentTimeMillis()
)
