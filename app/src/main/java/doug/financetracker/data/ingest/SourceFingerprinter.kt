package doug.financetracker.data.ingest

import java.security.MessageDigest

/**
 * Stable idempotency fingerprint for source ingestion (spec §8).
 *
 * fingerprint = sha256(normalizedSourceType | normalizedIdentifier |
 *                      normalizedContent | eventTime)
 *
 * Normalization (lowercase, collapse whitespace) keeps re-deliveries of the
 * same SMS/notification mapping to one fingerprint while remaining
 * deterministic and testable. Backed by a UNIQUE DB constraint; ingestion
 * inserts with IGNORE so races cannot duplicate events.
 */
object SourceFingerprinter {

    fun fingerprint(
        sourceType: String,
        sourceIdentifier: String,
        rawContent: String,
        eventTime: Long?
    ): String {
        val normalized = listOf(
            normalize(sourceType),
            normalize(sourceIdentifier),
            normalize(rawContent),
            (eventTime ?: -1L).toString()
        ).joinToString("|")
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(normalized.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    internal fun normalize(value: String): String =
        value.lowercase().replace(Regex("""\s+"""), " ").trim()
}
