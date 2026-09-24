package doug.financetracker.domain.usecase

import doug.financetracker.domain.model.Account
import doug.financetracker.domain.model.PendingStatus
import doug.financetracker.domain.model.Transaction
import doug.financetracker.domain.model.TransactionType
import doug.financetracker.domain.parser.AccountHint
import doug.financetracker.domain.parser.TransactionKind
import doug.financetracker.domain.repository.AccountRepository
import doug.financetracker.domain.repository.PendingReviewRepository
import doug.financetracker.domain.repository.TransactionRepository

/**
 * Converts a pending interpretation into an official transaction.
 *
 * One-tap confirm only succeeds when the snapshot is unambiguous: known kind,
 * parsed amount, and resolvable accounts. Anything else throws and the UI
 * must route the user to the edit form instead of guessing:
 * - [AmbiguousKind] → kind is UNKNOWN, user must pick Expense/Income/Transfer.
 * - [NeedsAccountSelection] → hint could not be matched to an account.
 */
class ConfirmPendingItem(
    private val pendingRepo: PendingReviewRepository,
    private val transactions: TransactionRepository,
    private val accounts: AccountRepository
) {
    class AmbiguousKind(val possible: List<String>) :
        Exception("Kind is ambiguous: ${possible.joinToString("/")}")

    class NeedsAccountSelection(message: String) : Exception(message)

    suspend fun confirm(pendingId: Long): Long {
        val item = pendingRepo.getItem(pendingId)
            ?: throw IllegalArgumentException("Pending item $pendingId not found")
        require(item.status == PendingStatus.PENDING) { "Item is no longer pending" }
        val parsed = item.parsed
        val amount = parsed.amountPesos
            ?: throw IllegalStateException("Cannot confirm without a parsed amount")
        val dateTime = parsed.timestampMillis
            ?: item.sourceEvent.eventTime
            ?: item.sourceEvent.receivedAt
        val allAccounts = accounts.getAll()

        val transaction = when (parsed.transactionKind) {
            doug.financetracker.domain.parser.TransactionKind.EXPENSE -> {
                val source = resolve(parsed.sourceAccountHint, allAccounts)
                    ?: throw NeedsAccountSelection("Select the source account for this expense.")
                Transaction(
                    type = TransactionType.EXPENSE,
                    amount = amount,
                    dateTime = dateTime,
                    counterparty = parsed.counterparty.orEmpty(),
                    sourceAccountId = source.id
                )
            }
            TransactionKind.INCOME -> {
                val dest = resolve(parsed.destinationAccountHint, allAccounts)
                    ?: throw NeedsAccountSelection("Select the destination account for this income.")
                Transaction(
                    type = TransactionType.INCOME,
                    amount = amount,
                    dateTime = dateTime,
                    counterparty = parsed.counterparty.orEmpty(),
                    destinationAccountId = dest.id
                )
            }
            TransactionKind.TRANSFER -> {
                val source = resolve(parsed.sourceAccountHint, allAccounts)
                    ?: throw NeedsAccountSelection("Select the source account for this transfer.")
                val dest = resolve(parsed.destinationAccountHint, allAccounts)
                    ?: throw NeedsAccountSelection("Select the destination account for this transfer.")
                Transaction(
                    type = TransactionType.TRANSFER,
                    amount = amount,
                    dateTime = dateTime,
                    counterparty = parsed.counterparty.orEmpty(),
                    sourceAccountId = source.id,
                    destinationAccountId = dest.id
                )
            }
            TransactionKind.UNKNOWN -> throw AmbiguousKind(parsed.possibleKinds.map { it.name })
        }

        val transactionId = transactions.create(transaction)
        pendingRepo.confirm(pendingId, transactionId)
        return transactionId
    }

    private fun resolve(hint: AccountHint?, allAccounts: List<Account>): Account? {
        if (hint == null) return null
        if (hint.isCash) {
            return allAccounts.firstOrNull { it.accountType == "CASH" }
                ?: allAccounts.firstOrNull { it.name.equals("Cash", ignoreCase = true) }
        }
        if (hint.lastDigits.isNotEmpty()) {
            allAccounts.firstOrNull { it.identifierSuffix == hint.lastDigits }?.let { return it }
        }
        // Label-only hints (e.g. "Nequi") match by institution or account name.
        hint.label?.takeIf { it.isNotBlank() }?.let { label ->
            allAccounts.firstOrNull {
                it.institution.equals(label, ignoreCase = true) ||
                    it.name.equals(label, ignoreCase = true)
            }?.let { return it }
        }
        return null
    }
}
