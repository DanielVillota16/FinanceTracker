package doug.financetracker.domain.repository

import doug.financetracker.domain.model.SourceEvent

interface SourceEventRepository {
    suspend fun getById(id: Long): SourceEvent?
}
