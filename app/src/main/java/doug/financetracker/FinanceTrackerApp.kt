package doug.financetracker

import android.app.Application
import doug.financetracker.data.local.database.FinanceDatabase
import doug.financetracker.data.repository.RoomAccountRepository
import doug.financetracker.data.repository.RoomPendingReviewRepository
import doug.financetracker.data.repository.RoomSourceEventRepository
import doug.financetracker.data.repository.RoomTagRepository
import doug.financetracker.data.repository.RoomTransactionRepository
import doug.financetracker.domain.usecase.ConfirmPendingItem
import doug.financetracker.domain.usecase.CreateTransaction
import doug.financetracker.domain.usecase.DeleteTransaction
import doug.financetracker.domain.usecase.IngestSourceMessage
import doug.financetracker.domain.usecase.ObserveTransactionDetails
import doug.financetracker.domain.usecase.ResolveTagNames
import doug.financetracker.domain.usecase.UpdateTransaction

class FinanceTrackerApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}

class AppContainer(app: Application) {
    private val db: FinanceDatabase = FinanceDatabase.get(app)

    val accountRepository = RoomAccountRepository(db.accountDao())
    val tagRepository = RoomTagRepository(db.tagDao())
    val transactionRepository = RoomTransactionRepository(
        db = db,
        transactionDao = db.transactionDao(),
        tagDao = db.tagDao(),
        accountDao = db.accountDao()
    )
    val sourceEventRepository = RoomSourceEventRepository(db.sourceEventDao())
    val pendingReviewRepository = RoomPendingReviewRepository(db)

    val observeTransactionDetails = ObserveTransactionDetails(transactionRepository)
    val createTransaction = CreateTransaction(transactionRepository)
    val updateTransaction = UpdateTransaction(transactionRepository)
    val deleteTransaction = DeleteTransaction(transactionRepository)
    val resolveTagNames = ResolveTagNames(transactionRepository)
    val ingestSourceMessage = IngestSourceMessage(db)
    val confirmPendingItem = ConfirmPendingItem(
        pendingReviewRepository, transactionRepository, accountRepository
    )
}
