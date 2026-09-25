package doug.financetracker.parser

import doug.financetracker.domain.parser.Confidence
import doug.financetracker.domain.parser.Direction
import doug.financetracker.domain.parser.GoogleWalletParser
import doug.financetracker.domain.parser.TransactionKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GoogleWalletParserTest {

    private val parser = GoogleWalletParser()

    @Test
    fun `real wallet purchase uses title as merchant`() {
        // Authoritative fixture: title + "\n" + text as composed by the listener.
        val result = parser.parse(
            "TIEN IA D1 PSTO IAM I\nCOP 99,320.00 with Mastercard Platinum ••1444"
        )
        assertNotNull(result)
        assertEquals(99_320L, result!!.amountPesos)
        assertEquals(Direction.OUTGOING, result.direction)
        assertEquals(TransactionKind.EXPENSE, result.transactionKind)
        assertEquals("Google Wallet", result.institution)
        assertEquals("TIEN IA D1 PSTO IAM I", result.counterparty)
        assertEquals("1444", result.sourceAccountHint?.lastDigits)
        assertEquals("Mastercard Platinum", result.reference)
        assertEquals(Confidence.HIGH, result.confidence)
    }

    @Test
    fun `wallet body carries no date so timestamp stays null`() {
        val result = parser.parse(
            "TIEN IA D1 PSTO IAM I\nCOP 99,320.00 with Mastercard Platinum ••1444"
        )
        assertNotNull(result)
        // The notification post time becomes the event time at ingestion.
        assertNull(result!!.timestampMillis)
        assertTrue(result.warnings.any { it.contains("date/time", ignoreCase = true) })
    }

    @Test
    fun `alternative mask glyphs still yield the suffix`() {
        val variants = listOf(
            "TIENDA X\nCOP 29,000.00 with Visa Gold ··0757",
            "TIENDA X\nCOP 29,000.00 with Visa Gold **0757",
            "TIENDA X\nCOP 29,000.00 with Visa Gold •• 0757"
        )
        for (body in variants) {
            val result = parser.parse(body)
            assertNotNull("failed for: $body", result)
            assertEquals("failed for: $body", 29_000L, result!!.amountPesos)
            assertEquals("failed for: $body", "TIENDA X", result.counterparty)
            assertEquals("failed for: $body", "0757", result.sourceAccountHint?.lastDigits)
        }
    }

    @Test
    fun `payment with masked card parses to expense`() {
        val result = parser.parse(
            "Pagaste \$45.900 en DROGUERIA ALEMANA con tu tarjeta •• 4821"
        )
        assertNotNull(result)
        assertEquals(45_900L, result!!.amountPesos)
        assertEquals(Direction.OUTGOING, result.direction)
        assertEquals(TransactionKind.EXPENSE, result.transactionKind)
        assertEquals("Google Wallet", result.institution)
        assertEquals("DROGUERIA ALEMANA", result.counterparty)
        assertEquals("4821", result.sourceAccountHint?.lastDigits)
        assertEquals(Confidence.HIGH, result.confidence)
    }

    @Test
    fun `bare purchase shape parses`() {
        val result = parser.parse("Compra por \$20.000 en TIENDA X")
        assertNotNull(result)
        assertEquals(20_000L, result!!.amountPesos)
        assertEquals(TransactionKind.EXPENSE, result.transactionKind)
        assertEquals("TIENDA X", result.counterparty)
    }

    @Test
    fun `received money parses to income`() {
        val result = parser.parse("Recibiste \$200.000 de JUAN PEREZ")
        assertNotNull(result)
        assertEquals(200_000L, result!!.amountPesos)
        assertEquals(Direction.INCOMING, result.direction)
        assertEquals(TransactionKind.INCOME, result.transactionKind)
        assertEquals("JUAN PEREZ", result.counterparty)
    }

    @Test
    fun `unknown wallet text returns low confidence`() {
        val result = parser.parse("Tu resumen semanal está listo")
        assertNotNull(result)
        assertEquals(TransactionKind.UNKNOWN, result!!.transactionKind)
        assertEquals(Confidence.LOW, result.confidence)
        assertTrue(result.warnings.isNotEmpty())
    }

    @Test
    fun `package matching is scoped to wallet`() {
        assertTrue(parser.matchesPackage("com.google.android.apps.walletnfcrel"))
        assertTrue(parser.matchesPackage("com.google.android.apps.Wallet"))
        assertTrue(!parser.matchesPackage("com.bbva.bbvacolombia"))
        assertTrue(!parser.matchesPackage("com.whatsapp"))
    }

    @Test
    fun `unrelated sms text is not claimed`() {
        // No wallet marker and no wallet shape.
        assertTrue(!parser.canHandle("Transferiste \$4,500.00 desde tu cuenta *8494."))
    }
}
