package doug.financetracker.ingest

import doug.financetracker.domain.model.Account
import doug.financetracker.domain.model.PendingItem
import doug.financetracker.domain.model.PendingStatus
import doug.financetracker.domain.model.SourceEvent
import doug.financetracker.domain.model.Transaction
import doug.financetracker.domain.model.TransactionDetails
import doug.financetracker.domain.parser.AccountHint
import doug.financetracker.domain.parser.Confidence
import doug.financetracker.domain.parser.Direction
import doug.financetracker.domain.parser.ParsedTransaction
import doug.financetracker.domain.parser.TransactionKind
import doug.financetracker.domain.repository.AccountRepository
import doug.financetracker.domain.repository.PendingReviewRepository
import doug.financetracker.domain.repository.TransactionRepository
import doug.financetracker.domain.usecase.ConfirmPendingItem
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.fail
import org.junit.Test

class ConfirmPendingItemTest {

    private val bancolombia = Account(
        id = 1, name = "Bancolombia", institution = "Bancolombia", identifierSuffix = "8494"
    )
    private val nequi = Account(id = 2, name = "Nequi", institution = "Nequi")
    private val cash = Account(id = 3, name = "Cash", institution = "Cash", accountType = "CASH")

    private class FakeAccounts(val list: List<Account>) : AccountRepository {
        override fun observeAccounts(): Flow<List<Account>> = flowOf(list)
        override suspend fun getAll(): List<Account> = list
        override suspend fun getById(id: Long): Account? = list.firstOrNull { it.id == id }
        override suspend fun create(account: Account): Long = error("unused")
        override suspend fun update(account: Account) = Unit
        override suspend fun delete(account: Account) = Unit
    }

    private class FakeTransactions : TransactionRepository {
        val created = mutableListOf<Transaction>()
        override fun observeDetails(): Flow<List<TransactionDetails>> = flowOf(emptyList())
        override suspend fun getDetails(id: Long): TransactionDetails? = null
        override suspend fun create(transaction: Transaction): Long {
            created += transaction
            return created.size.toLong()
        }
        override suspend fun update(transaction: Transaction) = Unit
        override suspend fun delete(id: Long) = Unit
    }

    private class FakePending(val items: MutableMap<Long, PendingItem>) : PendingReviewRepository {
        val confirmed = mutableListOf<Pair<Long, Long>>()
        val dismissed = mutableListOf<Long>()
        val dismissedCandidates = mutableListOf<Long>()
        override fun observePending(): Flow<List<PendingItem>> =
            MutableStateFlow(items.values.toList())
        override fun observeCandidates(): Flow<List<doug.financetracker.domain.model.PendingCandidate>> =
            flowOf(emptyList())
        override suspend fun getItem(id: Long): PendingItem? = items[id]
        override suspend fun confirm(id: Long, linkedTransactionId: Long) {
            confirmed += id to linkedTransactionId
        }
        override suspend fun dismiss(id: Long) {
            dismissed += id
        }
        override suspend fun dismissCandidate(candidateId: Long) {
            dismissedCandidates += candidateId
        }
        override suspend fun getCandidateForReview(reviewId: Long): doug.financetracker.domain.model.PendingCandidate? =
            null
    }

    private fun pendingItem(
        id: Long = 10L,
        parsed: ParsedTransaction,
        receivedAt: Long = 9_000L,
        eventTime: Long? = null
    ) = PendingItem(
        id = id,
        sourceEvent = SourceEvent(
            id = 7L,
            sourceType = "SMS",
            sourceIdentifier = "Bancolombia",
            receivedAt = receivedAt,
            eventTime = eventTime,
            rawContent = "raw",
            fingerprint = "fp"
        ),
        parsed = parsed
    )

