package doug.financetracker.domain.model

/** Transaction plus resolved tags/accounts for display. */
data class TransactionDetails(
    val transaction: Transaction,
    val tags: List<Tag> = emptyList(),
    val sourceAccount: Account? = null,
    val destinationAccount: Account? = null
)
