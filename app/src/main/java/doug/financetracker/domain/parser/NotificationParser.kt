package doug.financetracker.domain.parser

/**
 * Parser for one monitored notification source (spec §25).
 * Selection is package-based ([matchesPackage]); [canHandle]/[parse] work on
 * the notification's human-readable text (title + body).
 */
interface NotificationParser : TransactionParser {
    fun matchesPackage(packageName: String): Boolean
}
