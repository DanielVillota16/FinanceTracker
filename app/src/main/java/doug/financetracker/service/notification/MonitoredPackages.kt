package doug.financetracker.service.notification

import doug.financetracker.domain.parser.NotificationParserRegistry

/**
 * Package filter for the notification listener (spec §25).
 * Pure and unit-tested; the service itself stays a thin forwarder.
 */
object MonitoredPackages {

    data class Source(val packageName: String, val label: String)

    /** Canonical packages shown in Settings; variants are matched via parsers. */
    val MONITORED = listOf(
        Source("com.google.android.apps.walletnfcrel", "Google Wallet"),
        Source("co.com.bbva.mb", "BBVA Colombia")
    )

    fun isMonitored(packageName: String): Boolean {
        if (MONITORED.any { it.packageName.equals(packageName, ignoreCase = true) }) return true
        // Accept recognized package variants (e.g. regional BBVA apps).
        return NotificationParserRegistry.parserFor(packageName) != null
    }
}
