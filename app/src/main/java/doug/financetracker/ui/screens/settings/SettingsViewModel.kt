package doug.financetracker.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import doug.financetracker.domain.model.Account
import doug.financetracker.domain.model.Tag
import doug.financetracker.domain.repository.AccountRepository
import doug.financetracker.domain.repository.TagRepository
import doug.financetracker.domain.usecase.IngestSourceMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SettingsUiState(
    val accounts: List<Account> = emptyList(),
    val tags: List<Tag> = emptyList()
)

class SettingsViewModel(
    private val accounts: AccountRepository,
    private val tags: TagRepository,
    private val ingest: IngestSourceMessage
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
}