    private fun expenseParsed(
        amount: Long? = 29_000L,
        hint: AccountHint? = AccountHint("8494"),
        timestamp: Long? = 8_000L
    ) = ParsedTransaction(
        amountPesos = amount,
        direction = Direction.OUTGOING,
        transactionKind = TransactionKind.EXPENSE,
        institution = "Bancolombia",
        sourceAccountHint = hint,
        destinationAccountHint = null,
        counterparty = "BOLD SA",
        timestampMillis = timestamp,
        reference = null,
        confidence = Confidence.HIGH
    )

    private fun setup(
        item: PendingItem,
        accounts: List<Account> = listOf(bancolombia, nequi, cash)
    ): Triple<ConfirmPendingItem, FakeTransactions, FakePending> {
        val tx = FakeTransactions()
        val pending = FakePending(mutableMapOf(item.id to item))
        return Triple(
            ConfirmPendingItem(pending, tx, FakeAccounts(accounts)),
            tx, pending
        )
    }

    @Test
    fun `confirm expense resolves hint and links evidence`() = runTest {
        val (confirm, tx, pending) = setup(pendingItem(parsed = expenseParsed()))
        val txId = confirm.confirm(10L)
        assertEquals(1L, txId)
        val created = tx.created.single()
        assertEquals(doug.financetracker.domain.model.TransactionType.EXPENSE, created.type)
        assertEquals(29_000L, created.amount)
        assertEquals(8_000L, created.dateTime)
        assertEquals("BOLD SA", created.counterparty)
        assertEquals(1L, created.sourceAccountId)
        assertNull(created.destinationAccountId)
        assertEquals(listOf(10L to 1L), pending.confirmed)
    }

    @Test
    fun `ambiguous kind throws and creates nothing`() = runTest {
        val parsed = expenseParsed().copy(
            transactionKind = TransactionKind.UNKNOWN,
            possibleKinds = listOf(TransactionKind.TRANSFER, TransactionKind.EXPENSE)
        )
        val (confirm, tx, pending) = setup(pendingItem(parsed = parsed))
        try {
            confirm.confirm(10L)
            fail("expected AmbiguousKind")
        } catch (e: ConfirmPendingItem.AmbiguousKind) {
            assertEquals(listOf("TRANSFER", "EXPENSE"), e.possible)
        }
        assertEquals(0, tx.created.size)
        assertEquals(0, pending.confirmed.size)
    }

    @Test
    fun `unresolvable account throws and creates nothing`() = runTest {
        val (confirm, tx, _) = setup(
            pendingItem(parsed = expenseParsed(hint = AccountHint("0000")))
        )
        try {
            confirm.confirm(10L)
            fail("expected NeedsAccountSelection")
        } catch (_: ConfirmPendingItem.NeedsAccountSelection) {
        }
        assertEquals(0, tx.created.size)
    }

    @Test
    fun `atm transfer resolves cash destination`() = runTest {
        val parsed = ParsedTransaction(
            amountPesos = 100_000L,
            direction = Direction.OUTGOING,
            transactionKind = TransactionKind.TRANSFER,
            institution = "Bancolombia",
            sourceAccountHint = AccountHint("8494"),
            destinationAccountHint = AccountHint.CASH,
            counterparty = null,
            timestampMillis = 8_000L,
            reference = "EXIT_LA70_2",
            confidence = Confidence.HIGH
        )
        val (confirm, tx, _) = setup(pendingItem(parsed = parsed))
        confirm.confirm(10L)
        val created = tx.created.single()
        assertEquals(doug.financetracker.domain.model.TransactionType.TRANSFER, created.type)
        assertEquals(1L, created.sourceAccountId)
        assertEquals(3L, created.destinationAccountId)
    }

