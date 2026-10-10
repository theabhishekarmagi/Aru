@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.aru.journal.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.BackHandler
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Cameraswitch
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.aru.journal.R
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.exifinterface.media.ExifInterface
import com.aru.journal.domain.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.time.LocalDate
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

private val Ink = Color(0xFF242126)
private val Muted = Color(0xFF8E898F)
private val Purple = Color(0xFF8354EE)
private val Paper = Color(0xFFFCF8F5)
private val Blue = Color(0xFF288BE8)
private val SoftCard = Color(0xFFFFFDFC)
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
    Column(Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 30.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.weight(.72f))
        Image(painterResource(R.drawable.aru_logo), contentDescription = "Aru logo", contentScale = ContentScale.Fit, modifier = Modifier.size(188.dp))
        Spacer(Modifier.height(34.dp))
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("Welcome to Aru 👋", fontSize = 31.sp, lineHeight = 38.sp, fontWeight = FontWeight.Bold)
            Text("The simplest way to track calories and keep a personal food journal.", color = Ink, fontSize = 16.sp, lineHeight = 23.sp)
            Text("Built for Indian meals, restaurant food, and everyday portions.", color = Muted, fontSize = 15.sp, lineHeight = 22.sp)
        }
        Spacer(Modifier.weight(1f))
        if(auth.configured) {
            Surface(onClick = onSignIn, enabled = !auth.busy, shape = CircleShape, color = Color.White, border = BorderStroke(1.5.dp, Color(0xFF777777)), modifier = Modifier.fillMaxWidth().height(60.dp).shadow(5.dp, CircleShape, ambientColor = Color(0x12000000), spotColor = Color(0x16000000))) {
                Row(Modifier.fillMaxSize().padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                    GoogleMark()
                    Spacer(Modifier.width(14.dp))
                    Text(if(auth.busy) "Signing in…" else "Sign in with Google", color = Ink, fontSize = 18.sp, fontWeight = FontWeight.Medium)
                }
            }
        } else Text(stringResource(R.string.setup_pending), color = Purple)
        auth.error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 12.dp)) }
        Text("Sign in to keep your journal connected to your account.", color = Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 18.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        Spacer(Modifier.height(30.dp))
    }
}

@Composable private fun GoogleMark() {
    Canvas(Modifier.size(25.dp).semantics { contentDescription = "Google" }) {
        val stroke = Stroke(width = size.minDimension * .18f, cap = StrokeCap.Square)
        val inset = stroke.width / 2
        val arcSize = androidx.compose.ui.geometry.Size(size.width - stroke.width, size.height - stroke.width)
        val topLeft = androidx.compose.ui.geometry.Offset(inset, inset)
        drawArc(Color(0xFF4285F4), -42f, 132f, false, topLeft, arcSize, style = stroke)
        drawArc(Color(0xFF34A853), 90f, 88f, false, topLeft, arcSize, style = stroke)
        drawArc(Color(0xFFFBBC05), 178f, 54f, false, topLeft, arcSize, style = stroke)
        drawArc(Color(0xFFEA4335), 232f, 86f, false, topLeft, arcSize, style = stroke)
        drawLine(Color(0xFF4285F4), start = androidx.compose.ui.geometry.Offset(size.width * .52f, size.height * .51f), end = androidx.compose.ui.geometry.Offset(size.width * .94f, size.height * .51f), strokeWidth = stroke.width, cap = StrokeCap.Square)
    }
}

