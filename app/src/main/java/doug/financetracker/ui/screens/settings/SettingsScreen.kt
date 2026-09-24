package doug.financetracker.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import doug.financetracker.domain.model.Account
import doug.financetracker.service.notification.MonitoredPackages
import doug.financetracker.service.notification.NotificationAccess
import doug.financetracker.ui.appContainer
import doug.financetracker.ui.vmFactory

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    onOpenPending: () -> Unit
) {
    val context = LocalContext.current
    val vm: SettingsViewModel = viewModel(
        factory = vmFactory {
            val c = appContainer(context)
            SettingsViewModel(c.accountRepository, c.tagRepository, c.ingestSourceMessage)
        }
    )
    val state by vm.state.collectAsStateWithLifecycle()
    val ingestResult by vm.ingestResult.collectAsStateWithLifecycle()
    val notificationEnabled by vm.notificationEnabled.collectAsStateWithLifecycle()
    var showAddAccount by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        vm.refreshNotificationStatus(context.applicationContext)
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text("Settings", style = MaterialTheme.typography.headlineSmall)
        }
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Accounts", style = MaterialTheme.typography.titleMedium)
                IconButton(onClick = { showAddAccount = true }) {
                    Icon(Icons.Filled.Add, contentDescription = "Add account")
                }
            }
        }
        items(state.accounts, key = { it.id }) { account ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(account.displayLabel, style = MaterialTheme.typography.titleSmall)
                        Text(
                            account.institution,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = { vm.deleteAccount(account) }) {
                        Icon(Icons.Filled.Delete, contentDescription = "Delete ${account.name}")
                    }
                }
            }
        }
        item {
            Text("Tags", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(4.dp))
            if (state.tags.isEmpty()) {
                Text(
                    "No tags yet. Tags are created from the add/edit form.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    state.tags.forEach { tag ->
                        AssistChip(
                            onClick = {},
                            label = { Text(tag.name) },
                            trailingIcon = {
                                IconButton(onClick = { vm.deleteTag(tag) }) {
                                    Icon(
                                        Icons.Filled.Delete,
                                        contentDescription = "Delete ${tag.name}"
                                    )
                                }
                            }
                        )
                    }
                }
            }
        }
        item {
            Text("About", style = MaterialTheme.typography.titleMedium)
            Text(
                "FinanceTracker · private build · offline-first. " +
                    "SMS and sync features arrive in later phases.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        item {
            NotificationSourcesCard(
                enabled = notificationEnabled,
                onRefresh = { vm.refreshNotificationStatus(context.applicationContext) },
                onOpenSettings = { NotificationAccess.openSystemSettings(context.applicationContext) }
            )
        }
        item {
            IngestTestCard(
                result = ingestResult,
                onIngest = vm::ingestTestMessage,
                onClearResult = vm::clearIngestResult,
                onOpenPending = onOpenPending
            )
            Spacer(modifier = Modifier.height(32.dp))
        }
    }

    if (showAddAccount) {
        AddAccountDialog(
            onDismiss = { showAddAccount = false },
            onConfirm = { name, institution, suffix ->
                vm.addAccount(name, institution, suffix)
                showAddAccount = false
            }
        )
    }
}

@Composable
private fun IngestTestCard(
    result: String?,
    onIngest: (String, String) -> Unit,
    onClearResult: () -> Unit,
    onOpenPending: () -> Unit
) {
    var sender by remember { mutableStateOf("Bancolombia") }
    var message by remember { mutableStateOf("") }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("Test ingestion", style = MaterialTheme.typography.titleMedium)
            Text(
                "Paste a bank SMS to run it through the real pipeline " +
                    "(same path Phase 5 SMS delivery will use).",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedTextField(
                value = sender, onValueChange = { sender = it; onClearResult() },
                label = { Text("Sender") }, singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = message, onValueChange = { message = it; onClearResult() },
                label = { Text("Message") }, minLines = 3,
                modifier = Modifier.fillMaxWidth()
            )
            result?.let {
                Text(it, style = MaterialTheme.typography.bodySmall)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { onIngest(sender, message) }) { Text("Ingest") }
                TextButton(onClick = onOpenPending) { Text("View pending") }
            }
        }
    }
}

@Composable
private fun NotificationSourcesCard(
    enabled: Boolean,
    onRefresh: () -> Unit,
    onOpenSettings: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("Notification sources", style = MaterialTheme.typography.titleMedium)
            Text(
                if (enabled) "Listener enabled — monitoring financial notifications."
                else "Listener disabled — enable notification access to detect payments.",
                style = MaterialTheme.typography.bodySmall,
                color = if (enabled) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.error
            )
            MonitoredPackages.MONITORED.forEach { source ->
                Column {
                    Text(source.label, style = MaterialTheme.typography.bodyMedium)
                    Text(
                        source.packageName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onOpenSettings) { Text("System settings") }
                TextButton(onClick = onRefresh) { Text("Refresh") }
            }
        }
    }
}

@Composable
private fun AddAccountDialog(
    onDismiss: () -> Unit,
    onConfirm: (String, String, String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var institution by remember { mutableStateOf("") }
    var suffix by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank(),
                onClick = { onConfirm(name, institution, suffix) }
            ) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        title = { Text("Add account") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name, onValueChange = { name = it },
                    label = { Text("Name") }, singleLine = true
                )
                OutlinedTextField(
                    value = institution, onValueChange = { institution = it },
                    label = { Text("Institution") }, singleLine = true
                )
                OutlinedTextField(
                    value = suffix, onValueChange = { suffix = it.filter(Char::isDigit).takeLast(4) },
                    label = { Text("Last digits (optional)") }, singleLine = true
                )
            }
        }
    )
}
