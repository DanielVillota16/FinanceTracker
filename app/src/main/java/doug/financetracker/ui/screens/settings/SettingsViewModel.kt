package doug.financetracker.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import doug.financetracker.domain.model.Account
import doug.financetracker.domain.model.Tag
import android.Manifest
import android.app.Application
import android.content.Context
import android.content.pm.PackageManager
import doug.financetracker.domain.repository.AccountRepository
import doug.financetracker.domain.repository.TagRepository
import doug.financetracker.domain.usecase.IngestSourceMessage
import doug.financetracker.service.notification.NotificationAccess
import doug.financetracker.service.sms.SmsHistoryImporter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SettingsUiState(
    val accounts: List<Account> = emptyList(),
    val tags: List<Tag> = emptyList()
)

class SettingsViewModel(
    private val app: Application,
    private val accounts: AccountRepository,
    private val tags: TagRepository,
    private val ingest: IngestSourceMessage,
    private val auth: doug.financetracker.data.remote.supabase.AuthRepository,
    private val sync: doug.financetracker.data.remote.supabase.SyncEngine,
    private val senders: doug.financetracker.data.local.preferences.SmsSenderSettings
) : ViewModel() {

    val state: StateFlow<SettingsUiState> = combine(
        accounts.observeAccounts(),
        tags.observeTags()
    ) { accs, tgs -> SettingsUiState(accs, tgs) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    fun addAccount(name: String, institution: String, suffix: String) {
        val cleanName = name.trim()
        if (cleanName.isEmpty()) return
        viewModelScope.launch {
            accounts.create(
                Account(
                    name = cleanName,
                    institution = institution.trim().ifEmpty { cleanName },
                    identifierSuffix = suffix.trim().takeLast(4)
                )
            )
        }
    }

    fun deleteAccount(account: Account) {
        viewModelScope.launch { accounts.delete(account) }
    }

    fun deleteTag(tag: Tag) {
        viewModelScope.launch { tags.delete(tag) }
    }

    private val _ingestResult = MutableStateFlow<String?>(null)
    val ingestResult: StateFlow<String?> = _ingestResult.asStateFlow()

    /** Dev/test hook until Phase 5 wires real SMS delivery into the same pipeline. */
    fun ingestTestMessage(sender: String, message: String) {
        val cleanSender = sender.trim()
        val cleanMessage = message.trim()
        if (cleanSender.isEmpty() || cleanMessage.isEmpty()) {
            _ingestResult.value = "Enter a sender and a message."
            return
        }
        viewModelScope.launch {
            _ingestResult.value = try {
                when (val r = ingest("SMS", cleanSender, cleanMessage)) {
                    is IngestSourceMessage.Result.Created ->
                        "Queued for review (#${r.pendingId})."
                    is IngestSourceMessage.Result.Duplicate ->
                        "Duplicate — already ingested (no new item)."
                    is IngestSourceMessage.Result.Unsupported ->
                        "Sender not recognized; stored as unsupported."
                }
            } catch (e: Exception) {
                "Ingest failed: ${e.message}"
            }
        }
    }

    fun clearIngestResult() {
        _ingestResult.value = null
    }

    private val _notificationEnabled = MutableStateFlow(false)
    val notificationEnabled: StateFlow<Boolean> = _notificationEnabled.asStateFlow()

    fun refreshNotificationStatus(context: Context) {
        _notificationEnabled.value = NotificationAccess.isListenerEnabled(context)
    }

    // SMS sources (Phase 5) -------------------------------------------------

    private val _smsGranted = MutableStateFlow(false)
    val smsGranted: StateFlow<Boolean> = _smsGranted.asStateFlow()

    fun refreshSmsPermission() {
        _smsGranted.value = app.checkSelfPermission(Manifest.permission.READ_SMS) ==
            PackageManager.PERMISSION_GRANTED
    }

    sealed interface SmsImportState {
        data object Idle : SmsImportState
        data object Running : SmsImportState
        data class Done(val result: SmsHistoryImporter.Result) : SmsImportState
    }

    private val _smsImport = MutableStateFlow<SmsImportState>(SmsImportState.Idle)
    val smsImport: StateFlow<SmsImportState> = _smsImport.asStateFlow()

    /** Historical import over the same pipeline; safe to re-run (idempotent). */
    fun runSmsImport(daysBack: Long?) {
        if (_smsImport.value == SmsImportState.Running) return
        viewModelScope.launch {
            _smsImport.value = SmsImportState.Running
            val result = try {
                val extras = senders.observeExtraSenders().first()
                SmsHistoryImporter.importFromInbox(app, ingest, daysBack, extras)
            } catch (e: Exception) {
                SmsHistoryImporter.Result(error = e.message ?: "Import failed.")
            }
            _smsImport.value = SmsImportState.Done(result)
            refreshSmsPermission()
        }
    }

    fun clearSmsImport() {
        _smsImport.value = SmsImportState.Idle
    }

    // Extra SMS senders (user-managed allowlist additions) --------------------

    val extraSenders: StateFlow<Set<String>> = senders.observeExtraSenders()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    fun addExtraSender(value: String) {
        viewModelScope.launch { senders.addSender(value) }
    }

    fun removeExtraSender(value: String) {
        viewModelScope.launch { senders.removeSender(value) }
    }

    // Supabase auth (Phase 7) ------------------------------------------------

    val authConfigured: Boolean get() = auth.isConfigured

    val authSession: StateFlow<doug.financetracker.data.remote.supabase.AuthRepository.Session?> =
        auth.observeSession()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _authError = MutableStateFlow<String?>(null)
    val authError: StateFlow<String?> = _authError.asStateFlow()

    private val _authBusy = MutableStateFlow(false)
    val authBusy: StateFlow<Boolean> = _authBusy.asStateFlow()

    fun signIn(email: String, password: String) = authAction {
        auth.signIn(email, password)
    }

    fun signUp(email: String, password: String) = authAction {
        auth.signUp(email, password)
    }

    fun signOut() {
        viewModelScope.launch {
            try {
                auth.signOut()
                _authError.value = null
            } catch (e: Exception) {
                _authError.value = e.message ?: "Sign-out failed."
            }
        }
    }

    fun clearAuthError() {
        _authError.value = null
    }

    private fun authAction(block: suspend () -> Unit) {
        if (_authBusy.value) return
        viewModelScope.launch {
            _authBusy.value = true
            _authError.value = try {
                block()
                null
            } catch (e: Exception) {
                e.message ?: "Authentication failed."
            }
            _authBusy.value = false
        }
    }

    // Sync status (Phase 7) -------------------------------------------------

    val syncStatus: StateFlow<doug.financetracker.data.remote.supabase.SyncEngine.Status> =
        sync.status

    fun syncNow() {
        viewModelScope.launch { sync.syncNow() }
    }
}
