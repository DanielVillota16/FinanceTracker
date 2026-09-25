package doug.financetracker.parser

import doug.financetracker.domain.parser.BbvaParser
import doug.financetracker.domain.parser.Confidence
import doug.financetracker.domain.parser.Direction
import doug.financetracker.domain.parser.TransactionKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class BbvaParserTest {

    private val parser = BbvaParser()

    private fun millis(year: Int, month: Int, day: Int, hour: Int, minute: Int): Long =
        LocalDateTime.of(year, month, day, hour, minute)
            .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

    @Test
    fun `real play store purchase parses exactly`() {
        // Authoritative fixture: title + "\n" + text as composed by the listener.
        val result = parser.parse(
            "Compra Exitosa\n" +
                "Hola, realizaste una compra por \$4,000.00 en Google doan toan " +
                "con tu tarjeta BBVA *1444. El 2026-09-24 a las 13:06."
        )
        assertNotNull(result)
        assertEquals(4_000L, result!!.amountPesos)
        assertEquals(Direction.OUTGOING, result.direction)
        assertEquals(TransactionKind.EXPENSE, result.transactionKind)
        assertEquals("BBVA", result.institution)
        assertEquals("Google doan toan", result.counterparty)
        assertEquals("1444", result.sourceAccountHint?.lastDigits)
        assertEquals(millis(2026, 9, 24, 13, 6), result.timestampMillis)
        assertEquals(Confidence.HIGH, result.confidence)
    }

    @Test
    fun `real physical-store purchase parses exactly`() {
        val result = parser.parse(
            "Compra Exitosa\n" +
                "Hola, realizaste una compra por \$99,320.00 en Tien ia d1 psto " +
                "con tu tarjeta BBVA *1444. El 2026-09-24 a las 14:53."
        )
        assertNotNull(result)
        assertEquals(99_320L, result!!.amountPesos)
        assertEquals(Direction.OUTGOING, result.direction)
        assertEquals(TransactionKind.EXPENSE, result.transactionKind)
        assertEquals("Tien ia d1 psto", result.counterparty)
        assertEquals("1444", result.sourceAccountHint?.lastDigits)
        assertEquals(millis(2026, 9, 24, 14, 53), result.timestampMillis)
        assertEquals(Confidence.HIGH, result.confidence)
    }

    @Test
    fun `purchase parses to expense`() {
        val result = parser.parse("BBVA: Compra por \$85.000 en EXITO")
        assertNotNull(result)
        assertEquals(85_000L, result!!.amountPesos)
        assertEquals(Direction.OUTGOING, result.direction)
        assertEquals(TransactionKind.EXPENSE, result.transactionKind)
        assertEquals("BBVA", result.institution)
        assertEquals("EXITO", result.counterparty)
        assertEquals(Confidence.HIGH, result.confidence)
    }

    @Test
    fun `received transfer parses to income`() {
        val result = parser.parse(
            "Transferencia recibida de EMPRESA SAS por \$1.200.000"
        )
        assertNotNull(result)
        assertEquals(1_200_000L, result!!.amountPesos)
        assertEquals(Direction.INCOMING, result.direction)
        assertEquals(TransactionKind.INCOME, result.transactionKind)
        assertEquals("EMPRESA SAS", result.counterparty)
    }

    @Test
    fun `transfer out stays ambiguous`() {
        val result = parser.parse("Transferiste \$300.000 a cuenta *5678")
        assertNotNull(result)
        assertEquals(300_000L, result!!.amountPesos)
        assertEquals(TransactionKind.UNKNOWN, result.transactionKind)
        assertEquals(listOf(TransactionKind.TRANSFER, TransactionKind.EXPENSE), result.possibleKinds)
        assertTrue(result.warnings.any { it.contains("ownership") })
    }

    @Test
    fun `atm withdrawal is a transfer to cash`() {
        val result = parser.parse("Retiro de \$200.000 en cajero CALLE 100")
        assertNotNull(result)
        assertEquals(200_000L, result!!.amountPesos)
        assertEquals(TransactionKind.TRANSFER, result.transactionKind)
        assertEquals(true, result.destinationAccountHint?.isCash)
        assertEquals("CALLE 100", result.reference)
    }

    @Test
    fun `unknown bbva text returns low confidence`() {
        val result = parser.parse("BBVA: Tienes un nuevo mensaje")
        assertNotNull(result)
        assertEquals(TransactionKind.UNKNOWN, result!!.transactionKind)
        assertEquals(Confidence.LOW, result.confidence)
    }

    @Test
    fun `package matching is scoped to bbva`() {
        assertTrue(parser.matchesPackage("com.bbva.bbvacolombia"))
        assertTrue(parser.matchesPackage("com.bbva.bbvacontigo"))
        assertTrue(!parser.matchesPackage("com.google.android.apps.walletnfcrel"))
        assertTrue(!parser.matchesPackage("com.whatsapp"))
    }
}
