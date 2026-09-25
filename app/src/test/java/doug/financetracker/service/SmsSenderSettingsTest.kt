package doug.financetracker.service

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import doug.financetracker.data.local.preferences.SmsSenderSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.CoroutineScope

class SmsSenderSettingsTest {

    private val scopes = mutableListOf<kotlinx.coroutines.Job>()

    @org.junit.After
    fun tearDown() {
        scopes.forEach { it.cancel() }
        scopes.clear()
    }

    private fun settingsIn(dir: File): SmsSenderSettings {
        val job = SupervisorJob()
        scopes += job
        val store = PreferenceDataStoreFactory.create(
            scope = CoroutineScope(Dispatchers.IO + job),
            produceFile = { File(dir, "senders.preferences_pb") }
        )
        return SmsSenderSettings(store)
    }

    @Test
    fun `starts empty and rejects blanks`() = runTest {
        val dir = Files.createTempDirectory("senders").toFile()
        val settings = settingsIn(dir)
        assertEquals(emptySet<String>(), settings.observeExtraSenders().first())
        assertFalse(settings.addSender("   "))
        assertEquals(emptySet<String>(), settings.observeExtraSenders().first())
    }

    @Test
    fun `add observe and remove round-trip`() = runTest {
        val dir = Files.createTempDirectory("senders").toFile()
        val settings = settingsIn(dir)
        assertTrue(settings.addSender("  MiBanco  "))
        assertTrue(settings.addSender("12345"))
        assertEquals(setOf("MiBanco", "12345"), settings.observeExtraSenders().first())
        settings.removeSender("MiBanco")
        assertEquals(setOf("12345"), settings.observeExtraSenders().first())
    }

    @Test
    fun `entries persist across instances on the same file`() = runTest {
        val dir = Files.createTempDirectory("senders").toFile()
        settingsIn(dir).addSender("MiBanco")
        // Close the first store before opening the second on the same file.
        scopes.forEach { it.cancel() }
        scopes.clear()
        // Small pause so the cancelled store releases the file lock.
        kotlinx.coroutines.delay(50)
        assertEquals(setOf("MiBanco"), settingsIn(dir).observeExtraSenders().first())
    }
}
