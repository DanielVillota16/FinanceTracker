package doug.financetracker.domain.model

import doug.financetracker.domain.parser.ParsedTransaction

/**
 * One item in the Pending Review queue: a parser interpretation plus its
 * source evidence, awaiting explicit user confirmation.
 */
data class PendingItem(
    val id: Long = 0L,
    val sourceEvent: SourceEvent,
    val parsed: ParsedTransaction,
    val status: PendingStatus = PendingStatus.PENDING,
    val linkedTransactionId: Long? = null,
    val createdAt: Long = System.currentTimeMillis()
)

enum class PendingStatus {
    PENDING,
    CONFIRMED,
    DISMISSED
}
