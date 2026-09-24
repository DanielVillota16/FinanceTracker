package doug.financetracker.data.local.relation

import androidx.room.Embedded
import androidx.room.Junction
import androidx.room.Relation
import doug.financetracker.data.local.entity.AccountEntity
import doug.financetracker.data.local.entity.TagEntity
import doug.financetracker.data.local.entity.TransactionEntity
import doug.financetracker.data.local.entity.TransactionTagCrossRef

/**
 * Transaction row plus its tags for list display.
 * Accounts are resolved separately (source/destination may be null).
 */
data class TransactionWithTags(
    @Embedded val transaction: TransactionEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "id",
        associateBy = Junction(
            value = TransactionTagCrossRef::class,
            parentColumn = "transactionId",
            entityColumn = "tagId"
        )
    )
    val tags: List<TagEntity>
)

/** Convenience holder so the UI can show account labels without extra lookups. */
data class TransactionDetails(
    val transaction: TransactionEntity,
    val tags: List<TagEntity> = emptyList(),
    val sourceAccount: AccountEntity? = null,
    val destinationAccount: AccountEntity? = null
)
