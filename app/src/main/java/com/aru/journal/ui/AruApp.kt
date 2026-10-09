@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.aru.journal.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import com.aru.journal.R
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aru.journal.domain.*
import java.time.LocalDate
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

private val Ink = Color(0xFF242126)
private val Muted = Color(0xFF8E898F)
private val Purple = Color(0xFF8354EE)
private val Paper = Color(0xFFFCF8F5)
private val names = listOf("Calories", "Protein", "Carbs", "Fat", "Fiber")
private val colors = listOf(Color(0xFFFFB800), Color(0xFFE5B100), Color(0xFFFF3868), Color(0xFFD92CE6), Color(0xFF40AC82))
private fun number(value: Double?): String = value?.let { if (it % 1.0 == 0.0) it.roundToInt().toString() else "%.1f".format(it) } ?: "—"
private fun itemTotals(estimate: NutritionEstimate): List<Double?> = (0..4).map { i ->
    if (estimate.items.any { it.nutrients.values()[i] == null }) null else estimate.items.sumOf { it.nutrients.values()[i]!! }
}

data class AuthUiState(val configured: Boolean = false, val busy: Boolean = false, val error: String? = null)

@Composable fun AruApp(controller: JournalController, auth: AuthUiState = AuthUiState(), onSignIn: ()->Unit = {}, onSignOut: ()->Unit = {}) {
    val state by controller.state.collectAsState()
    MaterialTheme(colorScheme = lightColorScheme(primary = Purple, background = Paper, surface = Paper, onSurface = Ink)) {
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFFFFFAF3), Color(0xFFFCF7FE)))) ) {
            when {
                state.loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                state.accountRequired -> AccountGate(auth, onSignIn)
                else -> JournalScreen(state.copy(error = state.error ?: auth.error), controller, onSignOut)
            }
        }
    }
}

