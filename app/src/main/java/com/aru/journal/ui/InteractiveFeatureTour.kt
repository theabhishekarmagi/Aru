@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.aru.journal.ui

import android.Manifest
import android.animation.ValueAnimator
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.aru.journal.domain.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.util.UUID

private val TourPurple = Color(0xFF8354EE)
private val TourMuted = Color(0xFF8E898F)
private val TourCard = Color(0xFFFFFDFC)

@Composable
fun FeatureTour(
    controller: JournalController,
    saving: Boolean,
    error: String?,
    replay: Boolean = false,
    onFinish: () -> Unit,
    onSkip: () -> Unit,
    onPreviousIntro: (() -> Unit)? = null,
    dictationOverride: MealDictation? = null
) {
    val state by controller.state.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    var page by rememberSaveable { mutableIntStateOf(0) }
    val textId = rememberSaveable { UUID.randomUUID().toString() }
    val speechId = rememberSaveable { UUID.randomUUID().toString() }
    var text by rememberSaveable { mutableStateOf("") }
    var spoken by rememberSaveable { mutableStateOf("") }
    var photoId by rememberSaveable { mutableStateOf<String?>(null) }
    var speechAdded by rememberSaveable { mutableStateOf(false) }
    var micAsked by rememberSaveable { mutableStateOf(false) }
    var cameraAsked by rememberSaveable { mutableStateOf(false) }
    var micGranted by remember { mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) }
    var cameraGranted by remember { mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) }
    var startAfterPermission by remember { mutableStateOf(false) }
    var openAfterPermission by remember { mutableStateOf(false) }
    var listening by remember { mutableStateOf(false) }
    var speechError by remember { mutableStateOf<String?>(null) }
    var cameraError by remember { mutableStateOf<String?>(null) }
    var showCamera by remember { mutableStateOf(false) }
    var showPhotoNotice by remember { mutableStateOf(false) }
    var chooseGalleryAfterNotice by remember { mutableStateOf(false) }
    var pendingPhoto by remember { mutableStateOf<ByteArray?>(null) }
    var showTotals by rememberSaveable { mutableStateOf(false) }
    var totalsHintShown by rememberSaveable { mutableStateOf(false) }
    var totalsPulseCount by remember { mutableIntStateOf(0) }
    val totalsPulse = remember { Animatable(0f) }
    val motionEnabled = ValueAnimator.areAnimatorsEnabled()
    val dictation = remember(context, dictationOverride) { dictationOverride ?: AndroidMealDictation(context) }
    DisposableEffect(dictation) { onDispose { dictation.close() } }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle, dictation) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE) { dictation.stop(); listening = false }
            if (event == Lifecycle.Event.ON_RESUME) {
                micGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
                cameraGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }

    fun closeInput() { focus.clearFocus(); keyboard?.hide() }
    fun startDictation() {
        closeInput()
        speechError = null
        val base = spoken.trim()
        dictation.start(
            onText = { result -> spoken = listOf(base, result.trim()).filter(String::isNotBlank).joinToString(" "); speechAdded = false },
            onListening = { listening = it }, onError = { speechError = it }
        )
    }
    val microphonePermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        micGranted = granted
        if (!granted) speechError = "Microphone access was declined. You can type your meal or skip this step."
        else if (startAfterPermission && page == 1) startDictation()
        startAfterPermission = false
    }
    val cameraPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        cameraGranted = granted
        if (!granted) cameraError = "Camera access was declined. You can choose a photo or skip this step."
        else if (openAfterPermission && page == 2) showCamera = true
        openAfterPermission = false
    }
    fun submitPhoto(jpeg: ByteArray) {
        showCamera = false
        pendingPhoto = jpeg
        photoId = null
        controller.analyzePhoto(jpeg, LocalDate.now()) { photoId = it; pendingPhoto = null }
    }
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch {
            val jpeg = withContext(Dispatchers.IO) { runCatching { context.contentResolver.openInputStream(uri)?.use { compressMealPhoto(it.readBytes()) } }.getOrNull() }
            if (jpeg != null) submitPhoto(jpeg) else cameraError = "That photo couldn’t be opened. Please try another."
        }
    }
    fun openCamera() {
        closeInput()
        cameraError = null
        chooseGalleryAfterNotice = false
        if (!context.getSharedPreferences("aru_privacy", 0).getBoolean("photo_ai_notice_accepted", false)) showPhotoNotice = true
        else if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) showCamera = true
        else { cameraAsked = true; openAfterPermission = true; cameraPermission.launch(Manifest.permission.CAMERA) }
    }
    LaunchedEffect(page) {
        if (page != 1) { dictation.stop(); listening = false }
        if (page == 1 && !micAsked) {
            micAsked = true
            if (!micGranted) microphonePermission.launch(Manifest.permission.RECORD_AUDIO)
        }
        if (page == 2 && !cameraAsked) {
            cameraAsked = true
            if (!cameraGranted) cameraPermission.launch(Manifest.permission.CAMERA)
        }
        if (page == 4 && !totalsHintShown) {
            totalsHintShown = true
            if (motionEnabled) repeat(2) {
                totalsPulseCount++
                totalsPulse.animateTo(1f, tween(450))
                totalsPulse.animateTo(0f, tween(450))
                delay(180)
            }
        }
        if (page != 4) totalsPulse.snapTo(0f)
    }
    LaunchedEffect(state.photoAnalyzing, state.photoError) { if (!state.photoAnalyzing) pendingPhoto = null }

    val textEntry = state.journal.entries.find { it.id == textId }
    val speechEntry = state.journal.entries.find { it.id == speechId }
    val photoEntry = state.journal.entries.find { it.id == photoId }
    val completed = setOf(CalculationStatus.READY, CalculationStatus.MANUAL, CalculationStatus.NEEDS_REVIEW)
    val saveCandidate = listOfNotNull(textEntry, speechEntry, photoEntry).firstOrNull { it.status in completed && it.estimate != null }
        ?: state.journal.entries.firstOrNull { it.journalDate == LocalDate.now().toString() && it.status in completed && it.estimate != null }
    val saved = saveCandidate?.let { candidate -> state.journal.savedMeals.firstOrNull { it.text == candidate.text && it.estimate == candidate.estimate } }
    var savingMeal by remember { mutableStateOf(false) }
    LaunchedEffect(saved, state.error) { if (saved != null || state.error != null) savingMeal = false }
    val totals = dailyTotals(state.journal.entries, LocalDate.now().toString())
    val titles = listOf("Write what you ate", "Dictate your meal", "Photograph your plate", "Reuse saved meals", "Understand your day")
    val bodies = listOf(
        "Try your own meal below. After you pause, Aru calculates its nutrition and saves it in today’s journal.",
        "Tap the microphone, describe your meal, then review the words below. Add it to your journal when you’re ready.",
        "Try the compact camera. The identified meal and nutrition go into your journal; Aru does not keep the photo.",
        "Tap + to save your meal for next time. Adding a saved meal reuses its nutrition instantly.",
        "Tap your daily bar to see calories and macros together. These totals come from your journal."
    )
    val icons = listOf(Icons.Rounded.EditNote, Icons.Rounded.Mic, Icons.Rounded.PhotoCamera, Icons.Rounded.Bookmarks, Icons.Rounded.Insights)

    Column(Modifier.fillMaxSize().safeDrawingPadding().imePadding().padding(horizontal = 24.dp, vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(if (replay) "Try Aru’s features" else "Welcome to Aru", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            TextButton(onClick = { dictation.stop(); closeInput(); onSkip() }, enabled = !saving) { Text("Skip") }
        }
        AnimatedContent(page, modifier = Modifier.weight(1f).fillMaxWidth(), transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(120)) }, label = "interactive tour page") { index ->
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(vertical = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Surface(Modifier.size(80.dp), shape = RoundedCornerShape(26.dp), color = TourPurple.copy(alpha = .12f)) {
                    Box(contentAlignment = Alignment.Center) { Icon(icons[index], null, Modifier.size(40.dp), tint = TourPurple) }
                }
                Text(titles[index], fontSize = 28.sp, lineHeight = 34.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 20.dp))
                Text(bodies[index], color = TourMuted, fontSize = 15.sp, lineHeight = 22.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 10.dp, bottom = 24.dp))
                when (index) {
                    0 -> {
                        OutlinedTextField(text, {
                            text = it
                            controller.upsertDraft(textId, it, LocalDate.now())
                        }, placeholder = { Text("e.g. 2 idlis and a bowl of sambar") }, label = { Text("Your meal") }, modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Tour meal text" }, shape = RoundedCornerShape(22.dp), minLines = 2, maxLines = 4)
                        TourMealResult(textEntry, controller)
                    }
                    1 -> {
                        FilledIconButton(onClick = {
                            if (listening) { dictation.stop(); listening = false }
                            else if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) startDictation()
                            else { startAfterPermission = true; micAsked = true; microphonePermission.launch(Manifest.permission.RECORD_AUDIO) }
                        }, modifier = Modifier.size(64.dp).semantics { contentDescription = if (listening) "Stop tour dictation" else "Start tour dictation" }) {
                            Icon(if (listening) Icons.Rounded.Stop else Icons.Rounded.Mic, null, Modifier.size(30.dp))
                        }
                        Text(if (listening) "Listening…" else "Tap to dictate", color = TourPurple, modifier = Modifier.padding(8.dp))
                        OutlinedTextField(spoken, { spoken = it; speechAdded = false }, readOnly = listening, label = { Text("Your dictated meal") }, placeholder = { Text("Your words will appear here") }, minLines = 2, maxLines = 4, modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Tour dictated text" }, shape = RoundedCornerShape(22.dp))
                        Button(onClick = { closeInput(); controller.upsertDraft(speechId, spoken, LocalDate.now()); speechAdded = true }, enabled = spoken.isNotBlank() && !speechAdded && !listening, modifier = Modifier.padding(top = 12.dp)) { Text(if (speechAdded) "Added to journal" else "Add to journal") }
                        if (speechAdded) TourMealResult(speechEntry, controller)
                        speechError?.let { TourMessage(it); if (!micGranted) TourPermissionSettings() }
                    }
                    2 -> {
                        Button(onClick = ::openCamera, enabled = !state.photoAnalyzing) { Icon(Icons.Rounded.PhotoCamera, null); Spacer(Modifier.width(8.dp)); Text("Open camera") }
                        TextButton(onClick = {
                            closeInput()
                            if (context.getSharedPreferences("aru_privacy", 0).getBoolean("photo_ai_notice_accepted", false)) gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                            else { chooseGalleryAfterNotice = true; showPhotoNotice = true }
                        }, enabled = !state.photoAnalyzing) { Text("Choose a photo") }
                        pendingPhoto?.let { PhotoAttachmentPreview(it, state.photoAnalyzing, Modifier.fillMaxWidth().padding(top = 12.dp)) }
                        if (state.photoAnalyzing && pendingPhoto == null) LinearProgressIndicator(Modifier.fillMaxWidth())
                        photoEntry?.let { Text(it.estimate?.summary ?: it.text, modifier = Modifier.padding(top = 12.dp)); TourMealResult(it, controller) }
                        cameraError?.let { TourMessage(it); if (!cameraGranted) TourPermissionSettings() }
                        state.photoError?.let { TourMessage(nutritionErrorMessage(it)) }
                    }
                    3 -> {
                        if (saveCandidate == null) {
                            TourMessage("Calculate a meal first, then save it here.")
                            TextButton(onClick = { page = 0 }) { Text("Try writing a meal") }
                        } else {
                            val pulse = if (saved == null && !savingMeal && motionEnabled) {
                                val animation = rememberInfiniteTransition(label = "save meal hint")
                                val value by animation.animateFloat(0f, 1f, infiniteRepeatable(tween(650), RepeatMode.Reverse), label = "save plus pulse")
                                value
                            } else 0f
                            Surface(shape = RoundedCornerShape(24.dp), color = TourCard, modifier = Modifier.fillMaxWidth()) {
                                Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Text(saveCandidate.estimate?.summary ?: saveCandidate.text, modifier = Modifier.weight(1f), maxLines = 2)
                                    FilledIconButton(onClick = {
                                        savingMeal = true
                                        controller.clearError()
                                        controller.saveMeal(saveCandidate.id, (saveCandidate.estimate?.summary ?: saveCandidate.text).take(100))
                                    }, enabled = saved == null && !savingMeal, modifier = Modifier.padding(start = 12.dp).size(52.dp).scale(1f + .12f * pulse).semantics { contentDescription = "Save tour meal" }) {
                                        Icon(if (saved != null) Icons.Rounded.Check else Icons.Rounded.Add, null)
                                    }
                                }
                            }
                            Text(if (saved != null) "Saved! Find this meal under + in your journal." else "Tap + to save this meal", color = TourPurple, modifier = Modifier.padding(top = 12.dp))
                        }
                        state.journal.savedMeals.take(3).forEach { meal ->
                            OutlinedButton(onClick = { controller.reuse(meal.id, LocalDate.now()) }, modifier = Modifier.fillMaxWidth().padding(top = 10.dp).semantics { contentDescription = "Reuse tour meal ${meal.name}" }) { Text("Add ${meal.name} to today", maxLines = 2) }
                        }
                    }
                    4 -> {
                        Surface(onClick = { showTotals = true }, shape = RoundedCornerShape(30.dp), color = TourCard,
                            border = BorderStroke(1.dp + 2.dp * totalsPulse.value, TourPurple.copy(alpha = .12f + .65f * totalsPulse.value)),
                            modifier = Modifier.fillMaxWidth().scale(1f + .025f * totalsPulse.value).semantics {
                                contentDescription = "Tour daily nutrition bar"
                                stateDescription = "Hint pulses: $totalsPulseCount"
                            }) {
                            Row(Modifier.padding(horizontal = 12.dp, vertical = 20.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                                listOf(0 to "🔥", 2 to "C", 1 to "P", 3 to "F").forEach { (i, label) ->
                                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                                        Text(label, color = TourPurple, fontSize = 13.sp)
                                        Text(tourNumber(totals.values[i].knownAmount) + if (totals.values[i].missingItemCount > 0) "+" else "", fontWeight = FontWeight.SemiBold, fontSize = 16.sp, maxLines = 1)
                                    }
                                }
                            }
                        }
                        Text("Tap to see your nutrition", color = TourPurple, modifier = Modifier.padding(top = 12.dp))
                        if (totals.pendingEntryCount > 0) TourMessage("${totals.pendingEntryCount} meal(s) still waiting for nutrition.")
                    }
                }
                (error ?: state.error)?.let { TourMessage(it) }
            }
        }
        Row(Modifier.padding(vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            titles.indices.forEach { Box(Modifier.size(if (it == page) 22.dp else 7.dp, 7.dp).background(if (it == page) TourPurple else Color(0xFFD8D0DF), CircleShape)) }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = { closeInput(); if (page > 0) page-- else onPreviousIntro?.invoke() }, enabled = (page > 0 || onPreviousIntro != null) && !saving, modifier = Modifier.weight(1f).heightIn(min = 54.dp)) { Text("Previous") }
            Button(onClick = { closeInput(); if (page < titles.lastIndex) page++ else onFinish() }, enabled = !saving, modifier = Modifier.weight(1f).heightIn(min = 54.dp)) {
                Text(if (page == titles.lastIndex) { if (replay) "Back to journal" else "Start journaling" } else "Next", textAlign = TextAlign.Center)
            }
        }
    }
    if (showPhotoNotice) AlertDialog(onDismissRequest = { showPhotoNotice = false }, title = { Text("Analyze a meal photo") },
        text = { Text("Aru sends the photo to its AI provider to identify the meal. Only the description and nutrition are saved in your journal; Aru does not save the photo.") },
        confirmButton = { TextButton(onClick = {
            context.getSharedPreferences("aru_privacy", 0).edit().putBoolean("photo_ai_notice_accepted", true).apply()
            showPhotoNotice = false
            if (chooseGalleryAfterNotice) gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) else openCamera()
        }) { Text("Continue") } },
        dismissButton = { TextButton(onClick = { showPhotoNotice = false }) { Text("Cancel") } })
    if (showCamera) ModalBottomSheet(onDismissRequest = { showCamera = false }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        MealCamera(Modifier.fillMaxWidth().heightIn(min = 370.dp, max = 500.dp).padding(14.dp), onClose = { showCamera = false }, onGallery = { gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }, onCaptured = ::submitPhoto, onError = { cameraError = it; showCamera = false })
        Spacer(Modifier.navigationBarsPadding())
    }
    if (showTotals) ModalBottomSheet(onDismissRequest = { showTotals = false }) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(24.dp).navigationBarsPadding(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("Today’s nutrition", fontSize = 24.sp, fontWeight = FontWeight.Bold)
            listOf("Calories", "Protein", "Carbs", "Fat", "Fiber").forEachIndexed { i, label ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(label)
                    Text("${tourNumber(totals.values[i].knownAmount)}${if (totals.values[i].missingItemCount > 0) "+" else ""} ${if (i == 0) "kcal" else "g"}", fontWeight = FontWeight.Bold)
                }
            }
            if (totals.pendingEntryCount > 0) TourMessage("${totals.pendingEntryCount} meal(s) are not included yet.")
            if (totals.values.any { it.missingItemCount > 0 }) TourMessage("A + marks a nutrient total with missing values.")
            TextButton(onClick = { showTotals = false }) { Text("Close nutrition") }
        }
    }
}

