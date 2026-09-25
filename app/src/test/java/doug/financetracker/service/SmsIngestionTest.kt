package doug.financetracker.service

import doug.financetracker.domain.usecase.IngestSourceMessage
import doug.financetracker.service.sms.SmsHistoryImporter
import doug.financetracker.service.sms.SupportedSmsSenders
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SmsIngestionTest {

    @Test
    fun `supported senders match bank ids case-insensitively`() {
        assertTrue(SupportedSmsSenders.isSupported("Bancolombia"))
        assertTrue(SupportedSmsSenders.isSupported("BANCOLOMBIA"))
        assertTrue(SupportedSmsSenders.isSupported("Nequi"))
        assertTrue(SupportedSmsSenders.isSupported("Banco Davivienda"))
        assertTrue(SupportedSmsSenders.isSupported("Daviplata"))
        assertTrue(SupportedSmsSenders.isSupported("BBVA Colombia"))
    }

    @Test
    fun `unrelated and numeric senders are rejected`() {
        assertFalse(SupportedSmsSenders.isSupported(""))
        assertFalse(SupportedSmsSenders.isSupported("   "))
        assertFalse(SupportedSmsSenders.isSupported("+573001234567"))
        assertFalse(SupportedSmsSenders.isSupported("85888"))
        assertFalse(SupportedSmsSenders.isSupported("WhatsApp"))
        assertFalse(SupportedSmsSenders.isSupported("Google"))
    }

    @Test
    fun `verified numeric short codes match exactly`() {
        assertTrue(SupportedSmsSenders.isSupported("85540"))
        assertTrue(SupportedSmsSenders.isSupported(" 85540 "))
        // Exact digit equality: embedding the code in a longer number is not enough.
        assertFalse(SupportedSmsSenders.isSupported("+5785540"))
        assertFalse(SupportedSmsSenders.isSupported("855401"))
    }

    @Test
    fun `import loop gates senders and tallies outcomes`() = runTest {
        val rows = listOf(
            SmsHistoryImporter.SmsRow("Bancolombia", "Compraste \$5", 1000L),
            SmsHistoryImporter.SmsRow("Mom", "Hola", 1001L),
            SmsHistoryImporter.SmsRow("", "", 1002L),
            SmsHistoryImporter.SmsRow("Nequi", "Pagaste 1.000", 1003L),
            SmsHistoryImporter.SmsRow("Bancolombia", "Promo...", 1004L)
        )
        var calls = 0
        val result = SmsHistoryImporter.importRows(rows, now = 2000L) { row ->
            calls++
            when (row.body) {
                "Compraste \$5" -> IngestSourceMessage.Result.Created(1L)
                "Pagaste 1.000" -> IngestSourceMessage.Result.Duplicate(2L)
                else -> IngestSourceMessage.Result.Unsupported(3L)
            }
        }
        // Only the 3 bank rows reach ingest, oldest-first.
        assertEquals(3, calls)
        assertEquals(5, result.examined)
        assertEquals(1, result.created)
        assertEquals(1, result.duplicates)
        assertEquals(1, result.unsupported)
        assertEquals(1, result.skippedSenders)
        assertEquals(1, result.skippedBlank)
    }

    @Test
    fun `import loop is empty-safe`() = runTest {
        val result = SmsHistoryImporter.importRows(emptyList(), now = 0L) {
            IngestSourceMessage.Result.Duplicate(-1L)
        }
        assertEquals(0, result.examined)
        assertEquals(0, result.created)
    }
}
