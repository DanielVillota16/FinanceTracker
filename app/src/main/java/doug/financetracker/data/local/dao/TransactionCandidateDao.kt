package doug.financetracker.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import doug.financetracker.data.local.entity.TransactionCandidateEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TransactionCandidateDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(candidate: TransactionCandidateEntity): Long

    @Query("SELECT * FROM transaction_candidates WHERE id = :id")
    suspend fun getById(id: Long): TransactionCandidateEntity?

    @Update
    suspend fun update(candidate: TransactionCandidateEntity)

    @Query("SELECT * FROM transaction_candidates WHERE status = 'PENDING' ORDER BY createdAt DESC, id DESC")
    fun observePending(): Flow<List<TransactionCandidateEntity>>
}
