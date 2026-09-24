package doug.financetracker.parser

import doug.financetracker.domain.parser.Confidence
import doug.financetracker.domain.parser.Direction
import doug.financetracker.domain.parser.NequiParser
import doug.financetracker.domain.parser.TransactionKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NequiParserTest {

    private val parser = NequiParser()

    @Test
    fun `google one payment matches spec example`() {
        val result = parser.parse(
            "NEQUI: Pagaste 1.000,00 en GOOGLE *Google One el 21/09/2026 a las 08:00."
        )
        assertNotNull(result)
        assertEquals(1_000L, result!!.amountPesos)
        assertEquals(Direction.OUTGOING, result.direction)
        assertEquals(TransactionKind.EXPENSE, result.transactionKind)
        assertEquals("Nequi", result.institution)
        assertEquals("GOOGLE *Google One", result.counterparty)
        assertEquals(Confidence.HIGH, result.confidence)
    }

    @Test
    fun `youtube payment matches spec example`() {
        val result = parser.parse(
            "NEQUI: Pagaste 9.300,00 en GOOGLE YouTube el 21/09/2026 a las 08:00."
        )
        assertNotNull(result)
        assertEquals(9_300L, result!!.amountPesos)
        assertEquals(TransactionKind.EXPENSE, result.transactionKind)
        assertEquals("GOOGLE YouTube", result.counterparty)
    }

    @Test
    fun `received money produces income`() {
        val result = parser.parse(
            "NEQUI: Recibiste 50.000,00 de JUAN PEREZ el 21/09/2026 a las 09:00."
        )
        assertNotNull(result)
        assertEquals(50_000L, result!!.amountPesos)
        assertEquals(Direction.INCOMING, result.direction)
        assertEquals(TransactionKind.INCOME, result.transactionKind)
        assertEquals("JUAN PEREZ", result.counterparty)
    }

    @Test
    fun `recognized sender with unknown shape returns low confidence`() {
        val result = parser.parse("NEQUI: Tienes un nuevo mensaje.")
        assertNotNull(result)
        assertEquals(TransactionKind.UNKNOWN, result!!.transactionKind)
        assertEquals(Confidence.LOW, result.confidence)
        assertTrue(result.warnings.isNotEmpty())
    }

    @Test
    fun `other banks messages are not handled`() {
        assertNull(parser.parse("Compraste \$29.000,00 en TIENDA X con tu T.Deb *0757."))
        assertNull(parser.parse("DAVIbank: Realizaste transaccion en X por 1,000."))
    }
}
