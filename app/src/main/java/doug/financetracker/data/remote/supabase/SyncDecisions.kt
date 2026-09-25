package doug.financetracker.data.remote.supabase

import doug.financetracker.domain.model.SyncStatus

/**
 * Per-row merge rule (pure, unit-tested). Last-write-wins on client-managed
 * updated_at millis, with LOCAL-WINS ties: a local edit is never overwritten
 * by a stale-or-equal remote version. Anything still marked pending (or
 * errored) always pushes — the push path owns conflict-free uploads.
 */
object SyncDecisions {

    sealed interface Row {
        data object Push : Row
        data object ApplyRemote : Row
        data object Keep : Row
    }

    fun decide(
        localUpdatedMs: Long,
        localStatus: SyncStatus,
        remoteUpdatedMs: Long
    ): Row = when {
        localStatus == SyncStatus.PENDING_UPLOAD ||
            localStatus == SyncStatus.PENDING_UPDATE ||
            localStatus == SyncStatus.ERROR -> Row.Push
        remoteUpdatedMs > localUpdatedMs -> Row.ApplyRemote
        else -> Row.Keep
    }
}
