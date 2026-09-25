package doug.financetracker.correlation

import doug.financetracker.domain.correlation.PurchaseCorrelation
import doug.financetracker.domain.model.PendingItem
import doug.financetracker.domain.model.SourceEvent
import doug.financetracker.domain.parser.BancolombiaParser
import doug.financetracker.domain.parser.BbvaParser
import doug.financetracker.domain.parser.Confidence
import doug.financetracker.domain.parser.Direction
import doug.financetracker.domain.parser.GoogleWalletParser
import doug.financetracker.domain.parser.ParsedTransaction
import doug.financetracker.domain.parser.TransactionKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class PurchaseCorrelationTest {

    private fun millis(y: Int, mo: Int, d: Int, h: Int, mi: Int): Long =
        LocalDateTime.of(y, mo, d, h, mi).atZone(ZoneId.systemDefault())
            .toInstant().toEpochMilli()

    private fun item(
        id: Long,
        parsed: ParsedTransaction,
        sourceType: String = "SMS",
        identifier: String = "Bancolombia",
        receivedAt: Long = millis(2026, 9, 24, 14, 53),
        eventTime: Long? = null
    ) = PendingItem(
        id = id,
        sourceEvent = SourceEvent(
            id = id, sourceType = sourceType, sourceIdentifier = identifier,
            receivedAt = receivedAt, eventTime = eventTime,
            rawContent = "raw", fingerprint = "fp$id"
        ),
        parsed = parsed
    )

    private fun decide(a: PendingItem, b: PendingItem) =
        PurchaseCorrelation.decide(
            PurchaseCorrelation.inputOf(a), PurchaseCorrelation.inputOf(b)
        )

    // Real fixtures -------------------------------------------------------

    private val bbvaPhysical = checkNotNull(
        BbvaParser().parse(
            "Compra Exitosa\nHola, realizaste una compra por \$99,320.00 en " +
                "Tien ia d1 psto con tu tarjeta BBVA *1444. El 2026-09-24 a las 14:53."
        )
    )

    private val walletPhysical = checkNotNull(
        GoogleWalletParser().parse(
            "TIEN IA D1 PSTO IAM I\nCOP 99,320.00 with Mastercard Platinum ••1444"
        )
    )

    private val bancolombiaSms = checkNotNull(
        BancolombiaParser().parse(
            "Bancolombia: Compraste \$29.000,00 en BOLD SA*20 DE JU con tu T.Deb *0757, " +
                "el 21/09/2026 a las 08:27. Si tienes dudas, encuentranos aqui: 6045109095."
        )
    )

    private val walletBancolombia = checkNotNull(
        GoogleWalletParser().parse(
            "BOLD SA\nCOP 29,000.00 with Debit ••0757"
        )
    )

    @Test
    fun `scenario A - bbva notification plus wallet is one purchase`() {
        val bbva = item(1, bbvaPhysical, identifier = "co.com.bbva.mb")
        val wallet = item(
            2, walletPhysical,
            sourceType = "NOTIFICATION",
            identifier = "com.google.android.apps.walletnfcrel",
            eventTime = millis(2026, 9, 24, 14, 53)
        )
        val decision = decide(bbva, wallet)
        assertTrue("expected Correlated, got $decision", decision is PurchaseCorrelation.Decision.Correlated)
        decision as PurchaseCorrelation.Decision.Correlated
        // Different merchant strings still correlate with high confidence.
        assertEquals(Confidence.HIGH, decision.confidence)
        assertTrue(decision.evidence.any { it.contains("same amount") })
        assertTrue(decision.evidence.any { it.contains("****1444") })
    }

    @Test
    fun `scenario B - bancolombia sms plus wallet is one purchase`() {
        val sms = item(1, bancolombiaSms, receivedAt = millis(2026, 9, 21, 8, 27))
        val wallet = item(
            2, walletBancolombia,
            sourceType = "NOTIFICATION",
            identifier = "com.google.android.apps.walletnfcrel",
            eventTime = millis(2026, 9, 21, 8, 27)
        )
        val decision = decide(sms, wallet)
        assertTrue("expected Correlated, got $decision", decision is PurchaseCorrelation.Decision.Correlated)
    }

    @Test
    fun `ordering does not matter`() {
        val bbva = item(1, bbvaPhysical, identifier = "co.com.bbva.mb")
        val wallet = item(
            2, walletPhysical,
            sourceType = "NOTIFICATION",
            identifier = "com.google.android.apps.walletnfcrel",
            eventTime = millis(2026, 9, 24, 14, 53)
        )
        val forward = decide(bbva, wallet)
        val backward = decide(wallet, bbva)
        assertTrue(forward is PurchaseCorrelation.Decision.Correlated)
        assertTrue(backward is PurchaseCorrelation.Decision.Correlated)
        assertEquals(
            (forward as PurchaseCorrelation.Decision.Correlated).confidence,
            (backward as PurchaseCorrelation.Decision.Correlated).confidence
        )
    }

    // Negative cases ------------------------------------------------------

    private fun expense(
        amount: Long = 29_000L,
        suffix: String? = "0757",
        timestamp: Long? = millis(2026, 9, 21, 8, 27),
        counterparty: String? = "BOLD SA*20 DE JU"
    ) = ParsedTransaction(
        amountPesos = amount, direction = Direction.OUTGOING,
        transactionKind = TransactionKind.EXPENSE, institution = "Bancolombia",
        sourceAccountHint = suffix?.let { doug.financetracker.domain.parser.AccountHint(it) },
        destinationAccountHint = null, counterparty = counterparty,
        timestampMillis = timestamp, reference = null, confidence = Confidence.HIGH
    )

    private fun assertSeparate(a: PendingItem, b: PendingItem, why: String) {
        val decision = decide(a, b)
        assertTrue(
            "expected NotCorrelated ($why), got $decision",
            decision is PurchaseCorrelation.Decision.NotCorrelated
        )
    }

    @Test
    fun `same amount different card does not merge`() {
        assertSeparate(
            item(1, expense(suffix = "0757")),
            item(2, expense(suffix = "1444")),
            "different card"
        )
    }

    @Test
    fun `same card different amount does not merge`() {
        assertSeparate(
            item(1, expense(amount = 29_000L)),
            item(2, expense(amount = 40_000L)),
            "different amount"
        )
    }

    @Test
    fun `same amount and card but distant timestamps do not merge`() {
        assertSeparate(
            item(1, expense(timestamp = millis(2026, 9, 21, 8, 27))),
            item(2, expense(timestamp = millis(2026, 9, 21, 15, 40))),
            "distant timestamps"
        )
    }

    @Test
    fun `similar merchant different amount does not merge`() {
        assertSeparate(
            item(1, expense(amount = 29_000L, counterparty = "TIEN IA D1 PSTO")),
            item(2, expense(amount = 30_000L, counterparty = "TIEN IA D1 PSTO IAM I")),
            "different amount"
        )
    }

    @Test
    fun `two legitimate purchases at the same merchant do not merge`() {
        assertSeparate(
            item(1, expense(timestamp = millis(2026, 9, 21, 8, 27))),
            item(2, expense(timestamp = millis(2026, 9, 21, 10, 40))),
            "same merchant/amount/card but different times"
        )
    }

    @Test
    fun `missing card suffix does not merge`() {
        assertSeparate(
            item(1, expense(suffix = "0757")),
            item(2, expense(suffix = null)),
            "missing suffix"
        )
    }

    @Test
    fun `transfer-shaped pairs are not purchase duplicates`() {
        val transfer = expense().copy(
            transactionKind = TransactionKind.TRANSFER,
            possibleKinds = listOf(TransactionKind.TRANSFER)
        )
        assertSeparate(
            item(1, transfer),
            item(2, transfer),
            "transfers need transfer matching, not purchase correlation"
        )
    }

    @Test
    fun `income pairs are not purchase duplicates`() {
        val income = expense().copy(
            direction = Direction.INCOMING,
            transactionKind = TransactionKind.INCOME,
            possibleKinds = listOf(TransactionKind.INCOME)
        )
        assertSeparate(
            item(1, income),
            item(2, income),
            "income pairs are out of scope"
        )
    }

    // Merchant similarity -------------------------------------------------

    @Test
    fun `merchant similarity tolerates bank variants`() {
        assertEquals(
            1.0,
            PurchaseCorrelation.merchantSimilarity("Tien ia d1 psto", "TIEN IA D1 PSTO IAM I")!!,
            0.001
        )
        assertEquals(
            0.0,
            PurchaseCorrelation.merchantSimilarity("BOLD SA", "EXITO")!!,
            0.001
        )
        assertEquals(null, PurchaseCorrelation.merchantSimilarity(null, "EXITO"))
        assertEquals(null, PurchaseCorrelation.merchantSimilarity("", "EXITO"))
    }
}
