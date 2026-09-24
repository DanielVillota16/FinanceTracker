package doug.financetracker.data.local.mapper

import doug.financetracker.data.local.entity.PendingReviewEntity
import doug.financetracker.data.local.entity.SourceEventEntity
import doug.financetracker.domain.model.ParsedStatus
import doug.financetracker.domain.model.PendingItem
import doug.financetracker.domain.model.PendingStatus
import doug.financetracker.domain.model.SourceEvent
import doug.financetracker.domain.parser.AccountHint
import doug.financetracker.domain.parser.Confidence
import doug.financetracker.domain.parser.Direction
import doug.financetracker.domain.parser.ParsedTransaction
import doug.financetracker.domain.parser.TransactionKind

private const val SEP = "\u001F"

private fun join(values: List<String>): String = values.joinToString(SEP)

private fun split(value: String): List<String> =
    if (value.isEmpty()) emptyList() else value.split(SEP)

fun SourceEventEntity.toDomain() = SourceEvent(
    id = id,
    sourceType = sourceType,
    sourceIdentifier = sourceIdentifier,
    receivedAt = receivedAt,
    eventTime = eventTime,
    rawContent = rawContent,
    fingerprint = fingerprint,
    parsedStatus = runCatching { ParsedStatus.valueOf(parsedStatus) }
        .getOrDefault(ParsedStatus.RECEIVED)
)

fun SourceEvent.toEntity() = SourceEventEntity(
    id = id,
    sourceType = sourceType,
    sourceIdentifier = sourceIdentifier,
    receivedAt = receivedAt,
    eventTime = eventTime,
    rawContent = rawContent,
    fingerprint = fingerprint,
    parsedStatus = parsedStatus.name
)

private fun hintOrNull(digits: String?, label: String?, isCash: Boolean): AccountHint? {
    if (isCash) return AccountHint.CASH
    if (digits.isNullOrEmpty() && label.isNullOrEmpty()) return null
    return AccountHint(lastDigits = digits.orEmpty(), label = label)
}

fun PendingReviewEntity.toDomain(source: SourceEvent): PendingItem {
    val kinds = split(possibleKinds).mapNotNull { runCatching { TransactionKind.valueOf(it) }.getOrNull() }
    val kind = runCatching { TransactionKind.valueOf(kind) }.getOrDefault(TransactionKind.UNKNOWN)
    return PendingItem(
        id = id,
        sourceEvent = source,
        parsed = ParsedTransaction(
            amountPesos = amount,
            direction = direction?.let { runCatching { Direction.valueOf(it) }.getOrNull() },
            transactionKind = kind,
            possibleKinds = kinds.ifEmpty { listOf(kind) },
            institution = institution,
            sourceAccountHint = hintOrNull(sourceHintDigits, sourceHintLabel, sourceHintCash),
            destinationAccountHint = hintOrNull(destHintDigits, destHintLabel, destHintCash),
            counterparty = counterparty,
            timestampMillis = eventTime,
            reference = reference,
            confidence = runCatching { Confidence.valueOf(confidence) }
                .getOrDefault(Confidence.LOW),
            warnings = split(warnings)
        ),
        status = runCatching { PendingStatus.valueOf(status) }
            .getOrDefault(PendingStatus.PENDING),
        linkedTransactionId = linkedTransactionId,
        createdAt = createdAt
    )
}

fun PendingItem.toEntity(): PendingReviewEntity {
    val p = parsed
    return PendingReviewEntity(
        id = id,
        sourceEventId = sourceEvent.id,
        amount = p.amountPesos,
        direction = p.direction?.name,
        kind = p.transactionKind.name,
        possibleKinds = join(p.possibleKinds.map { it.name }),
        institution = p.institution,
        sourceHintDigits = p.sourceAccountHint?.lastDigits?.ifEmpty { null },
        sourceHintLabel = p.sourceAccountHint?.label,
        sourceHintCash = p.sourceAccountHint?.isCash == true,
        destHintDigits = p.destinationAccountHint?.lastDigits?.ifEmpty { null },
        destHintLabel = p.destinationAccountHint?.label,
        destHintCash = p.destinationAccountHint?.isCash == true,
        counterparty = p.counterparty,
        eventTime = p.timestampMillis,
        reference = p.reference,
        confidence = p.confidence.name,
        warnings = join(p.warnings),
        status = status.name,
        linkedTransactionId = linkedTransactionId,
        createdAt = createdAt,
        updatedAt = System.currentTimeMillis()
    )
}
