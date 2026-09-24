package doug.financetracker.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "transactions",
    foreignKeys = [
        ForeignKey(
            entity = AccountEntity::class,
            parentColumns = ["id"],
            childColumns = ["sourceAccountId"],
            onDelete = ForeignKey.SET_NULL
        ),
        ForeignKey(
            entity = AccountEntity::class,
            parentColumns = ["id"],
            childColumns = ["destinationAccountId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [
        Index("dateTime"),
        Index("type"),
        Index("sourceAccountId"),
        Index("destinationAccountId")
    ]
)
data class TransactionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    /** EXPENSE | INCOME | TRANSFER */
    val type: String,
    /** Exact COP pesos, always positive. */
    val amount: Long,
    /** Epoch millis of the movement. */
    val dateTime: Long,
    val description: String = "",
    val counterparty: String = "",
    val sourceAccountId: Long? = null,
    val destinationAccountId: Long? = null,
    /** SYNCED | PENDING_UPLOAD | PENDING_UPDATE | PENDING_DELETE | ERROR */
    val syncStatus: String = "PENDING_UPLOAD",
    val remoteId: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
