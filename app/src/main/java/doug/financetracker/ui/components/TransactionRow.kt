package doug.financetracker.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import doug.financetracker.domain.model.Money
import doug.financetracker.domain.model.TransactionDetails
import doug.financetracker.domain.model.TransactionType
import doug.financetracker.util.formatDateTime

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TransactionRow(
    item: TransactionDetails,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val t = item.transaction
    val (icon, tint) = when (t.type) {
        TransactionType.EXPENSE -> Icons.Filled.ArrowUpward to MaterialTheme.colorScheme.error
        TransactionType.INCOME -> Icons.Filled.ArrowDownward to MaterialTheme.colorScheme.primary
        TransactionType.TRANSFER -> Icons.Filled.SwapHoriz to MaterialTheme.colorScheme.tertiary
    }
    val title = when (t.type) {
        TransactionType.TRANSFER -> buildString {
            append(item.sourceAccount?.displayLabel ?: "?")
            append(" → ")
            append(item.destinationAccount?.displayLabel ?: "?")
        }
        else -> if (t.counterparty.isNotBlank()) t.counterparty
        else if (t.description.isNotBlank()) t.description else t.type.name
    }
    val subtitle = when (t.type) {
        TransactionType.TRANSFER ->
            listOfNotNull(t.description.takeIf { it.isNotBlank() }, formatDateTime(t.dateTime))
                .joinToString(" · ")
        else -> listOfNotNull(
            t.description.takeIf { it.isNotBlank() },
            accountLabel(item),
            formatDateTime(t.dateTime)
        ).joinToString(" · ")
    }

    Card(onClick = onClick, modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(imageVector = icon, contentDescription = t.type.name, tint = tint)
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, style = MaterialTheme.typography.titleMedium)
                if (subtitle.isNotBlank()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (item.tags.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        item.tags.forEach { tag ->
                            AssistChip(
                                onClick = {},
                                label = { Text(tag.name) }
                            )
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = (if (t.type == TransactionType.EXPENSE) "−" else "+") + Money.formatCop(t.amount),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                color = if (t.type == TransactionType.EXPENSE) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

private fun accountLabel(item: TransactionDetails): String? {
    val t = item.transaction
    return when (t.type) {
        TransactionType.EXPENSE -> item.sourceAccount?.displayLabel
        TransactionType.INCOME -> item.destinationAccount?.displayLabel
        TransactionType.TRANSFER -> null
    }
}
