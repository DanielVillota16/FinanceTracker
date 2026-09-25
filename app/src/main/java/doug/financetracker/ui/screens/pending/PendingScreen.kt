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
import doug.financetracker.domain.model.PendingCandidate
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
    val candidates by vm.candidates.collectAsStateWithLifecycle()
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
            "${candidates.size} candidate(s) awaiting confirmation",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(12.dp))
        if (candidates.isEmpty()) {
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
                items(candidates, key = { it.id }) { candidate ->
                    PendingCard(
                        candidate = candidate,
                        onConfirm = { vm.confirmCandidate(candidate) },
                        onEdit = { onEdit(candidate.primary.id) },
                        onDismiss = { dismissTarget = candidate.id }
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
                    vm.dismissCandidate(id)
                    dismissTarget = null
                }) { Text("Dismiss") }
            },
            dismissButton = {
                TextButton(onClick = { dismissTarget = null }) { Text("Cancel") }
            },
            title = { Text("Dismiss this candidate?") },
            text = { Text("All source evidence is kept; it just leaves the review queue.") }
        )
    }
}

@Composable
private fun PendingCard(
    candidate: PendingCandidate,
    onConfirm: () -> Unit,
    onEdit: () -> Unit,
    onDismiss: () -> Unit
) {
    val primary = candidate.primary
    val p = primary.parsed
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
    val extraCounterparties = candidate.counterparties.drop(1)

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
            extraCounterparties.forEach { cp ->
                Text(
                    "Also reported as: $cp",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
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
                if (candidate.isCorrelated) {
                    AssistChip(
                        onClick = {},
                        label = { Text("${candidate.members.size} sources · correlated") }
                    )
                }
                AssistChip(
                    onClick = {},
                    label = {
                        Text(
                            when (candidate.headlineConfidence) {
                                Confidence.HIGH -> "High confidence"
                                Confidence.MEDIUM -> "Medium confidence"
                                Confidence.LOW -> "Low confidence"
                            }
                        )
                    }
                )
            }
            candidate.members.flatMap { it.parsed.warnings }.distinct().forEach { warning ->
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    warning,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
            TextButton(onClick = { showSource = !showSource }) {
                Text(if (showSource) "Hide sources" else "Show sources (${candidate.members.size})")
            }
            if (showSource) {
                candidate.members.forEach { member ->
                    Text(
                        "${member.sourceEvent.sourceType} · ${member.sourceEvent.sourceIdentifier} · ${member.parsed.institution}",
                        style = MaterialTheme.typography.labelMedium
                    )
                    Text(
                        member.sourceEvent.rawContent,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                }
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
