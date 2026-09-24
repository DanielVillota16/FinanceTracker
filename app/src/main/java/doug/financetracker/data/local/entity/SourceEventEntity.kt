package doug.financetracker.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "source_events",
    indices = [
        Index(value = ["fingerprint"], unique = true),
        Index("receivedAt")
    ]
)
data class SourceEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val sourceType: String,
    val sourceIdentifier: String,
    val receivedAt: Long = System.currentTimeMillis(),
    val eventTime: Long? = null,
    val rawContent: String,
    val fingerprint: String,
    /** RECEIVED | PARSED | UNSUPPORTED */
    val parsedStatus: String = "RECEIVED"
)
