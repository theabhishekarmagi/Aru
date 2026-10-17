package com.aru.journal.ui

import androidx.compose.animation.AnimatedContent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aru.journal.domain.UserProfile
import com.aru.journal.domain.UserProfileDraft
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

private val ProfileInk = Color(0xFF242126)
private val ProfileMuted = Color(0xFF8E898F)
private val ProfilePurple = Color(0xFF8354EE)
private val ProfilePaper = Color(0xFFFCF8F5)
private val ProfileCard = Color(0xFFFFFDFC)

data class ProfileUiState(
    val enabled: Boolean = false,
    val loading: Boolean = false,
    val saving: Boolean = false,
    val profile: UserProfile? = null,
    val error: String? = null
)

@Composable
fun ProfileSetupFlow(
    profile: UserProfile?,
    saving: Boolean,
    error: String?,
    onSave: (UserProfileDraft) -> Unit,
    onSkipAll: () -> Unit,
    preview: Boolean = false,
    initialStep: Int = 0
) {
    var step by rememberSaveable { mutableIntStateOf(initialStep) }
    var name by rememberSaveable { mutableStateOf(profile?.displayName.orEmpty()) }
    var birthDate by rememberSaveable { mutableStateOf(profile?.dateOfBirth.orEmpty()) }
    var height by rememberSaveable { mutableStateOf(profile?.heightCm?.let(::compactNumber).orEmpty()) }
    var weight by rememberSaveable { mutableStateOf(profile?.weightKg?.let(::compactNumber).orEmpty()) }
    var validation by rememberSaveable { mutableStateOf<String?>(null) }
    var showDatePicker by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val labels = listOf("Your name", "Date of birth", "Height", "Weight")

    fun draft() = UserProfileDraft(
        name.trim().takeIf { it.isNotEmpty() },
        birthDate.trim().takeIf { it.isNotEmpty() },
        height.toDoubleOrNull(),
        weight.toDoubleOrNull()
    )
    fun next(skip: Boolean) {
        if (preview) {
            if (step < labels.lastIndex) step++ else onSave(draft())
            return
        }
        validation = null
        when (step) {
            0 -> if (skip) name = "" else if (name.trim().length > 100) validation = "Keep your name under 100 characters."
            1 -> if (skip) birthDate = "" else if (birthDate.isNotBlank() && runCatching { LocalDate.parse(birthDate) }.getOrNull()?.let { it <= LocalDate.now() } != true) validation = "Choose a valid date of birth."
            2 -> if (skip) height = "" else if (height.isNotBlank() && height.toDoubleOrNull()?.let { it in 50.0..300.0 } != true) validation = "Enter a height between 50 and 300 cm."
            3 -> if (skip) weight = "" else if (weight.isNotBlank() && weight.toDoubleOrNull()?.let { it in 20.0..500.0 } != true) validation = "Enter a weight between 20 and 500 kg."
        }
        if (validation == null) {
            focusManager.clearFocus()
            keyboardController?.hide()
            if (step < labels.lastIndex) step++ else onSave(draft())
        }
    }

    if (showDatePicker) ProfileDatePicker(birthDate, { birthDate = it; showDatePicker = false }, { showDatePicker = false })

    Column(
        Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 28.dp, vertical = 18.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { if (step > 0) step-- }, enabled = step > 0 && !saving) {
                Icon(Icons.Rounded.ArrowBack, "Previous question", tint = if (step > 0) ProfileInk else Color.Transparent)
            }
            Text("${step + 1} of ${labels.size}", color = ProfileMuted, fontSize = 13.sp)
            TextButton(onClick = onSkipAll, enabled = !saving) { Text(if (preview) "Skip" else "Skip for now") }
        }
        if (preview) Text("Onboarding preview · your details stay saved", color = ProfileMuted, fontSize = 12.sp, modifier = Modifier.padding(bottom = 12.dp))
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            labels.indices.forEach { index ->
                Box(Modifier.weight(1f).height(5.dp).background(if (index <= step) ProfilePurple else Color(0xFFE8E1EC), CircleShape))
            }
        }
        AnimatedContent(
            targetState = step,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            transitionSpec = {
                val direction = if (targetState > initialState) 1 else -1
                (slideInHorizontally(tween(320, easing = FastOutSlowInEasing)) { it * direction } + fadeIn()) togetherWith
                    (slideOutHorizontally(tween(240)) { -it * direction } + fadeOut())
            }, label = "profile question"
        ) { current ->
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                AruProfileIllustration(current)
                Spacer(Modifier.height(18.dp))
                Text(labels[current], fontSize = 31.sp, fontWeight = FontWeight.Bold, color = ProfileInk)
                Text(
                    listOf("What should Aru call you?", "This helps keep your profile personal.", "Centimetres work best for your profile.", "Kilograms work best for your profile.")[current],
                    color = ProfileMuted, fontSize = 16.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 10.dp, bottom = 28.dp)
                )
                when (current) {
                    0 -> ProfileField(name, { name = it }, "Name", keyboard = KeyboardType.Text, readOnly = preview)
                    1 -> ProfileField(birthDate, { birthDate = it }, "YYYY-MM-DD", keyboard = KeyboardType.Number, readOnly = preview, trailing = {
                        IconButton(onClick = { showDatePicker = true }, enabled = !preview) { Icon(Icons.Rounded.CalendarMonth, "Choose date") }
                    })
                    2 -> ProfileField(height, { height = decimalInput(it) }, "Height", "cm", readOnly = preview)
                    else -> ProfileField(weight, { weight = decimalInput(it) }, "Weight", "kg", readOnly = preview)
                }
                validation?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 13.sp, modifier = Modifier.padding(top = 10.dp)) }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 13.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 10.dp)) }
            }
        }
        Button(
            onClick = { next(false) }, enabled = !saving,
            modifier = Modifier.fillMaxWidth().height(56.dp), shape = RoundedCornerShape(28.dp)
        ) { if (saving) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp, color = Color.White) else Text(if (preview) { if (step == labels.lastIndex) "Explore features" else "Next" } else if (step == labels.lastIndex) "Continue to Aru" else "Continue", fontWeight = FontWeight.Bold) }
        if (!preview) TextButton(onClick = { next(true) }, enabled = !saving) { Text("Skip this question") }
    }
}

