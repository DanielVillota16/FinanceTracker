package doug.financetracker.domain.model

/** Raw evidence received from an external source. Never deleted after confirmation. */
data class SourceEvent(
    val id: Long = 0L,
    /** SMS | NOTIFICATION */
    val sourceType: String,
    /** Sender address / notification package, e.g. "Bancolombia", "com.google.android.apps.walletnfcrel" */
    val sourceIdentifier: String,
    /** Epoch millis when the app received it. */
    val receivedAt: Long = System.currentTimeMillis(),
    /** Epoch millis of the underlying event, when known. */
    val eventTime: Long? = null,
    val rawContent: String,
    val fingerprint: String,
    val parsedStatus: ParsedStatus = ParsedStatus.RECEIVED
)

enum class ParsedStatus {
    RECEIVED,
    PARSED,
    UNSUPPORTED
}
