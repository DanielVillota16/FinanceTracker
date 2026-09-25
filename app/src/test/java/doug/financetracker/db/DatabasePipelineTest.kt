package doug.financetracker.db

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import doug.financetracker.data.local.database.FinanceDatabase
import doug.financetracker.data.local.entity.AccountEntity
import doug.financetracker.data.repository.RoomAccountRepository
import doug.financetracker.data.repository.RoomPendingReviewRepository
import doug.financetracker.data.repository.RoomTransactionRepository
import doug.financetracker.domain.model.TransactionType
import doug.financetracker.domain.usecase.ConfirmPendingItem
import doug.financetracker.domain.usecase.IngestSourceMessage
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * End-to-end pipeline against a real (in-memory) database:
 * 2 SourceEvents → 1 candidate → 1 official transaction, with idempotent
 * ingestion. Covers the BBVA + Wallet purchase pair and a transfer pair.
 */
@RunWith(RobolectricTestRunner::class)
// SDK 35: highest sandbox Robolectric 4.17 runs reliably in this
// environment (the 36 sandbox hits untamed framework internals).
@Config(sdk = [35])
class DatabasePipelineTest {

    private lateinit var db: FinanceDatabase
    private lateinit var ingest: IngestSourceMessage
    private lateinit var pending: RoomPendingReviewRepository
    private lateinit var transactions: RoomTransactionRepository
    private lateinit var confirm: ConfirmPendingItem

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, FinanceDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        ingest = IngestSourceMessage(db)
        pending = RoomPendingReviewRepository(db)
        transactions = RoomTransactionRepository(db, db.transactionDao(), db.tagDao(), db.accountDao())
        confirm = ConfirmPendingItem(pending, transactions, RoomAccountRepository(db))

        runTest {
            db.accountDao().insert(
                AccountEntity(name = "BBVA", institution = "BBVA", identifierSuffix = "1444")
            )
            db.accountDao().insert(
                AccountEntity(name = "Bancolombia", institution = "Bancolombia", identifierSuffix = "8494")
            )
            db.accountDao().insert(
                AccountEntity(name = "Nequi", institution = "Nequi")
            )
            db.accountDao().insert(
                AccountEntity(name = "Cash", institution = "Cash", accountType = "CASH")
            )
        }
    }

    @After
    fun teardown() {
        db.close()
    }

    @Test
    fun `same sms twice ingests once`() = runTest {
        val sms = "Bancolombia: Compraste \$29.000,00 en BOLD SA*20 DE JU con tu T.Deb " +
            "*0757, el 21/09/2026 a las 08:27. Estamos cerca"
        val first = ingest("SMS", "Bancolombia", sms, eventTime = 1000L)
        val second = ingest("SMS", "Bancolombia", sms, eventTime = 1000L)
        assertTrue(first is IngestSourceMessage.Result.Created)
        assertTrue(second is IngestSourceMessage.Result.Duplicate)
        assertEquals(1, db.sourceEventDao().count())
        assertEquals(1, db.pendingReviewDao().pendingCount())
    }

    @Test
    fun `bbva plus wallet become one expense after confirm`() = runTest {
        // Wallet carries no date: its notification post time is the event time.
        val postTime = java.time.LocalDateTime.of(2026, 9, 24, 14, 53)
            .atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
        val bbva = "Compra Exitosa\nHola, realizaste una compra por \$99,320.00 en " +
            "Tien ia d1 psto con tu tarjeta BBVA *1444. El 2026-09-24 a las 14:53."
        val wallet = "TIEN IA D1 PSTO IAM I\nCOP 99,320.00 with Mastercard Platinum ••1444"

        val r1 = ingest("NOTIFICATION", "co.com.bbva.mb", bbva, eventTime = postTime)
        val r2 = ingest(
            "NOTIFICATION", "com.google.android.apps.walletnfcrel",
            wallet, eventTime = postTime
        )
        assertTrue(r1 is IngestSourceMessage.Result.Created)
        assertTrue(r2 is IngestSourceMessage.Result.Created)
        assertEquals(2, db.sourceEventDao().count())

        val candidates = pending.observeCandidates().first()
        assertEquals(1, candidates.size)
        assertEquals(2, candidates.single().members.size)

        val txId = confirm.confirm(candidates.single().primary.id)
        val details = transactions.getDetails(txId)!!
        assertEquals(TransactionType.EXPENSE, details.transaction.type)
        assertEquals(99_320L, details.transaction.amount)
        assertEquals("Tien ia d1 psto", details.transaction.counterparty)
        assertEquals("BBVA", details.sourceAccount?.name)
        // Queue drained, evidence retained.
        assertEquals(0, db.pendingReviewDao().pendingCount())
        assertEquals(2, db.sourceEventDao().count())
    }

    @Test
    fun `transfer pair becomes one transfer after confirm`() = runTest {
        val out = "Transferiste \$100,000.00 desde tu cuenta *8494 a la cuenta *5678 " +
            "el 22/09/2026 a las 10:02."
        val inc = "NEQUI: Recibiste 100.000,00 de CARLOS RUIZ el 22/09/2026 a las 10:03."
        ingest("SMS", "Bancolombia", out, eventTime = 2000L)
        ingest("SMS", "Nequi", inc, eventTime = 3000L)

        val candidates = pending.observeCandidates().first()
        assertEquals(1, candidates.size)
        val candidate = candidates.single()
        assertEquals(
            doug.financetracker.domain.parser.TransactionKind.TRANSFER,
            candidate.suggestedKind
        )

        val txId = confirm.confirm(candidate.primary.id)
        val details = transactions.getDetails(txId)!!
        assertEquals(TransactionType.TRANSFER, details.transaction.type)
        assertEquals(100_000L, details.transaction.amount)
        assertEquals("Bancolombia", details.sourceAccount?.name)
        assertEquals("Nequi", details.destinationAccount?.name)
    }
}
