package doug.financetracker.data.repository

import androidx.room.withTransaction
import doug.financetracker.data.local.database.FinanceDatabase
import doug.financetracker.data.local.mapper.toDomain
import doug.financetracker.domain.model.PendingCandidate
import doug.financetracker.domain.model.PendingItem
import doug.financetracker.domain.model.PendingStatus
import doug.financetracker.domain.repository.PendingReviewRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

class RoomPendingReviewRepository(
    private val db: FinanceDatabase
) : PendingReviewRepository {

    private val reviews get() = db.pendingReviewDao()
    private val candidates get() = db.transactionCandidateDao()

    override fun observePending(): Flow<List<PendingItem>> =
        reviews.observePending().map { rows ->
            rows.map { row -> row.review.toDomain(row.source.toDomain()) }
        }

    override fun observeCandidates(): Flow<List<PendingCandidate>> =
        combine(
            candidates.observePending(),
            reviews.observePending()
        ) { candidateRows, reviewRows ->
            val membersByCandidate = reviewRows
                .filter { it.review.candidateId != null }
                .groupBy { it.review.candidateId!! }
            candidateRows.mapNotNull { candidate ->
                val members = membersByCandidate[candidate.id]
                    ?.map { row -> row.review.toDomain(row.source.toDomain()) }
                    .orEmpty()
                if (members.isEmpty()) return@mapNotNull null
                PendingCandidate(
                    id = candidate.id,
                    members = members.sortedBy { it.createdAt },
                    primary = selectPrimary(members),
                    status = runCatching { PendingStatus.valueOf(candidate.status) }
                        .getOrDefault(PendingStatus.PENDING),
                    createdAt = candidate.createdAt
                )
            }
        }

    override suspend fun getItem(id: Long): PendingItem? {
        val row = reviews.getWithSource(id) ?: return null
        return row.review.toDomain(row.source.toDomain())
    }

    /**
     * Confirming one member confirms the whole candidate against the same
     * transaction — members are evidence of one movement, and every row
     * keeps its link (nothing deleted).
     */
    override suspend fun confirm(id: Long, linkedTransactionId: Long) {
        db.withTransaction {
            val row = reviews.getWithSource(id) ?: return@withTransaction
            val now = System.currentTimeMillis()
            val candidateId = row.review.candidateId
            if (candidateId == null) {
                reviews.update(
                    row.review.copy(
                        status = PendingStatus.CONFIRMED.name,
                        linkedTransactionId = linkedTransactionId,
                        updatedAt = now
                    )
                )
                return@withTransaction
            }
            reviews.confirmCandidateMembers(candidateId, linkedTransactionId, now)
            candidates.getById(candidateId)?.let { candidate ->
                candidates.update(
                    candidate.copy(
                        status = PendingStatus.CONFIRMED.name,
                        linkedTransactionId = linkedTransactionId,
                        updatedAt = now
                    )
                )
            }
        }
    }

    override suspend fun dismiss(id: Long) {
        val row = reviews.getWithSource(id) ?: return
        reviews.update(
            row.review.copy(
                status = PendingStatus.DISMISSED.name,
                updatedAt = System.currentTimeMillis()
            )
        )
    }

    override suspend fun dismissCandidate(candidateId: Long) {
        db.withTransaction {
            val now = System.currentTimeMillis()
            reviews.dismissCandidateMembers(candidateId, now)
            candidates.getById(candidateId)?.let { candidate ->
                candidates.update(
                    candidate.copy(
                        status = PendingStatus.DISMISSED.name,
                        updatedAt = now
                    )
                )
            }
        }
    }

    private fun selectPrimary(members: List<PendingItem>): PendingItem =
        members.minWithOrNull(
            compareBy(
                { it.parsed.confidence.ordinal },
                { it.createdAt },
                { it.id }
            )
        ) ?: members.first()
}
