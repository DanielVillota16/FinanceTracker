package doug.financetracker.domain.correlation

import doug.financetracker.domain.model.Account
import doug.financetracker.domain.model.PendingItem
import doug.financetracker.domain.parser.Confidence
import doug.financetracker.domain.parser.Direction
import doug.financetracker.domain.parser.TransactionKind

/**
 * Transfer matching: the counterpart to [PurchaseCorrelation].
 *
 * Answers: do this OUTGOING event and this INCOMING event describe the SAME
 * internal movement (e.g. Bancolombia −$100,000 plus Nequi +$100,000)?
 * Semantically distinct from purchase correlation: a matched pair proposes
 * one TRANSFER, never an expense/income pair and never a purchase duplicate.
 *
 * Required gates (all must hold):
 * - same exact amount (never match on anything less; never on amount alone)
 * - opposite directions: exactly one OUTGOING and one INCOMING side
 * - transfer-compatible kinds: the outgoing side is UNKNOWN still
 *   possibly-TRANSFER (a confident EXPENSE is a purchase — pairing it with an
 *   unrelated income would invent a transfer); the incoming side is INCOME or
 *   UNKNOWN still possibly-TRANSFER. Complete TRANSFER interpretations
 *   (e.g. an ATM withdrawal, already transfer-to-Cash) never match here.
 * - timestamps within [TIME_WINDOW_MINUTES] (transfers settle slower than
 *   tap-to-pay duplicates, hence a wider window than purchases)
 * - both hints resolve to configured accounts, both user-owned, and distinct
 *   (same account both sides, or an unresolvable hint → no match)
 *
 * Merchant/counterparty values are intentionally IGNORED: the two sides of a
 * transfer legitimately name different counterparties.
 *
 * Pure, deterministic, order-independent — fully unit-tested.
 */
object TransferMatcher {

    const val TIME_WINDOW_MINUTES = 60L

    data class Input(
        val amountPesos: Long?,
        val timestampMillis: Long?,
        val kind: TransactionKind,
        val possibleKinds: List<TransactionKind>,
        val direction: Direction?,
        val sourceHint: doug.financetracker.domain.parser.AccountHint?,
        val destHint: doug.financetracker.domain.parser.AccountHint?
    )

    data class Match(
        val confidence: Confidence,
        val evidence: List<String>,
        val sourceAccount: Account,
        val destinationAccount: Account
    )

    sealed interface Decision {
        data class Matched(val match: Match) : Decision
        data class NotMatched(val reasons: List<String>) : Decision
    }

    fun inputOf(item: PendingItem): Input {
        val parsed = item.parsed
        return Input(
            amountPesos = parsed.amountPesos,
            timestampMillis = parsed.timestampMillis
                ?: item.sourceEvent.eventTime
                ?: item.sourceEvent.receivedAt,
            kind = parsed.transactionKind,
            possibleKinds = parsed.possibleKinds,
            direction = parsed.direction,
            sourceHint = parsed.sourceAccountHint,
            destHint = parsed.destinationAccountHint
        )
    }

    /**
     * Tries both orientations; returns the first match. Needs the configured
     * accounts so hints resolve exactly like one-tap confirm does.
     */
    fun findMatch(
        existing: List<Pair<PendingItem, Long>>,
        newItem: PendingItem,
        allAccounts: List<Account>
    ): Pair<Long, Match>? {
        val newInput = inputOf(newItem)
        for ((item, candidateId) in existing) {
            decide(inputOf(item), newInput, allAccounts)?.let { return candidateId to it }
                ?: decide(newInput, inputOf(item), allAccounts)?.let { return candidateId to it }
        }
        return null
    }

    /** Oriented decision: [out] must be the outgoing side, [inc] the incoming. */
    fun decide(out: Input, inc: Input, allAccounts: List<Account>): Match? {
        if (out.amountPesos == null || inc.amountPesos == null) return null
        if (out.amountPesos != inc.amountPesos) return null
        if (out.direction != Direction.OUTGOING || inc.direction != Direction.INCOMING) return null
        if (!outgoingCompatible(out) || !incomingCompatible(inc)) return null
        val outTime = out.timestampMillis ?: return null
        val inTime = inc.timestampMillis ?: return null
        val deltaMinutes = kotlin.math.abs(outTime - inTime) / 60_000L
        if (deltaMinutes > TIME_WINDOW_MINUTES) return null
        val source = AccountResolver.resolve(out.sourceHint, allAccounts) ?: return null
        val dest = AccountResolver.resolve(inc.destHint, allAccounts) ?: return null
        if (!source.isOwnedByUser || !dest.isOwnedByUser) return null
        if (source.id == dest.id) return null

        val evidence = mutableListOf(
            "same amount (${out.amountPesos})",
            "opposite directions (out ${out.kind} / in ${inc.kind})",
            "timestamps $deltaMinutes min apart",
            "distinct owned accounts (${source.displayLabel} → ${dest.displayLabel})"
        )
        // Tight timing raises confidence; the match itself is already gated.
        val confidence = if (deltaMinutes <= PurchaseCorrelation.TIME_WINDOW_MINUTES) {
            Confidence.HIGH
        } else {
            evidence += "wider transfer window — confirm the accounts carefully"
            Confidence.MEDIUM
        }
        return Match(confidence, evidence, source, dest)
    }

    private fun outgoingCompatible(input: Input): Boolean =
        // Strictly UNKNOWN: a confident EXPENSE is a purchase, and pairing a
        // purchase with an unrelated same-amount income would invent a
        // transfer. Transfer-out messages parse as UNKNOWN by design.
        input.kind == TransactionKind.UNKNOWN &&
            TransactionKind.TRANSFER in input.possibleKinds

    private fun incomingCompatible(input: Input): Boolean =
        input.kind == TransactionKind.INCOME ||
            (input.kind == TransactionKind.UNKNOWN &&
                TransactionKind.TRANSFER in input.possibleKinds)
}
