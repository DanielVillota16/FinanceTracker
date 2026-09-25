package doug.financetracker.parser

import doug.financetracker.domain.parser.BancolombiaParser
import doug.financetracker.domain.parser.DavibankParser
import doug.financetracker.domain.parser.NequiParser
import doug.financetracker.domain.parser.ParserRegistry
import doug.financetracker.domain.parser.TransactionKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ParserRegistryTest {

    @Test
    fun `routes to the owning parser`() {
        assertTrue(
            ParserRegistry.parserFor("NEQUI: Pagaste 1.000,00 en GOOGLE X.") is NequiParser
        )
        assertTrue(
            ParserRegistry.parserFor("DAVIbank: Realizaste transaccion en X por 1,000.") is DavibankParser
        )
        assertTrue(
            ParserRegistry.parserFor("Compraste \$29.000,00 en X con tu T.Deb *0757.") is BancolombiaParser
        )
    }

    @Test
    fun `nequi prefix wins over generic verbs`() {
        // "Pagaste ... en ..." alone is Nequi-shaped; the registry must not
        // hand prefixed Nequi messages to Bancolombia.
        val parsed = ParserRegistry.parse("NEQUI: Pagaste 1.000,00 en GOOGLE *Google One.")
        assertNotNull(parsed)
        assertEquals("Nequi", parsed!!.institution)
        assertEquals(TransactionKind.EXPENSE, parsed.transactionKind)
        assertEquals(1_000L, parsed.amountPesos)
    }

    @Test
    fun `unsupported messages return null`() {
        assertNull(ParserRegistry.parse("Hola, ¿cómo estás?"))
        assertNull(ParserRegistry.parse("Tu paquete ha sido enviado."))
        assertNull(ParserRegistry.parserFor("BBVA: pago de tarjeta por \$100.000"))
    }

    @Test
    fun `sender routing maps known sender ids`() {
        assertTrue(ParserRegistry.parserForSender("Bancolombia") is BancolombiaParser)
        assertTrue(ParserRegistry.parserForSender("85540") is BancolombiaParser)
        assertTrue(ParserRegistry.parserForSender("Nequi") is NequiParser)
        assertTrue(ParserRegistry.parserForSender("Daviplata") is DavibankParser)
        assertNull(ParserRegistry.parserForSender("co.com.bbva.mb"))
        assertNull(ParserRegistry.parserForSender("+5785540"))
        assertNull(ParserRegistry.parserForSender(""))
    }

    @Test
    fun `sender wins over misleading body text`() {
        // Production case: Bancolombia SMS naming Davivienda must not route
        // to Davibank — the sender id is ground truth.
        val body = "Bancolombia: Pagaste \$975,500.00 a Banco Davivienda S A Zona Pa " +
            "desde tu producto 8494 el 23/09/2026 17:32:32. Estamos cerca"
        val parsed = ParserRegistry.parse(body, sender = "85540")
        assertNotNull(parsed)
        assertEquals("Bancolombia", parsed!!.institution)
        assertEquals(975_500L, parsed.amountPesos)
        assertEquals("Banco Davivienda S A Zona Pa", parsed.counterparty)
        assertEquals("8494", parsed.sourceAccountHint?.lastDigits)
        assertEquals(TransactionKind.UNKNOWN, parsed.transactionKind)
    }

    @Test
    fun `unknown sender falls back to text routing`() {
        val body = "Bancolombia: Pagaste \$975,500.00 a Banco Davivienda S A Zona Pa " +
            "desde tu producto 8494 el 23/09/2026 17:32:32. Estamos cerca"
        val parsed = ParserRegistry.parse(body, sender = "99999")
        assertNotNull(parsed)
        assertEquals("Bancolombia", parsed!!.institution)
    }
}
