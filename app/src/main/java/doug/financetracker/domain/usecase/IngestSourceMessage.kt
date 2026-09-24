package doug.financetracker.domain.usecase

import androidx.room.withTransaction
import doug.financetracker.data.ingest.SourceFingerprinter
import doug.financetracker.data.local.database.FinanceDatabase
import doug.financetracker.data.local.entity.SourceEventEntity
import doug.financetracker.data.local.mapper.toEntity
import doug.financetracker.domain.model.ParsedStatus
import doug.financetracker.domain.model.PendingItem
import doug.financetracker.domain.model.SourceEvent
import doug.financetracker.domain.parser.NotificationParserRegistry
import doug.financetracker.domain.parser.ParserRegistry

/**
 * Single entry point for the Source → Interpretation → Candidate pipeline
 * (spec §6). Used identically by the SMS receiver, the historical importer,
 * and the notification listener (Phases 4–5) — and by the Settings test hook.
 *
 * Idempotent: re-ingesting the same message returns [Duplicate] without
 * creating new rows (fingerprint check + UNIQUE constraint + IGNORE insert).
 */
class IngestSourceMessage(
    private val db: FinanceDatabase
) {
    sealed interface Result {
        data class Created(val pendingId: Long) : Result
        data class Duplicate(val sourceEventId: Long) : Result
        data class Unsupported(val sourceEventId: Long) : Result
    }

    suspend operator fun invoke(
        sourceType: String,
        sourceIdentifier: String,
        rawContent: String,
        eventTime: Long? = null,
        receivedAt: Long = System.currentTimeMillis()
    ): Result {
        val fingerprint = SourceFingerprinter.fingerprint(
            sourceType, sourceIdentifier, rawContent, eventTime
        )
        db.sourceEventDao().getByFingerprint(fingerprint)?.let {
            return Result.Duplicate(it.id)
        }
        // Notifications are interpreted by package-scoped parsers first; the
        // generic SMS registry is only a fallback. Separate registries keep
        // SMS and notification patterns from claiming each other (Phase 4).
        val parsed = if (sourceType == "NOTIFICATION") {
            NotificationParserRegistry.parse(sourceIdentifier, rawContent)
                ?: ParserRegistry.parse(rawContent)
        } else {
            ParserRegistry.parse(rawContent)
        }
        lateinit var result: Result
        db.withTransaction {
            // Recheck inside the transaction: a concurrent ingest may have won.
            db.sourceEventDao().getByFingerprint(fingerprint)?.let {
                result = Result.Duplicate(it.id)
                return@withTransaction
            }
            var eventId = db.sourceEventDao().insertIgnore(
                SourceEventEntity(
                    sourceType = sourceType,
                    sourceIdentifier = sourceIdentifier,
                    receivedAt = receivedAt,
                    eventTime = eventTime,
                    rawContent = rawContent,
                    fingerprint = fingerprint,
                    parsedStatus = if (parsed == null) ParsedStatus.UNSUPPORTED.name
                    else ParsedStatus.PARSED.name
                )
            )
            if (eventId == -1L) {
                // Lost an insert race; the winner owns this fingerprint.
                val winner = db.sourceEventDao().getByFingerprint(fingerprint)
                result = Result.Duplicate(winner?.id ?: -1L)
                return@withTransaction
            }
            result = if (parsed == null) {
                Result.Unsupported(eventId)
            } else {
                val pendingId = db.pendingReviewDao().findPendingForEvent(eventId)?.id
                    ?: db.pendingReviewDao().insert(
                        PendingItem(
                            sourceEvent = SourceEvent(
                                id = eventId,
                                sourceType = sourceType,
                                sourceIdentifier = sourceIdentifier,
                                receivedAt = receivedAt,
                                eventTime = eventTime,
                                rawContent = rawContent,
                                fingerprint = fingerprint,
                                parsedStatus = ParsedStatus.PARSED
                            ),
                            parsed = parsed
                        ).toEntity().copy(sourceEventId = eventId)
                    )
                Result.Created(pendingId)
            }
        }
        return result
    }
}
