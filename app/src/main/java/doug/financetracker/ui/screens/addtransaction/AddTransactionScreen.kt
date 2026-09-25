package doug.financetracker.ui.screens.addtransaction

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import doug.financetracker.domain.model.TransactionType
import doug.financetracker.ui.appContainer
import doug.financetracker.ui.vmFactory
import doug.financetracker.util.formatFormDate
import doug.financetracker.util.formatTime

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AddTransactionScreen(
    editId: Long?,
    pendingId: Long?,
    onDone: () -> Unit
) {
    val context = LocalContext.current
    val vm: AddTransactionViewModel = viewModel(
        key = "add_${editId ?: -1}_${pendingId ?: -1}",
        factory = vmFactory {
            val c = appContainer(context)
            AddTransactionViewModel(
                editId, pendingId, c.transactionRepository, c.tagRepository,
                c.accountRepository, c.pendingReviewRepository
            )
        }
    )
    val s by vm.state.collectAsStateWithLifecycle()
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
    val nextAction = androidx.compose.foundation.text.KeyboardActions(
        onNext = { focusManager.moveFocus(androidx.compose.ui.focus.FocusDirection.Down) }
    )

    LaunchedEffect(s.saved, s.deleted) {
        if (s.saved || s.deleted) onDone()
    }

    Column(
        modifier = Modifier.fillMaxSize()
            .verticalScroll(rememberScrollState())
            // Keep the focused field above the soft keyboard: resize alone
            // shrinks the window but never scrolls the focused field into view.
            .imePadding()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = if (s.isEditMode) "Edit transaction" else "Add transaction",
            style = MaterialTheme.typography.headlineSmall
        )

        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            TransactionType.entries.forEachIndexed { index, type ->
                SegmentedButton(
                    selected = s.type == type,
                    onClick = { vm.onTypeChange(type) },
                    shape = SegmentedButtonDefaults.itemShape(index, TransactionType.entries.size),
                    label = {
                        Text(
                            when (type) {
                                TransactionType.EXPENSE -> "Expense"
                                TransactionType.INCOME -> "Income"
                                TransactionType.TRANSFER -> "Transfer"
                            }
                        )
                    }
                )
            }
        }

        OutlinedTextField(
            value = s.amountText,
            onValueChange = vm::onAmountChange,
            label = { Text("Amount (COP)") },
            prefix = { Text("$") },
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Number,
                imeAction = androidx.compose.ui.text.input.ImeAction.Next
            ),
            keyboardActions = nextAction,
            isError = s.amountError != null,
            supportingText = s.amountError?.let { { Text(it) } },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = { showDatePicker = true },
                modifier = Modifier.weight(1f)
            ) { Text(formatFormDate(s.dateMillis)) }
            OutlinedButton(
                onClick = { showTimePicker = true },
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    formatTime(
                        doug.financetracker.util.combineDateAndTime(s.dateMillis, s.hour, s.minute)
                    )
                )
            }
        }

        if (s.type == TransactionType.TRANSFER) {
            AccountDropdown(
                label = "From account",
                accounts = s.accounts,
                selectedId = s.sourceAccountId,
                onSelect = vm::onSourceAccountChange
            )
            AccountDropdown(
                label = "To account",
                accounts = s.accounts,
                selectedId = s.destinationAccountId,
                onSelect = vm::onDestinationAccountChange
            )
        } else {
            AccountDropdown(
                label = "Account",
                accounts = s.accounts,
                selectedId = if (s.type == TransactionType.EXPENSE) s.sourceAccountId else s.destinationAccountId,
                onSelect = {
                    if (s.type == TransactionType.EXPENSE) vm.onSourceAccountChange(it)
                    else vm.onDestinationAccountChange(it)
                }
            )
        }
        s.accountError?.let { Text(it, color = MaterialTheme.colorScheme.error) }

        if (s.type != TransactionType.TRANSFER) {
            OutlinedTextField(
                value = s.counterparty,
                onValueChange = vm::onCounterpartyChange,
                label = { Text(if (s.type == TransactionType.EXPENSE) "Paid to (counterparty)" else "Received from (counterparty)") },
                keyboardOptions = KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Next),
                keyboardActions = nextAction,
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        }
        OutlinedTextField(
            value = s.description,
            onValueChange = vm::onDescriptionChange,
            label = { Text("Description") },
            keyboardOptions = KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Next),
            keyboardActions = nextAction,
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        Text("Tags", style = MaterialTheme.typography.labelLarge)
        if (s.existingTags.isNotEmpty()) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                s.existingTags.forEach { tag ->
                    FilterChip(
                        selected = tag.id in s.selectedTagIds,
                        onClick = { vm.toggleTag(tag.id) },
                        label = { Text(tag.name) }
                    )
                }
            }
        }
        OutlinedTextField(
            value = s.tagText,
            onValueChange = vm::onTagTextChange,
            label = { Text("New tags (comma separated)") },
            keyboardOptions = KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Done),
            keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                onDone = { focusManager.clearFocus() }
            ),
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        s.formError?.let { Text(it, color = MaterialTheme.colorScheme.error) }

        Button(
            onClick = vm::save,
            enabled = !s.isSaving,
            modifier = Modifier.fillMaxWidth()
        ) { Text(if (s.isEditMode) "Save changes" else "Add transaction") }

        if (s.isEditMode) {
            OutlinedButton(
                onClick = { showDeleteConfirm = true },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Delete", color = MaterialTheme.colorScheme.error) }
        }
        Spacer(modifier = Modifier.height(32.dp))
    }

    if (showDatePicker) {
        val dateState = rememberDatePickerState(initialSelectedDateMillis = s.dateMillis)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    dateState.selectedDateMillis?.let(vm::onDateChange)
                    showDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Cancel") }
            }
        ) { DatePicker(state = dateState) }
    }

    if (showTimePicker) {
        val timeState = rememberTimePickerState(initialHour = s.hour, initialMinute = s.minute)
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    vm.onTimeChange(timeState.hour, timeState.minute)
                    showTimePicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showTimePicker = false }) { Text("Cancel") }
            },
            text = { TimePicker(state = timeState) }
        )
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteConfirm = false
                    vm.delete()
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") }
            },
            title = { Text("Delete transaction?") },
            text = { Text("This cannot be undone.") }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AccountDropdown(
    label: String,
    accounts: List<doug.financetracker.domain.model.Account>,
    selectedId: Long?,
    onSelect: (Long?) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val selected = accounts.firstOrNull { it.id == selectedId }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it }
    ) {
        OutlinedTextField(
            value = selected?.displayLabel ?: "",
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable)
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            accounts.forEach { account ->
                DropdownMenuItem(
                    text = { Text(account.displayLabel) },
                    onClick = {
                        onSelect(account.id)
                        expanded = false
                    }
                )
            }
        }
    }
}
