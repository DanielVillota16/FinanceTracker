package doug.financetracker.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Stored parser interpretation awaiting review. The parsed snapshot is kept
 * as columns (not a serialized blob) so future queries/migrations stay simple.
 * Multi-value fields use \u001F (unit separator) joins.
 */
@Entity(
    tableName = "pending_reviews",
    foreignKeys = [
        ForeignKey(
            entity = SourceEventEntity::class,
            parentColumns = ["id"],
            childColumns = ["sourceEventId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("sourceEventId"),
        Index("status"),
        Index("candidateId")
    ]
)
data class PendingReviewEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val sourceEventId: Long,
    /** Owning logical candidate; intentionally no FK — evidence must survive. */
    val candidateId: Long? = null,
    val amount: Long? = null,
    /** INCOMING | OUTGOING */
    val direction: String? = null,
    /** EXPENSE | INCOME | TRANSFER | UNKNOWN */
    val kind: String,
    /** \u001F-joined possible kinds */
    val possibleKinds: String,
    val institution: String,
    val sourceHintDigits: String? = null,
    val sourceHintLabel: String? = null,
    val sourceHintCash: Boolean = false,
    val destHintDigits: String? = null,
    val destHintLabel: String? = null,
    val destHintCash: Boolean = false,
    val counterparty: String? = null,
    val eventTime: Long? = null,
    val reference: String? = null,
    /** HIGH | MEDIUM | LOW */
    val confidence: String,
    /** \u001F-joined warnings */
    val warnings: String = "",
    /** PENDING | CONFIRMED | DISMISSED */
    val status: String = "PENDING",
    val linkedTransactionId: Long? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
