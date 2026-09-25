package doug.financetracker.domain.model

import doug.financetracker.domain.parser.ParsedTransaction

/**
 * One member of the Pending Review queue: a parser interpretation plus its
 * source evidence, awaiting explicit user confirmation. Members are grouped
 * into [PendingCandidate]s; the grouping never deletes or merges rows.
 */
data class PendingItem(
    val id: Long = 0L,
    val sourceEvent: SourceEvent,
    val parsed: ParsedTransaction,
    val status: PendingStatus = PendingStatus.PENDING,
    val linkedTransactionId: Long? = null,
    val candidateId: Long? = null,
    val createdAt: Long = System.currentTimeMillis()
)

enum class PendingStatus {
    PENDING,
    CONFIRMED,
    DISMISSED
}
