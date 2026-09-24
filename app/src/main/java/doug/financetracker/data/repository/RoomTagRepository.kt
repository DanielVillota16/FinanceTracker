package doug.financetracker.data.repository

import doug.financetracker.data.local.dao.TagDao
import doug.financetracker.data.local.entity.TagEntity
import doug.financetracker.data.local.mapper.toDomain
import doug.financetracker.data.local.mapper.toEntity
import doug.financetracker.domain.model.Tag
import doug.financetracker.domain.repository.TagRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RoomTagRepository(
    private val dao: TagDao
) : TagRepository {
    override fun observeTags(): Flow<List<Tag>> =
        dao.observeAll().map { list -> list.map { it.toDomain() } }

    override suspend fun getOrCreate(name: String): Long {
        val clean = name.trim()
        require(clean.isNotBlank()) { "Tag name must not be blank" }
        dao.getByName(clean)?.let { return it.id }
        return dao.insert(TagEntity(name = clean))
    }

    override suspend fun delete(tag: Tag) {
        dao.delete(tag.toEntity())
    }
}
