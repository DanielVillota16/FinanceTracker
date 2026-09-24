package doug.financetracker.ui.screens.addtransaction

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import doug.financetracker.data.repository.RoomTransactionRepository
import doug.financetracker.domain.model.Account
import doug.financetracker.domain.model.Tag
import doug.financetracker.domain.model.Transaction
import doug.financetracker.domain.model.TransactionType
import doug.financetracker.domain.parser.TransactionKind
import doug.financetracker.domain.repository.AccountRepository
import doug.financetracker.domain.repository.PendingReviewRepository
import doug.financetracker.domain.repository.TagRepository
import doug.financetracker.domain.repository.TransactionRepository
import doug.financetracker.util.combineDateAndTime
import doug.financetracker.util.hourOf
import doug.financetracker.util.minuteOf
import doug.financetracker.util.parseAmountToPesos
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AddTransactionUiState(
    val isEditMode: Boolean = false,
    val type: TransactionType = TransactionType.EXPENSE,
    val amountText: String = "",
    val amountError: String? = null,
    val dateMillis: Long = System.currentTimeMillis(),
    val hour: Int = 12,
    val minute: Int = 0,
    val description: String = "",
    val counterparty: String = "",
    val sourceAccountId: Long? = null,
    val destinationAccountId: Long? = null,
    val accountError: String? = null,
    val tagText: String = "",
    val selectedTagIds: Set<Long> = emptySet(),
    val accounts: List<Account> = emptyList(),
    val existingTags: List<Tag> = emptyList(),
    val isSaving: Boolean = false,
    val saved: Boolean = false,
    val deleted: Boolean = false,
    val formError: String? = null
)