@Composable private fun AccountGate(auth: AuthUiState, onSignIn: ()->Unit) {
    Column(Modifier.fillMaxSize().safeDrawingPadding().padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.weight(1f))
        Text("Aru", color = Purple, fontSize = 52.sp, fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(28.dp))
        Text("A little note.\nA clearer picture.", fontSize = 29.sp, lineHeight = 36.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(18.dp))
        Text("Write what you ate. Keep your meals,\nnutrition, and goals together.", color = Muted, lineHeight = 25.sp)
        Spacer(Modifier.weight(1f))
        WhiteCard {
            Text("Your journal belongs to you", fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            Text(stringResource(R.string.account_required), color = Muted)
            Spacer(Modifier.height(14.dp))
            if(auth.configured) {
                Button(onClick = onSignIn, enabled = !auth.busy, modifier = Modifier.fillMaxWidth()) {
                    Text(if(auth.busy) "Signing in…" else "Continue with Google")
                }
            } else Text(stringResource(R.string.setup_pending), color = Purple)
            auth.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
        Spacer(Modifier.height(28.dp))
    }
}

@Composable private fun JournalScreen(state: JournalUiState, controller: JournalController, onSignOut: ()->Unit) {
    var dateString by rememberSaveable { mutableStateOf(LocalDate.now().toString()) }
    val date = LocalDate.parse(dateString)
    var sheet by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
    var focusId by remember { mutableStateOf<String?>(null) }
    var calendar by remember { mutableStateOf(false) }
    var settings by remember { mutableStateOf(false) }
    val focus = LocalFocusManager.current
    val totals = dailyTotals(state.journal.entries, dateString)
    val entries = state.journal.entries.filter { it.journalDate == dateString }
    Column(Modifier.fillMaxSize().safeDrawingPadding().imePadding().padding(horizontal = 22.dp)) {
        Row(Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 32.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Aru", color = Purple, fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold, fontSize = 27.sp, modifier = Modifier.weight(1f))
            Pill(onClick = { focus.clearFocus(); calendar = true }) {
                Text(if (date == LocalDate.now()) "Today" else date.format(DateTimeFormatter.ofPattern("d MMM")), fontWeight = FontWeight.Medium)
            }
            Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
                Pill(onClick = { settings = true }, label = "Settings") { Text("⚙", fontSize = 23.sp) }
                DropdownMenu(expanded = settings, onDismissRequest = { settings = false }) {
                    DropdownMenuItem(text = { Text("Sign out") }, onClick = { settings = false; onSignOut() })
                    DropdownMenuItem(text = { Text("Daily goals") }, onClick = { settings = false; sheet = "goals" })
                    DropdownMenuItem(text = { Text("Saved meals") }, onClick = { settings = false; sheet = "saved" })
                }
            }
        }
        if (state.error != null) {
            Text(state.error, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(bottom = 8.dp))
            TextButton(onClick = controller::clearError) { Text("Dismiss") }
        }
        LazyColumn(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(24.dp)) {
            items(entries, key = { it.id }) { entry ->
                EntryLine(entry, focusId == entry.id, { focusId = null }, { controller.edit(entry.id, it) }, {
                    focus.clearFocus(); selectedId = entry.id; sheet = "details"
                }, { controller.add("", date) { focusId = it } })
            }
            item {
                TextButton(onClick = { controller.add("", date) { focusId = it } }, modifier = Modifier.fillMaxWidth()) {
                    Text(if (entries.isEmpty()) "What did you eat today?" else "+ Add a line", color = Muted, modifier = Modifier.fillMaxWidth())
                }
                if (entries.isEmpty()) Text("Just write it down, like a note.", color = Muted.copy(alpha = .7f), fontSize = 13.sp, modifier = Modifier.padding(start = 12.dp))
            }
        }
        if (state.undoToken != null) Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Entry deleted", color = Muted, modifier = Modifier.weight(1f))
            TextButton(onClick = controller::undo) { Text("Undo") }
        }
        if (totals.pendingEntryCount > 0 || totals.reviewEntryCount > 0) Text(
            listOfNotNull(if(totals.pendingEntryCount > 0) "${totals.pendingEntryCount} not calculated" else null,
                if(totals.reviewEntryCount > 0) "${totals.reviewEntryCount} to review" else null).joinToString(" · "),
            fontSize = 12.sp, color = Muted, modifier = Modifier.align(Alignment.CenterHorizontally).padding(bottom = 10.dp))
        Row(Modifier.fillMaxWidth().padding(bottom = 14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Pill(onClick = { focus.clearFocus(); sheet = "goals" }, modifier = Modifier.weight(1f), label = "Daily totals and goals") {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    listOf(0,2,1,3,4).forEach { i ->
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(listOf("kcal","P","C","F","Fi")[i], color = colors[i], fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            Text(number(totals.values[i].knownAmount) + if(totals.values[i].missingItemCount > 0) "+" else "", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
            Pill(onClick = { focus.clearFocus(); sheet = "saved" }, label = "Saved meals") { Text("+", color = Purple, fontSize = 26.sp) }
        }
    }
    if (calendar) {
        val picker = rememberDatePickerState(initialSelectedDateMillis = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
        DatePickerDialog(onDismissRequest = { calendar = false }, confirmButton = {
            TextButton(onClick = { picker.selectedDateMillis?.let { dateString = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate().toString() }; calendar = false }) { Text("Done") }
        }, dismissButton = { TextButton(onClick = { dateString = LocalDate.now().toString(); calendar = false }) { Text("Today") } }) { DatePicker(picker) }
    }
    if (sheet != null) ModalBottomSheet(onDismissRequest = { sheet = null }, containerColor = Paper,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().imePadding().padding(horizontal = 20.dp).verticalScroll(rememberScrollState()).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(when(sheet) { "goals" -> "Daily goals"; "saved" -> "Saved meals"; else -> "Nutrition details" }, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                TextButton(onClick = { sheet = null }) { Text("Close") }
            }
            when(sheet) {
                "goals" -> GoalsContent(totals, state.journal.goals) { controller.goals(it) }
                "saved" -> {
                    if(state.journal.savedMeals.isEmpty()) Text("Your go-to meals, ready for next time. Open an entry’s nutrition details to save one.", color = Muted, lineHeight = 24.sp)
                    state.journal.savedMeals.forEach { meal ->
                        WhiteCard {
                            Text(meal.name, fontWeight = FontWeight.SemiBold)
                            Text(meal.text, color = Muted)
                            TextButton(onClick = { controller.reuse(meal.id, date); sheet = null }) { Text("Add to ${if(date == LocalDate.now()) "today" else date.format(DateTimeFormatter.ofPattern("d MMM"))}") }
                        }
                    }
                }
                else -> state.journal.entries.find { it.id == selectedId }?.let { entry ->
                    DetailsContent(entry, controller, onDelete = { controller.delete(entry.id); sheet = null })
                }
            }
        }
    }
}

@Composable private fun EntryLine(entry: JournalEntry, requestFocus: Boolean, onFocused: ()->Unit, onEdit: (String)->Unit, onDetails: ()->Unit, onNext: ()->Unit) {
    var text by rememberSaveable(entry.id) { mutableStateOf(entry.text) }
    val requester = remember { FocusRequester() }
    LaunchedEffect(requestFocus) { if(requestFocus) { requester.requestFocus(); onFocused() } }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        BasicTextField(value = text, onValueChange = { next -> text = next; onEdit(next) }, modifier = Modifier.weight(1f).focusRequester(requester).semantics { contentDescription = "Food entry" },
            textStyle = TextStyle(color = Ink, fontSize = 17.sp, lineHeight = 27.sp), cursorBrush = androidx.compose.ui.graphics.SolidColor(Purple),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next), keyboardActions = KeyboardActions(onNext = { onNext() }),
            decorationBox = { inner -> Box { if(text.isEmpty()) Text("Write what you ate…", color = Muted, fontSize = 17.sp); inner() } })
        val label = when(entry.status) {
            CalculationStatus.CALCULATING -> "Calculating…"
            CalculationStatus.QUEUED -> "Waiting…"
            CalculationStatus.FAILED -> "Retry"
            CalculationStatus.DRAFT -> "Calculate"
            else -> "${entry.estimate?.let { number(itemTotals(it)[0]) } ?: "—"} cal"
        }
        TextButton(onClick = onDetails, contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp), modifier = Modifier.heightIn(min = 48.dp).widthIn(min = 64.dp, max = 112.dp).offset(y = (-10).dp).semantics { contentDescription = "Nutrition for ${entry.text}" }) {
            Text(label, color = if(entry.status == CalculationStatus.NEEDS_REVIEW) Purple else Muted, fontSize = 15.sp)
        }
    }
}

@Composable private fun DetailsContent(entry: JournalEntry, controller: JournalController, onDelete: ()->Unit) {
    var edit by remember(entry.id) { mutableStateOf(false) }
    var save by remember { mutableStateOf(false) }
    var mealName by remember { mutableStateOf("") }
    val focus = LocalFocusManager.current
    Text(entry.text.ifBlank { "New entry" }, fontSize = 25.sp, fontWeight = FontWeight.Bold, lineHeight = 32.sp)
    val estimate = entry.estimate
    if(estimate == null) WhiteCard {
        Text(if(controller.canEstimate) "Nutrition hasn’t been calculated yet." else "AI calculation is not connected yet.", fontWeight = FontWeight.Medium)
        Text(entry.failureCode?.let { nutritionErrorMessage(it) } ?: "Tap Calculate for an AI estimate, or enter nutrition manually. Unknown values stay blank.", color = Muted, modifier = Modifier.padding(top = 8.dp))
    } else {
        WhiteCard {
            val sums = itemTotals(estimate)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                Text("${number(sums[0])}", fontSize = 34.sp, fontWeight = FontWeight.Bold)
                Text("  total kcal", color = Muted, fontSize = 13.sp)
            }
            NutrientRow(sums)
        }
        Text("Items", color = Muted)
        estimate.items.forEach { item ->
            var expanded by remember(item.name) { mutableStateOf(false) }
            WhiteCard {
                Row(Modifier.fillMaxWidth().clickable { expanded = !expanded }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(item.name, modifier = Modifier.weight(1f))
                    Text("${number(item.nutrients.caloriesKcal)} cal  ${if(expanded) "⌃" else "⌄"}", fontWeight = FontWeight.Medium)
                }
                Text("${number(item.portion.quantity)} ${item.portion.unitKey}${if(item.portion.assumed) " · assumed" else ""}", color = Muted, fontSize = 13.sp)
                if(expanded) {
                    NutrientRow(item.nutrients.values())
                    (item.assumptions + listOfNotNull(item.portion.assumption)).distinct().forEach { Text(it, color = Purple, modifier = Modifier.padding(top = 8.dp)) }
                }
            }
        }
        Text("Estimate notes", color = Muted)
        WhiteCard {
            if(estimate.needsReview) Text("Review the portions", color = Purple, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(bottom = 8.dp))
            Text(estimate.explanation, lineHeight = 23.sp)
            TextButton(onClick = { edit = true }) { Text("Something off? Edit nutrition") }
        }
        Text("References", color = Muted)
        val uri = LocalUriHandler.current
        estimate.items.flatMap { it.sources }.distinct().forEach { source ->
            WhiteCard {
                Text(source.title, color = if(source.url != null) Purple else Ink, fontWeight = FontWeight.Medium)
                listOfNotNull(source.recordId, source.version, source.market, source.menuItem, source.menuSize, source.basis).takeIf { it.isNotEmpty() }?.let { Text(it.joinToString(" · "), fontSize = 12.sp, color = Muted) }
                source.url?.let { url -> TextButton(onClick = { uri.openUri(url) }) { Text("Open source ↗") } }
            }
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        TextButton(onClick = { edit = !edit }, enabled = entry.text.isNotBlank()) { Text("Edit nutrition") }
        if(controller.canEstimate) TextButton(onClick = { controller.calculate(entry.id) }, enabled = entry.text.isNotBlank() && entry.status !in setOf(CalculationStatus.CALCULATING, CalculationStatus.QUEUED)) { Text(if(estimate == null) "Calculate" else "Recalculate") }
    }
    if(edit) ManualEditor(entry) { controller.correct(entry.id, it); edit = false }
    if(estimate != null) TextButton(onClick = { save = !save }) { Text("Save as meal") }
    if(save) {
        val commitMeal = {
            if (mealName.isNotBlank()) {
                controller.saveMeal(entry.id, mealName.trim()); save = false; mealName = ""; focus.clearFocus()
            }
        }
        OutlinedTextField(mealName, { mealName = it }, label = { Text("Meal name") }, singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done), keyboardActions = KeyboardActions(onDone = { commitMeal() }), modifier = Modifier.fillMaxWidth())
        Button(onClick = commitMeal, enabled = mealName.isNotBlank()) { Text("Save meal") }
    }
    TextButton(onClick = onDelete) { Text("Delete entry", color = MaterialTheme.colorScheme.error) }
}

