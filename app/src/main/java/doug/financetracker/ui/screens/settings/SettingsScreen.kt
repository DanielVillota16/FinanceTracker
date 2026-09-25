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
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import doug.financetracker.FinanceTrackerApp
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
            val app = context.applicationContext as FinanceTrackerApp
            SettingsViewModel(app, c.accountRepository, c.tagRepository, c.ingestSourceMessage, c.authRepository, c.syncEngine)
        }
    )
    val state by vm.state.collectAsStateWithLifecycle()
    val ingestResult by vm.ingestResult.collectAsStateWithLifecycle()
    val notificationEnabled by vm.notificationEnabled.collectAsStateWithLifecycle()
    val smsGranted by vm.smsGranted.collectAsStateWithLifecycle()
    val smsImport by vm.smsImport.collectAsStateWithLifecycle()
    val authSession by vm.authSession.collectAsStateWithLifecycle()
    val authBusy by vm.authBusy.collectAsStateWithLifecycle()
    val authError by vm.authError.collectAsStateWithLifecycle()
    val syncStatus by vm.syncStatus.collectAsStateWithLifecycle()
    var showAddAccount by remember { mutableStateOf(false) }

    val smsPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { vm.refreshSmsPermission() }

    LaunchedEffect(Unit) {
        vm.refreshNotificationStatus(context.applicationContext)
        vm.refreshSmsPermission()
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
            SmsSourcesCard(
                granted = smsGranted,
                importState = smsImport,
                onGrant = {
                    smsPermissionLauncher.launch(android.Manifest.permission.READ_SMS)
                },
                onImport = { days -> vm.runSmsImport(days) },
                onOpenPending = onOpenPending
            )
        }
        item {
            SupabaseAuthCard(
                configured = vm.authConfigured,
                sessionEmail = authSession?.email,
                signedIn = authSession != null,
                busy = authBusy,
                error = authError,
                onSignIn = vm::signIn,
                onSignUp = vm::signUp,
                onSignOut = vm::signOut,
                onClearError = vm::clearAuthError
            )
        }
        if (vm.authConfigured && authSession != null) {
            item {
                SyncCard(
                    status = syncStatus,
                    onSyncNow = vm::syncNow
                )
            }
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
private fun SmsSourcesCard(
    granted: Boolean,
    importState: SettingsViewModel.SmsImportState,
    onGrant: () -> Unit,
    onImport: (Long?) -> Unit,
    onOpenPending: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("SMS sources", style = MaterialTheme.typography.titleMedium)
            Text(
                if (granted) "SMS access granted — new bank messages are detected automatically."
                else "Grant SMS access to detect bank messages and enable import.",
                style = MaterialTheme.typography.bodySmall,
                color = if (granted) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.error
            )
            Text(
                "Only messages from recognized bank senders are processed; " +
                    "everything else is ignored before any record exists.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (!granted) {
                TextButton(onClick = onGrant) { Text("Grant SMS access") }
            }
            Text("Historical import (safe to re-run — duplicates are skipped)", style = MaterialTheme.typography.labelLarge)
            val running = importState == SettingsViewModel.SmsImportState.Running
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(
                    enabled = granted && !running,
                    onClick = { onImport(7L) }
                ) { Text("7 days") }
                TextButton(
                    enabled = granted && !running,
                    onClick = { onImport(30L) }
                ) { Text("30 days") }
                TextButton(
                    enabled = granted && !running,
                    onClick = { onImport(90L) }
                ) { Text("90 days") }
                TextButton(
                    enabled = granted && !running,
                    onClick = { onImport(null) }
                ) { Text("All") }
            }
            when (importState) {
                SettingsViewModel.SmsImportState.Idle -> Unit
                SettingsViewModel.SmsImportState.Running -> Text(
                    "Importing…",
                    style = MaterialTheme.typography.bodySmall
                )
                is SettingsViewModel.SmsImportState.Done -> {
                    val r = importState.result
                    Text(
                        if (r.error != null) r.error
                        else "Examined ${r.examined} · ${r.created} new · " +
                            "${r.duplicates} duplicates · ${r.unsupported} unsupported",
                        style = MaterialTheme.typography.bodySmall
                    )
                    TextButton(onClick = onOpenPending) { Text("View pending") }
                }
            }
        }
    }
}

@Composable
private fun SupabaseAuthCard(
    configured: Boolean,
    sessionEmail: String?,
    signedIn: Boolean,
    busy: Boolean,
    error: String?,
    onSignIn: (String, String) -> Unit,
    onSignUp: (String, String) -> Unit,
    onSignOut: () -> Unit,
    onClearError: () -> Unit
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("Remote backup (Supabase)", style = MaterialTheme.typography.titleMedium)
            if (!configured) {
                Text(
                    "Sync is not configured on this build (no Supabase URL/key). " +
                        "The app works fully offline.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                return@Column
            }
            Text(
                if (signedIn) "Signed in as ${sessionEmail ?: "user"}."
                else "Sign in to enable encrypted remote backup of transactions, accounts and tags.",
                style = MaterialTheme.typography.bodySmall,
                color = if (signedIn) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (!signedIn) {
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it; onClearError() },
                    label = { Text("Email") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it; onClearError() },
                    label = { Text("Password") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                error?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(
                        enabled = !busy && email.isNotBlank() && password.isNotBlank(),
                        onClick = { onSignIn(email, password) }
                    ) { Text("Sign in") }
                    TextButton(
                        enabled = !busy && email.isNotBlank() && password.isNotBlank(),
                        onClick = { onSignUp(email, password) }
                    ) { Text("Create account") }
                }
            } else {
                error?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
                TextButton(enabled = !busy, onClick = onSignOut) { Text("Sign out") }
            }
        }
    }
}

@Composable
private fun SyncCard(
    status: doug.financetracker.data.remote.supabase.SyncEngine.Status,
    onSyncNow: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("Backup sync", style = MaterialTheme.typography.titleMedium)
            val message = when (status) {
                doug.financetracker.data.remote.supabase.SyncEngine.Status.Idle ->
                    "Sync has not run yet on this device."
                doug.financetracker.data.remote.supabase.SyncEngine.Status.NotConfigured ->
                    "Sync is not configured on this build."
                doug.financetracker.data.remote.supabase.SyncEngine.Status.SignedOut ->
                    "Sign in to sync."
                doug.financetracker.data.remote.supabase.SyncEngine.Status.Syncing ->
                    "Syncing…"
                is doug.financetracker.data.remote.supabase.SyncEngine.Status.Success ->
                    "Last sync: ${status.pushed} pushed · ${status.pulled} pulled" +
                        if (status.errors > 0) " · ${status.errors} errors (will retry)" else " · no errors"
                is doug.financetracker.data.remote.supabase.SyncEngine.Status.Error ->
                    "Sync failed: ${status.message} — local data is untouched."
            }
            Text(
                message,
                style = MaterialTheme.typography.bodySmall,
                color = if (status is doug.financetracker.data.remote.supabase.SyncEngine.Status.Error) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
            Text(
                "Only transactions, accounts and tags sync. " +
                    "Source messages never leave this device.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            TextButton(
                enabled = status != doug.financetracker.data.remote.supabase.SyncEngine.Status.Syncing,
                onClick = onSyncNow
            ) { Text("Sync now") }
        }
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
