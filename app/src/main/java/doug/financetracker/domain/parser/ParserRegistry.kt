package doug.financetracker.domain.parser

/**
 * Dispatcher over the institution SMS parsers.
 *
 * Two-stage routing:
 * 1. Sender-aware (primary): the SMS sender id is ground truth — a message
 *    from 85540/Bancolombia belongs to Bancolombia even when its BODY names
 *    another bank ("...a Banco Davivienda..."). Text-only matching can never
 *    be trusted for that case, so the owning parser interprets it directly
 *    (returning LOW + warnings when the shape itself is unknown).
 * 2. Text-only fallback: for unknown senders (e.g. user-added extras), the
 *    first parser claiming the text wins, as before.
 *
 * Order in the fallback matters: parsers with explicit sender markers
 * (Nequi, DAVIbank) run before Bancolombia, whose verb-based fallback could
 * otherwise claim other banks' messages.
 */
object ParserRegistry {

    private val parsers: List<TransactionParser> = listOf(
        NequiParser(),
        DavibankParser(),
        BancolombiaParser()
    )

    /**
     * Owning SMS parser for a sender id, or null when the sender is unknown
     * (or has no SMS parser, e.g. BBVA — notification-only for now).
     */
    fun parserForSender(sender: String): TransactionParser? {
        val normalized = sender.trim().lowercase()
        if (normalized.isEmpty()) return null
        if ("bancolombia" in normalized) return BancolombiaParser()
        // Numeric short codes match by exact digit equality only.
        if (normalized.filter { it.isDigit() } == "85540" && normalized.all { it.isDigit() || it.isWhitespace() }) {
            return BancolombiaParser()
        }
        if ("nequi" in normalized) return NequiParser()
        if ("davi" in normalized) return DavibankParser()
        return null
    }

    /** First parser claiming the message, or null when unsupported. */
    fun parserFor(raw: String): TransactionParser? {
        // Explicit attribution wins: the bank named FIRST in the text owns it
        // ("Bancolombia: ... Davivienda ..." is Bancolombia's message about a
        // Davivienda destination, not Davivienda's). Falls back to verb shapes
        // when no marker is present.
        val lower = raw.lowercase()
        val attributed = listOf(
            "bancolombia" to BancolombiaParser(),
            "nequi" to NequiParser(),
            "davibank" to DavibankParser(),
            "davivienda" to DavibankParser(),
            "daviplata" to DavibankParser()
        ).mapNotNull { (marker, parser) ->
            val index = lower.indexOf(marker)
            if (index >= 0) index to parser else null
        }.sortedBy { it.first }
        for ((_, parser) in attributed) {
            if (parser.canHandle(raw)) return parser
        }
        return parsers.firstOrNull { it.canHandle(raw) }
    }

    /**
     * Best-effort interpretation of a raw message.
     * Returns null only when no parser claims the message (unsupported sender).
     */
    fun parse(raw: String, sender: String? = null): ParsedTransaction? {
        if (sender != null) {
            parserForSender(sender)?.let { return it.parse(raw) }
        }
        return parserFor(raw)?.parse(raw)
    }
}
