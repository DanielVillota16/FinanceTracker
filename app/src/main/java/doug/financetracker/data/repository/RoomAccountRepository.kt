package doug.financetracker.data.repository

import androidx.room.withTransaction
import doug.financetracker.data.local.database.FinanceDatabase
import doug.financetracker.data.local.entity.SyncTombstoneEntity
import doug.financetracker.data.local.mapper.toDomain
import doug.financetracker.data.local.mapper.toEntity
import doug.financetracker.domain.model.Account
import doug.financetracker.domain.model.SyncStatus
import doug.financetracker.domain.repository.AccountRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RoomAccountRepository(
    private val db: FinanceDatabase
) : AccountRepository {
    private val dao get() = db.accountDao()

    override fun observeAccounts(): Flow<List<Account>> =
        dao.observeAll().map { list -> list.map { it.toDomain() } }

    override suspend fun getAll(): List<Account> =
        dao.getAll().map { it.toDomain() }

    override suspend fun getById(id: Long): Account? =
        dao.getById(id)?.toDomain()

    override suspend fun create(account: Account): Long =
        dao.insert(
            account.toEntity().copy(
                id = 0L,
                remoteId = null,
                syncStatus = SyncStatus.PENDING_UPLOAD.name,
                updatedAt = System.currentTimeMillis()
            )
        )

    override suspend fun update(account: Account) {
        dao.update(
            account.toEntity().copy(
                syncStatus = SyncStatus.PENDING_UPDATE.name,
                updatedAt = System.currentTimeMillis()
            )
        )
    }

    override suspend fun delete(account: Account) {
        db.withTransaction {
            // Remember the remote row so sync can replay the delete offline-safe.
            dao.getById(account.id)?.remoteId?.let { remoteId ->
                db.syncTombstoneDao().insert(
                    SyncTombstoneEntity(tableName = "accounts", remoteId = remoteId)
                )
            }
            dao.delete(account.toEntity())
        }
    }
}
