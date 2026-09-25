package doug.financetracker.service.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import doug.financetracker.FinanceTrackerApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Forwards incoming financial SMS into the ingestion pipeline (spec §23).
 *
 * Deliberately thin: action check → sender gate → handoff to
 * [IngestSourceMessage] via `goAsync`. No parsing, matching, sync, UI, or
 * network here; a failure only drops one SMS, never crashes delivery.
 */
class SmsReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
        val messages = try {
            Telephony.Sms.Intents.getMessagesFromIntent(intent)
        } catch (_: Exception) {
            return
        } ?: return
        if (messages.isEmpty()) return
        val sender = messages.firstNotNullOfOrNull { it.originatingAddress }?.trim().orEmpty()
        val body = messages.mapNotNull { it.messageBody }.joinToString("")
        if (sender.isEmpty() || body.isBlank()) return
        if (!SupportedSmsSenders.isSupported(sender)) return
        val eventTime = messages.firstOrNull()?.timestampMillis
            ?: System.currentTimeMillis()

        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val container = (context.applicationContext as? FinanceTrackerApp)?.container
                container?.ingestSourceMessage(
                    sourceType = "SMS",
                    sourceIdentifier = sender,
                    rawContent = body,
                    eventTime = eventTime
                )
            } catch (_: Exception) {
                // Drop one SMS rather than crash delivery.
            } finally {
                pending.finish()
            }
        }
    }
}
