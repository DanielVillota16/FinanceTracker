package doug.financetracker.domain.model

import doug.financetracker.domain.parser.Confidence
import doug.financetracker.domain.parser.TransactionKind

/**
 * One logical transaction under review with all its source evidence.
 *
 * [primary] is the member shown first and used for one-tap confirm/edit:
 * highest parser confidence, ties broken by earliest detection.
 */
data class PendingCandidate(
    val id: Long,
    val members: List<PendingItem>,
    val primary: PendingItem,
    val status: PendingStatus = PendingStatus.PENDING,
    /** TRANSFER when a transfer pair was matched; null = purchase/default. */
    val suggestedKind: TransactionKind? = null,
    val createdAt: Long = System.currentTimeMillis()
) {
    init {
        require(members.isNotEmpty()) { "A candidate needs at least one member" }
    }

    val isCorrelated: Boolean get() = members.size > 1

    /** Lowest member confidence — the honest headline for a merged card. */
    val headlineConfidence: Confidence
        get() = members.minOfOrNull { it.parsed.confidence } ?: Confidence.LOW

    /** Counterparties across members, primary first, deduplicated. */
    val counterparties: List<String>
        get() = (listOfNotNull(primary.parsed.counterparty) +
            members.filter { it.id != primary.id }.mapNotNull { it.parsed.counterparty })
            .distinct()
}
