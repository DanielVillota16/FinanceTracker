package doug.financetracker.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import doug.financetracker.data.local.entity.SourceEventEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SourceEventDao {
    @Query("SELECT * FROM source_events WHERE fingerprint = :fingerprint LIMIT 1")
    suspend fun getByFingerprint(fingerprint: String): SourceEventEntity?

    @Query("SELECT * FROM source_events WHERE id = :id")
    suspend fun getById(id: Long): SourceEventEntity?

    /** IGNORE keeps ingestion idempotent even under races. Returns -1 on conflict. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnore(event: SourceEventEntity): Long

    @Update
    suspend fun update(event: SourceEventEntity)

    @Query("SELECT COUNT(*) FROM source_events")
    suspend fun count(): Int
}