@Composable private fun JournalScreen(state: JournalUiState, controller: JournalController, onSignOut: ()->Unit) {
    var dateString by rememberSaveable { mutableStateOf(LocalDate.now().toString()) }
    val date = LocalDate.parse(dateString)
    var sheet by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
    var focusId by remember { mutableStateOf<String?>(null) }
    var activeEntryId by remember { mutableStateOf<String?>(null) }
    var calendar by remember { mutableStateOf(false) }
    var settings by remember { mutableStateOf(false) }
    var listening by remember { mutableStateOf(false) }
    var actionError by remember { mutableStateOf<String?>(null) }
    var showCamera by remember { mutableStateOf(false) }
    var pendingPhotoPreview by remember { mutableStateOf<ByteArray?>(null) }
    var showPhotoPrivacy by remember { mutableStateOf(false) }
    var savedQuery by rememberSaveable { mutableStateOf("") }
    val focus = LocalFocusManager.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val latestState = rememberUpdatedState(state)
    val latestActiveEntry = rememberUpdatedState(activeEntryId)
    val totals = dailyTotals(state.journal.entries, dateString)
    val entries = state.journal.entries.filter { it.journalDate == dateString }

    val speechRecognizer = remember {
        if (SpeechRecognizer.isRecognitionAvailable(context)) SpeechRecognizer.createSpeechRecognizer(context) else null
    }
    DisposableEffect(speechRecognizer) {
        speechRecognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) { listening = true; actionError = null }
            override fun onBeginningOfSpeech() = Unit
            override fun onRmsChanged(rmsdB: Float) = Unit
            override fun onBufferReceived(buffer: ByteArray?) = Unit
            override fun onEndOfSpeech() { listening = false }
            override fun onError(error: Int) {
                listening = false
                if (error != SpeechRecognizer.ERROR_CLIENT && error != SpeechRecognizer.ERROR_NO_MATCH)
                    actionError = "Dictation couldn’t hear that. Please try again."
            }
            override fun onResults(results: Bundle?) {
                listening = false
                val spoken = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.trim().orEmpty()
                val id = latestActiveEntry.value
                val entry = latestState.value.journal.entries.find { it.id == id }
                if (spoken.isNotBlank() && entry != null) {
                    controller.edit(entry.id, listOf(entry.text.trim(), spoken).filter { it.isNotBlank() }.joinToString(" "))
                }
            }
            override fun onPartialResults(partialResults: Bundle?) = Unit
            override fun onEvent(eventType: Int, params: Bundle?) = Unit
        })
        onDispose { speechRecognizer?.destroy() }
    }
    val startSpeech = {
        if (speechRecognizer == null) actionError = "Dictation is not available on this device."
        else {
            speechRecognizer.cancel()
            speechRecognizer.startListening(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, java.util.Locale.getDefault().toLanguageTag())
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
                putExtra(RecognizerIntent.EXTRA_PROMPT, "Describe what you ate")
            })
            listening = true
        }
    }
    val microphonePermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) startSpeech() else actionError = "Microphone permission is required for dictation."
    }
    val cameraPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) showCamera = true else actionError = "Camera permission is required to photograph a meal."
    }
    val galleryPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            val jpeg = withContext(Dispatchers.IO) {
                context.contentResolver.openInputStream(uri)?.use { compressMealPhoto(it.readBytes()) }
            }
            if (jpeg != null) { showCamera = false; pendingPhotoPreview = jpeg; controller.analyzePhoto(jpeg, date) }
            else actionError = "That photo couldn’t be opened."
        }
    }
    val openCamera = {
        val preferences = context.getSharedPreferences("aru_privacy", 0)
        if (!preferences.getBoolean("photo_ai_notice_accepted", false)) showPhotoPrivacy = true
        else if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) showCamera = true
        else cameraPermission.launch(Manifest.permission.CAMERA)
    }
    LaunchedEffect(state.photoAnalyzing, state.photoError) {
        if (!state.photoAnalyzing && pendingPhotoPreview != null) {
            delay(if(state.photoError == null) 550 else 1200)
            pendingPhotoPreview = null
        }
    }
    Column(Modifier.fillMaxSize().safeDrawingPadding().imePadding().padding(horizontal = 24.dp)) {
        Row(Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 40.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                Image(painterResource(R.drawable.aru_logo), contentDescription = "Aru", contentScale = ContentScale.Fit, modifier = Modifier.size(width = 66.dp, height = 48.dp))
            }
            Pill(onClick = { focus.clearFocus(); calendar = true }) {
                Text(if (date == LocalDate.now()) "Today" else date.format(DateTimeFormatter.ofPattern("d MMM")), fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
            }
            Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
                Pill(onClick = { settings = true }, label = "Settings") {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("🔥", fontSize = 17.sp)
                        Text("⚙", fontSize = 22.sp)
                    }
                }
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
        LazyColumn(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(28.dp)) {
            items(entries, key = { it.id }) { entry ->
                EntryLine(entry, focusId == entry.id, { focusId = null }, { controller.edit(entry.id, it) }, {
                    focus.clearFocus(); selectedId = entry.id; sheet = "details"
                }, { controller.add("", date) { focusId = it } }, { focused ->
                    if (focused) activeEntryId = entry.id else if (activeEntryId == entry.id) activeEntryId = null
                })
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
        AnimatedVisibility(pendingPhotoPreview != null, enter = fadeIn(tween(180)), exit = fadeOut(tween(220))) {
            pendingPhotoPreview?.let { PhotoAttachmentPreview(it, state.photoAnalyzing, Modifier.fillMaxWidth().padding(bottom = 10.dp)) }
        }
        if (totals.pendingEntryCount > 0 || totals.reviewEntryCount > 0) Text(
            listOfNotNull(if(totals.pendingEntryCount > 0) "${totals.pendingEntryCount} not calculated" else null,
                if(totals.reviewEntryCount > 0) "${totals.reviewEntryCount} to review" else null).joinToString(" · "),
            fontSize = 12.sp, color = Muted, modifier = Modifier.align(Alignment.CenterHorizontally).padding(bottom = 10.dp))
        actionError?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp, modifier = Modifier.align(Alignment.CenterHorizontally).padding(bottom = 8.dp)) }
        if (state.photoAnalyzing) Row(Modifier.align(Alignment.CenterHorizontally).padding(bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(Modifier.size(15.dp), strokeWidth = 2.dp)
            Text("  Identifying your meal…", color = Muted, fontSize = 12.sp)
        }
        state.photoError?.let {
            Text(nutritionErrorMessage(it), color = MaterialTheme.colorScheme.error, fontSize = 12.sp, modifier = Modifier.align(Alignment.CenterHorizontally).padding(bottom = 4.dp))
            TextButton(onClick = controller::clearPhotoError, modifier = Modifier.align(Alignment.CenterHorizontally)) { Text("Dismiss") }
        }
        AnimatedContent(targetState = activeEntryId != null, label = "journal action bar", transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(120)) }) { editing ->
            if (editing) {
                CompactJournalBar(
                    calorieText = caloriesRemainingLabel(totals.values[0].knownAmount, state.journal.goals.targets.caloriesKcal),
                    listening = listening,
                    onCalories = { focus.clearFocus(); sheet = "goals" },
                    onMic = {
                        actionError = null
                        if (listening) { speechRecognizer?.stopListening(); listening = false }
                        else if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) startSpeech()
                        else microphonePermission.launch(Manifest.permission.RECORD_AUDIO)
                    },
                    onSaved = { focus.clearFocus(); savedQuery = ""; sheet = "saved" },
                    onCamera = {
                        if(state.photoAnalyzing) actionError = "Aru is already analyzing a meal photo."
                        else { focus.clearFocus(); openCamera() }
                    },
                    modifier = Modifier.fillMaxWidth().padding(bottom = 18.dp)
                )
            } else Pill(onClick = { focus.clearFocus(); sheet = "goals" }, modifier = Modifier.fillMaxWidth().padding(bottom = 18.dp), label = "Daily totals and goals") {
                Row(Modifier.fillMaxWidth().height(28.dp), verticalAlignment = Alignment.CenterVertically) {
                    DailyTotalMetric("🔥", null, "${number(totals.values[0].knownAmount)}${if(totals.values[0].missingItemCount > 0) "+" else ""}", Ink, Modifier.weight(1.25f), true)
                    DailyTotalMetric(null, "C", number(totals.values[2].knownAmount), colors[2], Modifier.weight(1f))
                    DailyTotalMetric(null, "P", number(totals.values[1].knownAmount), colors[1], Modifier.weight(1f))
                    DailyTotalMetric(null, "F", number(totals.values[3].knownAmount), colors[3], Modifier.weight(1f))
                }
            }
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
        Column(Modifier.fillMaxWidth().navigationBarsPadding().imePadding().padding(horizontal = 22.dp).verticalScroll(rememberScrollState()).padding(bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(when(sheet) { "goals" -> "Daily goals"; "saved" -> "Saved meals"; else -> "Nutrition Details" }, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                if(sheet == "details") Surface(shape = CircleShape, color = Color.White.copy(alpha = .8f)) {
                    Text("•••", color = Muted, modifier = Modifier.padding(horizontal = 13.dp, vertical = 8.dp), fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.width(8.dp))
                Surface(onClick = { sheet = null }, shape = CircleShape, color = Color.White.copy(alpha = .8f), modifier = Modifier.semantics { contentDescription = "Close" }) {
                    Text("×", color = Muted, fontSize = 27.sp, modifier = Modifier.padding(horizontal = 12.dp, vertical = 3.dp))
                }
            }
            when(sheet) {
                "goals" -> GoalsContent(totals, state.journal.goals) { controller.goals(it) }
                "saved" -> {
                    if(state.journal.savedMeals.isEmpty()) Text("Your go-to meals, ready for next time. Open an entry’s nutrition details to save one.", color = Muted, lineHeight = 24.sp)
                    else OutlinedTextField(savedQuery, { savedQuery = it }, placeholder = { Text("Search meals") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    state.journal.savedMeals.filter { savedQuery.isBlank() || it.name.contains(savedQuery, true) || it.text.contains(savedQuery, true) }.forEach { meal ->
                        val values = itemTotals(meal.estimate)
                        WhiteCard {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(meal.name, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                    Text(meal.text, color = Muted, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                    Text("🔥 ${number(values[0])} cal  ·  P ${number(values[1])}  ·  C ${number(values[2])}  ·  F ${number(values[3])}", color = Muted, fontSize = 11.sp, maxLines = 1)
                                }
                                FilledIconButton(onClick = { controller.reuse(meal.id, date); sheet = null }, colors = IconButtonDefaults.filledIconButtonColors(containerColor = Purple), modifier = Modifier.semantics { contentDescription = "Add ${meal.name}" }) {
                                    Icon(Icons.Rounded.Add, contentDescription = null, tint = Color.White)
                                }
                            }
                        }
                    }
                }
                else -> state.journal.entries.find { it.id == selectedId }?.let { entry ->
                    DetailsContent(entry, controller, onDelete = { controller.delete(entry.id); sheet = null })
                }
            }
        }
    }
    if (showPhotoPrivacy) AlertDialog(
        onDismissRequest = { showPhotoPrivacy = false },
        title = { Text("Analyze a meal photo") },
        text = { Text("Aru sends the photo to its AI provider only to identify the meal. Aru does not save the photo; only the dish description, nutrition estimate, confidence, and references are kept. Avoid including faces or personal information.") },
        confirmButton = { TextButton(onClick = {
            context.getSharedPreferences("aru_privacy", 0).edit().putBoolean("photo_ai_notice_accepted", true).apply()
            showPhotoPrivacy = false
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) showCamera = true
            else cameraPermission.launch(Manifest.permission.CAMERA)
        }) { Text("Continue") } },
        dismissButton = { TextButton(onClick = { showPhotoPrivacy = false }) { Text("Cancel") } }
    )
    if (showCamera) ModalBottomSheet(
        onDismissRequest = { showCamera = false },
        containerColor = Paper,
        scrimColor = Color.Black.copy(alpha = .22f),
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        dragHandle = { BottomSheetDefaults.DragHandle(color = Muted.copy(alpha = .45f)) }
    ) {
        MealCamera(
            modifier = Modifier.fillMaxWidth().heightIn(min = 370.dp, max = 500.dp).padding(horizontal = 14.dp, vertical = 8.dp),
            onClose = { showCamera = false },
            onGallery = { galleryPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
            onCaptured = { jpeg -> showCamera = false; pendingPhotoPreview = jpeg; controller.analyzePhoto(jpeg, date) },
            onError = { actionError = it; showCamera = false }
        )
        Spacer(Modifier.height(12.dp).navigationBarsPadding())
    }
}

private fun caloriesRemainingLabel(consumed: Double, goal: Double?): String = when {
    goal == null -> "${number(consumed)} cal"
    consumed <= goal -> "${number(goal - consumed)} left"
    else -> "${number(consumed - goal)} over"
}

@Composable private fun CompactJournalBar(calorieText: String, listening: Boolean, onCalories: () -> Unit, onMic: () -> Unit, onSaved: () -> Unit, onCamera: () -> Unit, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Pill(onClick = onCalories, modifier = Modifier.weight(1f), label = "Calories remaining") {
            Text("🔥 $calorieText", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
        }
        JournalActionButton(if(listening) "Stop dictation" else "Dictate meal", onMic) {
            Icon(Icons.Rounded.Mic, contentDescription = null, tint = if(listening) Blue else Ink)
        }
        JournalActionButton("Saved meals", onSaved) { Icon(Icons.Rounded.Add, contentDescription = null, tint = Ink) }
        JournalActionButton("Photograph meal", onCamera) { Icon(Icons.Rounded.PhotoCamera, contentDescription = null, tint = Ink) }
    }
}

@Composable private fun JournalActionButton(label: String, onClick: () -> Unit, icon: @Composable () -> Unit) {
    Surface(onClick = onClick, shape = CircleShape, color = Color.White.copy(alpha = .96f), modifier = Modifier.size(54.dp).shadow(12.dp, CircleShape).semantics { contentDescription = label }) {
        Box(contentAlignment = Alignment.Center, content = { icon() })
    }
}

@Composable private fun PhotoAttachmentPreview(jpeg: ByteArray, analyzing: Boolean, modifier: Modifier = Modifier) {
    val bitmap = remember(jpeg) { BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size)?.asImageBitmap() }
    Surface(modifier, color = Color.White.copy(alpha = .96f), shape = RoundedCornerShape(20.dp), shadowElevation = 6.dp) {
        Row(Modifier.padding(9.dp), verticalAlignment = Alignment.CenterVertically) {
            bitmap?.let { Image(it, "Attached meal photo", Modifier.size(66.dp).clip(RoundedCornerShape(15.dp)), contentScale = ContentScale.Crop) }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("Meal photo attached", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                Text(if(analyzing) "Identifying dishes and portions…" else "Adding meal to your journal…", color = Muted, fontSize = 12.sp)
            }
            if(analyzing) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.5.dp)
        }
    }
}

