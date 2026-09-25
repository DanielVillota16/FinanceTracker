package doug.financetracker.data.local.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.smsSendersStore: DataStore<Preferences> by preferencesDataStore(
    name = "sms_senders"
)

/**
 * User-managed additions to the SMS sender allowlist.
 *
 * The hardcoded verified list in [SupportedSmsSenders] stays the seed of
 * trust; entries here are the user's own additions (e.g. a newly discovered
 * bank short code), evaluated with the same fail-closed semantics. Stored
 * locally in DataStore — never synced, never leaves the device.
 */
class SmsSenderSettings(
    private val store: DataStore<Preferences>
) {
    companion object {
        private val EXTRA_SENDERS = stringSetPreferencesKey("extra_sms_senders")

        fun create(context: Context): SmsSenderSettings =
            SmsSenderSettings(context.applicationContext.smsSendersStore)
    }

    fun observeExtraSenders(): Flow<Set<String>> =
        store.data.map { it[EXTRA_SENDERS].orEmpty() }

    /** Returns false when the value is blank (nothing stored). */
    suspend fun addSender(sender: String): Boolean {
        val clean = sender.trim()
        if (clean.isEmpty()) return false
        store.edit { it[EXTRA_SENDERS] = it[EXTRA_SENDERS].orEmpty() + clean }
        return true
    }

    suspend fun removeSender(sender: String) {
        store.edit { it[EXTRA_SENDERS] = it[EXTRA_SENDERS].orEmpty() - sender }
    }
}
