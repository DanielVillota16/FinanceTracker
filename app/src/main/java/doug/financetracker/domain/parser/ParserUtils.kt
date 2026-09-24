package doug.financetracker.domain.parser

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Shared deterministic helpers for the institution parsers. Internal — not public API. */
internal object ParserUtils {

    private val ZONE: ZoneId = ZoneId.systemDefault()

    private val DATE_TIME_FORMATS = listOf(
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"),
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
        DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"),
        DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm"),
        DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss")
    )
    private val DATE_FORMATS = listOf(
        DateTimeFormatter.ofPattern("yyyy-MM-dd"),
        DateTimeFormatter.ofPattern("dd/MM/yyyy"),
        DateTimeFormatter.ofPattern("dd-MM-yyyy")
    )

    // Matches "2026-05-22 08:30", "21/09/2026 08:27", "21/09/2026 a las 08:27",
    // "21-09-2026 08:27", with optional seconds.
    private val DATE_TIME_RX =
        Regex(
            """(\d{4}-\d{2}-\d{2}|\d{2}[/-]\d{2}[/-]\d{4})(?:\s+a\s+las)?\s+(\d{2}:\d{2}(?::\d{2})?)""",
            RegexOption.IGNORE_CASE
        )
    private val DATE_RX =
        Regex("""(\d{4}-\d{2}-\d{2}|\d{2}[/-]\d{2}[/-]\d{4})""")

    /** Masked account suffix like "*8494" or "*0757". */
    private val ACCOUNT_SUFFIX_RX = Regex("""\*(\d{3,6})""")

    /** First parseable timestamp in the message, or null. */
    fun extractTimestamp(raw: String, warnings: MutableList<String>): Long? {
        val dateTimeMatch = DATE_TIME_RX.find(raw)
        if (dateTimeMatch != null) {
            val text = "${dateTimeMatch.groupValues[1]} ${dateTimeMatch.groupValues[2]}"
            for (fmt in DATE_TIME_FORMATS) {
                try {
                    return LocalDateTime.parse(text, fmt).atZone(ZONE)
                        .toInstant().toEpochMilli()
                } catch (_: Exception) {
                    continue
                }
            }
            // Matched the shape but no format parsed — fall through to date-only.
        }
        val dateMatch = DATE_RX.find(raw)
        if (dateMatch != null) {
            val text = dateMatch.value.trim()
            for (fmt in DATE_FORMATS) {
                try {
                    return LocalDate.parse(text, fmt).atStartOfDay(ZONE)
                        .toInstant().toEpochMilli().also {
                            warnings += "Message has a date but no time; using start of day."
                        }
                } catch (_: Exception) {
                    continue
                }
            }
        }
        warnings += "No parseable date/time in message; ingestion time will be used."
        return null
    }

    fun extractAccountHint(raw: String): AccountHint? {
        val match = ACCOUNT_SUFFIX_RX.find(raw) ?: return null
        return AccountHint(lastDigits = match.groupValues[1])
    }

    fun extractAllAccountHints(raw: String): List<AccountHint> =
        ACCOUNT_SUFFIX_RX.findAll(raw)
            .map { AccountHint(lastDigits = it.groupValues[1]) }
            .toList()

    fun cleanCounterparty(raw: String?): String? {
        if (raw == null) return null
        val clean = raw.trim().replace(Regex("""\s+"""), " ")
            .trimEnd('.', ',', ';', ':')
            .trim()
        return clean.ifEmpty { null }
    }

    /** First "$"-style amount candidate near the match, normalized to pesos. */
    fun extractAmount(around: String): Long? {
        val match = Regex("""\$?\s*([\d.,]+)""").find(around) ?: return null
        return CopAmountParser.parse(match.groupValues[1])
    }
}
