package com.aru.journal.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aru.journal.domain.DailyTotals
import com.aru.journal.sdui.RemoteUiCard

internal fun remoteAccent(name: String): Color = when (name) {
    "blue" -> Color(0xFF286DB5)
    "green" -> Color(0xFF287458)
    else -> Color(0xFF8354EE)
}

@Composable
internal fun RemoteJournalCard(card: RemoteUiCard, totals: DailyTotals, onAction: (String) -> Unit) {
    val accent = MaterialTheme.colorScheme.primary
    Surface(
        modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp),
        color = if (card.tone == "accent") accent.copy(alpha = .08f) else Color.White.copy(alpha = .85f)
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(card.title, fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold)
            if (card.body.isNotBlank()) Text(card.body, fontSize = 14.sp, lineHeight = 21.sp, color = Color(0xFF706A74))
            if (card.type == "daily_summary") {
                fun amount(index: Int): String {
                    val value = totals.values[index]
                    val number = if (value.knownAmount % 1.0 == 0.0) value.knownAmount.toLong().toString() else "%.1f".format(value.knownAmount)
                    return number + if (value.missingItemCount > 0) "+" else ""
                }
                Text("${amount(0)} cal logged", fontSize = 22.sp, fontWeight = FontWeight.SemiBold, color = accent)
                Text("Protein ${amount(1)}g · Carbs ${amount(2)}g · Fat ${amount(3)}g", fontSize = 12.sp, lineHeight = 19.sp)
                if (totals.pendingEntryCount > 0 || totals.values.any { it.missingItemCount > 0 })
                    Text("Totals are incomplete; some nutrition is not calculated.", fontSize = 12.sp, lineHeight = 18.sp, color = Color(0xFF706A74))
            }
            card.actions.forEach { action ->
                OutlinedButton(onClick = { onAction(action.action) }, modifier = Modifier.fillMaxWidth()) {
                    Text(action.label)
                }
            }
        }
    }
}
