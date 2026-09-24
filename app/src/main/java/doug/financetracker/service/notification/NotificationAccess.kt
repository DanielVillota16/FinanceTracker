package doug.financetracker.service.notification

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings

/**
 * System notification-access status for [FinanceNotificationListener].
 * Android framework calls only — no unit tests (covered on-device).
 */
object NotificationAccess {

    fun isListenerEnabled(context: Context): Boolean {
        val flat = Settings.Secure.getString(
            context.contentResolver, "enabled_notification_listeners"
        ) ?: return false
        val me = ComponentName(context, FinanceNotificationListener::class.java)
            .flattenToString()
        return flat.split(":").any { it.equals(me, ignoreCase = true) }
    }

    fun openSystemSettings(context: Context) {
        val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }
}
