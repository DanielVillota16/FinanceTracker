package doug.financetracker.domain.usecase

import androidx.room.withTransaction
import doug.financetracker.data.ingest.SourceFingerprinter
import doug.financetracker.data.local.database.FinanceDatabase
import doug.financetracker.data.local.entity.SourceEventEntity
import doug.financetracker.data.local.entity.TransactionCandidateEntity
import doug.financetracker.data.local.mapper.toDomain
import doug.financetracker.data.local.mapper.toEntity
import doug.financetracker.domain.correlation.CandidateMerger
import doug.financetracker.domain.correlation.TransferMatcher
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
                val newItem = PendingItem(
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
                )
                val existingPending = db.pendingReviewDao().findPendingForEvent(eventId)
                val pendingId = existingPending?.id
                    ?: db.pendingReviewDao().insert(
                        newItem.toEntity().copy(sourceEventId = eventId)
                    )
                if (existingPending == null) {
                    correlate(pendingId, newItem)
                }
                Result.Created(pendingId)
            }
        }
        return result
    }

    /**
     * Matching, in order: purchase correlation first (same movement), then
     * transfer matching (opposite legs of one internal movement). Either step
     * is order-independent — the second arrival joins the first. Weak evidence
     * stays separate for the user. Must be called inside the ingest transaction.
     */
    private suspend fun correlate(pendingId: Long, newItem: PendingItem) {
        val recent = db.pendingReviewDao().findRecentPending(CORRELATION_LOOKBACK)
            .filter { it.review.id != pendingId }
            .mapNotNull { row ->
                val candidateId = row.review.candidateId ?: return@mapNotNull null
                row.review.toDomain(row.source.toDomain()) to candidateId
            }
        when (val merge = CandidateMerger.decide(recent, newItem)) {
            is CandidateMerger.Decision.Join -> {
                db.pendingReviewDao().setCandidate(pendingId, merge.candidateId)
                return
            }
            CandidateMerger.Decision.NewCandidate -> Unit // fall through to transfers
        }
        val transferHit = TransferMatcher.findMatch(
            recent, newItem, db.accountDao().getAll().map { it.toDomain() }
        )?.takeIf { (candidateId, _) ->
            // Only join solo candidates: folding a transfer leg into an
            // already-correlated purchase group would corrupt it.
            db.pendingReviewDao().getByCandidate(candidateId).size <= 1
        }
        if (transferHit != null) {
            val (candidateId, _) = transferHit
            db.transactionCandidateDao().markTransfer(candidateId, System.currentTimeMillis())
            db.pendingReviewDao().setCandidate(pendingId, candidateId)
        } else {
            val candidateId = db.transactionCandidateDao().insert(TransactionCandidateEntity())
            db.pendingReviewDao().setCandidate(pendingId, candidateId)
        }
    }

    companion object {
        /** Queue depth scanned for correlation partners (single-user scale). */
        const val CORRELATION_LOOKBACK = 100
    }
}
