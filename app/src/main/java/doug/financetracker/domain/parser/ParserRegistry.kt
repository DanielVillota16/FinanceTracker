package doug.financetracker.domain.parser

/**
 * Dispatcher over the institution parsers. Order matters: parsers with
 * explicit sender markers (Nequi, DAVIbank) run before Bancolombia, whose
 * verb-based fallback could otherwise claim other banks' messages.
 */
object ParserRegistry {

    private val parsers: List<TransactionParser> = listOf(
        NequiParser(),
        DavibankParser(),
        BancolombiaParser()
    )

    /** First parser claiming the message, or null when unsupported. */
    fun parserFor(raw: String): TransactionParser? =
        parsers.firstOrNull { it.canHandle(raw) }

    /**
     * Best-effort interpretation of a raw message.
     * Returns null only when no parser claims the message (unsupported sender).
     */
    fun parse(raw: String): ParsedTransaction? =
        parserFor(raw)?.parse(raw)
}