@Composable private fun TourMealResult(entry: JournalEntry?, controller: JournalController) {
    if (entry == null || entry.text.isBlank()) return
    when (entry.status) {
        CalculationStatus.DRAFT, CalculationStatus.QUEUED, CalculationStatus.CALCULATING -> {
            if (controller.canEstimate) {
                LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 16.dp))
                TourMessage("Calculating your meal…")
            } else TourMessage("Your meal is saved. Nutrition calculation is not connected yet.")
        }
        CalculationStatus.FAILED -> {
            TourMessage(nutritionErrorMessage(entry.failureCode ?: "unavailable"))
            TextButton(onClick = { controller.calculate(entry.id) }) { Text("Retry nutrition") }
        }
        else -> entry.estimate?.let { estimate ->
            val calories = if (estimate.items.any { it.nutrients.caloriesKcal == null }) "—" else tourNumber(estimate.items.sumOf { it.nutrients.caloriesKcal ?: 0.0 })
            Text("$calories cal · Saved to today’s journal", color = TourPurple, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 16.dp))
        }
    }
}
@Composable private fun TourMessage(message: String) { Text(message, color = TourMuted, fontSize = 13.sp, lineHeight = 19.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 12.dp)) }
@Composable private fun TourPermissionSettings() {
    val context = LocalContext.current
    TextButton(onClick = { context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))) }) { Text("Open app settings") }
}
private fun tourNumber(value: Double) = if (value % 1.0 == 0.0) value.toLong().toString() else "%.1f".format(value)
