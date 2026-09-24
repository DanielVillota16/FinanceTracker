package doug.financetracker.data.repository

import doug.financetracker.data.local.dao.AccountDao
import doug.financetracker.data.local.mapper.toDomain
import doug.financetracker.data.local.mapper.toEntity
import doug.financetracker.domain.model.Account
import doug.financetracker.domain.repository.AccountRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RoomAccountRepository(
    private val dao: AccountDao
) : AccountRepository {
    override fun observeAccounts(): Flow<List<Account>> =
        dao.observeAll().map { list -> list.map { it.toDomain() } }

    override suspend fun getById(id: Long): Account? =
        dao.getById(id)?.toDomain()

    override suspend fun create(account: Account): Long =
        dao.insert(account.toEntity().copy(id = 0L))

    override suspend fun update(account: Account) {
        dao.update(account.toEntity())
    }

    override suspend fun delete(account: Account) {
        dao.delete(account.toEntity())
    }
}
