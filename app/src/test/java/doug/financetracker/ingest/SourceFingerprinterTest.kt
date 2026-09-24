package doug.financetracker.ingest

import doug.financetracker.data.ingest.SourceFingerprinter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class SourceFingerprinterTest {

    @Test
    fun `same inputs produce the same fingerprint`() {
        val a = SourceFingerprinter.fingerprint("SMS", "Bancolombia", "Compraste \$5", 123L)
        val b = SourceFingerprinter.fingerprint("SMS", "Bancolombia", "Compraste \$5", 123L)
        assertEquals(a, b)
        assertEquals(64, a.length)
    }

    @Test
    fun `normalization ignores case and whitespace`() {
        val a = SourceFingerprinter.fingerprint("SMS", "Bancolombia", "Compraste  \$5", 123L)
        val b = SourceFingerprinter.fingerprint("sms", "  BANCOLOMBIA ", "compraste \$5  ", 123L)
        assertEquals(a, b)
    }

    @Test
    fun `different content produces different fingerprints`() {
        val a = SourceFingerprinter.fingerprint("SMS", "Bancolombia", "Compraste \$5", 123L)
        val b = SourceFingerprinter.fingerprint("SMS", "Bancolombia", "Compraste \$6", 123L)
        assertNotEquals(a, b)
    }

    @Test
    fun `sender and timestamp are part of the fingerprint`() {
        val base = SourceFingerprinter.fingerprint("SMS", "Bancolombia", "Msg", 1L)
        assertNotEquals(base, SourceFingerprinter.fingerprint("SMS", "Nequi", "Msg", 1L))
        assertNotEquals(base, SourceFingerprinter.fingerprint("SMS", "Bancolombia", "Msg", 2L))
        assertNotEquals(base, SourceFingerprinter.fingerprint("SMS", "Bancolombia", "Msg", null))
        assertNotEquals(
            base,
            SourceFingerprinter.fingerprint("NOTIFICATION", "Bancolombia", "Msg", 1L)
        )
    }
}