class AddTransactionViewModel(
    private val editId: Long?,
    private val pendingId: Long?,
    private val transactions: TransactionRepository,
    private val tagRepository: TagRepository,
    private val accountRepository: AccountRepository,
    private val pendingRepo: PendingReviewRepository
) : ViewModel() {

    private val formOnly = MutableStateFlow(
        AddTransactionUiState(
            isEditMode = editId != null && editId >= 0,
            dateMillis = System.currentTimeMillis(),
            hour = hourOf(System.currentTimeMillis()),
            minute = minuteOf(System.currentTimeMillis())
        )
    )

    val state: StateFlow<AddTransactionUiState> = combine(
        formOnly,
        accountRepository.observeAccounts(),
        tagRepository.observeTags()
    ) { form, accounts, tags ->
        val now = System.currentTimeMillis()
        form.copy(
            accounts = accounts,
            existingTags = tags,
            // Default account selections once accounts load.
            sourceAccountId = form.sourceAccountId ?: accounts.firstOrNull()?.id,
            destinationAccountId = form.destinationAccountId ?: when (form.type) {
                TransactionType.TRANSFER -> accounts.getOrNull(1)?.id ?: accounts.firstOrNull()?.id
                TransactionType.INCOME -> accounts.firstOrNull()?.id
                TransactionType.EXPENSE -> form.destinationAccountId
            },
            dateMillis = if (form.dateMillis == 0L) now else form.dateMillis
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AddTransactionUiState())

    init {
        if (editId != null && editId >= 0) {
            viewModelScope.launch {
                val details = transactions.getDetails(editId) ?: return@launch
                val t = details.transaction
                formOnly.update {
                    it.copy(
                        isEditMode = true,
                        type = t.type,
                        amountText = t.amount.toString(),
                        dateMillis = t.dateTime,
                        hour = hourOf(t.dateTime),
                        minute = minuteOf(t.dateTime),
                        description = t.description,
                        counterparty = t.counterparty,
                        sourceAccountId = t.sourceAccountId,
                        destinationAccountId = t.destinationAccountId,
                        selectedTagIds = t.tagIds.toSet()
                    )
                }
            }
        } else if (pendingId != null && pendingId >= 0) {
            viewModelScope.launch {
                val item = pendingRepo.getItem(pendingId) ?: return@launch
                val parsed = item.parsed
                val accounts = accountRepository.getAll()
                val dateTime = parsed.timestampMillis
                    ?: item.sourceEvent.eventTime
                    ?: item.sourceEvent.receivedAt
                formOnly.update {
                    it.copy(
                        type = when (parsed.transactionKind) {
                            TransactionKind.EXPENSE -> TransactionType.EXPENSE
                            TransactionKind.INCOME -> TransactionType.INCOME
                            TransactionKind.TRANSFER -> TransactionType.TRANSFER
                            TransactionKind.UNKNOWN -> it.type
                        },
                        amountText = parsed.amountPesos?.toString().orEmpty(),
                        dateMillis = dateTime,
                        hour = hourOf(dateTime),
                        minute = minuteOf(dateTime),
                        counterparty = parsed.counterparty.orEmpty(),
                        sourceAccountId = resolveHint(parsed.sourceAccountHint, accounts)?.id
                            ?: it.sourceAccountId,
                        destinationAccountId = resolveHint(parsed.destinationAccountHint, accounts)?.id
                            ?: it.destinationAccountId
                    )
                }
            }
        }
    }

    private fun resolveHint(
        hint: doug.financetracker.domain.parser.AccountHint?,
        accounts: List<Account>
    ): Account? {
        if (hint == null) return null
        if (hint.isCash) {
            return accounts.firstOrNull { it.accountType == "CASH" }
                ?: accounts.firstOrNull { it.name.equals("Cash", ignoreCase = true) }
        }
        if (hint.lastDigits.isNotEmpty()) {
            accounts.firstOrNull { it.identifierSuffix == hint.lastDigits }?.let { return it }
        }
        hint.label?.takeIf { it.isNotBlank() }?.let { label ->
            accounts.firstOrNull {
                it.institution.equals(label, ignoreCase = true) ||
                    it.name.equals(label, ignoreCase = true)
            }?.let { return it }
        }
        return null
    }

    fun onTypeChange(type: TransactionType) {
        formOnly.update { it.copy(type = type, accountError = null) }
    }

    fun onAmountChange(v: String) = formOnly.update { it.copy(amountText = v, amountError = null) }
    fun onDescriptionChange(v: String) = formOnly.update { it.copy(description = v) }
    fun onCounterpartyChange(v: String) = formOnly.update { it.copy(counterparty = v) }
    fun onSourceAccountChange(id: Long?) = formOnly.update { it.copy(sourceAccountId = id, accountError = null) }
    fun onDestinationAccountChange(id: Long?) =
        formOnly.update { it.copy(destinationAccountId = id, accountError = null) }

    fun onDateChange(millis: Long) = formOnly.update { it.copy(dateMillis = millis) }
    fun onTimeChange(hour: Int, minute: Int) = formOnly.update { it.copy(hour = hour, minute = minute) }
    fun onTagTextChange(v: String) = formOnly.update { it.copy(tagText = v) }

    fun toggleTag(id: Long) {
        formOnly.update {
            val next = it.selectedTagIds.toMutableSet()
            if (!next.add(id)) next.remove(id)
            it.copy(selectedTagIds = next)
        }
    }

    fun save() {
        val s = formOnly.value
        val amount = parseAmountToPesos(s.amountText)
        if (amount == null) {
            formOnly.update { it.copy(amountError = "Enter a valid amount") }
            return
        }
        val accountError = when (s.type) {
            TransactionType.TRANSFER ->
                if (s.sourceAccountId == null || s.destinationAccountId == null)
                    "Select both accounts"
                else if (s.sourceAccountId == s.destinationAccountId)
                    "Accounts must be different"
                else null
            TransactionType.EXPENSE ->
                if (s.sourceAccountId == null) "Select an account" else null
            TransactionType.INCOME ->
                if (s.destinationAccountId == null) "Select an account" else null
        }
        if (accountError != null) {
            formOnly.update { it.copy(accountError = accountError) }
            return
        }
        formOnly.update { it.copy(isSaving = true, formError = null) }
        viewModelScope.launch {
            try {
                val typedTagNames = s.tagText.split(",", ";")
                    .map { it.trim().trimStart('#') }
                    .filter { it.isNotEmpty() }
                val typedIds = if (typedTagNames.isEmpty()) {
                    emptyList()
                } else {
                    val roomRepo = transactions as? RoomTransactionRepository
                    if (roomRepo != null) roomRepo.resolveTagNames(typedTagNames)
                    else s.selectedTagIds.toList()
                }
                val tagIds = (s.selectedTagIds + typedIds).distinct()
                val dateTime = combineDateAndTime(s.dateMillis, s.hour, s.minute)
                if (s.isEditMode && editId != null && editId >= 0) {
                    val current = transactions.getDetails(editId)?.transaction
                    val base = current ?: Transaction(
                        type = s.type, amount = amount, dateTime = dateTime
                    )
                    transactions.update(
                        base.copy(
                            type = s.type,
                            amount = amount,
                            dateTime = dateTime,
                            description = s.description.trim(),
                            counterparty = s.counterparty.trim(),
                            sourceAccountId = if (s.type == TransactionType.INCOME) null else s.sourceAccountId,
                            destinationAccountId = if (s.type == TransactionType.EXPENSE) null else s.destinationAccountId,
                            tagIds = tagIds
                        )
                    )
                } else {
                    val newId = transactions.create(
                        Transaction(
                            type = s.type,
                            amount = amount,
                            dateTime = dateTime,
                            description = s.description.trim(),
                            counterparty = s.counterparty.trim(),
                            sourceAccountId = if (s.type == TransactionType.INCOME) null else s.sourceAccountId,
                            destinationAccountId = if (s.type == TransactionType.EXPENSE) null else s.destinationAccountId,
                            tagIds = tagIds
                        )
                    )
                    // Saving from a pending item converts it: link evidence, leave queue.
                    if (pendingId != null && pendingId >= 0) {
                        pendingRepo.confirm(pendingId, newId)
                    }
                }
                formOnly.update { it.copy(isSaving = false, saved = true) }
            } catch (e: Exception) {
                formOnly.update { it.copy(isSaving = false, formError = e.message ?: "Could not save") }
            }
        }
    }

    fun delete() {
        val id = editId ?: return
        if (id < 0) return
        viewModelScope.launch {
            transactions.delete(id)
            formOnly.update { it.copy(deleted = true) }
        }
    }
}
