package doug.financetracker.domain.parser

/**
 * Package-based dispatcher for notification sources (spec §25).
 * Kept separate from [ParserRegistry] so SMS verb patterns can never claim a
 * notification and notification text patterns can never claim an SMS.
 */
object NotificationParserRegistry {

    private val parsers: List<NotificationParser> = listOf(
        GoogleWalletParser(),
        BbvaParser()
    )

    fun parserFor(packageName: String): NotificationParser? =
        parsers.firstOrNull { it.matchesPackage(packageName) }

    /** Best-effort interpretation; null when the package is not monitored. */
    fun parse(packageName: String, text: String): ParsedTransaction? =
        parserFor(packageName)?.parse(text)

    fun monitoredInstitutions(): List<String> = parsers.map { it.institution }
}
