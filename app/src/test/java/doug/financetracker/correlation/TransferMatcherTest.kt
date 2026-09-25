package doug.financetracker.correlation

import doug.financetracker.domain.correlation.TransferMatcher
import doug.financetracker.domain.model.Account
import doug.financetracker.domain.model.PendingItem
import doug.financetracker.domain.model.SourceEvent
import doug.financetracker.domain.parser.AccountHint
import doug.financetracker.domain.parser.BancolombiaParser
import doug.financetracker.domain.parser.Confidence
import doug.financetracker.domain.parser.Direction
import doug.financetracker.domain.parser.NequiParser
import doug.financetracker.domain.parser.ParsedTransaction
import doug.financetracker.domain.parser.TransactionKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class TransferMatcherTest {

    private val bancolombia = Account(
        id = 1, name = "Bancolombia", institution = "Bancolombia",
        identifierSuffix = "8494", isOwnedByUser = true
    )
    private val nequi = Account(
        id = 2, name = "Nequi", institution = "Nequi", isOwnedByUser = true
    )
    private val cash = Account(
        id = 3, name = "Cash", institution = "Cash",
        accountType = "CASH", isOwnedByUser = true
    )
    private val accounts = listOf(bancolombia, nequi, cash)

    private fun millis(y: Int, mo: Int, d: Int, h: Int, mi: Int): Long =
        LocalDateTime.of(y, mo, d, h, mi).atZone(ZoneId.systemDefault())
            .toInstant().toEpochMilli()

    private fun item(
        id: Long,
        parsed: ParsedTransaction,
        receivedAt: Long = millis(2026, 9, 22, 10, 2)
    ) = PendingItem(
        id = id,
        sourceEvent = SourceEvent(
            id = id, sourceType = "SMS", sourceIdentifier = "Bancolombia",
            receivedAt = receivedAt, eventTime = null,
            rawContent = "raw", fingerprint = "fp$id"
        ),
        parsed = parsed
    )

    // Real parser outputs: transfer-out SMS + Nequi receipt -----------------

    private val transferOut = checkNotNull(
        BancolombiaParser().parse(
            "Transferiste \$100,000.00 desde tu cuenta *8494 a la cuenta *5678 " +
                "el 22/09/2026 a las 10:02."
        )
    )

    private val nequiIn = checkNotNull(
        NequiParser().parse(
            "NEQUI: Recibiste 100.000,00 de CARLOS RUIZ el 22/09/2026 a las 10:03."
        )
    )

    @Test
    fun `outgoing unknown plus incoming income is one transfer`() {
        val out = item(1, transferOut)
        val inc = item(2, nequiIn)
        val match = TransferMatcher.decide(
            TransferMatcher.inputOf(out), TransferMatcher.inputOf(inc), accounts
        )
        assertNotNull("expected match, got null", match)
        assertEquals(1L, match!!.sourceAccount.id)
        assertEquals(2L, match.destinationAccount.id)
        assertEquals(Confidence.HIGH, match.confidence)
        assertTrue(match.evidence.any { it.contains("same amount") })
        assertTrue(match.evidence.any { it.contains("opposite directions") })
    }

    @Test
    fun `findMatch works regardless of arrival order`() {
        val out = item(1, transferOut)
        val inc = item(2, nequiIn)
        val outFirst = TransferMatcher.findMatch(listOf(out to 100L), inc, accounts)
        val inFirst = TransferMatcher.findMatch(listOf(inc to 200L), out, accounts)
        assertNotNull(outFirst)
        assertNotNull(inFirst)
        assertEquals(100L, outFirst!!.first)
        assertEquals(200L, inFirst!!.first)
        assertEquals(1L, outFirst.second.sourceAccount.id)
        assertEquals(2L, outFirst.second.destinationAccount.id)
    }

    // Negative cases --------------------------------------------------------

    private fun parsed(
        amount: Long? = 100_000L,
        kind: TransactionKind = TransactionKind.UNKNOWN,
        possible: List<TransactionKind> = listOf(TransactionKind.TRANSFER, TransactionKind.EXPENSE),
        direction: Direction? = Direction.OUTGOING,
        sourceHint: AccountHint? = AccountHint("8494"),
        destHint: AccountHint? = null,
        timestamp: Long? = millis(2026, 9, 22, 10, 2)
    ) = ParsedTransaction(
        amountPesos = amount, direction = direction, transactionKind = kind,
        possibleKinds = possible, institution = "Bancolombia",
        sourceAccountHint = sourceHint, destinationAccountHint = destHint,
        counterparty = null, timestampMillis = timestamp,
        reference = null, confidence = Confidence.MEDIUM
    )

    private fun incoming(
        amount: Long? = 100_000L,
        destHint: AccountHint? = AccountHint("", "Nequi"),
        timestamp: Long? = millis(2026, 9, 22, 10, 3)
    ) = ParsedTransaction(
        amountPesos = amount, direction = Direction.INCOMING,
        transactionKind = TransactionKind.INCOME,
        possibleKinds = listOf(TransactionKind.INCOME), institution = "Nequi",
        sourceAccountHint = null, destinationAccountHint = destHint,
        counterparty = "CARLOS RUIZ", timestampMillis = timestamp,
        reference = null, confidence = Confidence.HIGH
    )

    private fun assertNoMatch(out: PendingItem, inc: PendingItem, why: String) {
        val match = TransferMatcher.decide(
            TransferMatcher.inputOf(out), TransferMatcher.inputOf(inc), accounts
        )
        assertNull("expected no match ($why), got $match", match)
    }

    @Test
    fun `confident expense plus income never invents a transfer`() {
        val purchase = parsed(
            kind = TransactionKind.EXPENSE,
            possible = listOf(TransactionKind.EXPENSE)
        )
        assertNoMatch(item(1, purchase), item(2, incoming()), "purchase is not a transfer leg")
    }

    @Test
    fun `same direction pairs do not match`() {
        val anotherOut = parsed(timestamp = millis(2026, 9, 22, 10, 5))
        assertNoMatch(item(1, parsed()), item(2, anotherOut), "both outgoing")
    }

    @Test
    fun `same account both sides does not match`() {
        val loop = incoming(destHint = AccountHint("8494"))
        assertNoMatch(item(1, parsed()), item(2, loop), "same account")
    }

    @Test
    fun `unowned account does not match`() {
        val external = bancolombia.copy(id = 9, isOwnedByUser = false)
        val match = TransferMatcher.decide(
            TransferMatcher.inputOf(item(1, parsed())),
            TransferMatcher.inputOf(item(2, incoming())),
            listOf(external, nequi, cash)
        )
        assertNull("expected no match (unowned source), got $match", match)
    }

    @Test
    fun `unresolvable hint does not match`() {
        assertNoMatch(
            item(1, parsed(sourceHint = AccountHint("0000"))),
            item(2, incoming()),
            "unknown source suffix"
        )
    }

    @Test
    fun `amount mismatch does not match`() {
        assertNoMatch(
            item(1, parsed()),
            item(2, incoming(amount = 50_000L)),
            "different amounts"
        )
    }

    @Test
    fun `distant timestamps do not match`() {
        assertNoMatch(
            item(1, parsed()),
            item(2, incoming(timestamp = millis(2026, 9, 22, 12, 30))),
            "148 min apart"
        )
    }

    @Test
    fun `wider window still matches with medium confidence`() {
        val match = TransferMatcher.decide(
            TransferMatcher.inputOf(item(1, parsed())),
            TransferMatcher.inputOf(item(2, incoming(timestamp = millis(2026, 9, 22, 10, 47)))),
            accounts
        )
        assertNotNull(match)
        assertEquals(Confidence.MEDIUM, match!!.confidence)
    }

    @Test
    fun `complete transfer interpretations never rematch`() {
        val atm = parsed(
            kind = TransactionKind.TRANSFER,
            possible = listOf(TransactionKind.TRANSFER),
            destHint = AccountHint.CASH
        )
        assertNoMatch(item(1, atm), item(2, incoming()), "ATM leg is already complete")
    }

    @Test
    fun `purchase pairs never match as transfers`() {
        val purchase = parsed(
            kind = TransactionKind.EXPENSE,
            possible = listOf(TransactionKind.EXPENSE),
            direction = Direction.OUTGOING
        )
        val other = parsed(
            kind = TransactionKind.EXPENSE,
            possible = listOf(TransactionKind.EXPENSE),
            direction = Direction.OUTGOING,
            timestamp = millis(2026, 9, 22, 10, 5)
        )
        assertNoMatch(item(1, purchase), item(2, other), "same direction")
    }
}
