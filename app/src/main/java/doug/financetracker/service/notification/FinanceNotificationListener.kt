package doug.financetracker.service.notification

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import doug.financetracker.FinanceTrackerApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Forwards monitored financial notifications into the ingestion pipeline
 * (spec §25–26).
 *
 * Deliberately thin: package filter → extract title/body → [IngestSourceMessage].
 * No parsing, matching, sync, UI, or network logic here — a failure only drops
 * one event, never crashes the listener.
 */
class FinanceNotificationListener : NotificationListenerService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        val posted = sbn ?: return
        try {
            if (!MonitoredPackages.isMonitored(posted.packageName)) return
            // Ongoing/progress notifications (downloads, music, navigation) are
            // never financial events.
            if (posted.notification.flags and android.app.Notification.FLAG_ONGOING_EVENT != 0) return
            val extras = posted.notification.extras
            val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
            val bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString().orEmpty()
            val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()
            val body = listOf(title, bigText.ifEmpty { text })
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .joinToString("\n")
            if (body.isBlank()) return
            val container = (application as? FinanceTrackerApp)?.container ?: return
            scope.launch {
                try {
                    container.ingestSourceMessage(
                        sourceType = "NOTIFICATION",
                        sourceIdentifier = posted.packageName,
                        rawContent = body,
                        eventTime = posted.postTime
                    )
                } catch (_: Exception) {
                    // Drop one event rather than crash the listener.
                }
            }
        } catch (_: Exception) {
            // Never propagate out of the system callback.
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        // Removals carry no financial information.
    }
}