@Composable private fun MealCamera(modifier: Modifier = Modifier, onClose: () -> Unit, onGallery: () -> Unit, onCaptured: (ByteArray) -> Unit, onError: (String) -> Unit) {
    BackHandler(onBack = onClose)
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val previewView = remember { PreviewView(context).apply { scaleType = PreviewView.ScaleType.FILL_CENTER } }
    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }
    var lensFacing by remember { mutableIntStateOf(CameraSelector.LENS_FACING_BACK) }
    var takingPhoto by remember { mutableStateOf(false) }
    DisposableEffect(lifecycleOwner, lensFacing) {
        val future = ProcessCameraProvider.getInstance(context)
        var provider: ProcessCameraProvider? = null
        var disposed = false
        future.addListener({
            if (disposed) return@addListener
            try {
                provider = future.get()
                val preview = Preview.Builder().build().also { it.surfaceProvider = previewView.surfaceProvider }
                val capture = ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY).build()
                val selector = CameraSelector.Builder().requireLensFacing(lensFacing).build()
                provider?.unbindAll()
                provider?.bindToLifecycle(lifecycleOwner, selector, preview, capture)
                imageCapture = capture
            } catch (_: Exception) { onError("Camera couldn’t start on this device.") }
        }, ContextCompat.getMainExecutor(context))
        onDispose { disposed = true; imageCapture = null; provider?.unbindAll() }
    }
    Box(modifier.clip(RoundedCornerShape(28.dp)).background(Color.Black)) {
        AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())
        Surface(onClick = onClose, shape = CircleShape, color = Color.Black.copy(alpha = .45f), modifier = Modifier.padding(14.dp).size(44.dp).align(Alignment.TopStart).semantics { contentDescription = "Close camera" }) {
            Box(contentAlignment = Alignment.Center) { Icon(Icons.Rounded.Close, null, tint = Color.White) }
        }
        Surface(onClick = { lensFacing = if(lensFacing == CameraSelector.LENS_FACING_BACK) CameraSelector.LENS_FACING_FRONT else CameraSelector.LENS_FACING_BACK }, shape = CircleShape, color = Color.Black.copy(alpha = .45f), modifier = Modifier.padding(14.dp).size(44.dp).align(Alignment.TopEnd).semantics { contentDescription = "Switch camera" }) {
            Box(contentAlignment = Alignment.Center) { Icon(Icons.Rounded.Cameraswitch, null, tint = Color.White) }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 26.dp, vertical = 17.dp).align(Alignment.BottomCenter), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Surface(onClick = onGallery, shape = CircleShape, color = Color.Black.copy(alpha = .5f), modifier = Modifier.size(48.dp).semantics { contentDescription = "Choose meal photo" }) {
                Box(contentAlignment = Alignment.Center) { Icon(Icons.Rounded.PhotoLibrary, null, tint = Color.White) }
            }
            Surface(onClick = {
                val capture = imageCapture ?: return@Surface
                if(takingPhoto) return@Surface
                takingPhoto = true
                val file = File.createTempFile("aru-meal-", ".jpg", context.cacheDir)
                capture.takePicture(ImageCapture.OutputFileOptions.Builder(file).build(), ContextCompat.getMainExecutor(context), object : ImageCapture.OnImageSavedCallback {
                    override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                        scope.launch {
                            val jpeg = withContext(Dispatchers.IO) { try { compressMealPhoto(file.readBytes()) } finally { file.delete() } }
                            takingPhoto = false
                            if(jpeg != null) onCaptured(jpeg) else onError("The meal photo couldn’t be prepared.")
                        }
                    }
                    override fun onError(exception: ImageCaptureException) { takingPhoto = false; file.delete(); onError("The camera couldn’t take that photo.") }
                })
            }, shape = CircleShape, color = Color.White, border = BorderStroke(4.dp, Color.White.copy(alpha = .55f)), modifier = Modifier.size(68.dp).semantics { contentDescription = "Take meal photo" }) {
                Box(Modifier.fillMaxSize().padding(6.dp).border(2.dp, Color.Black.copy(alpha = .3f), CircleShape), contentAlignment = Alignment.Center) {
                    if(takingPhoto) CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 3.dp)
                }
            }
            Spacer(Modifier.size(48.dp))
        }
        Text("Center the whole meal", color = Color.White, fontSize = 13.sp, modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 100.dp).background(Color.Black.copy(alpha = .45f), CircleShape).padding(horizontal = 14.dp, vertical = 7.dp))
    }
}

