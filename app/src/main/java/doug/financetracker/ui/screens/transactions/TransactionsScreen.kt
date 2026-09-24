package doug.financetracker.ui.screens.transactions

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import doug.financetracker.domain.model.Money
import doug.financetracker.ui.appContainer
import doug.financetracker.ui.components.TransactionRow
import doug.financetracker.ui.vmFactory

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TransactionsScreen(
    onAdd: () -> Unit,
    onEdit: (Long) -> Unit
) {
    val context = LocalContext.current
    val vm: TransactionsViewModel = viewModel(
        factory = vmFactory {
            val c = appContainer(context)
            TransactionsViewModel(c.observeTransactionDetails, c.tagRepository, c.deleteTransaction)
        }
    )
    val state by vm.state.collectAsStateWithLifecycle()

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(onClick = onAdd) {
                Icon(Icons.Filled.Add, contentDescription = "Add transaction")
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp)
        ) {
            Spacer(modifier = Modifier.height(8.dp))
            TotalsHeader(
                income = state.totals.totalIncome,
                expenses = state.totals.totalExpenses,
                net = state.totals.net
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = state.query,
                onValueChange = vm::onQueryChange,
                label = { Text("Search") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TypeFilter.entries.forEach { tf ->
                    FilterChip(
                        selected = state.typeFilter == tf,
                        onClick = { vm.onTypeFilterChange(tf) },
                        label = { Text(tf.name.lowercase().replaceFirstChar { it.uppercase() }) }
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DateFilter.entries.forEach { df ->
                    FilterChip(
                        selected = state.dateFilter == df,
                        onClick = { vm.onDateFilterChange(df) },
                        label = {
                            Text(
                                when (df) {
                                    DateFilter.ALL -> "All time"
                                    DateFilter.TODAY -> "Today"
                                    DateFilter.LAST_7_DAYS -> "7 days"
                                    DateFilter.LAST_30_DAYS -> "30 days"
                                    DateFilter.THIS_MONTH -> "Month"
                                }
                            )
                        }
                    )
                }
            }
            if (state.allTags.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    state.allTags.forEach { tag ->
                        FilterChip(
                            selected = state.tagFilterId == tag.id,
                            onClick = {
                                vm.onTagFilterChange(
                                    if (state.tagFilterId == tag.id) null else tag.id
                                )
                            },
                            label = { Text(tag.name) }
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            if (state.items.isEmpty()) {
                Text(
                    text = "No transactions yet. Tap + to record one.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(bottom = 88.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(state.items, key = { it.transaction.id }) { item ->
                        TransactionRow(
                            item = item,
                            onClick = { onEdit(item.transaction.id) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TotalsHeader(income: Long, expenses: Long, net: Long) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text("Income", style = MaterialTheme.typography.labelMedium)
                Text(Money.formatCop(income), style = MaterialTheme.typography.titleMedium)
            }
            Column {
                Text("Expenses", style = MaterialTheme.typography.labelMedium)
                Text(
                    Money.formatCop(expenses),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.error
                )
            }
            Column {
                Text("Net", style = MaterialTheme.typography.labelMedium)
                Text(Money.formatCop(net), style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}
