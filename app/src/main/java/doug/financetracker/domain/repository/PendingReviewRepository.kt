package doug.financetracker.domain.repository

import doug.financetracker.domain.model.PendingCandidate
import doug.financetracker.domain.model.PendingItem
import kotlinx.coroutines.flow.Flow

interface PendingReviewRepository {
    fun observePending(): Flow<List<PendingItem>>
    /** Candidates group correlated members; the UI works in these units. */
    fun observeCandidates(): Flow<List<PendingCandidate>>
    suspend fun getItem(id: Long): PendingItem?
    suspend fun confirm(id: Long, linkedTransactionId: Long)
    suspend fun dismiss(id: Long)
    suspend fun dismissCandidate(candidateId: Long)
}