/** Replay uses the same illustrated setup as signup, with no persistence callbacks. */
@Composable
fun OnboardingReplay(profile: UserProfile?, controller: JournalController, onClose: () -> Unit) {
    var showingFeatures by rememberSaveable { mutableStateOf(false) }
    var returningToProfile by rememberSaveable { mutableStateOf(false) }
    BackHandler(onBack = onClose)
    if (showingFeatures) {
        FeatureTour(controller, false, null, replay = true, onFinish = onClose, onSkip = onClose,
            onPreviousIntro = { returningToProfile = true; showingFeatures = false })
    } else {
        ProfileSetupFlow(profile, false, null, onSave = { showingFeatures = true }, onSkipAll = onClose,
            preview = true, initialStep = if (returningToProfile) 3 else 0)
    }
}

@Composable
fun SettingsPage(
    profile: UserProfile,
    saving: Boolean,
    error: String?,
    onBack: () -> Unit,
    onSaveProfile: (UserProfileDraft) -> Unit,
    onGoals: () -> Unit,
    onSavedMeals: () -> Unit,
    onReplayTour: () -> Unit,
    onSignOut: () -> Unit
) {
    var editing by rememberSaveable { mutableStateOf(false) }
    var name by rememberSaveable(profile.accountId) { mutableStateOf(profile.displayName.orEmpty()) }
    var birthDate by rememberSaveable(profile.accountId) { mutableStateOf(profile.dateOfBirth.orEmpty()) }
    var height by rememberSaveable(profile.accountId) { mutableStateOf(profile.heightCm?.let(::compactNumber).orEmpty()) }
    var weight by rememberSaveable(profile.accountId) { mutableStateOf(profile.weightKg?.let(::compactNumber).orEmpty()) }
    var showDatePicker by remember { mutableStateOf(false) }
    var validation by rememberSaveable { mutableStateOf<String?>(null) }
    var submitted by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(profile, saving, error) {
        if (submitted && !saving && error == null) {
            name = profile.displayName.orEmpty(); birthDate = profile.dateOfBirth.orEmpty()
            height = profile.heightCm?.let(::compactNumber).orEmpty(); weight = profile.weightKg?.let(::compactNumber).orEmpty()
            editing = false; submitted = false
        }
    }
    if (showDatePicker) ProfileDatePicker(birthDate, { birthDate = it; showDatePicker = false }, { showDatePicker = false })

    Column(Modifier.fillMaxSize().safeDrawingPadding().background(ProfilePaper)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Rounded.ArrowBack, "Back") }
            Text("Settings", fontSize = 25.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 8.dp))
        }
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 8.dp)) {
            Text("Your profile", color = ProfileMuted, fontWeight = FontWeight.SemiBold)
            Surface(Modifier.fillMaxWidth().padding(top = 10.dp), shape = RoundedCornerShape(28.dp), color = ProfileCard, shadowElevation = 2.dp) {
                Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(17.dp)) {
                    if (editing) {
                        ProfileField(name, { name = it }, "Name")
                        ProfileField(birthDate, { birthDate = it }, "YYYY-MM-DD", keyboard = KeyboardType.Number, trailing = { IconButton(onClick = { showDatePicker = true }) { Icon(Icons.Rounded.CalendarMonth, "Choose date") } })
                        ProfileField(height, { height = decimalInput(it) }, "Height", "cm")
                        ProfileField(weight, { weight = decimalInput(it) }, "Weight", "kg")
                        validation?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 13.sp) }
                        error?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 13.sp) }
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            TextButton(onClick = { editing = false }, enabled = !saving) { Text("Cancel") }
                            Button(onClick = {
                                validation = validateProfileDraft(birthDate, height, weight)
                                if (validation == null) {
                                    submitted = true
                                    onSaveProfile(UserProfileDraft(name.trim().takeIf { it.isNotEmpty() }, birthDate.takeIf { it.isNotBlank() }, height.toDoubleOrNull(), weight.toDoubleOrNull()))
                                }
                            }, enabled = !saving) { Text(if (saving) "Saving…" else "Save profile") }
                        }
                    } else {
                        ProfileValue(Icons.Rounded.Person, "Name", profile.displayName)
                        ProfileValue(Icons.Rounded.Cake, "Date of birth", profile.dateOfBirth)
                        ProfileValue(Icons.Rounded.Height, "Height", profile.heightCm?.let { "${compactNumber(it)} cm" })
                        ProfileValue(Icons.Rounded.MonitorWeight, "Weight", profile.weightKg?.let { "${compactNumber(it)} kg" })
                        Button(onClick = { editing = true }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp)) { Icon(Icons.Rounded.Edit, null); Spacer(Modifier.width(8.dp)); Text("Edit profile") }
                    }
                }
            }
            Spacer(Modifier.height(26.dp))
            SettingsLink(Icons.Rounded.TrackChanges, "Daily goals", "Calories and macro targets", onGoals)
            SettingsLink(Icons.Rounded.Bookmarks, "Saved meals", "Meals you can add instantly", onSavedMeals)
            SettingsLink(Icons.Rounded.AutoAwesome, "Replay onboarding", "Meet Aru and explore every feature", onReplayTour)
            SettingsLink(Icons.Rounded.Logout, "Sign out", "Sign out of this account", onSignOut, destructive = true)
            Spacer(Modifier.height(28.dp))
        }
    }
}

