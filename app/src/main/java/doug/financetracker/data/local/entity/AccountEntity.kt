package doug.financetracker.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "accounts")
data class AccountEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val name: String,
    val institution: String,
    val accountType: String = "",
    val identifierSuffix: String = "",
    val isOwnedByUser: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
    /** Remote backup/sync metadata (Phase 7). Null until first upload. */
    val remoteId: String? = null,
    /** SYNCED | PENDING_UPLOAD | PENDING_UPDATE | ERROR */
    val syncStatus: String = "PENDING_UPLOAD",
    val updatedAt: Long = System.currentTimeMillis()
)
