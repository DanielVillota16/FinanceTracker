package doug.financetracker.domain.model

/** Core financial movement type. Transfers must never count as income/expense. */
enum class TransactionType {
    EXPENSE,
    INCOME,
    TRANSFER
}
