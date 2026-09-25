package doug.financetracker.domain.correlation

import doug.financetracker.domain.model.PendingItem
import doug.financetracker.domain.parser.Confidence
import doug.financetracker.domain.parser.Direction
import doug.financetracker.domain.parser.TransactionKind

/**
 * Source-agnostic purchase correlation (NOT transfer matching).
 *
 * Answers: do these two pending interpretations describe the SAME physical
 * purchase (e.g. a Bancolombia SMS plus its Google Wallet notification)?
 * Never bank-specific: no rule mentions BBVA, Wallet, SMS, or notifications.
 * Only evidence matters.
 *
 * Required gates (all must hold for [Decision.Correlated]):
 * - same exact amount (never merge on anything less; never merge on amount alone)
 * - purchase-compatible kinds: both EXPENSE, or UNKNOWN still possibly-EXPENSE.
 *   TRANSFER/INCOME pairs are a different problem (transfer matching) and never
 *   correlate here.
 * - both OUTGOING
 * - timestamps within [TIME_WINDOW_MINUTES] (effective time = parsed event time,
 *   else source event time, else reception time)
 * - same card suffix present on both sides (missing or different → no merge)
 *
 * Merchant similarity is SUPPORTING evidence only: it raises confidence but is
 * never required (banks report different merchant strings) and never sufficient
 * (two legitimate purchases at one merchant must stay separate).
 *
 * Pure and deterministic — fully unit-tested, no database involved.
 */
object PurchaseCorrelation {

    const val TIME_WINDOW_MINUTES = 15L
    const val MERCHANT_SUPPORT_THRESHOLD = 0.5

    data class Input(
        val amountPesos: Long?,
        val timestampMillis: Long?,
        val cardSuffix: String?,
        val kind: TransactionKind,
        val possibleKinds: List<TransactionKind>,
        val direction: Direction?,
        val counterparty: String?,
        val institution: String
    )

    sealed interface Decision {
        data class Correlated(val confidence: Confidence, val evidence: List<String>) : Decision
        data class NotCorrelated(val reasons: List<String>) : Decision
    }

    /** Normalized snapshot of a pending item for scoring. Order-independent. */
    fun inputOf(item: PendingItem): Input {
        val parsed = item.parsed
        val suffix = parsed.sourceAccountHint?.lastDigits?.ifBlank { null }
            ?: parsed.destinationAccountHint?.lastDigits?.ifBlank { null }
        return Input(
            amountPesos = parsed.amountPesos,
            timestampMillis = parsed.timestampMillis
                ?: item.sourceEvent.eventTime
                ?: item.sourceEvent.receivedAt,
            cardSuffix = suffix,
            kind = parsed.transactionKind,
            possibleKinds = parsed.possibleKinds,
            direction = parsed.direction,
            counterparty = parsed.counterparty,
            institution = parsed.institution
        )
    }

    fun decide(a: Input, b: Input): Decision {
        val reasons = mutableListOf<String>()
        if (a.amountPesos == null || b.amountPesos == null) {
            reasons += "amount missing"
        } else if (a.amountPesos != b.amountPesos) {
            reasons += "amounts differ (${a.amountPesos} vs ${b.amountPesos})"
        }
        if (!purchaseCompatible(a) || !purchaseCompatible(b)) {
            reasons += "kinds not purchase-compatible (${a.kind}/${b.kind})"
        }
        if (a.direction != Direction.OUTGOING || b.direction != Direction.OUTGOING) {
            reasons += "directions not both outgoing (${a.direction}/${b.direction})"
        }
        val timeDeltaMinutes = if (a.timestampMillis != null && b.timestampMillis != null) {
            kotlin.math.abs(a.timestampMillis - b.timestampMillis) / 60_000L
        } else null
        if (timeDeltaMinutes == null) {
            reasons += "timestamp missing"
        } else if (timeDeltaMinutes > TIME_WINDOW_MINUTES) {
            reasons += "timestamps $timeDeltaMinutes min apart (window $TIME_WINDOW_MINUTES min)"
        }
        if (a.cardSuffix.isNullOrBlank() || b.cardSuffix.isNullOrBlank()) {
            reasons += "card suffix missing on at least one side"
        } else if (a.cardSuffix != b.cardSuffix) {
            reasons += "card suffixes differ (****${a.cardSuffix} vs ****${b.cardSuffix})"
        }
        if (reasons.isNotEmpty()) return Decision.NotCorrelated(reasons)

        val evidence = mutableListOf(
            "same amount (${a.amountPesos})",
            "timestamps $timeDeltaMinutes min apart",
            "same card suffix (****${a.cardSuffix})",
            "compatible purchase kinds (${a.kind}/${b.kind})"
        )
        val similarity = merchantSimilarity(a.counterparty, b.counterparty)
        val confidence = if (similarity != null && similarity >= MERCHANT_SUPPORT_THRESHOLD) {
            evidence += "merchant similarity ${(similarity * 100).toInt()}% " +
                "(${a.counterparty} / ${b.counterparty})"
            Confidence.HIGH
        } else {
            if (similarity != null) {
                evidence += "merchant similarity only ${(similarity * 100).toInt()}% — " +
                    "kept as supporting evidence, raw values preserved"
            } else {
                evidence += "merchant missing on at least one side"
            }
            Confidence.MEDIUM
        }
        return Decision.Correlated(confidence, evidence)
    }

    private fun purchaseCompatible(input: Input): Boolean =
        input.kind == TransactionKind.EXPENSE ||
            (input.kind == TransactionKind.UNKNOWN &&
                TransactionKind.EXPENSE in input.possibleKinds)

    /**
     * Token-containment similarity in [0,1], or null when either side is blank.
     * "TIEN IA D1 PSTO" vs "TIEN IA D1 PSTO IAM I" → 1.0 (subset).
     */
    fun merchantSimilarity(a: String?, b: String?): Double? {
        val tokensA = tokens(a)
        val tokensB = tokens(b)
        if (tokensA.isEmpty() || tokensB.isEmpty()) return null
        val intersection = tokensA.intersect(tokensB).size.toDouble()
        val denom = minOf(tokensA.size, tokensB.size).toDouble()
        return intersection / denom
    }

    private fun tokens(value: String?): Set<String> {
        if (value.isNullOrBlank()) return emptySet()
        return value.uppercase()
            .replace(Regex("[^A-Z0-9 ]"), " ")
            .split(Regex("\\s+"))
            .filter { it.isNotBlank() }
            .toSet()
    }
}