private fun compressMealPhoto(bytes: ByteArray): ByteArray? = runCatching {
    val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return@runCatching null
    val orientation = runCatching { ExifInterface(ByteArrayInputStream(bytes)).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL) }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
    val degrees = when(orientation) {
        ExifInterface.ORIENTATION_ROTATE_90 -> 90f
        ExifInterface.ORIENTATION_ROTATE_180 -> 180f
        ExifInterface.ORIENTATION_ROTATE_270 -> 270f
        else -> 0f
    }
    val oriented = if(degrees == 0f) decoded else Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, Matrix().apply { postRotate(degrees) }, true).also { decoded.recycle() }
    val longest = maxOf(oriented.width, oriented.height)
    val scaled = if(longest > 1280) {
        val ratio = 1280f / longest
        Bitmap.createScaledBitmap(oriented, (oriented.width * ratio).roundToInt(), (oriented.height * ratio).roundToInt(), true).also { oriented.recycle() }
    } else oriented
    var quality = 86
    var result: ByteArray
    do {
        result = ByteArrayOutputStream().use { output -> scaled.compress(Bitmap.CompressFormat.JPEG, quality, output); output.toByteArray() }
        quality -= 8
    } while(result.size > 1_450_000 && quality >= 54)
    scaled.recycle()
    result.takeIf { it.size <= 1_500_000 }
}.getOrNull()