    @Test
    fun `label-only nequi hint resolves by institution`() = runTest {
        val parsed = ParsedTransaction(
            amountPesos = 1_000L,
            direction = Direction.OUTGOING,
            transactionKind = TransactionKind.EXPENSE,
            institution = "Nequi",
            sourceAccountHint = AccountHint(lastDigits = "", label = "Nequi"),
            destinationAccountHint = null,
            counterparty = "GOOGLE *Google One",
            timestampMillis = 8_000L,
            reference = null,
            confidence = Confidence.HIGH
        )
        val (confirm, tx, _) = setup(pendingItem(parsed = parsed))
        confirm.confirm(10L)
        assertEquals(2L, tx.created.single().sourceAccountId)
    }

    @Test
    fun `timestamp falls back to event time then received time`() = runTest {
        val (confirm, tx, _) = setup(
            pendingItem(
                parsed = expenseParsed(timestamp = null),
                receivedAt = 9_000L,
                eventTime = 8_500L
            )
        )
        confirm.confirm(10L)
        assertEquals(8_500L, tx.created.single().dateTime)

        val (confirm2, tx2, _) = setup(
            pendingItem(
                parsed = expenseParsed(timestamp = null),
                receivedAt = 9_000L,
                eventTime = null
            )
        )
        confirm2.confirm(10L)
        assertEquals(9_000L, tx2.created.single().dateTime)
    }

    @Test
    fun `missing amount cannot be confirmed`() = runTest {
        val (confirm, tx, _) = setup(pendingItem(parsed = expenseParsed(amount = null)))
        try {
            confirm.confirm(10L)
            fail("expected IllegalStateException")
        } catch (_: IllegalStateException) {
        }
        assertEquals(0, tx.created.size)
    }

    @Test
    fun `transfer candidate confirms one transfer from both legs`() = runTest {
        val outParsed = ParsedTransaction(
            amountPesos = 100_000L, direction = Direction.OUTGOING,
            transactionKind = TransactionKind.UNKNOWN,
            possibleKinds = listOf(TransactionKind.TRANSFER, TransactionKind.EXPENSE),
            institution = "Bancolombia",
            sourceAccountHint = AccountHint("8494"), destinationAccountHint = null,
            counterparty = null, timestampMillis = 8_000L,
            reference = null, confidence = Confidence.MEDIUM
        )
        val inParsed = ParsedTransaction(
            amountPesos = 100_000L, direction = Direction.INCOMING,
            transactionKind = TransactionKind.INCOME,
            possibleKinds = listOf(TransactionKind.INCOME), institution = "Nequi",
            sourceAccountHint = null,
            destinationAccountHint = AccountHint("", "Nequi"),
            counterparty = "CARLOS RUIZ", timestampMillis = 8_100L,
            reference = null, confidence = Confidence.HIGH
        )
        val outItem = pendingItem(id = 10L, parsed = outParsed)
        val inItem = pendingItem(id = 11L, parsed = inParsed)
        val candidate = doug.financetracker.domain.model.PendingCandidate(
            id = 50L, members = listOf(outItem, inItem), primary = outItem,
            suggestedKind = TransactionKind.TRANSFER
        )
        val tx = FakeTransactions()
        val pending = FakePending(mutableMapOf(10L to outItem, 11L to inItem))
        val candidateByReview = mapOf(10L to candidate, 11L to candidate)
        val repo = object : PendingReviewRepository by pending {
            override suspend fun getCandidateForReview(reviewId: Long) =
                candidateByReview[reviewId]
        }
        val confirm = ConfirmPendingItem(
            repo, tx, FakeAccounts(listOf(bancolombia, nequi, cash))
        )
        val txId = confirm.confirm(10L)
        assertEquals(1L, txId)
        val created = tx.created.single()
        assertEquals(doug.financetracker.domain.model.TransactionType.TRANSFER, created.type)
        assertEquals(100_000L, created.amount)
        assertEquals(1L, created.sourceAccountId)
        assertEquals(2L, created.destinationAccountId)
        assertEquals(8_000L, created.dateTime)
        // Primary confirm links the whole candidate.
        assertEquals(listOf(10L to 1L), pending.confirmed)
    }
}
