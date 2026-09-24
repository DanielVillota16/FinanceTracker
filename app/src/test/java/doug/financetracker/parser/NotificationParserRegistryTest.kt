package doug.financetracker.parser

import doug.financetracker.domain.parser.BbvaParser
import doug.financetracker.domain.parser.GoogleWalletParser
import doug.financetracker.domain.parser.NotificationParserRegistry
import doug.financetracker.domain.parser.ParserRegistry
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationParserRegistryTest {

    @Test
    fun `routes by package name`() {
        assertTrue(
            NotificationParserRegistry.parserFor("com.google.android.apps.walletnfcrel")
                is GoogleWalletParser
        )
        assertTrue(
            NotificationParserRegistry.parserFor("com.bbva.bbvacolombia") is BbvaParser
        )
        assertNull(NotificationParserRegistry.parserFor("com.whatsapp"))
        assertNull(NotificationParserRegistry.parserFor("com.google.android.gm"))
    }

    @Test
    fun `parses wallet notification text`() {
        val parsed = NotificationParserRegistry.parse(
            "com.google.android.apps.walletnfcrel",
            "Pagaste \$45.900 en DROGUERIA ALEMANA con tu tarjeta •• 4821"
        )
        assertNotNull(parsed)
    }

    @Test
    fun `notification parsers never leak into the sms registry`() {
        // SMS registry must stay sender-based; wallet/bbva text without markers
        // must not be claimed by SMS parsers.
        assertNull(ParserRegistry.parse("Compra por \$20.000 en TIENDA X"))
        assertNull(ParserRegistry.parse("Transferencia recibida de X por \$1.200.000"))
    }
}
