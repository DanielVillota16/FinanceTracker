package doug.financetracker.domain.usecase

import doug.financetracker.data.repository.RoomTransactionRepository
import doug.financetracker.domain.model.Transaction
import doug.financetracker.domain.model.TransactionDetails
import doug.financetracker.domain.repository.TransactionRepository
import kotlinx.coroutines.flow.Flow

class ObserveTransactionDetails(
    private val repo: TransactionRepository
) {
    operator fun invoke(): Flow<List<TransactionDetails>> = repo.observeDetails()
}

class CreateTransaction(
    private val repo: TransactionRepository
) {
    suspend operator fun invoke(transaction: Transaction): Long =
        repo.create(transaction)
}

class UpdateTransaction(
    private val repo: TransactionRepository
) {
    suspend operator fun invoke(transaction: Transaction) =
        repo.update(transaction)
}

class DeleteTransaction(
    private val repo: TransactionRepository
) {
    suspend operator fun invoke(id: Long) = repo.delete(id)
}

class ResolveTagNames(
    private val repo: RoomTransactionRepository
) {
    suspend operator fun invoke(names: List<String>): List<Long> =
        repo.resolveTagNames(names)
}
