package doug.financetracker.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One logical transaction under review (spec §19).
 *
 * A candidate groups one or more [PendingReviewEntity] rows that the
 * correlation layer believes describe the same movement (e.g. a bank
 * notification plus its Google Wallet counterpart). Solo detections get a
 * single-member candidate so the review UI always works in candidates.
 *
 * Member rows are NEVER deleted on merge/confirm/dismiss — evidence stays.
 */
@Entity(
    tableName = "transaction_candidates",
    indices = [Index("status")]
)
data class TransactionCandidateEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    /** PENDING | CONFIRMED | DISMISSED */
    val status: String = "PENDING",
    val linkedTransactionId: Long? = null,
    /**
     * Matcher proposal, null when members simply co-occur. TRANSFER means a
     * transfer pair was matched — confirm builds one TRANSFER, and the card
     * renders source → destination. Purchases need no marker (default).
     */
    val suggestedKind: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