@Composable private fun DailyTotalMetric(icon: String?, label: String?, value: String, color: Color, modifier: Modifier, prominent: Boolean = false) {
    Row(modifier, horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
        icon?.let { Text(it, fontSize = 15.sp, maxLines = 1) }
        label?.let { Text(it, color = color, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1) }
        Spacer(Modifier.width(if(icon != null) 5.dp else 3.dp))
        Text(value, color = Ink, fontSize = if(prominent) 16.sp else 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, softWrap = false, overflow = TextOverflow.Clip)
    }
}

@Composable private fun EntryLine(entry: JournalEntry, requestFocus: Boolean, onFocused: ()->Unit, onEdit: (String)->Unit, onDetails: ()->Unit, onNext: ()->Unit, onFocusChanged: (Boolean)->Unit) {
    var text by rememberSaveable(entry.id) { mutableStateOf(entry.text) }
    LaunchedEffect(entry.text) { if(entry.text != text && entry.text.length >= text.length) text = entry.text }
    val requester = remember { FocusRequester() }
    LaunchedEffect(requestFocus) { if(requestFocus) { requester.requestFocus(); onFocused() } }
    var observedEstimateAt by remember(entry.id) { mutableLongStateOf(entry.estimate?.calculatedAtEpochMillis ?: -1L) }
    var revealStage by remember(entry.id) { mutableIntStateOf(2) }
    LaunchedEffect(entry.estimate?.calculatedAtEpochMillis) {
        val calculatedAt = entry.estimate?.calculatedAtEpochMillis ?: return@LaunchedEffect
        if(observedEstimateAt != calculatedAt) {
            observedEstimateAt = calculatedAt
            revealStage = 1
            delay(1200)
            revealStage = 2
        }
    }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        BasicTextField(value = text, onValueChange = { next -> text = next; onEdit(next) }, modifier = Modifier.weight(1f).focusRequester(requester).onFocusChanged { onFocusChanged(it.isFocused) }.semantics { contentDescription = "Food entry" },
            textStyle = TextStyle(color = Ink, fontSize = 18.sp, lineHeight = 29.sp, fontWeight = FontWeight.Medium), cursorBrush = androidx.compose.ui.graphics.SolidColor(Purple),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next), keyboardActions = KeyboardActions(onNext = { onNext() }),
            decorationBox = { inner -> Box { if(text.isEmpty()) Text("Write what you ate…", color = Muted, fontSize = 18.sp); inner() } })
        TextButton(onClick = onDetails, contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp), modifier = Modifier.heightIn(min = 48.dp).widthIn(min = 78.dp, max = 126.dp).offset(y = (-10).dp).semantics { contentDescription = "Nutrition for ${entry.text}" }) {
            AnimatedContent(targetState = when {
                entry.status in setOf(CalculationStatus.CALCULATING, CalculationStatus.QUEUED) -> 0
                entry.status == CalculationStatus.FAILED -> 3
                entry.estimate != null && revealStage == 1 -> 1
                entry.estimate != null -> 2
                else -> 4
            }, transitionSpec = { fadeIn(tween(280)) togetherWith fadeOut(tween(180)) }, label = "nutrition status") { stage ->
                when(stage) {
                    0 -> ThinkingLabel()
                    1 -> SourceLabel(entry.estimate?.items?.flatMap { it.sources }?.distinct()?.size ?: 0)
                    2 -> entry.estimate?.let { CalorieLabel(itemTotals(it)[0]) } ?: ThinkingLabel()
                    3 -> Text("Retry", color = Muted, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                    else -> Text(if(entry.text.isBlank()) "" else "Thinking", color = Muted.copy(alpha = .55f), fontSize = 16.sp)
                }
            }
        }
    }
}

