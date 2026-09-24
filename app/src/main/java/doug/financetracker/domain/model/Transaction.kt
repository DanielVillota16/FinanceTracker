package doug.financetracker.domain.model

/**
 * Official user-confirmed financial record.
 * Amounts are exact COP pesos as Long (no floating point).
 *
 * - EXPENSE: sourceAccountId + counterparty
 * - INCOME: destinationAccountId + counterparty
 * - TRANSFER: sourceAccountId + destinationAccountId
 */
data class Transaction(
    val id: Long = 0L,
    val type: TransactionType,
    /** Exact amount in COP pesos. Always positive; direction comes from [type]. */
    val amount: Long,
    /** Epoch millis of when the movement happened. */
    val dateTime: Long,
    val description: String = "",
    val counterparty: String = "",
    val sourceAccountId: Long? = null,
    val destinationAccountId: Long? = null,
    val tagIds: List<Long> = emptyList(),
    val syncStatus: SyncStatus = SyncStatus.PENDING_UPLOAD,
    val remoteId: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
) {
    init {
        require(amount > 0) { "Amount must be positive, got $amount" }
    }
}

enum class SyncStatus {
    SYNCED,
    PENDING_UPLOAD,
    PENDING_UPDATE,
    PENDING_DELETE,
    ERROR
}
