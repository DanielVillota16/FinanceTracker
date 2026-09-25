package doug.financetracker.data.repository

import androidx.room.withTransaction
import doug.financetracker.data.local.dao.AccountDao
import doug.financetracker.data.local.dao.TagDao
import doug.financetracker.data.local.dao.TransactionDao
import doug.financetracker.data.local.database.FinanceDatabase
import doug.financetracker.data.local.entity.TagEntity
import doug.financetracker.data.local.entity.TransactionTagCrossRef
import doug.financetracker.data.local.mapper.toDomain
import doug.financetracker.data.local.mapper.toEntity
import doug.financetracker.domain.model.SyncStatus
import doug.financetracker.domain.model.Transaction
import doug.financetracker.domain.model.TransactionDetails
import doug.financetracker.domain.repository.TransactionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

class RoomTransactionRepository(
    private val db: FinanceDatabase,
    private val transactionDao: TransactionDao,
    private val tagDao: TagDao,
    private val accountDao: AccountDao
) : TransactionRepository {

    override fun observeDetails(): Flow<List<TransactionDetails>> =
        combine(
            transactionDao.observeAllWithTags(),
            accountDao.observeAll()
        ) { withTags, accounts ->
            val accountsById = accounts.associateBy { it.id }
            withTags.map { row ->
                val tags = row.tags.map { it.toDomain() }
                TransactionDetails(
                    transaction = row.transaction.toDomain(tagIds = tags.map { it.id }),
                    tags = tags,
                    sourceAccount = row.transaction.sourceAccountId
                        ?.let { accountsById[it]?.toDomain() },
                    destinationAccount = row.transaction.destinationAccountId
                        ?.let { accountsById[it]?.toDomain() }
                )
            }
        }

    override suspend fun getDetails(id: Long): TransactionDetails? {
        val row = transactionDao.getWithTags(id) ?: return null
        val tags = row.tags.map { it.toDomain() }
        return TransactionDetails(
            transaction = row.transaction.toDomain(tagIds = tags.map { it.id }),
            tags = tags,
            sourceAccount = row.transaction.sourceAccountId
                ?.let { accountDao.getById(it)?.toDomain() },
            destinationAccount = row.transaction.destinationAccountId
                ?.let { accountDao.getById(it)?.toDomain() }
        )
    }

    override suspend fun create(transaction: Transaction): Long {
        validate(transaction)
        var newId = 0L
        db.withTransaction {
            newId = transactionDao.insert(
                transaction.toEntity().copy(
                    id = 0L,
                    syncStatus = SyncStatus.PENDING_UPLOAD.name,
                    createdAt = System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis()
                )
            )
            linkTags(newId, transaction.tagIds)
        }
        return newId
    }

    override suspend fun update(transaction: Transaction) {
        validate(transaction)
        db.withTransaction {
            transactionDao.update(
                transaction.toEntity().copy(
                    syncStatus = SyncStatus.PENDING_UPDATE.name,
                    updatedAt = System.currentTimeMillis()
                )
            )
            tagDao.unlinkAll(transaction.id)
            linkTags(transaction.id, transaction.tagIds)
        }
    }

    override suspend fun delete(id: Long) {
        db.withTransaction {
            // Remember the remote row so sync can replay the delete offline-safe.
            transactionDao.getById(id)?.remoteId?.let { remoteId ->
                db.syncTombstoneDao().insert(
                    doug.financetracker.data.local.entity.SyncTombstoneEntity(
                        tableName = "transactions",
                        remoteId = remoteId
                    )
                )
            }
            tagDao.unlinkAll(id)
            transactionDao.deleteById(id)
        }
    }

    private suspend fun linkTags(transactionId: Long, tagIds: List<Long>) {
        for (tagId in tagIds) {
            tagDao.linkTag(TransactionTagCrossRef(transactionId, tagId))
        }
    }

    private fun validate(t: Transaction) {
        when (t.type) {
            doug.financetracker.domain.model.TransactionType.TRANSFER -> {
                require(t.sourceAccountId != null && t.destinationAccountId != null) {
                    "Transfer requires source and destination accounts"
                }
                require(t.sourceAccountId != t.destinationAccountId) {
                    "Transfer source and destination must differ"
                }
            }
            doug.financetracker.domain.model.TransactionType.EXPENSE -> {
                require(t.sourceAccountId != null) { "Expense requires a source account" }
            }
            doug.financetracker.domain.model.TransactionType.INCOME -> {
                require(t.destinationAccountId != null) { "Income requires a destination account" }
            }
        }
    }

    /** Resolve free-form tag names to ids, creating missing tags. */
    suspend fun resolveTagNames(names: List<String>): List<Long> {
        val ids = mutableListOf<Long>()
        for (raw in names) {
            val clean = raw.trim().trimStart('#')
            if (clean.isEmpty()) continue
            val existing = tagDao.getByName(clean)
            if (existing != null) {
                ids += existing.id
            } else {
                ids += tagDao.insert(TagEntity(name = clean))
            }
        }
        return ids.distinct()
    }
}