@Composable private fun ThinkingLabel() {
    val transition = rememberInfiniteTransition(label = "thinking")
    val pulse by transition.animateFloat(.42f, 1f, infiniteRepeatable(tween(720, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "thinking pulse")
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp), modifier = Modifier.alpha(pulse)) {
        Text("Thinking", color = Muted, fontSize = 16.sp, fontWeight = FontWeight.Medium)
        Text("•••", color = Purple, fontSize = 12.sp, letterSpacing = 1.sp)
    }
}

@Composable private fun SourceLabel(count: Int) {
    val scale by animateFloatAsState(1f, tween(420, easing = FastOutSlowInEasing), label = "source reveal")
    Row(verticalAlignment = Alignment.CenterVertically) {
        SourceDots(count)
        Text("${count.coerceAtLeast(1)} source${if(count == 1) "" else "s"}", color = Muted, fontSize = 15.sp, fontWeight = FontWeight.Medium, modifier = Modifier.alpha(scale))
    }
}

@Composable private fun SourceDots(count: Int) {
    Row(Modifier.width(31.dp), horizontalArrangement = Arrangement.spacedBy((-7).dp)) {
        listOf(Color(0xFFF05252), Color(0xFFFFB43B), Color(0xFFB9C7E7)).take(count.coerceIn(1, 3)).forEach { color ->
            Box(Modifier.size(17.dp).clip(CircleShape).background(color).border(1.dp, Paper, CircleShape))
        }
    }
}

