package doug.financetracker.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import doug.financetracker.data.local.entity.SyncTombstoneEntity

@Dao
interface SyncTombstoneDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(tombstone: SyncTombstoneEntity): Long

    @Query("SELECT * FROM sync_tombstones ORDER BY createdAt ASC")
    suspend fun getAll(): List<SyncTombstoneEntity>

    @Query("DELETE FROM sync_tombstones WHERE id = :id")
    suspend fun delete(id: Long)
}