@Composable private fun ProfileField(value: String, onChange: (String) -> Unit, label: String, suffix: String? = null, keyboard: KeyboardType = KeyboardType.Decimal, trailing: (@Composable () -> Unit)? = null, readOnly: Boolean = false) {
    OutlinedTextField(value, onChange, label = { Text(label) }, readOnly = readOnly, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = keyboard), trailingIcon = trailing ?: suffix?.let { { Text(it, color = ProfileMuted, modifier = Modifier.padding(end = 12.dp)) } }, modifier = Modifier.fillMaxWidth().semantics { contentDescription = "$label profile field" }, shape = RoundedCornerShape(20.dp))
}

@Composable private fun ProfileValue(icon: ImageVector, label: String, value: String?) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Surface(Modifier.size(42.dp), shape = CircleShape, color = ProfilePurple.copy(alpha = .1f)) { Box(contentAlignment = Alignment.Center) { Icon(icon, null, tint = ProfilePurple, modifier = Modifier.size(21.dp)) } }
        Column(Modifier.padding(start = 13.dp)) { Text(label, color = ProfileMuted, fontSize = 12.sp); Text(value ?: "Not shared", fontWeight = FontWeight.SemiBold, fontSize = 16.sp) }
    }
}

@Composable private fun SettingsLink(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit, destructive: Boolean = false) {
    Surface(Modifier.fillMaxWidth().padding(bottom = 10.dp).clickable(onClick = onClick), shape = RoundedCornerShape(22.dp), color = ProfileCard) {
        Row(Modifier.padding(17.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = if (destructive) MaterialTheme.colorScheme.error else ProfilePurple)
            Column(Modifier.weight(1f).padding(horizontal = 14.dp)) { Text(title, fontWeight = FontWeight.SemiBold, color = if (destructive) MaterialTheme.colorScheme.error else ProfileInk); Text(subtitle, color = ProfileMuted, fontSize = 12.sp) }
            Icon(Icons.Rounded.ChevronRight, null, tint = ProfileMuted)
        }
    }
}

@Composable private fun ProfileDatePicker(current: String, onSelected: (String) -> Unit, onDismiss: () -> Unit) {
    val initial = runCatching { LocalDate.parse(current).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli() }.getOrNull()
    val state = rememberDatePickerState(initialSelectedDateMillis = initial)
    DatePickerDialog(onDismissRequest = onDismiss, confirmButton = {
        TextButton(onClick = { state.selectedDateMillis?.let { onSelected(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate().toString()) } ?: onDismiss() }) { Text("Use date") }
    }, dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }) { DatePicker(state) }
}

private fun validateProfileDraft(date: String, height: String, weight: String): String? = when {
    date.isNotBlank() && runCatching { LocalDate.parse(date) }.getOrNull()?.let { it <= LocalDate.now() } != true -> "Choose a valid date of birth."
    height.isNotBlank() && height.toDoubleOrNull()?.let { it in 50.0..300.0 } != true -> "Height must be between 50 and 300 cm."
    weight.isNotBlank() && weight.toDoubleOrNull()?.let { it in 20.0..500.0 } != true -> "Weight must be between 20 and 500 kg."
    else -> null
}

private fun decimalInput(value: String) = value.filter { it.isDigit() || it == '.' }.take(6)
private fun compactNumber(value: Double) = if (value % 1.0 == 0.0) value.toInt().toString() else "%.1f".format(value)
