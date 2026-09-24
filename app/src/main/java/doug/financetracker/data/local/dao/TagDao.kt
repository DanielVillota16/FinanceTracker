package doug.financetracker.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import doug.financetracker.data.local.entity.TagEntity
import doug.financetracker.data.local.entity.TransactionTagCrossRef
import kotlinx.coroutines.flow.Flow

@Dao
interface TagDao {
    @Query("SELECT * FROM tags ORDER BY name ASC")
    fun observeAll(): Flow<List<TagEntity>>

    @Query("SELECT * FROM tags ORDER BY name ASC")
    suspend fun getAll(): List<TagEntity>

    @Query("SELECT * FROM tags WHERE name = :name COLLATE NOCASE LIMIT 1")
    suspend fun getByName(name: String): TagEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(tag: TagEntity): Long

    @Delete
    suspend fun delete(tag: TagEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun linkTag(ref: TransactionTagCrossRef)

    @Query("DELETE FROM transaction_tags WHERE transactionId = :transactionId")
    suspend fun unlinkAll(transactionId: Long)

    @Query("SELECT tagId FROM transaction_tags WHERE transactionId = :transactionId")
    suspend fun tagIdsFor(transactionId: Long): List<Long>
}
