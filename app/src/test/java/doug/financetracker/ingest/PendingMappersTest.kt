package doug.financetracker.ingest

import doug.financetracker.data.local.mapper.toDomain
import doug.financetracker.data.local.mapper.toEntity
import doug.financetracker.domain.model.PendingItem
import doug.financetracker.domain.model.PendingStatus
import doug.financetracker.domain.model.SourceEvent
import doug.financetracker.domain.parser.AccountHint
import doug.financetracker.domain.parser.Confidence
import doug.financetracker.domain.parser.Direction
import doug.financetracker.domain.parser.ParsedTransaction
import doug.financetracker.domain.parser.TransactionKind
import org.junit.Assert.assertEquals
import org.junit.Test

class PendingMappersTest {

    @Test
    fun `pending item survives entity round-trip`() {
        val source = SourceEvent(
            id = 7L, sourceType = "SMS", sourceIdentifier = "Bancolombia",
            receivedAt = 9_000L, eventTime = 8_500L,
            rawContent = "raw", fingerprint = "fp"
        )
        val item = PendingItem(
            id = 10L,
            sourceEvent = source,
            parsed = ParsedTransaction(
                amountPesos = 60_000L,
                direction = Direction.OUTGOING,
                transactionKind = TransactionKind.UNKNOWN,
                possibleKinds = listOf(TransactionKind.TRANSFER, TransactionKind.EXPENSE),
                institution = "Bancolombia",
                sourceAccountHint = AccountHint("8494"),
                destinationAccountHint = null,
                counterparty = null,
                timestampMillis = 8_000L,
                reference = "nequi789",
                confidence = Confidence.MEDIUM,
                warnings = listOf("w1", "w2")
            ),
            status = PendingStatus.PENDING,
            linkedTransactionId = null
        )
        val restored = item.toEntity().toDomain(source)
        assertEquals(item, restored)
    }

    @Test
    fun `cash hint and confirmed status survive round-trip`() {
        val source = SourceEvent(
            id = 7L, sourceType = "SMS", sourceIdentifier = "Bancolombia",
            receivedAt = 9_000L, eventTime = null,
            rawContent = "raw", fingerprint = "fp"
        )
        val item = PendingItem(
            id = 11L,
            sourceEvent = source,
            parsed = ParsedTransaction(
                amountPesos = 100_000L,
                direction = Direction.OUTGOING,
                transactionKind = TransactionKind.TRANSFER,
                institution = "Bancolombia",
                sourceAccountHint = AccountHint("0757"),
                destinationAccountHint = AccountHint.CASH,
                counterparty = null,
                timestampMillis = null,
                reference = "EXIT_LA70_2",
                confidence = Confidence.HIGH,
                warnings = emptyList()
            ),
            status = PendingStatus.CONFIRMED,
            linkedTransactionId = 42L
        )
        val restored = item.toEntity().toDomain(source)
        assertEquals(item, restored)
    }
}
