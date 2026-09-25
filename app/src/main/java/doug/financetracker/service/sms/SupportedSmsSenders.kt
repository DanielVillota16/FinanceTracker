package doug.financetracker.service.sms

/**
 * Sender allowlist for SMS ingestion (spec §24).
 *
 * Only messages from recognized financial senders ever reach the pipeline —
 * personal/OTP/promo SMS from anyone else is dropped before any row exists.
 * Matching is a case-insensitive substring on the sender id.
 *
 * Conservative by design: numeric short codes are NOT allowlisted until
 * verified on-device (avoids vacuuming unrelated SMS). Alphanumeric sender
 * ids ("Bancolombia", "Nequi", …) are what Colombian banks use for alerts.
 */
object SupportedSmsSenders {

    private val MARKERS = listOf(
        "bancolombia",
        "nequi",
        "davivienda",
        "daviplata",
        "bbva"
    )

    fun isSupported(sender: String): Boolean {
        val normalized = sender.trim().lowercase()
        if (normalized.isEmpty()) return false
        return MARKERS.any { it in normalized }
    }
}
