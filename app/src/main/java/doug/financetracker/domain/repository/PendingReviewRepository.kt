package doug.financetracker.domain.repository

import doug.financetracker.domain.model.PendingItem
import kotlinx.coroutines.flow.Flow

interface PendingReviewRepository {
    fun observePending(): Flow<List<PendingItem>>
    suspend fun getItem(id: Long): PendingItem?
    suspend fun confirm(id: Long, linkedTransactionId: Long)
    suspend fun dismiss(id: Long)
}
