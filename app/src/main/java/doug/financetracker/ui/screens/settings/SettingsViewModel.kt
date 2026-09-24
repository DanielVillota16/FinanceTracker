package doug.financetracker.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import doug.financetracker.domain.model.Account
import doug.financetracker.domain.model.Tag
import doug.financetracker.domain.repository.AccountRepository
import doug.financetracker.domain.repository.TagRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SettingsUiState(
    val accounts: List<Account> = emptyList(),
    val tags: List<Tag> = emptyList()
)

class SettingsViewModel(
    private val accounts: AccountRepository,
    private val tags: TagRepository
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
}
