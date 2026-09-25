package doug.financetracker.service.sms

/**
 * Sender allowlist for SMS ingestion (spec §24).
 *
 * Only messages from recognized financial senders ever reach the pipeline —
 * personal/OTP/promo SMS from anyone else is dropped before any row exists.
 *
 * Two match modes:
 * - alphanumeric sender IDs match by case-insensitive substring
 *   ("Bancolombia", "Nequi", … — what banks use for alert sender IDs);
 * - numeric short codes match by EXACT digit equality only, so a personal
 *   number that merely contains those digits can never collide.
 *
 * Conservative by design: unknown numeric senders stay excluded until
 * verified on-device (avoids vacuuming unrelated SMS).
 */
object SupportedSmsSenders {

    private val MARKERS = listOf(
        "bancolombia",
        "nequi",
        "davivienda",
        "daviplata",
        "bbva"
    )

    /** Verified numeric short codes: code → institution. */
    private val NUMERIC_CODES = mapOf(
        "85540" to "Bancolombia" // Bancolombia alert SMS, verified on-device
    )

    fun isSupported(sender: String): Boolean {
        val normalized = sender.trim().lowercase()
        if (normalized.isEmpty()) return false
        if (MARKERS.any { it in normalized }) return true
        return normalized.filter { it.isDigit() } in NUMERIC_CODES
    }

    /** Human label for a supported numeric code, for diagnostics. */
    fun institutionFor(sender: String): String? {
        val digits = sender.trim().lowercase().filter { it.isDigit() }
        return NUMERIC_CODES[digits]
    }
}