@Composable private fun CalorieLabel(calories: Double?) {
    val animated = remember { Animatable(0f) }
    var settled by remember(calories) { mutableStateOf(false) }
    LaunchedEffect(calories) {
        settled = false
        animated.snapTo(0f)
        animated.animateTo((calories ?: 0.0).toFloat(), tween(720, easing = FastOutSlowInEasing))
        delay(650)
        settled = true
    }
    AnimatedContent(targetState = settled, transitionSpec = { fadeIn(tween(240)) togetherWith fadeOut(tween(180)) }, label = "calorie settle") { isSettled ->
        if(isSettled) Text("${calories?.roundToInt() ?: "—"} cal", color = Muted, fontSize = 16.sp, fontWeight = FontWeight.Normal)
        else Text("✦ ${animated.value.roundToInt()} cal", color = Blue, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
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
        Text(entry.failureCode?.let { nutritionErrorMessage(it) } ?: "Nutrition calculates automatically when you pause typing. You can also enter values manually. Unknown values stay blank.", color = Muted, modifier = Modifier.padding(top = 8.dp))
    } else {
        val sums = itemTotals(estimate)
        NutritionSummaryCard(sums, estimate.calculatedAtEpochMillis)
        Text("Items", color = Muted, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        estimate.items.forEach { item ->
            var expanded by remember(item.name) { mutableStateOf(false) }
            Surface(color = SoftCard, shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth().shadow(10.dp, RoundedCornerShape(18.dp), ambientColor = Color(0x14000000), spotColor = Color(0x16000000))) {
                Column(Modifier.padding(horizontal = 18.dp, vertical = 12.dp)) {
                    Row(Modifier.fillMaxWidth().clickable { expanded = !expanded }.padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(item.name, modifier = Modifier.weight(1f), fontWeight = FontWeight.Medium, fontSize = 16.sp)
                        Text("${number(item.nutrients.caloriesKcal)} cal", fontWeight = FontWeight.SemiBold)
                        Text(if(expanded) "  ⌃" else "  ⌄", color = Muted)
                    }
                    Text("${number(item.portion.quantity)} ${item.portion.unitKey}${if(item.portion.assumed) " · estimated portion" else ""}", color = Muted, fontSize = 13.sp)
                    AnimatedVisibility(expanded) {
                        Column {
                            NutrientRow(item.nutrients.values())
                            (item.assumptions + listOfNotNull(item.portion.assumption)).distinct().forEach { Text(it, color = Purple, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp)) }
                        }
                    }
                }
            }
        }
        Text("How confident is Aru?", color = Muted, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        WhiteCard {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(bottom = 10.dp)) {
                ConfidenceRing(estimate.confidenceScore)
                Column {
                    Text("Confidence level", color = Muted, fontSize = 13.sp)
                    Text(confidenceLabel(estimate.confidenceScore), color = confidenceColor(estimate.confidenceScore), fontWeight = FontWeight.Bold)
                }
            }
            Text(displayExplanation(estimate.explanation), lineHeight = 24.sp, fontSize = 15.sp)
            TextButton(onClick = { edit = true }) { Text("Something off? Edit nutrition") }
        }
        Text("References", color = Muted, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        val uri = LocalUriHandler.current
        val references = displayReferences(entry, estimate)
        WhiteCard {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                SourceDots(references.size)
                Spacer(Modifier.width(10.dp))
                Text("${references.size} reference${if(references.size == 1) "" else "s"}", color = Muted, modifier = Modifier.weight(1f))
            }
        }
        references.forEach { source ->
            WhiteCard {
                Text(source.title, color = if(source.url != null) Purple else Ink, fontWeight = FontWeight.Medium)
                listOfNotNull(source.recordId, source.version, source.market, source.menuItem, source.menuSize, source.basis).takeIf { it.isNotEmpty() }?.let { Text(it.joinToString(" · "), fontSize = 12.sp, color = Muted) }
                source.url?.let { url -> TextButton(onClick = { uri.openUri(url) }) { Text("Open source ↗") } }
            }
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        TextButton(onClick = { edit = !edit }, enabled = entry.text.isNotBlank()) { Text("Edit nutrition") }
        if(controller.canEstimate && entry.status == CalculationStatus.FAILED) TextButton(onClick = { controller.calculate(entry.id) }, enabled = entry.text.isNotBlank() && entry.status !in setOf(CalculationStatus.CALCULATING, CalculationStatus.QUEUED)) { Text("Retry") }
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

private fun displayExplanation(value: String) = value.replaceFirst(Regex("^AI estimate\\s*[—-]\\s*", RegexOption.IGNORE_CASE), "")

private fun displayReferences(entry: JournalEntry, estimate: NutritionEstimate): List<SourceReference> {
    val trusted = estimate.items.flatMap { it.sources }.filter { it.kind != SourceKind.AI_ESTIMATE }.toMutableList()
    if(trusted.none { it.kind == SourceKind.ARU_DATABASE }) trusted += SourceReference(SourceKind.ARU_DATABASE, "Aru nutrition reference library", basis = "Saved nutrition result and portion assumptions.")
    val text = entry.text.lowercase()
    if("burger king" in text && trusted.none { it.kind == SourceKind.OFFICIAL_RESTAURANT }) trusted += SourceReference(
        SourceKind.OFFICIAL_RESTAURANT, "Burger King India nutrition information",
        "https://hygiene.fssai.gov.in/files/reports/quiz21668916_raw%20material%20details.pdf",
        market = "India", menuItem = entry.text, menuSize = "Described portion",
        basis = "Official brand reference. Confirm the exact menu variant because recipes and serving sizes can change."
    )
    val indianDish = Regex("\\b(idli|dosa|sambar|poha|upma|roti|chapati|paratha|biryani|pulao|dal|rajma|chole|paneer|sabzi|curry|khichdi|chaat|samosa|vada|uttapam|appam|pongal|dhokla|aloo)\\b").containsMatchIn(text)
    if(indianDish && trusted.none { it.kind == SourceKind.INDB }) trusted += SourceReference(
        SourceKind.INDB, "Indian Nutrient Databank (INDB)", "https://www.anuvaad.org.in/indian-nutrient-databank/",
        recordId = "INDB recipe catalogue", version = "2024 publication", basis = "Indian recipe reference; preparation and serving size may vary."
    )
    return trusted.distinct()
}

@Composable private fun ConfidenceRing(score: Int) {
    val progress by animateFloatAsState(score.coerceIn(0, 100) / 100f, tween(700, easing = FastOutSlowInEasing), label = "confidence")
    val color = confidenceColor(score)
    Box(Modifier.size(58.dp), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxSize(), color = color, trackColor = Color(0xFFE8E4E7), strokeWidth = 5.dp)
        Text(score.toString(), color = color, fontSize = 15.sp, fontWeight = FontWeight.Bold)
    }
}

private fun confidenceLabel(score: Int) = when {
    score >= 80 -> "High"
    score >= 60 -> "Good"
    else -> "Review suggested"
}

private fun confidenceColor(score: Int) = when {
    score >= 80 -> Color(0xFF22B86A)
    score >= 60 -> Color(0xFFE49B19)
    else -> Purple
}

@Composable private fun NutritionSummaryCard(values: List<Double?>, calculatedAt: Long) {
    val calories = remember(calculatedAt) { Animatable(0f) }
    LaunchedEffect(calculatedAt) { calories.animateTo((values[0] ?: 0.0).toFloat(), tween(760, easing = FastOutSlowInEasing)) }
    Surface(color = SoftCard, shape = RoundedCornerShape(22.dp), modifier = Modifier.fillMaxWidth().shadow(14.dp, RoundedCornerShape(22.dp), ambientColor = Color(0x14000000), spotColor = Color(0x16000000))) {
        Column(Modifier.padding(horizontal = 18.dp, vertical = 22.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                Text("🔥", fontSize = 27.sp)
                Spacer(Modifier.width(8.dp))
                Text(calories.value.roundToInt().toString(), fontSize = 40.sp, fontWeight = FontWeight.Bold)
                Text("  total calories", color = Muted, fontSize = 14.sp)
            }
            NutrientRow(values)
        }
    }
}

@Composable private fun NutrientRow(values: List<Double?>) {
    Row(Modifier.fillMaxWidth().padding(top = 18.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
        (1..3).forEach { i -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("${number(values[i])} g", fontWeight = FontWeight.Medium, fontSize = 15.sp)
            Text("${listOf("✦", "●", "♦")[i-1]} ${names[i]}", color = colors[i], fontSize = 11.sp)
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
            sources = listOf(SourceReference(SourceKind.USER,"Manually entered by you")), assumptions = emptyList()) }, false, "Nutrition and portions were entered manually. Values apply to the portions shown.", System.currentTimeMillis(), 100))
    }, enabled = valid) { Text("Save nutrition") }
}

