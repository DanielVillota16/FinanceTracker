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
