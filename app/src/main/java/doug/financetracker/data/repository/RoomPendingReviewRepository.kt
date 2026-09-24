package doug.financetracker.data.repository

import doug.financetracker.data.local.dao.PendingReviewDao
import doug.financetracker.data.local.mapper.toDomain
import doug.financetracker.domain.model.PendingItem
import doug.financetracker.domain.model.PendingStatus
import doug.financetracker.domain.repository.PendingReviewRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RoomPendingReviewRepository(
    private val dao: PendingReviewDao
) : PendingReviewRepository {

    override fun observePending(): Flow<List<PendingItem>> =
        dao.observePending().map { rows ->
            rows.map { row -> row.review.toDomain(row.source.toDomain()) }
        }

    override suspend fun getItem(id: Long): PendingItem? {
        val row = dao.getWithSource(id) ?: return null
        return row.review.toDomain(row.source.toDomain())
    }

    override suspend fun confirm(id: Long, linkedTransactionId: Long) {
        val row = dao.getWithSource(id) ?: return
        dao.update(
            row.review.copy(
                status = PendingStatus.CONFIRMED.name,
                linkedTransactionId = linkedTransactionId,
                updatedAt = System.currentTimeMillis()
            )
        )
    }

    override suspend fun dismiss(id: Long) {
        val row = dao.getWithSource(id) ?: return
        dao.update(
            row.review.copy(
                status = PendingStatus.DISMISSED.name,
                updatedAt = System.currentTimeMillis()
            )
        )
    }
}
