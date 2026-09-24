package doug.financetracker.domain.repository

import doug.financetracker.domain.model.Account
import kotlinx.coroutines.flow.Flow

interface AccountRepository {
    fun observeAccounts(): Flow<List<Account>>
    suspend fun getAll(): List<Account>
    suspend fun getById(id: Long): Account?
    suspend fun create(account: Account): Long
    suspend fun update(account: Account)
    suspend fun delete(account: Account)
}
