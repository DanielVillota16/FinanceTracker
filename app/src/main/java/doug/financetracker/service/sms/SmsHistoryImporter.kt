package doug.financetracker.service.sms

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.Telephony
import doug.financetracker.domain.usecase.IngestSourceMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * One-shot import of historical financial SMS (spec §24).
 *
 * Uses the exact same pipeline as live delivery ([IngestSourceMessage]), so
 * re-running an import is idempotent: already-seen messages come back as
 * duplicates, never as new rows. Only allowlisted senders are read; the
 * inbox is otherwise untouched (no unrelated SMS leaves the device).
 */
object SmsHistoryImporter {

    data class SmsRow(val sender: String, val body: String, val dateMillis: Long)

    data class Result(
        val examined: Int = 0,
        val created: Int = 0,
        val duplicates: Int = 0,
        val unsupported: Int = 0,
        val skippedSenders: Int = 0,
        val skippedBlank: Int = 0,
        val error: String? = null
    )

    suspend fun importFromInbox(
        context: Context,
        ingest: IngestSourceMessage,
        daysBack: Long?,
        now: Long = System.currentTimeMillis()
    ): Result = withContext(Dispatchers.IO) {
        if (context.checkSelfPermission(Manifest.permission.READ_SMS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return@withContext Result(error = "SMS permission not granted.")
        }
        val rows = readInbox(context, daysBack, now) ?: return@withContext Result(
            error = "Could not read the SMS inbox."
        )
        importRows(rows, now) { row ->
            ingest(
                sourceType = "SMS",
                sourceIdentifier = row.sender,
                rawContent = row.body,
                eventTime = row.dateMillis
            )
        }
    }

    private fun readInbox(context: Context, daysBack: Long?, now: Long): List<SmsRow>? {
        if (daysBack != null) require(daysBack > 0) { "daysBack must be positive" }
        val since = daysBack?.let { now - it * 24L * 60L * 60L * 1000L }
        return try {
            context.contentResolver.query(
                Telephony.Sms.Inbox.CONTENT_URI,
                arrayOf(Telephony.Sms.ADDRESS, Telephony.Sms.BODY, Telephony.Sms.DATE),
                if (since == null) null else "${Telephony.Sms.DATE} >= ?",
                if (since == null) null else arrayOf(since.toString()),
                "${Telephony.Sms.DATE} ASC"
            )?.use { cursor ->
                val addressIdx = cursor.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)
                val bodyIdx = cursor.getColumnIndexOrThrow(Telephony.Sms.BODY)
                val dateIdx = cursor.getColumnIndexOrThrow(Telephony.Sms.DATE)
                buildList {
                    while (cursor.moveToNext()) {
                        add(
                            SmsRow(
                                sender = cursor.getString(addressIdx).orEmpty(),
                                body = cursor.getString(bodyIdx).orEmpty(),
                                dateMillis = cursor.getLong(dateIdx)
                            )
                        )
                    }
                }
            }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Pure row loop (no Android APIs): sender gate → ingest → tally.
     * Rows are processed oldest-first so candidates enrich in arrival order
     * (correlation itself is order-independent).
     */
    suspend fun importRows(
        rows: List<SmsRow>,
        now: Long,
        ingest: suspend (SmsRow) -> IngestSourceMessage.Result
    ): Result {
        var created = 0
        var duplicates = 0
        var unsupported = 0
        var skippedSenders = 0
        var skippedBlank = 0
        for (row in rows) {
            if (row.sender.isBlank() || row.body.isBlank()) {
                skippedBlank++
                continue
            }
            if (!SupportedSmsSenders.isSupported(row.sender)) {
                skippedSenders++
                continue
            }
            when (ingest(row)) {
                is IngestSourceMessage.Result.Created -> created++
                is IngestSourceMessage.Result.Duplicate -> duplicates++
                is IngestSourceMessage.Result.Unsupported -> unsupported++
            }
        }
        return Result(
            examined = rows.size,
            created = created,
            duplicates = duplicates,
            unsupported = unsupported,
            skippedSenders = skippedSenders,
            skippedBlank = skippedBlank
        )
    }
}
