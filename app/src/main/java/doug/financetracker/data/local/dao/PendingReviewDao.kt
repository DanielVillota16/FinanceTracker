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

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(review: PendingReviewEntity): Long

    @Update
    suspend fun update(review: PendingReviewEntity)

    @Query("SELECT COUNT(*) FROM pending_reviews WHERE status = 'PENDING'")
    suspend fun pendingCount(): Int
}
