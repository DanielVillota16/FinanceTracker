package doug.financetracker.domain.repository

import doug.financetracker.domain.model.Transaction
import doug.financetracker.domain.model.TransactionDetails
import kotlinx.coroutines.flow.Flow

interface TransactionRepository {
    /** All transactions with tags/accounts resolved, newest first. */
    fun observeDetails(): Flow<List<TransactionDetails>>
    suspend fun getDetails(id: Long): TransactionDetails?
    suspend fun create(transaction: Transaction): Long
    suspend fun update(transaction: Transaction)
    suspend fun delete(id: Long)
}
