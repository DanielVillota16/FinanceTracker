package doug.financetracker.ui.screens.pending

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import doug.financetracker.domain.model.Money
import doug.financetracker.domain.model.PendingItem
import doug.financetracker.domain.parser.AccountHint
import doug.financetracker.domain.parser.Confidence
import doug.financetracker.domain.parser.TransactionKind
import doug.financetracker.ui.appContainer
import doug.financetracker.ui.vmFactory
import doug.financetracker.util.formatDateTime
import kotlinx.coroutines.flow.collectLatest

@Composable
fun PendingScreen(
    onEdit: (Long) -> Unit,
    snackbar: SnackbarHostState
) {
    val context = LocalContext.current
    val vm: PendingViewModel = viewModel(
        factory = vmFactory {
            val c = appContainer(context)
            PendingViewModel(c.pendingReviewRepository, c.confirmPendingItem)
        }
    )
    val items by vm.items.collectAsStateWithLifecycle()
    var dismissTarget by remember { mutableStateOf<Long?>(null) }

    LaunchedEffect(vm) {
        vm.events.collectLatest { event ->
            when (event) {
                is PendingViewModel.Event.OpenEdit -> onEdit(event.pendingId)
                is PendingViewModel.Event.Error ->
                    snackbar.showSnackbar(event.message)
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("Pending Review", style = MaterialTheme.typography.headlineSmall)
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            "${items.size} item(s) awaiting confirmation",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(12.dp))
        if (items.isEmpty()) {
            Text(
                "Nothing to review. New financial messages will appear here.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            LazyColumn(
                contentPadding = PaddingValues(bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(items, key = { it.id }) { item ->
                    PendingCard(
                        item = item,
                        onConfirm = { vm.confirmItem(item.id) },
                        onEdit = { onEdit(item.id) },
                        onDismiss = { dismissTarget = item.id }
                    )
                }
            }
        }
    }

    dismissTarget?.let { id ->
        AlertDialog(
            onDismissRequest = { dismissTarget = null },
            confirmButton = {
                TextButton(onClick = {
                    vm.dismissItem(id)
                    dismissTarget = null
                }) { Text("Dismiss") }
            },
            dismissButton = {
                TextButton(onClick = { dismissTarget = null }) { Text("Cancel") }
            },
            title = { Text("Dismiss this detection?") },
            text = { Text("The source evidence is kept; it just leaves the review queue.") }
        )
    }
}

@Composable
private fun PendingCard(
    item: PendingItem,
    onConfirm: () -> Unit,
    onEdit: () -> Unit,
    onDismiss: () -> Unit
) {
    val p = item.parsed
    var showSource by remember { mutableStateOf(false) }

    val title = when (p.transactionKind) {
        TransactionKind.EXPENSE -> "Expense"
        TransactionKind.INCOME -> "Income"
        TransactionKind.TRANSFER -> "Transfer"
        TransactionKind.UNKNOWN ->
            "Possible " + p.possibleKinds.joinToString(" / ") {
                it.name.lowercase().replaceFirstChar { c -> c.uppercase() }
            }
    }
    val amountText = p.amountPesos?.let { Money.formatCop(it) } ?: "Amount unknown"

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(title, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold))
                Text(amountText, style = MaterialTheme.typography.titleMedium)
            }
            Spacer(modifier = Modifier.height(4.dp))
            if (!p.counterparty.isNullOrBlank()) {
                Text(p.counterparty, style = MaterialTheme.typography.bodyMedium)
            }
            accountLine(p.sourceAccountHint, p.destinationAccountHint)?.let {
                Text(it, style = MaterialTheme.typography.bodySmall)
            }
            Text(
                p.timestampMillis?.let { formatDateTime(it) } ?: "Date unknown",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (!p.reference.isNullOrBlank()) {
                Text(
                    "Ref: ${p.reference}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                AssistChip(onClick = {}, label = { Text(p.institution) })
                AssistChip(
                    onClick = {},
                    label = {
                        Text(
                            when (p.confidence) {
                                Confidence.HIGH -> "High confidence"
                                Confidence.MEDIUM -> "Medium confidence"
                                Confidence.LOW -> "Low confidence"
                            }
                        )
                    }
                )
            }
            p.warnings.forEach { warning ->
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    warning,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
            TextButton(onClick = { showSource = !showSource }) {
                Text(if (showSource) "Hide source" else "Show source")
            }
            if (showSource) {
                Text(
                    "${item.sourceEvent.sourceType} · ${item.sourceEvent.sourceIdentifier}",
                    style = MaterialTheme.typography.labelMedium
                )
                Text(
                    item.sourceEvent.rawContent,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            // Editable description/tags live in the edit form (spec §16).
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onConfirm) { Text("Confirm") }
                OutlinedButton(onClick = onEdit) { Text("Edit") }
                OutlinedButton(onClick = onDismiss) { Text("Dismiss") }
            }
        }
    }
}

private fun accountLine(source: AccountHint?, dest: AccountHint?): String? {
    fun label(h: AccountHint): String = when {
        h.isCash -> "Cash"
        h.label?.isNotBlank() == true && h.lastDigits.isEmpty() -> h.label
        h.lastDigits.isNotEmpty() -> "****${h.lastDigits}"
        else -> "?"
    }
    return when {
        source != null && dest != null -> "${label(source)} → ${label(dest)}"
        source != null -> label(source)
        dest != null -> label(dest)
        else -> null
    }
}
