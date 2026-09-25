package doug.financetracker.domain.correlation

import doug.financetracker.domain.model.PendingItem

/**
 * Pure candidate-assignment step used by ingestion.
 *
 * Given the queue's current PENDING reviews (each already carrying a
 * candidate id) and one newly ingested item, either joins the first
 * correlated candidate or requests a fresh one. Deterministic and
 * order-independent: A-then-B joins the same candidate as B-then-A.
 *
 * Transitive multi-candidate merges are deliberately out of scope: joining
 * the first strong match is enough, and uncertain cases stay visible for
 * the user instead of being auto-chained.
 */
object CandidateMerger {

    sealed interface Decision {
        /** Join the existing candidate (member review ids that matched). */
        data class Join(val candidateId: Long, val matchedReviewId: Long) : Decision
        data object NewCandidate : Decision
    }

    fun decide(
        existing: List<Pair<PendingItem, Long>>,
        newItem: PendingItem
    ): Decision {
        val newInput = PurchaseCorrelation.inputOf(newItem)
        for ((item, candidateId) in existing) {
            val decision = PurchaseCorrelation.decide(
                PurchaseCorrelation.inputOf(item), newInput
            )
            if (decision is PurchaseCorrelation.Decision.Correlated) {
                return Decision.Join(candidateId, item.id)
            }
        }
        return Decision.NewCandidate
    }
}
