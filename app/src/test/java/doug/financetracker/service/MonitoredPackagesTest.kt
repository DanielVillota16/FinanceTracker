package doug.financetracker.service

import doug.financetracker.service.notification.MonitoredPackages
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MonitoredPackagesTest {

    @Test
    fun `canonical packages are monitored`() {
        assertTrue(MonitoredPackages.isMonitored("com.google.android.apps.walletnfcrel"))
        // Real BBVA Colombia package (Play Store id=co.com.bbva.mb).
        assertTrue(MonitoredPackages.isMonitored("co.com.bbva.mb"))
    }

    @Test
    fun `recognized variants are monitored`() {
        assertTrue(MonitoredPackages.isMonitored("com.bbva.bbvacontigo"))
    }

    @Test
    fun `unrelated packages are ignored`() {
        assertFalse(MonitoredPackages.isMonitored("com.whatsapp"))
        assertFalse(MonitoredPackages.isMonitored("com.google.android.gm"))
        assertFalse(MonitoredPackages.isMonitored("com.android.systemui"))
    }
}