private fun toNutrients(fields: List<String>) = fields.map { it.toDoubleOrNull() }.let { Nutrients(it[0],it[1],it[2],it[3],it[4]) }
@Composable private fun NutrientFields(fields: List<String>, change: (List<String>)->Unit) {
    names.forEachIndexed { i, name -> OutlinedTextField(fields[i], { v -> change(fields.toMutableList().also { it[i] = v }) }, label = { Text("$name (${if(i == 0) "kcal" else "g"})") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true, modifier = Modifier.fillMaxWidth()) }
}
@Composable private fun WhiteCard(content: @Composable ColumnScope.()->Unit) {
    Surface(color = SoftCard, shape = RoundedCornerShape(22.dp), modifier = Modifier.fillMaxWidth().shadow(10.dp, RoundedCornerShape(22.dp), ambientColor = Color(0x10000000), spotColor = Color(0x16000000))) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(3.dp), content = content)
    }
}
@Composable private fun Pill(onClick: ()->Unit, modifier: Modifier = Modifier, label: String? = null, content: @Composable ()->Unit) {
    Surface(onClick = onClick, shape = CircleShape, color = Color.White.copy(alpha = .94f), modifier = modifier.heightIn(min = 52.dp).shadow(16.dp, CircleShape, ambientColor = Color(0x22B5A4AD), spotColor = Color(0x26D5C5BB)).then(if(label != null) Modifier.semantics { contentDescription = label } else Modifier)) {
        Box(Modifier.padding(horizontal = 20.dp, vertical = 13.dp), contentAlignment = Alignment.Center) { content() }
    }
}

internal fun nutritionErrorMessage(code: String): String = when(code) {
    "not_configured" -> "Nutrition service setup is incomplete. You can still enter values manually."
    "provider_quota" -> "OpenRouter cannot serve this request with the current account limits. Check the OpenRouter key and account limits. Manual nutrition still works."
    "provider_configuration" -> "Nutrition service credentials need attention. You can enter nutrition manually."
    "provider_credentials" -> "The OpenRouter API key is invalid or revoked. Replace OPENROUTER_API_KEY in Supabase, then try again."
    "provider_access_denied" -> "OpenRouter denied access for this API key. Check the key permissions and account settings."
    "rate_limited" -> "Calculation limit reached. Try later or enter nutrition manually."
    "invalid_request" -> "Use a food description up to 2,000 characters."
    "photo_too_large", "payload_too_large" -> "That photo is too large to analyze. Try taking it again."
    "no_food", "uncertain_food" -> "Aru couldn’t identify enough food information. Try a clearer description or photo."
    "account_required" -> "Please sign in again to calculate nutrition."
    "timeout", "provider_busy", "provider_rate_limit", "provider_unavailable" -> "The nutrition service is busy. Please retry shortly."
    else -> "Calculation couldn’t finish. Retry or enter nutrition manually."
}
