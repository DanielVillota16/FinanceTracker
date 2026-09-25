package doug.financetracker.sync

import doug.financetracker.data.remote.supabase.RemoteAccount
import doug.financetracker.data.remote.supabase.RemoteTag
import doug.financetracker.data.remote.supabase.RemoteTransaction
import doug.financetracker.data.remote.supabase.RemoteTransactionTag
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RemoteModelsTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `transaction encodes with snake_case money-safe types`() {
        val tx = RemoteTransaction(
            id = "00000000-0000-0000-0000-000000000001",
            type = "EXPENSE", amount = 3050707L, dateTimeMs = 1_778_360_000_000L,
            description = "d", counterparty = "BOLD SA",
            sourceAccountId = "00000000-0000-0000-0000-000000000002",
            createdAtMs = 1L, updatedAtMs = 2L
        )
        val encoded = json.encodeToString(tx)
        assertTrue(encoded.contains("\"date_time_ms\""))
        assertTrue(encoded.contains("\"source_account_id\""))
        // Exact integer pesos — never floating point.
        assertTrue(encoded.contains("3050707"))
        assertEquals(tx, json.decodeFromString<RemoteTransaction>(encoded))
    }

    @Test
    fun `account tag and link round-trip`() {
        val account = RemoteAccount(
            id = "a", name = "Bancolombia", institution = "Bancolombia",
            identifierSuffix = "8494", createdAtMs = 1L, updatedAtMs = 2L
        )
        val tag = RemoteTag(id = "t", name = "comida", createdAtMs = 1L, updatedAtMs = 2L)
        val link = RemoteTransactionTag(transactionId = "x", tagId = "t")
        assertEquals(account, json.decodeFromString<RemoteAccount>(json.encodeToString(account)))
        assertEquals(tag, json.decodeFromString<RemoteTag>(json.encodeToString(tag)))
        assertEquals(link, json.decodeFromString<RemoteTransactionTag>(json.encodeToString(link)))
        assertTrue(json.encodeToString(account).contains("\"identifier_suffix\":\"8494\""))
    }
}
