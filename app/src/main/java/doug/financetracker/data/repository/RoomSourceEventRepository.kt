package doug.financetracker.data.repository

import doug.financetracker.data.local.dao.SourceEventDao
import doug.financetracker.data.local.mapper.toDomain
import doug.financetracker.domain.model.SourceEvent
import doug.financetracker.domain.repository.SourceEventRepository

class RoomSourceEventRepository(
    private val dao: SourceEventDao
) : SourceEventRepository {
    override suspend fun getById(id: Long): SourceEvent? =
        dao.getById(id)?.toDomain()
}
