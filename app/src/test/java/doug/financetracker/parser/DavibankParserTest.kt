package doug.financetracker.parser

import doug.financetracker.domain.parser.Confidence
import doug.financetracker.domain.parser.DavibankParser
import doug.financetracker.domain.parser.Direction
import doug.financetracker.domain.parser.TransactionKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DavibankParserTest {

    private val parser = DavibankParser()

    @Test
    fun `airbnb purchase matches spec example`() {
        val result = parser.parse(
            "DAVIbank: Realizaste transaccion en AIRBNB * HMJYTBHWZW por 492,292 el 21/09/2026 a las 10:00."
        )
        assertNotNull(result)
        assertEquals(492_292L, result!!.amountPesos)
        assertEquals(Direction.OUTGOING, result.direction)
        assertEquals(TransactionKind.EXPENSE, result.transactionKind)
        assertEquals("DAVIbank", result.institution)
        assertEquals("AIRBNB * HMJYTBHWZW", result.counterparty)
        assertEquals(Confidence.HIGH, result.confidence)
    }

    @Test
    fun `transfer out stays ambiguous for review`() {
        val result = parser.parse(
            "DAVIVIENDA: Transferiste 200.000,00 a cuenta *5678 el 22/09/2026 a las 11:00."
        )
        assertNotNull(result)
        assertEquals(200_000L, result!!.amountPesos)
        assertEquals(TransactionKind.UNKNOWN, result.transactionKind)
        assertEquals(listOf(TransactionKind.TRANSFER, TransactionKind.EXPENSE), result.possibleKinds)
        assertTrue(result.warnings.any { it.contains("ownership") })
    }

    @Test
    fun `received money produces income`() {
        val result = parser.parse(
            "DAVIVIENDA: Recibiste 150.000,00 de MARIA GOMEZ el 22/09/2026 a las 12:00."
        )
        assertNotNull(result)
        assertEquals(150_000L, result!!.amountPesos)
        assertEquals(Direction.INCOMING, result.direction)
        assertEquals(TransactionKind.INCOME, result.transactionKind)
        assertEquals("MARIA GOMEZ", result.counterparty)
    }

    @Test
    fun `recognized sender with unknown shape returns low confidence`() {
        val result = parser.parse("DAVIbank: Hola, tienes un nuevo mensaje.")
        assertNotNull(result)
        assertEquals(TransactionKind.UNKNOWN, result!!.transactionKind)
        assertEquals(Confidence.LOW, result.confidence)
        assertTrue(result.warnings.isNotEmpty())
    }

    @Test
    fun `other banks messages are not handled`() {
        assertNull(parser.parse("NEQUI: Pagaste 1.000,00 en GOOGLE X."))
        assertNull(parser.parse("Bancolombia: Compraste \$5.000,00 en X con tu T.Deb *0757."))
    }
}
