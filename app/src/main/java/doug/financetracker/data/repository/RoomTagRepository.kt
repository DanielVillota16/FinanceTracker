package doug.financetracker.data.repository

import androidx.room.withTransaction
import doug.financetracker.data.local.database.FinanceDatabase
import doug.financetracker.data.local.entity.SyncTombstoneEntity
import doug.financetracker.data.local.entity.TagEntity
import doug.financetracker.data.local.mapper.toDomain
import doug.financetracker.data.local.mapper.toEntity
import doug.financetracker.domain.model.SyncStatus
import doug.financetracker.domain.model.Tag
import doug.financetracker.domain.repository.TagRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RoomTagRepository(
    private val db: FinanceDatabase
) : TagRepository {
    private val dao get() = db.tagDao()

    override fun observeTags(): Flow<List<Tag>> =
        dao.observeAll().map { list -> list.map { it.toDomain() } }

    override suspend fun getOrCreate(name: String): Long {
        val clean = name.trim()
        require(clean.isNotBlank()) { "Tag name must not be blank" }
        dao.getByName(clean)?.let { return it.id }
        return dao.insert(
            TagEntity(
                name = clean,
                syncStatus = SyncStatus.PENDING_UPLOAD.name,
                updatedAt = System.currentTimeMillis()
            )
        )
    }

    override suspend fun delete(tag: Tag) {
        db.withTransaction {
            dao.getByName(tag.name)?.remoteId?.let { remoteId ->
                db.syncTombstoneDao().insert(
                    SyncTombstoneEntity(tableName = "tags", remoteId = remoteId)
                )
            }
            dao.delete(tag.toEntity())
        }
    }
}
