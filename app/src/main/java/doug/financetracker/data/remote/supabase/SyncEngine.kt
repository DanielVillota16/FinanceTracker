package doug.financetracker.data.remote.supabase

import androidx.room.withTransaction
import doug.financetracker.data.local.database.FinanceDatabase
import doug.financetracker.data.local.entity.AccountEntity
import doug.financetracker.data.local.entity.TagEntity
import doug.financetracker.data.local.entity.TransactionEntity
import doug.financetracker.data.local.entity.TransactionTagCrossRef
import doug.financetracker.domain.model.SyncStatus
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

/**
 * Offline-first backup sync (Phase 7). Room is the source of truth; this
 * engine only moves official data (accounts, tags, transactions, links) to
 * and from Supabase. Source evidence never leaves the device.
 *
 * Each run: replay tombstones → push pending rows → pull remote rows.
 * Merging is last-write-wins on client-managed updated_at millis with
 * local-wins ties ([SyncDecisions]) — a local edit is never overwritten by
 * stale remote data. Failures are counted and retried next run; local data is
 * never rolled back because the network failed.
 *
 * No-ops (by design): unconfigured builds, signed-out sessions, and remote
 * deletions (local rows are never deleted by a pull).
 */
class SyncEngine(
    private val db: FinanceDatabase,
    private val provider: SupabaseProvider,
    private val auth: AuthRepository
) {
    sealed interface Status {
        data object Idle : Status
        data object NotConfigured : Status
        data object SignedOut : Status
        data object Syncing : Status
        data class Success(val pushed: Int, val pulled: Int, val errors: Int, val at: Long) : Status
        data class Error(val message: String) : Status
    }

    private val _status = MutableStateFlow<Status>(Status.Idle)
    val status: StateFlow<Status> = _status.asStateFlow()

    suspend fun syncNow() {
        if (_status.value == Status.Syncing) return
        _status.value = Status.Syncing
        _status.value = try {
            run()
        } catch (e: Exception) {
            Status.Error(e.message ?: "Sync failed.")
        }
    }

    private suspend fun run(): Status {
        val client = provider.client ?: return Status.NotConfigured
        auth.currentSession() ?: return Status.SignedOut
        val pg = client.postgrest
        var pushed = 0
        var pulled = 0
        var errors = 0

        suspend fun attempt(block: suspend () -> Unit) {
            try {
                block()
            } catch (_: Exception) {
                errors++
            }
        }

        // 1. Replay local deletes (works offline: tombstones wait for a run).
        for (tombstone in db.syncTombstoneDao().getAll()) {
            attempt {
                pg.from(tombstone.tableName).delete {
                    filter { eq("id", tombstone.remoteId) }
                }
                db.syncTombstoneDao().delete(tombstone.id)
                pushed++
            }
        }

        // 2. Push accounts (fetch-or-create by natural key, upsert by id).
        for (entity in db.accountDao().getAll()) {
            if (entity.syncStatus == SyncStatus.SYNCED.name) continue
            attempt {
                val remoteId = entity.remoteId
                    ?: findRemoteAccount(pg, entity)?.id
                    ?: UUID.randomUUID().toString()
                pg.from("accounts").upsert(toRemote(entity, remoteId)) {
                    onConflict = "id"
                }
                db.accountDao().update(
                    entity.copy(remoteId = remoteId, syncStatus = SyncStatus.SYNCED.name)
                )
                pushed++
            }
        }

        // 3. Push tags (fetch-or-create by name — unique per owner server-side).
        for (entity in db.tagDao().getAll()) {
            if (entity.syncStatus == SyncStatus.SYNCED.name) continue
            attempt {
                val remoteId = entity.remoteId
                    ?: findRemoteTag(pg, entity.name)?.id
                    ?: UUID.randomUUID().toString()
                pg.from("tags").upsert(toRemote(entity, remoteId)) {
                    onConflict = "id"
                }
                db.tagDao().update(
                    entity.copy(remoteId = remoteId, syncStatus = SyncStatus.SYNCED.name)
                )
                pushed++
            }
        }

        // 4. Push transactions + their links.
        val transactions = db.transactionDao().getAllWithTags()
        for (row in transactions) {
            val entity = row.transaction
            if (entity.syncStatus == SyncStatus.SYNCED.name) continue
            attempt {
                val sourceRemote = entity.sourceAccountId?.let { localId ->
                    db.accountDao().getById(localId)?.remoteId
                        ?: throw IllegalStateException("Source account not synced yet")
                }
                val destRemote = entity.destinationAccountId?.let { localId ->
                    db.accountDao().getById(localId)?.remoteId
                        ?: throw IllegalStateException("Destination account not synced yet")
                }
                val remoteId = entity.remoteId ?: UUID.randomUUID().toString()
                pg.from("transactions").upsert(toRemote(entity, remoteId, sourceRemote, destRemote)) {
                    onConflict = "id"
                }
                // Links are rewritten wholesale: deterministic, no drift.
                pg.from("transaction_tags").delete {
                    filter { eq("transaction_id", remoteId) }
                }
                val links = row.tags.mapNotNull { tag ->
                    db.tagDao().getById(tag.id)?.remoteId?.let { tagRemote ->
                        RemoteTransactionTag(remoteId, tagRemote)
                    }
                }
                if (links.size < row.tags.size) {
                    throw IllegalStateException("Tag not synced yet")
                }
                if (links.isNotEmpty()) {
                    pg.from("transaction_tags").insert(links)
                }
                db.transactionDao().update(
                    entity.copy(remoteId = remoteId, syncStatus = SyncStatus.SYNCED.name)
                )
                pushed++
            }
        }

        // 5. Pull accounts.
        val remoteAccounts = pg.from("accounts").select().decodeList<RemoteAccount>()
        for (remote in remoteAccounts) {
            attempt {
                val local = db.accountDao().getByRemoteId(remote.id)
                    ?: adoptAccount(remote)
                    ?: run {
                        db.accountDao().insert(fromRemote(remote))
                        pulled++
                        return@attempt
                    }
                when (
                    SyncDecisions.decide(
                        local.updatedAt, statusOf(local.syncStatus), remote.updatedAtMs
                    )
                ) {
                    SyncDecisions.Row.Push -> Unit // push phase owns pending rows
                    SyncDecisions.Row.ApplyRemote -> {
                        db.accountDao().update(applyRemote(local, remote))
                        pulled++
                    }
                    SyncDecisions.Row.Keep -> Unit
                }
            }
        }

        // 6. Pull tags.
        val remoteTags = pg.from("tags").select().decodeList<RemoteTag>()
        for (remote in remoteTags) {
            attempt {
                val local = db.tagDao().getByRemoteId(remote.id)
                    ?: adoptTag(remote)
                    ?: run {
                        db.tagDao().insert(fromRemote(remote))
                        pulled++
                        return@attempt
                    }
                when (
                    SyncDecisions.decide(
                        local.updatedAt, statusOf(local.syncStatus), remote.updatedAtMs
                    )
                ) {
                    SyncDecisions.Row.Push -> Unit
                    SyncDecisions.Row.ApplyRemote -> {
                        db.tagDao().update(applyRemote(local, remote))
                        pulled++
                    }
                    SyncDecisions.Row.Keep -> Unit
                }
            }
        }

        // 7. Pull transactions (+ links for inserted/applied rows only).
        val remoteLinks = pg.from("transaction_tags").select()
            .decodeList<RemoteTransactionTag>()
            .groupBy { it.transactionId }
        val remoteTransactions = pg.from("transactions").select().decodeList<RemoteTransaction>()
        for (remote in remoteTransactions) {
            attempt {
                val local = db.transactionDao().getByRemoteId(remote.id)
                if (local == null) {
                    val localId = insertRemoteTransaction(remote, remoteLinks[remote.id].orEmpty())
                    if (localId != null) pulled++
                    return@attempt
                }
                when (
                    SyncDecisions.decide(
                        local.updatedAt, statusOf(local.syncStatus), remote.updatedAtMs
                    )
                ) {
                    SyncDecisions.Row.Push -> Unit
                    SyncDecisions.Row.ApplyRemote -> {
                        applyRemoteTransaction(local, remote, remoteLinks[remote.id].orEmpty())
                        pulled++
                    }
                    SyncDecisions.Row.Keep -> Unit
                }
            }
        }

        return Status.Success(pushed, pulled, errors, System.currentTimeMillis())
    }

    // Natural-key lookups ---------------------------------------------------

    private suspend fun findRemoteAccount(
        pg: io.github.jan.supabase.postgrest.Postgrest,
        entity: AccountEntity
    ): RemoteAccount? = pg.from("accounts").select {
        filter {
            eq("name", entity.name)
            eq("institution", entity.institution)
            eq("identifier_suffix", entity.identifierSuffix)
        }
    }.decodeList<RemoteAccount>().firstOrNull()

    private suspend fun findRemoteTag(
        pg: io.github.jan.supabase.postgrest.Postgrest,
        name: String
    ): RemoteTag? = pg.from("tags").select {
        filter { eq("name", name) }
    }.decodeList<RemoteTag>().firstOrNull()

    /** Adopt a same-named local-only row instead of duplicating it. */
    private suspend fun adoptAccount(remote: RemoteAccount): AccountEntity? {
        val clash = db.accountDao().getAll().firstOrNull {
            it.remoteId == null && it.name == remote.name &&
                it.institution == remote.institution &&
                it.identifierSuffix == remote.identifierSuffix
        } ?: return null
        val adopted = applyRemote(clash, remote)
        db.accountDao().update(adopted)
        return adopted
    }

    private suspend fun adoptTag(remote: RemoteTag): TagEntity? {
        val clash = db.tagDao().getByName(remote.name)?.takeIf { it.remoteId == null }
            ?: return null
        val adopted = applyRemote(clash, remote)
        db.tagDao().update(adopted)
        return adopted
    }

    // Remote application ----------------------------------------------------

    private suspend fun insertRemoteTransaction(
        remote: RemoteTransaction,
        links: List<RemoteTransactionTag>
    ): Long? {
        val sourceLocal = remote.sourceAccountId?.let { db.accountDao().getByRemoteId(it)?.id }
        val destLocal = remote.destinationAccountId?.let { db.accountDao().getByRemoteId(it)?.id }
        // Referenced accounts must exist locally first; they are pulled before
        // transactions, so a miss means partial data — skip, retry next run.
        if (remote.sourceAccountId != null && sourceLocal == null) return null
        if (remote.destinationAccountId != null && destLocal == null) return null
        var localId = 0L
        db.withTransaction {
            localId = db.transactionDao().insert(
                fromRemote(remote, sourceLocal, destLocal)
            )
            linkRemoteTags(localId, links)
        }
        return localId
    }

    private suspend fun applyRemoteTransaction(
        local: TransactionEntity,
        remote: RemoteTransaction,
        links: List<RemoteTransactionTag>
    ) {
        val sourceLocal = remote.sourceAccountId?.let { db.accountDao().getByRemoteId(it)?.id }
        val destLocal = remote.destinationAccountId?.let { db.accountDao().getByRemoteId(it)?.id }
        if (remote.sourceAccountId != null && sourceLocal == null) return
        if (remote.destinationAccountId != null && destLocal == null) return
        db.withTransaction {
            db.transactionDao().update(applyRemote(local, remote, sourceLocal, destLocal))
            db.tagDao().unlinkAll(local.id)
            linkRemoteTags(local.id, links)
        }
    }

    private suspend fun linkRemoteTags(localTxId: Long, links: List<RemoteTransactionTag>) {
        for (link in links) {
            val localTagId = db.tagDao().getByRemoteId(link.tagId)?.id ?: continue
            db.tagDao().linkTag(TransactionTagCrossRef(localTxId, localTagId))
        }
    }

    // Mappings ---------------------------------------------------------------

    private fun toRemote(e: AccountEntity, remoteId: String) = RemoteAccount(
        id = remoteId, name = e.name, institution = e.institution,
        accountType = e.accountType, identifierSuffix = e.identifierSuffix,
        isOwnedByUser = e.isOwnedByUser, createdAtMs = e.createdAt, updatedAtMs = e.updatedAt
    )

    private fun toRemote(e: TagEntity, remoteId: String) = RemoteTag(
        id = remoteId, name = e.name, createdAtMs = e.createdAt, updatedAtMs = e.updatedAt
    )

    private fun toRemote(
        e: TransactionEntity,
        remoteId: String,
        sourceRemote: String?,
        destRemote: String?
    ) = RemoteTransaction(
        id = remoteId, type = e.type, amount = e.amount, dateTimeMs = e.dateTime,
        description = e.description, counterparty = e.counterparty,
        sourceAccountId = sourceRemote, destinationAccountId = destRemote,
        createdAtMs = e.createdAt, updatedAtMs = e.updatedAt
    )

    private fun fromRemote(r: RemoteAccount) = AccountEntity(
        name = r.name, institution = r.institution, accountType = r.accountType,
        identifierSuffix = r.identifierSuffix, isOwnedByUser = r.isOwnedByUser,
        createdAt = r.createdAtMs, remoteId = r.id,
        syncStatus = SyncStatus.SYNCED.name, updatedAt = r.updatedAtMs
    )

    private fun applyRemote(local: AccountEntity, r: RemoteAccount) = local.copy(
        name = r.name, institution = r.institution, accountType = r.accountType,
        identifierSuffix = r.identifierSuffix, isOwnedByUser = r.isOwnedByUser,
        remoteId = r.id, syncStatus = SyncStatus.SYNCED.name, updatedAt = r.updatedAtMs
    )

    private fun fromRemote(r: RemoteTag) = TagEntity(
        name = r.name, createdAt = r.createdAtMs, remoteId = r.id,
        syncStatus = SyncStatus.SYNCED.name, updatedAt = r.updatedAtMs
    )

    private fun applyRemote(local: TagEntity, r: RemoteTag) = local.copy(
        name = r.name, remoteId = r.id,
        syncStatus = SyncStatus.SYNCED.name, updatedAt = r.updatedAtMs
    )

    private fun fromRemote(
        r: RemoteTransaction,
        sourceLocal: Long?,
        destLocal: Long?
    ) = TransactionEntity(
        type = r.type, amount = r.amount, dateTime = r.dateTimeMs,
        description = r.description, counterparty = r.counterparty,
        sourceAccountId = sourceLocal, destinationAccountId = destLocal,
        syncStatus = SyncStatus.SYNCED.name, remoteId = r.id,
        createdAt = r.createdAtMs, updatedAt = r.updatedAtMs
    )

    private fun applyRemote(
        local: TransactionEntity,
        r: RemoteTransaction,
        sourceLocal: Long?,
        destLocal: Long?
    ) = local.copy(
        type = r.type, amount = r.amount, dateTime = r.dateTimeMs,
        description = r.description, counterparty = r.counterparty,
        sourceAccountId = sourceLocal, destinationAccountId = destLocal,
        syncStatus = SyncStatus.SYNCED.name, remoteId = r.id, updatedAt = r.updatedAtMs
    )

    private fun statusOf(value: String): SyncStatus =
        runCatching { SyncStatus.valueOf(value) }.getOrDefault(SyncStatus.ERROR)
}
