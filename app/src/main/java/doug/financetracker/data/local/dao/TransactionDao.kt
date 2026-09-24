package doug.financetracker.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import doug.financetracker.data.local.entity.TransactionEntity
import doug.financetracker.data.local.relation.TransactionWithTags
import kotlinx.coroutines.flow.Flow

@Dao
interface TransactionDao {
    @Transaction
    @Query("SELECT * FROM transactions ORDER BY dateTime DESC, id DESC")
    fun observeAllWithTags(): Flow<List<TransactionWithTags>>

    @Transaction
    @Query("SELECT * FROM transactions WHERE id = :id")
    suspend fun getWithTags(id: Long): TransactionWithTags?

    @Query("SELECT * FROM transactions WHERE id = :id")
    suspend fun getById(id: Long): TransactionEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(transaction: TransactionEntity): Long

    @Update
    suspend fun update(transaction: TransactionEntity)

    @Delete
    suspend fun delete(transaction: TransactionEntity)

    @Query("DELETE FROM transactions WHERE id = :id")
    suspend fun deleteById(id: Long)
}
