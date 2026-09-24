package doug.financetracker.domain.parser

/** Direction of money movement relative to the user. */
enum class Direction {
    INCOMING,
    OUTGOING
}

/**
 * Interpreted kind of movement. UNKNOWN means the parser recognized a
 * financial message but cannot safely classify it — it must go to
 * Pending Review with [ParsedTransaction.possibleKinds] as hints.
 */
enum class TransactionKind {
    EXPENSE,
    INCOME,
    TRANSFER,
    UNKNOWN
}

enum class Confidence {
    HIGH,
    MEDIUM,
    LOW
}

/** Partial account identification extracted from a message (e.g. "*8494"). */
data class AccountHint(
    val lastDigits: String,
    val label: String? = null,
    val isCash: Boolean = false
) {
    companion object {
        val CASH = AccountHint(lastDigits = "", label = "Cash", isCash = true)
    }
}

/**
 * Structured interpretation of a raw financial message.
 * Never written directly as an official transaction — always reviewed first.
 */
data class ParsedTransaction(
    val amountPesos: Long?,
    val direction: Direction?,
    val transactionKind: TransactionKind,
    val possibleKinds: List<TransactionKind> = listOf(transactionKind),
    val institution: String,
    val sourceAccountHint: AccountHint?,
    val destinationAccountHint: AccountHint?,
    val counterparty: String?,
    /** Epoch millis when the movement happened, null when the message carries no date. */
    val timestampMillis: Long?,
    /** Extra detail: BRE-B llave, ATM id, bill reference, etc. */
    val reference: String?,
    val confidence: Confidence,
    val warnings: List<String> = emptyList()
)

/**
 * Deterministic rule-based parser for one institution's messages.
 * No network, no ML — pure string rules so behavior is testable offline.
 *
 * Contract:
 * - [canHandle]: cheap check whether this parser owns the message
 *   (sender marker / institution keywords). Returns false for other
 *   institutions' messages.
 * - [parse]: best-effort interpretation. Never throws on malformed input;
 *   instead returns a LOW-confidence result with warnings, or null only
 *   when [canHandle] is false.
 */
interface TransactionParser {
    val institution: String
    fun canHandle(raw: String): Boolean
    fun parse(raw: String): ParsedTransaction?
}