@Composable private fun NutrientRow(values: List<Double?>) {
    Row(Modifier.fillMaxWidth().padding(top = 18.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
        (1..4).forEach { i -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("${number(values[i])} g", fontWeight = FontWeight.Medium, fontSize = 15.sp)
            Text(names[i], color = colors[i], fontSize = 11.sp)
        } }
    }
}

@Composable private fun GoalsContent(totals: DailyTotals, goals: NutritionGoals, save: (NutritionGoals)->Unit) {
    var editing by remember { mutableStateOf(false) }
    var fields by remember(goals) { mutableStateOf(goals.targets.values().map { it?.let(::number) ?: "" }) }
    WhiteCard {
        names.forEachIndexed { i, name ->
            val value = totals.values[i]
            val goal = goals.targets.values()[i]
            Row(Modifier.fillMaxWidth().padding(top = if(i == 0) 0.dp else 22.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(name, color = Muted, modifier = Modifier.weight(1f))
                Text("${number(value.knownAmount)}${if(value.missingItemCount > 0) "+" else ""} / ${number(goal)}${if(i > 0) " g" else " kcal"}", fontWeight = FontWeight.SemiBold)
            }
            LinearProgressIndicator(progress = { if(goal != null) (value.knownAmount / goal).toFloat().coerceIn(0f,1f) else 0f }, modifier = Modifier.fillMaxWidth().padding(top = 10.dp).height(7.dp), color = colors[i], trackColor = Color(0xFFEDE8EC), gapSize = 0.dp, drawStopIndicator = {})
        }
    }
    Text("${if(totals.pendingEntryCount > 0) "${totals.pendingEntryCount} entries are not included yet. " else ""}A + marks an incomplete nutrient total. Blank targets are unset.", color = Muted, fontSize = 13.sp, lineHeight = 20.sp)
    TextButton(onClick = { editing = !editing }) { Text("Edit goals") }
    if(editing) {
        NutrientFields(fields, { fields = it })
        val valid = fields.all { it.isBlank() || (it.toDoubleOrNull()?.let { n -> n.isFinite() && n > 0 } == true) }
        if(!valid) Text("Use positive numbers or leave a target blank.", color = MaterialTheme.colorScheme.error)
        Button(onClick = { save(NutritionGoals(toNutrients(fields))); editing = false }, enabled = valid) { Text("Save goals") }
    }
}

@Composable private fun ManualEditor(entry: JournalEntry, save: (NutritionEstimate)->Unit) {
    // Edit every matched item independently; no false rescaling of other items.
    val original = entry.estimate?.items ?: listOf(EstimatedItem(entry.text, Portion(1.0,"serving",PortionKind.SERVING), Nutrients(), listOf(SourceReference(SourceKind.USER,"Entered by you"))))
    var fields by remember(entry.id, entry.revision) { mutableStateOf(original.map { it.nutrients.values().map { n -> n?.let(::number) ?: "" } }) }
    var portions by remember(entry.id, entry.revision) { mutableStateOf(original.map { number(it.portion.quantity) }) }
    var units by remember(entry.id, entry.revision) { mutableStateOf(original.map { it.portion.unitKey }) }
    var kinds by remember(entry.id, entry.revision) { mutableStateOf(original.map { it.portion.kind }) }
    Text("Manual nutrition", fontWeight = FontWeight.SemiBold)
    Text("Enter totals for each portion below. Changing a portion does not automatically scale nutrients. Leave unknown values blank.", color = Muted, fontSize = 13.sp)
    original.forEachIndexed { i, item ->
        Text(item.name, fontWeight = FontWeight.Medium)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(portions[i], { v -> portions = portions.toMutableList().also { it[i] = v } }, label = { Text("Quantity") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f))
            OutlinedTextField(units[i], { v -> units = units.toMutableList().also { it[i] = v } }, label = { Text("Unit") }, modifier = Modifier.weight(1f))
        }
        var menu by remember { mutableStateOf(false) }
        Box {
            TextButton(onClick = { menu = true }) { Text("Portion type: ${kinds[i].name.lowercase()}") }
            DropdownMenu(menu, { menu = false }) { PortionKind.entries.forEach { kind -> DropdownMenuItem(text = { Text(kind.name.lowercase()) }, onClick = { kinds = kinds.toMutableList().also { it[i] = kind }; menu = false }) } }
        }
        NutrientFields(fields[i], { v -> fields = fields.toMutableList().also { it[i] = v } })
    }
    val valid = fields.flatten().all { it.isBlank() || it.toDoubleOrNull()?.let { n -> n.isFinite() && n >= 0 } == true } && portions.all { it.toDoubleOrNull()?.let { n -> n.isFinite() && n > 0 } == true } && units.all { it.isNotBlank() }
    if(!valid) Text("Use valid non-negative nutrients, a positive quantity, and a unit.", color = MaterialTheme.colorScheme.error)
    Button(onClick = {
        save(NutritionEstimate(original.mapIndexed { i, item -> item.copy(
            portion = Portion(portions[i].toDouble(), units[i].trim(), kinds[i]), nutrients = toNutrients(fields[i]),
            sources = listOf(SourceReference(SourceKind.USER,"Manually entered by you")), assumptions = emptyList()) }, false, "Nutrition and portions were entered manually. Values apply to the portions shown.", System.currentTimeMillis()))
    }, enabled = valid) { Text("Save nutrition") }
}

