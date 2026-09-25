package doug.financetracker.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import doug.financetracker.data.local.entity.PendingReviewEntity
import doug.financetracker.data.local.entity.SourceEventEntity

/** Joined row for building PendingItems with their source evidence. */
data class PendingReviewWithSource(
    @androidx.room.Embedded val review: PendingReviewEntity,
    @androidx.room.Relation(parentColumn = "sourceEventId", entityColumn = "id")
    val source: SourceEventEntity
)

@Dao
interface PendingReviewDao {
    @Transaction
    @Query("SELECT * FROM pending_reviews WHERE status = 'PENDING' ORDER BY createdAt DESC, id DESC")
    fun observePending(): kotlinx.coroutines.flow.Flow<List<PendingReviewWithSource>>

    @Transaction
    @Query("SELECT * FROM pending_reviews WHERE id = :id")
    suspend fun getWithSource(id: Long): PendingReviewWithSource?

    @Query("SELECT * FROM pending_reviews WHERE sourceEventId = :sourceEventId AND status = 'PENDING' LIMIT 1")
    suspend fun findPendingForEvent(sourceEventId: Long): PendingReviewEntity?

    /** Recent queue for correlation scoring (includes candidate links). */
    @Transaction
    @Query("SELECT * FROM pending_reviews WHERE status = 'PENDING' ORDER BY createdAt DESC, id DESC LIMIT :limit")
    suspend fun findRecentPending(limit: Int): List<PendingReviewWithSource>

    @Query("UPDATE pending_reviews SET candidateId = :candidateId WHERE id = :reviewId")
    suspend fun setCandidate(reviewId: Long, candidateId: Long)

    @Query("SELECT * FROM pending_reviews WHERE candidateId = :candidateId")
    suspend fun getByCandidate(candidateId: Long): List<PendingReviewEntity>

    @Transaction
    @Query("SELECT * FROM pending_reviews WHERE candidateId = :candidateId")
    suspend fun getMembersWithSource(candidateId: Long): List<PendingReviewWithSource>

    /** Confirm every member of a candidate against the same transaction. */
    @Query(
        "UPDATE pending_reviews SET status = 'CONFIRMED', linkedTransactionId = :transactionId, " +
            "updatedAt = :now WHERE candidateId = :candidateId AND status = 'PENDING'"
    )
    suspend fun confirmCandidateMembers(candidateId: Long, transactionId: Long, now: Long)

    @Query(
        "UPDATE pending_reviews SET status = 'DISMISSED', updatedAt = :now " +
            "WHERE candidateId = :candidateId AND status = 'PENDING'"
    )
    suspend fun dismissCandidateMembers(candidateId: Long, now: Long)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(review: PendingReviewEntity): Long

    @Update
    suspend fun update(review: PendingReviewEntity)

    @Query("SELECT COUNT(*) FROM pending_reviews WHERE status = 'PENDING'")
    suspend fun pendingCount(): Int
}
