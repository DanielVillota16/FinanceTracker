package doug.financetracker.parser

import doug.financetracker.domain.parser.BancolombiaParser
import doug.financetracker.domain.parser.Confidence
import doug.financetracker.domain.parser.Direction
import doug.financetracker.domain.parser.TransactionKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class BancolombiaParserTest {

    private val parser = BancolombiaParser()

    private fun millis(year: Int, month: Int, day: Int, hour: Int, minute: Int): Long =
        LocalDateTime.of(year, month, day, hour, minute)
            .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

    @Test
    fun `real purchase sms with service tail parses exactly`() {
        // Authoritative fixture, verbatim including the service tail.
        val result = parser.parse(
            "Bancolombia: Compraste \$29.000,00 en BOLD SA*20 DE JU con tu T.Deb *0757, " +
                "el 21/09/2026 a las 08:27. Si tienes dudas, encuentranos aqui: " +
                "6045109095 o 018000931987. Estamos cerca"
        )
        assertNotNull(result)
        assertEquals(29_000L, result!!.amountPesos)
        assertEquals(Direction.OUTGOING, result.direction)
        assertEquals(TransactionKind.EXPENSE, result.transactionKind)
        assertEquals("Bancolombia", result.institution)
        assertEquals("BOLD SA*20 DE JU", result.counterparty)
        assertEquals("0757", result.sourceAccountHint?.lastDigits)
        assertEquals(millis(2026, 9, 21, 8, 27), result.timestampMillis)
        assertEquals(Confidence.HIGH, result.confidence)
    }

    @Test
    fun `purchase produces expense with counterparty and account hint`() {
        val result = parser.parse(
            "Compraste \$29.000,00 en BOLD SA*20 DE JU con tu T.Deb *0757 el 21/09/2026 a las 08:27."
        )
        assertNotNull(result)
        assertEquals(29_000L, result!!.amountPesos)
        assertEquals(Direction.OUTGOING, result.direction)
        assertEquals(TransactionKind.EXPENSE, result.transactionKind)
        assertEquals("Bancolombia", result.institution)
        assertEquals("BOLD SA*20 DE JU", result.counterparty)
        assertEquals("0757", result.sourceAccountHint?.lastDigits)
        assertNull(result.destinationAccountHint)
        assertEquals(millis(2026, 9, 21, 8, 27), result.timestampMillis)
        assertEquals(Confidence.HIGH, result.confidence)
    }

    @Test
    fun `transfer out with known destination account stays ambiguous`() {
        val result = parser.parse(
            "Transferiste \$4,500.00 desde tu cuenta *8494 a la cuenta *1234 el 22/09/2026 a las 10:02."
        )
        assertNotNull(result)
        assertEquals(4_500L, result!!.amountPesos)
        assertEquals(Direction.OUTGOING, result.direction)
        assertEquals(TransactionKind.UNKNOWN, result.transactionKind)
        assertEquals(listOf(TransactionKind.TRANSFER, TransactionKind.EXPENSE), result.possibleKinds)
        assertEquals("8494", result.sourceAccountHint?.lastDigits)
        assertEquals("1234", result.destinationAccountHint?.lastDigits)
        assertTrue(result.warnings.any { it.contains("ownership") })
    }

    @Test
    fun `transfer out without destination warns`() {
        val result = parser.parse(
            "Transferiste \$4,500.00 desde tu cuenta *8494 el 22/09/2026 a las 10:02."
        )
        assertNotNull(result)
        assertEquals(TransactionKind.UNKNOWN, result!!.transactionKind)
        assertEquals("8494", result.sourceAccountHint?.lastDigits)
        assertNull(result.destinationAccountHint)
        assertTrue(result.warnings.any { it.contains("destination", ignoreCase = true) })
    }

    @Test
    fun `BRE-B transfer captures llave reference and stays ambiguous`() {
        val result = parser.parse(
            "Transferiste \$60,000.00 a la llave nequi789 desde tu cuenta *8494 el 22/09/2026 a las 10:05."
        )
        assertNotNull(result)
        assertEquals(60_000L, result!!.amountPesos)
        assertEquals(TransactionKind.UNKNOWN, result.transactionKind)
        assertEquals(listOf(TransactionKind.TRANSFER, TransactionKind.EXPENSE), result.possibleKinds)
        assertEquals("nequi789", result.reference)
        assertEquals("8494", result.sourceAccountHint?.lastDigits)
    }

    @Test
    fun `QR payment produces expense`() {
        val result = parser.parse(
            "Pagaste \$40,000.00 por codigo QR en TIENDA DONDE JUAN el 22/09/2026 a las 12:00."
        )
        assertNotNull(result)
        assertEquals(40_000L, result!!.amountPesos)
        assertEquals(TransactionKind.EXPENSE, result.transactionKind)
        assertEquals("TIENDA DONDE JUAN", result.counterparty)
    }

    @Test
    fun `QR payment without merchant warns but still parses amount`() {
        val result = parser.parse(
            "Bancolombia: Pagaste \$40,000.00 por codigo QR el 22/09/2026 a las 12:00."
        )
        assertNotNull(result)
        assertEquals(40_000L, result!!.amountPesos)
        assertEquals(TransactionKind.EXPENSE, result.transactionKind)
        assertNull(result.counterparty)
        assertTrue(result.warnings.isNotEmpty())
    }

    @Test
    fun `ATM withdrawal is a transfer to Cash`() {
        val result = parser.parse(
            "Retiraste \$100.000,00 en EXIT_LA70_2 con tu T.Deb *0757 el 22/09/2026 a las 09:00."
        )
        assertNotNull(result)
        assertEquals(100_000L, result!!.amountPesos)
        assertEquals(Direction.OUTGOING, result.direction)
        assertEquals(TransactionKind.TRANSFER, result.transactionKind)
        assertEquals("0757", result.sourceAccountHint?.lastDigits)
        assertEquals(true, result.destinationAccountHint?.isCash)
        assertEquals("EXIT_LA70_2", result.reference)
    }

    @Test
    fun `scheduled bill payment produces expense`() {
        val result = parser.parse(
            "Bancolombia informa pago Factura Programada IGS MULTIASISTE por \$85.000,00 " +
                "desde tu cuenta *8494 el 20/09/2026 a las 07:00."
        )
        assertNotNull(result)
        assertEquals(85_000L, result!!.amountPesos)
        assertEquals(TransactionKind.EXPENSE, result.transactionKind)
        assertEquals("IGS MULTIASISTE", result.counterparty)
        assertEquals("8494", result.sourceAccountHint?.lastDigits)
    }

    @Test
    fun `incoming transfer matches spec expectations exactly`() {
        val result = parser.parse(
            "Recibiste una transferencia de DLOCAL COLOMBIA SAS por \$3,050,707.00 " +
                "en tu cuenta *8494 el 22/05/2026 a las 08:30."
        )
        assertNotNull(result)
        assertEquals(3_050_707L, result!!.amountPesos)
        assertEquals(Direction.INCOMING, result.direction)
        assertEquals(TransactionKind.INCOME, result.transactionKind)
        assertEquals("Bancolombia", result.institution)
        assertEquals("8494", result.destinationAccountHint?.lastDigits)
        assertEquals("DLOCAL COLOMBIA SAS", result.counterparty)
        assertEquals(millis(2026, 5, 22, 8, 30), result.timestampMillis)
        assertEquals(Confidence.HIGH, result.confidence)
    }

    @Test
    fun `ISO datetime is supported`() {
        val result = parser.parse(
            "Bancolombia: Compraste \$29.000,00 en TIENDA X con tu T.Deb *0757 2026-09-21 08:27."
        )
        assertNotNull(result)
        assertEquals(millis(2026, 9, 21, 8, 27), result!!.timestampMillis)
    }

    @Test
    fun `message without date warns and leaves timestamp null`() {
        val result = parser.parse(
            "Bancolombia: Compraste \$29.000,00 en TIENDA X con tu T.Deb *0757."
        )
        assertNotNull(result)
        assertNull(result!!.timestampMillis)
        assertTrue(result.warnings.any { it.contains("date/time", ignoreCase = true) })
    }

    @Test
    fun `unrelated message is not handled`() {
        assertNull(parser.parse("NEQUI: Pagaste 1.000,00 en GOOGLE *Google One."))
        assertNull(parser.parse("Hola, ¿cómo estás?"))
    }
}
