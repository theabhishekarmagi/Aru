package com.aru.journal.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aru.journal.domain.JournalEntry
import com.aru.journal.domain.WaterIntake

private val WaterBlue = Color(0xFF288BE8)
@Composable internal fun WaterAmountLabel(water: WaterIntake) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        Icon(Icons.Rounded.WaterDrop, contentDescription = null, tint = WaterBlue, modifier = Modifier.size(17.dp))
        Text("${water.milliliters} ml", color = WaterBlue, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
    }
}

@Composable internal fun EntryDescriptionEditor(entry: JournalEntry, controller: JournalController) {
    var description by rememberSaveable(entry.id, entry.text) { mutableStateOf(entry.text) }
    Surface(color = Color.White.copy(alpha = .9f), shape = RoundedCornerShape(20.dp)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(description, { description = it.take(2000) }, label = { Text("Description") },
                modifier = Modifier.fillMaxWidth().semantics { contentDescription = if(entry.water != null) "Water description" else "Meal description" })
            if (description != entry.text) Button(onClick = { controller.edit(entry.id, description) }, enabled = description.isNotBlank()) {
                Text("Save description")
            }
        }
    }

}

@OptIn(ExperimentalLayoutApi::class)
@Composable internal fun WaterEntryControls(entry: JournalEntry, controller: JournalController) {
    val water = entry.water ?: return
    EntryDescriptionEditor(entry, controller)
    Text("Water amount", fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
    WaterAmountLabel(water)
    val choices = listOf("1 glass · 250 ml" to 250, "2 glasses · 500 ml" to 500, "1 bottle · 500 ml" to 500, "1 litre · 1000 ml" to 1000)
    val selected = water.portionLabel ?: when (water.milliliters) {
        250 -> choices[0].first
        500 -> if (water.detectionKey.contains("bottle")) choices[2].first else choices[1].first
        1000 -> choices[3].first
        else -> null
    }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        choices.forEach { (label, ml) -> FilterChip(selected = selected == label,
            onClick = { controller.water(entry.id, ml, label, assumed = ml != 1000) }, label = { Text(label) }) }
    }
    Text("Glass preset: 250 ml. Bottle preset: 500 ml. Sizes vary; enter your measured amount below.",
        fontSize = 13.sp, lineHeight = 19.sp, color = Color(0xFF8E898F))
    var custom by rememberSaveable(entry.id, water.milliliters) { mutableStateOf(water.milliliters.toString()) }
    val ml = custom.toIntOrNull()
    val valid = ml != null && ml in 1..20000
    OutlinedTextField(custom, { custom = it.take(6) }, label = { Text("Custom amount (ml)") }, singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), isError = custom.isNotBlank() && !valid,
        modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Custom water amount in ml" })
    if (custom.isNotBlank() && !valid) Text("Enter a whole number from 1 to 20000 ml.", color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
    Button(onClick = { ml?.let { controller.water(entry.id, it, "Custom amount", assumed = false) } }, enabled = valid) { Text("Save water amount") }
}
