package doug.financetracker.ui.screens.transactions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import doug.financetracker.domain.model.Money
import doug.financetracker.domain.model.Tag
import doug.financetracker.domain.model.TransactionDetails
import doug.financetracker.domain.model.TransactionType
import doug.financetracker.domain.repository.TagRepository
import doug.financetracker.domain.usecase.DeleteTransaction
import doug.financetracker.domain.usecase.ObserveTransactionDetails
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

enum class TypeFilter { ALL, EXPENSE, INCOME, TRANSFER }

enum class DateFilter { ALL, TODAY, LAST_7_DAYS, LAST_30_DAYS, THIS_MONTH }

data class TransactionsUiState(
    val items: List<TransactionDetails> = emptyList(),
    val allTags: List<Tag> = emptyList(),
    val query: String = "",
    val typeFilter: TypeFilter = TypeFilter.ALL,
    val tagFilterId: Long? = null,
    val dateFilter: DateFilter = DateFilter.ALL,
    val totals: Money.Totals = Money.Totals()
)

class TransactionsViewModel(
    observeDetails: ObserveTransactionDetails,
    tagRepository: TagRepository,
    private val deleteTransaction: DeleteTransaction
) : ViewModel() {

    private val query = MutableStateFlow("")
    private val typeFilter = MutableStateFlow(TypeFilter.ALL)
    private val tagFilterId = MutableStateFlow<Long?>(null)
    private val dateFilter = MutableStateFlow(DateFilter.ALL)

    val state: StateFlow<TransactionsUiState> = combine(
        observeDetails(),
        tagRepository.observeTags(),
        query,
        typeFilter,
        tagFilterId,
        dateFilter
    ) { args ->
        @Suppress("UNCHECKED_CAST")
        val all = args[0] as List<TransactionDetails>
        val tags = args[1] as List<Tag>
        val q = args[2] as String
        val tf = args[3] as TypeFilter
        val tagId = args[4] as Long?
        val df = args[5] as DateFilter
        val filtered = all.filter { matches(it, q, tf, tagId, df) }
        TransactionsUiState(
            items = filtered,
            allTags = tags,
            query = q,
            typeFilter = tf,
            tagFilterId = tagId,
            dateFilter = df,
            totals = Money.totalsOf(filtered.map { it.transaction })
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TransactionsUiState())

    fun onQueryChange(value: String) { query.value = value }
    fun onTypeFilterChange(value: TypeFilter) { typeFilter.value = value }
    fun onTagFilterChange(value: Long?) { tagFilterId.value = value }
    fun onDateFilterChange(value: DateFilter) { dateFilter.value = value }

    fun delete(id: Long) {
        viewModelScope.launch { deleteTransaction(id) }
    }

    private fun matches(
        item: TransactionDetails,
        q: String,
        tf: TypeFilter,
        tagId: Long?,
        df: DateFilter
    ): Boolean {
        val t = item.transaction
        if (tf != TypeFilter.ALL && t.type != TransactionType.valueOf(tf.name)) return false
        if (tagId != null && tagId !in t.tagIds) return false
        if (!matchesDate(t.dateTime, df)) return false
        if (q.isNotBlank()) {
            val needle = q.trim().lowercase()
            val haystack = listOf(t.description, t.counterparty)
                .plus(item.tags.map { it.name })
                .plus(listOfNotNull(item.sourceAccount?.name, item.destinationAccount?.name))
                .joinToString(" ").lowercase()
            if (needle !in haystack) return false
        }
        return true
    }

    private fun matchesDate(millis: Long, df: DateFilter): Boolean {
        if (df == DateFilter.ALL) return true
        val zone = ZoneId.systemDefault()
        val date: LocalDate = Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()
        val today = LocalDate.now(zone)
        return when (df) {
            DateFilter.ALL -> true
            DateFilter.TODAY -> date == today
            DateFilter.LAST_7_DAYS -> !date.isBefore(today.minusDays(6))
            DateFilter.LAST_30_DAYS -> !date.isBefore(today.minusDays(29))
            DateFilter.THIS_MONTH -> date.year == today.year && date.month == today.month
        }
    }
}
