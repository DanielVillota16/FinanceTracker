package doug.financetracker.sync

import doug.financetracker.data.remote.supabase.SyncDecisions
import doug.financetracker.domain.model.SyncStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class SyncDecisionsTest {

    @Test
    fun `pending and errored rows always push`() {
        for (status in listOf(
            SyncStatus.PENDING_UPLOAD, SyncStatus.PENDING_UPDATE, SyncStatus.ERROR
        )) {
            assertEquals(
                SyncDecisions.Row.Push,
                SyncDecisions.decide(100L, status, 200L)
            )
            assertEquals(
                SyncDecisions.Row.Push,
                SyncDecisions.decide(200L, status, 100L)
            )
        }
    }

    @Test
    fun `newer remote applies when local is clean`() {
        assertEquals(
            SyncDecisions.Row.ApplyRemote,
            SyncDecisions.decide(100L, SyncStatus.SYNCED, 200L)
        )
    }

    @Test
    fun `local wins ties and newer local keeps`() {
        assertEquals(
            SyncDecisions.Row.Keep,
            SyncDecisions.decide(200L, SyncStatus.SYNCED, 200L)
        )
        assertEquals(
            SyncDecisions.Row.Keep,
            SyncDecisions.decide(300L, SyncStatus.SYNCED, 200L)
        )
    }
}
