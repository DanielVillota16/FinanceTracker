package doug.financetracker.pending

import doug.financetracker.domain.model.Account
import doug.financetracker.domain.model.PendingCandidate
import doug.financetracker.domain.model.PendingItem
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
import doug.financetracker.ui.screens.pending.PendingViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PendingBulkTest {

    private val mainDispatcher = StandardTestDispatcher()

    private val bancolombia = Account(
        id = 1, name = "Bancolombia", institution = "Bancolombia", identifierSuffix = "8494"
    )

    private class FakeAccounts : AccountRepository {
        override fun observeAccounts(): Flow<List<Account>> = flowOf(emptyList())
        override suspend fun getAll(): List<Account> = listOf(
            Account(id = 1, name = "Bancolombia", institution = "Bancolombia", identifierSuffix = "8494")
        )
        override suspend fun getById(id: Long): Account? = getAll().firstOrNull { it.id == id }
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

    private class FakePending(val candidates: List<PendingCandidate>) : PendingReviewRepository {
        val dismissed = mutableListOf<Long>()
        override fun observePending(): Flow<List<PendingItem>> =
            flowOf(candidates.flatMap { it.members })
        override fun observeCandidates(): Flow<List<PendingCandidate>> = flowOf(candidates)
        override suspend fun getItem(id: Long): PendingItem? =
            candidates.flatMap { it.members }.firstOrNull { it.id == id }
        override suspend fun confirm(id: Long, linkedTransactionId: Long) = Unit
        override suspend fun dismiss(id: Long) = Unit
        override suspend fun dismissCandidate(candidateId: Long) {
            dismissed += candidateId
        }
        override suspend fun getCandidateForReview(reviewId: Long): PendingCandidate? = null
    }

    private fun expenseItem(id: Long) = PendingItem(
        id = id,
        sourceEvent = SourceEvent(
            id = id, sourceType = "SMS", sourceIdentifier = "Bancolombia",
            receivedAt = 1000L, eventTime = 1000L, rawContent = "raw", fingerprint = "fp$id"
        ),
        parsed = ParsedTransaction(
            amountPesos = 1_000L, direction = Direction.OUTGOING,
            transactionKind = TransactionKind.EXPENSE, institution = "Bancolombia",
            sourceAccountHint = AccountHint("8494"), destinationAccountHint = null,
            counterparty = "X", timestampMillis = 1000L, reference = null,
            confidence = Confidence.HIGH
        )
    )

    private fun ambiguousItem(id: Long) = expenseItem(id).copy(
        parsed = expenseItem(id).parsed.copy(
            transactionKind = TransactionKind.UNKNOWN,
            possibleKinds = listOf(TransactionKind.TRANSFER, TransactionKind.EXPENSE),
            confidence = Confidence.MEDIUM
        )
    )

    private fun candidate(id: Long, vararg members: PendingItem): PendingCandidate {
        val list = members.toList()
        return PendingCandidate(id = id, members = list, primary = list.first())
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(mainDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `bulk confirm counts confirmed and ambiguous separately`() = runTest {
        val tx = FakeTransactions()
        val repo = FakePending(
            listOf(candidate(100L, expenseItem(10L)), candidate(200L, ambiguousItem(11L)))
        )
        val vm = PendingViewModel(repo, ConfirmPendingItem(repo, tx, FakeAccounts()))
        // Start the WhileSubscribed candidates flow like the screen does.
        val watching = launch { vm.candidates.collect {} }
        advanceUntilIdle()

        val messages = mutableListOf<String>()
        val collect = launch {
            vm.events.collect {
                if (it is PendingViewModel.Event.Info) messages += it.message
            }
        }
        vm.setSelecting(true)
        vm.toggleSelect(100L)
        vm.toggleSelect(200L)
        vm.confirmSelected()
        advanceUntilIdle()

        assertEquals(1, tx.created.size)
        assertEquals(listOf("1 confirmed, 1 need individual review"), messages)
        assertEquals(emptySet<Long>(), vm.selected.value)
        collect.cancel()
        watching.cancel()
    }

    @Test
    fun `bulk dismiss retires every selected candidate`() = runTest {
        val tx = FakeTransactions()
        val repo = FakePending(
            listOf(candidate(100L, expenseItem(10L)), candidate(200L, expenseItem(11L)))
        )
        val vm = PendingViewModel(repo, ConfirmPendingItem(repo, tx, FakeAccounts()))
        // Start the WhileSubscribed candidates flow like the screen does.
        val watching = launch { vm.candidates.collect {} }
        advanceUntilIdle()

        vm.setSelecting(true)
        vm.toggleSelect(100L)
        vm.toggleSelect(200L)
        vm.dismissSelected()
        advanceUntilIdle()

        assertTrue(repo.dismissed.containsAll(listOf(100L, 200L)))
        assertEquals(emptySet<Long>(), vm.selected.value)
        watching.cancel()
    }

    @Test
    fun `cancel clears the selection`() = runTest {
        val tx = FakeTransactions()
        val repo = FakePending(listOf(candidate(100L, expenseItem(10L))))
        val vm = PendingViewModel(repo, ConfirmPendingItem(repo, tx, FakeAccounts()))
        // Start the WhileSubscribed candidates flow like the screen does.
        val watching = launch { vm.candidates.collect {} }
        advanceUntilIdle()

        vm.setSelecting(true)
        vm.toggleSelect(100L)
        vm.setSelecting(false)
        assertEquals(emptySet<Long>(), vm.selected.value)
        assertEquals(0, tx.created.size)
        watching.cancel()
    }
}