private fun toNutrients(fields: List<String>) = fields.map { it.toDoubleOrNull() }.let { Nutrients(it[0],it[1],it[2],it[3],it[4]) }
@Composable private fun NutrientFields(fields: List<String>, change: (List<String>)->Unit) {
    names.forEachIndexed { i, name -> OutlinedTextField(fields[i], { v -> change(fields.toMutableList().also { it[i] = v }) }, label = { Text("$name (${if(i == 0) "kcal" else "g"})") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true, modifier = Modifier.fillMaxWidth()) }
}
@Composable private fun WhiteCard(content: @Composable ColumnScope.()->Unit) {
    Surface(color = Color.White.copy(alpha = .87f), shape = RoundedCornerShape(23.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(3.dp), content = content)
    }
}
@Composable private fun Pill(onClick: ()->Unit, modifier: Modifier = Modifier, label: String? = null, content: @Composable ()->Unit) {
    Surface(onClick = onClick, shape = CircleShape, color = Color.White.copy(alpha = .92f), modifier = modifier.heightIn(min = 48.dp).shadow(14.dp, CircleShape, ambientColor = Color(0xFFB5A4AD), spotColor = Color(0xFFD5C5BB)).then(if(label != null) Modifier.semantics { contentDescription = label } else Modifier)) {
        Box(Modifier.padding(horizontal = 17.dp, vertical = 12.dp), contentAlignment = Alignment.Center) { content() }
    }
}

internal fun nutritionErrorMessage(code: String): String = when(code) {
    "not_configured" -> "Nutrition service setup is incomplete. You can still enter values manually."
    "provider_quota" -> "AI calculation is unavailable because the service has no API quota. You can enter nutrition manually."
    "provider_configuration" -> "Nutrition service credentials need attention. You can enter nutrition manually."
    "rate_limited" -> "Calculation limit reached. Try later or enter nutrition manually."
    "invalid_request" -> "Use a food description up to 2,000 characters."
    "no_food", "uncertain_food" -> "Please describe the food and portion more clearly."
    "account_required" -> "Please sign in again to calculate nutrition."
    "timeout", "provider_busy", "provider_unavailable" -> "The nutrition service is busy. Please retry shortly."
    else -> "Calculation couldn’t finish. Retry or enter nutrition manually."
}
