package doug.financetracker.domain.usecase

import doug.financetracker.domain.correlation.AccountResolver
import doug.financetracker.domain.model.PendingCandidate
import doug.financetracker.domain.model.PendingStatus
import doug.financetracker.domain.model.Transaction
import doug.financetracker.domain.model.TransactionType
import doug.financetracker.domain.parser.Direction
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
        // Transfer candidates confirm as one TRANSFER built from both legs.
        // Anything unresolvable falls through to single-snapshot logic, which
        // routes to the edit form instead of guessing.
        val candidate = pendingRepo.getCandidateForReview(pendingId)
        if (candidate != null && candidate.suggestedKind == TransactionKind.TRANSFER) {
            confirmTransfer(candidate)?.let { return it }
        }
        val parsed = item.parsed
        val amount = parsed.amountPesos
            ?: throw IllegalStateException("Cannot confirm without a parsed amount")
        val dateTime = parsed.timestampMillis
            ?: item.sourceEvent.eventTime
            ?: item.sourceEvent.receivedAt
        val allAccounts = accounts.getAll()

        val transaction = when (parsed.transactionKind) {
            doug.financetracker.domain.parser.TransactionKind.EXPENSE -> {
                val source = AccountResolver.resolve(parsed.sourceAccountHint, allAccounts)
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
                val dest = AccountResolver.resolve(parsed.destinationAccountHint, allAccounts)
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
                val source = AccountResolver.resolve(parsed.sourceAccountHint, allAccounts)
                    ?: throw NeedsAccountSelection("Select the source account for this transfer.")
                val dest = AccountResolver.resolve(parsed.destinationAccountHint, allAccounts)
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

    /**
     * Builds one TRANSFER from a matched pair's legs. Returns null when the
     * legs cannot be identified (caller falls back to single-snapshot logic);
     * throws [NeedsAccountSelection] when accounts don't resolve.
     */
    private suspend fun confirmTransfer(candidate: PendingCandidate): Long? {
        val outgoing = candidate.members.firstOrNull { it.parsed.direction == Direction.OUTGOING }
            ?: return null
        val incoming = candidate.members.firstOrNull { it.parsed.direction == Direction.INCOMING }
            ?: return null
        val amount = outgoing.parsed.amountPesos ?: incoming.parsed.amountPesos ?: return null
        val allAccounts = accounts.getAll()
        val source = AccountResolver.resolve(outgoing.parsed.sourceAccountHint, allAccounts)
            ?: throw NeedsAccountSelection("Select the source account for this transfer.")
        val dest = AccountResolver.resolve(incoming.parsed.destinationAccountHint, allAccounts)
            ?: throw NeedsAccountSelection("Select the destination account for this transfer.")
        if (source.id == dest.id) {
            throw NeedsAccountSelection("Transfer accounts must differ; resolve in the edit form.")
        }
        val dateTime = outgoing.parsed.timestampMillis
            ?: outgoing.sourceEvent.eventTime
            ?: incoming.parsed.timestampMillis
            ?: incoming.sourceEvent.eventTime
            ?: outgoing.sourceEvent.receivedAt
        val transactionId = transactions.create(
            Transaction(
                type = TransactionType.TRANSFER,
                amount = amount,
                dateTime = dateTime,
                sourceAccountId = source.id,
                destinationAccountId = dest.id
            )
        )
        // Confirming the primary links every member to the same transaction.
        pendingRepo.confirm(candidate.primary.id, transactionId)
        return transactionId
    }
}
