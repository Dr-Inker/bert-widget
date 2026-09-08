package global.bert.widget

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import global.bert.widget.data.*

@Composable
internal fun HoldingsScreen(position: BERTPosition, state: QuoteState, now: Long, save: (BERTPosition) -> Unit) {
    var editing by rememberSaveable { mutableStateOf(position.amount == null) }
    var removing by remember { mutableStateOf(false) }
    val available = state as? QuoteState.Available
    val price = available?.quote?.priceUsd
    Text("Your corner of BERT.", color = Cream, fontSize = 27.sp, fontWeight = FontWeight.Bold)
    Text("A private record of your holdings. Stored on this device, with no wallet connection.", color = Muted, fontSize = 14.sp, lineHeight = 21.sp)
    if (position.amount != null) {
        Card(colors = CardDefaults.cardColors(containerColor = PanelStrong), shape = RoundedCornerShape(24.dp)) {
            Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SectionLabel("ESTIMATED VALUE")
                Text(formatHoldingsUsd(position.valueUsd(price)), color = Cream, fontSize = 34.sp, fontWeight = FontWeight.ExtraBold)
                Text("${formatTokenAmount(position.amount)} BERT", color = Cream, fontSize = 17.sp)
                if (available != null) QuoteFreshness(available, now)
                else Text("A quote is needed to estimate your position.", color = Amber, fontSize = 13.sp)
                if (position.totalCostUsd != null) {
                    HorizontalDivider(color = Muted.copy(alpha = 0.2f))
                    Text("Total cost · ${formatHoldingsUsd(position.totalCostUsd)}", color = Muted, fontSize = 14.sp)
                    val gain = position.gainUsd(price)
                    Text("Unrealized ${if (gain != null && gain < 0) "loss" else "gain"} · ${formatHoldingsUsd(gain)}", color = movementColor(gain), fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Text("${formatPercent(position.gainPercent(price))} return on entered cost", color = Muted, fontSize = 13.sp)
                    Text("An estimate from your entered cost and the displayed quote. Excludes fees and realized trades.", color = Muted, fontSize = 12.sp, lineHeight = 18.sp)
                } else Text("Add your total cost to see an estimated gain or loss.", color = Muted, fontSize = 13.sp)
            }
        }
        if (!editing) {
            Button(onClick = { editing = true }, modifier = Modifier.fillMaxWidth()) { Text("Edit holdings") }
            TextButton(onClick = { removing = true }) { Text("Remove holdings", color = Muted) }
        }
    }
    if (editing) {
        PositionEditor(position, onCancel = if (position.amount != null) ({ editing = false }) else null) {
            save(it)
            editing = false
        }
    }
    if (removing) {
        AlertDialog(
            onDismissRequest = { removing = false },
            title = { Text("Remove saved holdings?") },
            text = { Text("This removes your amount and total cost from this app and your Market widget.") },
            confirmButton = { TextButton(onClick = { save(BERTPosition()); removing = false; editing = true }) { Text("Remove") } },
            dismissButton = { TextButton(onClick = { removing = false }) { Text("Keep holdings") } },
        )
    }
}

@Composable
private fun PositionEditor(position: BERTPosition, onCancel: (() -> Unit)?, save: (BERTPosition) -> Unit) {
    var amount by rememberSaveable(position.amount) { mutableStateOf(position.amount?.toBigDecimal()?.stripTrailingZeros()?.toPlainString() ?: "") }
    var cost by rememberSaveable(position.totalCostUsd) { mutableStateOf(position.totalCostUsd?.toBigDecimal()?.stripTrailingZeros()?.toPlainString() ?: "") }
    var attempted by rememberSaveable { mutableStateOf(false) }
    val parsedAmount = parsePositionNumber(amount)
    val parsedCost = parsePositionNumber(cost)
    val amountError = attempted && parsedAmount == null
    val costError = attempted && cost.isNotBlank() && parsedCost == null
    Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(20.dp)) {
        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            SectionLabel(if (position.amount == null) "ADD YOUR HOLDINGS" else "EDIT YOUR HOLDINGS")
            OutlinedTextField(
                value = amount, onValueChange = { amount = it }, modifier = Modifier.fillMaxWidth(),
                label = { Text("BERT amount") }, placeholder = { Text("250000") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), isError = amountError,
                supportingText = { Text(if (amountError) "Enter zero or more, using a decimal point." else "Use a decimal point, e.g. 250000.5") },
            )
            OutlinedTextField(
                value = cost, onValueChange = { cost = it }, modifier = Modifier.fillMaxWidth(),
                label = { Text("Total cost in USD (optional)") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), isError = costError,
                supportingText = { Text(if (costError) "Enter a valid USD amount or leave blank." else "Total paid for this amount, not price per token.") },
            )
            Button(onClick = {
                attempted = true
                if (parsedAmount != null && (cost.isBlank() || parsedCost != null)) save(BERTPosition(parsedAmount, parsedCost))
            }, modifier = Modifier.fillMaxWidth()) { Text("Save holdings") }
            if (onCancel != null) TextButton(onClick = onCancel) { Text("Cancel", color = Cream) }
            Text("Your BERT amount also appears on the Market widget. Clearing app data or uninstalling removes this record.", color = Muted, fontSize = 12.sp, lineHeight = 18.sp)
        }
    }
}
