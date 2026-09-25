package doug.financetracker.correlation

import doug.financetracker.domain.correlation.CandidateMerger
import doug.financetracker.domain.correlation.PurchaseCorrelation
import doug.financetracker.domain.model.PendingItem
import doug.financetracker.domain.model.SourceEvent
import doug.financetracker.domain.parser.AccountHint
import doug.financetracker.domain.parser.Confidence
import doug.financetracker.domain.parser.Direction
import doug.financetracker.domain.parser.ParsedTransaction
import doug.financetracker.domain.parser.TransactionKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CandidateMergerTest {

    private fun item(
        id: Long,
        amount: Long = 99_320L,
        suffix: String? = "1444",
        timestamp: Long = 1_000_000L,
        counterparty: String? = "Tien ia d1 psto"
    ) = PendingItem(
        id = id,
        sourceEvent = SourceEvent(
            id = id, sourceType = "NOTIFICATION",
            sourceIdentifier = "co.com.bbva.mb",
            receivedAt = timestamp, eventTime = timestamp,
            rawContent = "raw", fingerprint = "fp$id"
        ),
        parsed = ParsedTransaction(
            amountPesos = amount, direction = Direction.OUTGOING,
            transactionKind = TransactionKind.EXPENSE, institution = "BBVA",
            sourceAccountHint = suffix?.let { AccountHint(it) },
            destinationAccountHint = null, counterparty = counterparty,
            timestampMillis = timestamp, reference = null, confidence = Confidence.HIGH
        )
    )

    @Test
    fun `new item joins the correlated candidate`() {
        val existing = listOf(item(1) to 100L, item(3, amount = 5_000L) to 101L)
        val decision = CandidateMerger.decide(existing, item(2))
        assertTrue(decision is CandidateMerger.Decision.Join)
        decision as CandidateMerger.Decision.Join
        assertEquals(100L, decision.candidateId)
        assertEquals(1L, decision.matchedReviewId)
    }

    @Test
    fun `uncorrelated item requests a new candidate`() {
        val existing = listOf(item(1) to 100L)
        val decision = CandidateMerger.decide(existing, item(2, amount = 5_000L))
        assertEquals(CandidateMerger.Decision.NewCandidate, decision)
    }

    @Test
    fun `empty queue requests a new candidate`() {
        assertEquals(
            CandidateMerger.Decision.NewCandidate,
            CandidateMerger.decide(emptyList(), item(1))
        )
    }

    @Test
    fun `merger is symmetric regardless of arrival order`() {
        val walletSide = item(1, counterparty = "TIEN IA D1 PSTO IAM I")
        val bbvaSide = item(2, counterparty = "Tien ia d1 psto")
        val walletFirst = CandidateMerger.decide(listOf(walletSide to 100L), bbvaSide)
        val bbvaFirst = CandidateMerger.decide(listOf(bbvaSide to 200L), walletSide)
        assertTrue(walletFirst is CandidateMerger.Decision.Join)
        assertTrue(bbvaFirst is CandidateMerger.Decision.Join)
        // Each joins the other's candidate: same logical grouping either way.
        assertEquals(100L, (walletFirst as CandidateMerger.Decision.Join).candidateId)
        assertEquals(200L, (bbvaFirst as CandidateMerger.Decision.Join).candidateId)
    }
}
