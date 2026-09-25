package doug.financetracker.data.local.mapper

import doug.financetracker.data.local.entity.AccountEntity
import doug.financetracker.data.local.entity.TagEntity
import doug.financetracker.data.local.entity.TransactionEntity
import doug.financetracker.domain.model.Account
import doug.financetracker.domain.model.SyncStatus
import doug.financetracker.domain.model.Tag
import doug.financetracker.domain.model.Transaction
import doug.financetracker.domain.model.TransactionType

fun AccountEntity.toDomain() = Account(
    id = id,
    name = name,
    institution = institution,
    accountType = accountType,
    identifierSuffix = identifierSuffix,
    isOwnedByUser = isOwnedByUser,
    createdAt = createdAt,
    remoteId = remoteId,
    syncStatus = runCatching { SyncStatus.valueOf(syncStatus) }
        .getOrDefault(SyncStatus.PENDING_UPLOAD),
    updatedAt = updatedAt
)

fun Account.toEntity() = AccountEntity(
    id = id,
    name = name,
    institution = institution,
    accountType = accountType,
    identifierSuffix = identifierSuffix,
    isOwnedByUser = isOwnedByUser,
    createdAt = createdAt,
    remoteId = remoteId,
    syncStatus = syncStatus.name,
    updatedAt = updatedAt
)

fun TransactionEntity.toDomain(tagIds: List<Long> = emptyList()) = Transaction(
    id = id,
    type = TransactionType.valueOf(type),
    amount = amount,
    dateTime = dateTime,
    description = description,
    counterparty = counterparty,
    sourceAccountId = sourceAccountId,
    destinationAccountId = destinationAccountId,
    tagIds = tagIds,
    syncStatus = runCatching { SyncStatus.valueOf(syncStatus) }
        .getOrDefault(SyncStatus.PENDING_UPLOAD),
    remoteId = remoteId,
    createdAt = createdAt,
    updatedAt = updatedAt
)

fun Transaction.toEntity() = TransactionEntity(
    id = id,
    type = type.name,
    amount = amount,
    dateTime = dateTime,
    description = description,
    counterparty = counterparty,
    sourceAccountId = sourceAccountId,
    destinationAccountId = destinationAccountId,
    syncStatus = syncStatus.name,
    remoteId = remoteId,
    createdAt = createdAt,
    updatedAt = updatedAt
)

fun TagEntity.toDomain() = Tag(
    id = id,
    name = name,
    createdAt = createdAt,
    remoteId = remoteId,
    syncStatus = runCatching { SyncStatus.valueOf(syncStatus) }
        .getOrDefault(SyncStatus.PENDING_UPLOAD),
    updatedAt = updatedAt
)

fun Tag.toEntity() = TagEntity(
    id = id,
    name = name,
    createdAt = createdAt,
    remoteId = remoteId,
    syncStatus = syncStatus.name,
    updatedAt = updatedAt
)
