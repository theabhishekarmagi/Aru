package com.aru.journal

import com.aru.journal.auth.UnconfiguredSessionProvider

import android.Manifest
import android.graphics.Bitmap
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.aru.journal.auth.*
import com.aru.journal.data.AtomicJournalStore
import com.aru.journal.domain.*
import com.aru.journal.ui.*
import kotlinx.coroutines.*
import org.junit.*
import java.io.File
import java.time.LocalDate
import java.time.ZoneId
import android.content.ContextWrapper

/** Test sessions/fixtures exist only in the instrumentation APK, never in the app. */
class JournalUiTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var repo: JournalRepository
    private lateinit var controller: JournalController
    private val date = LocalDate.now()
    @After fun finish() { scope.cancel() }
    private fun launch(seed: Boolean = false, estimator: NutritionEstimator? = null, photoAnalyzer: PhotoNutritionAnalyzer? = null) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = File(context.cacheDir, "ui-test-${System.nanoTime()}")
        val isolated = object : ContextWrapper(context) { override fun getFilesDir(): File = directory }
        repo = JournalRepository(SessionProvider { AccountSession("instrumentation-only", Long.MAX_VALUE) }, AtomicJournalStore(isolated))
        if(seed) {
            val entry = repo.addDraft("Test meal · two idlis and sambar", date, ZoneId.systemDefault())
            repo.correct(entry.id, NutritionEstimate(listOf(EstimatedItem("Test fixture — idli and sambar", Portion(1.0,"serving",PortionKind.SERVING), Nutrients(310.0,11.0,54.0,6.0,7.0), listOf(SourceReference(SourceKind.USER,"Synthetic UI test fixture")))), false, "Synthetic nutrition values used only to verify the interface.", 1))
            repo.setGoals(NutritionGoals(Nutrients(2000.0,90.0,250.0,65.0,30.0)))
        }
        controller = JournalController(repo,scope,estimator,photoAnalyzer)
        rule.activity.runOnUiThread { rule.activity.setContent { AruApp(controller) } }
        rule.waitUntil(10000) { !controller.state.value.loading }
        rule.waitForIdle()
    }
    private fun shot(name: String) {
        rule.waitForIdle()
        val inst = InstrumentationRegistry.getInstrumentation()
        val file = File(inst.targetContext.getExternalFilesDir(null), "$name.png")
        file.outputStream().use { inst.uiAutomation.takeScreenshot().compress(Bitmap.CompressFormat.PNG,100,it) }
        android.os.ParcelFileDescriptor.AutoCloseInputStream(inst.uiAutomation.executeShellCommand("cp ${file.absolutePath} /data/local/tmp/$name.png")).use { it.readBytes() }
    }
    private fun closeKeyboard() {
        rule.activity.runOnUiThread {
            rule.activity.currentFocus?.let { view ->
                (rule.activity.getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager)
                    .hideSoftInputFromWindow(view.windowToken, 0)
                view.clearFocus()
            }
        }
        rule.waitForIdle()
    }
    @Test fun signedOutCannotAccessJournal() {
        val signedOut = JournalController(JournalRepository(UnconfiguredSessionProvider, object : JournalStore {
            override fun read(accountId: String) = error("Signed-out read")
            override fun write(accountId: String, state: JournalState) = error("Signed-out write")
        }), scope)
        rule.activity.runOnUiThread { rule.activity.setContent { AruApp(signedOut, AuthUiState(configured = true)) } }
        rule.waitUntil(5000) { !signedOut.state.value.loading }
        rule.onNodeWithText("Welcome to Aru 👋").assertIsDisplayed()
        rule.onNodeWithContentDescription("Sign in with Google").assertIsDisplayed()
        rule.onNodeWithContentDescription("Food entry").assertDoesNotExist()
        Thread.sleep(900) // Capture after the launch transition has finished fading.
        shot("aru-account")
    }
    @Test fun optionalProfileOnboardingAndSettingsAreInteractive() {
        grantTourPermissions()
        launch(seed = true)
        var saveCalls = 0
        rule.activity.runOnUiThread {
            rule.activity.setContent {
                var profileState by remember {
                    mutableStateOf(ProfileUiState(enabled = true, profile = null))
                }
                AruApp(controller, profileUi = profileState, onSaveProfile = { draft, setup, onboarded ->
                    saveCalls++
                    profileState = ProfileUiState(enabled = true, profile = UserProfile(
                        "instrumentation-only", draft.displayName, draft.dateOfBirth, draft.heightCm, draft.weightKg, setup, onboarded
                    ))
                })
            }
        }
        rule.onNodeWithText("Your name").assertIsDisplayed()
        shot("aru-profile-name")
        shot("aru-illustration-name")
        rule.onNodeWithContentDescription("Name profile field").performTextInput("Abhishek")
        rule.onNodeWithText("Continue").performClick()
        rule.onNodeWithText("Date of birth").assertIsDisplayed()
        shot("aru-illustration-birthday")
        rule.onNodeWithText("Skip this question").performClick()
        shot("aru-illustration-height")
        rule.onNodeWithContentDescription("Height profile field").assertIsDisplayed().performTextInput("175")
        rule.onNodeWithText("Continue").performClick()
        shot("aru-illustration-weight")
        rule.onNodeWithContentDescription("Weight profile field").assertIsDisplayed().performTextInput("70")
        rule.onNodeWithText("Continue to Aru").performClick()
        rule.onNodeWithText("Write what you ate").assertIsDisplayed()
        Thread.sleep(500) // Let the onboarding transition settle before visual capture.
        shot("aru-feature-tour")
        rule.onNodeWithText("Next").performClick()
        rule.onNodeWithText("Dictate your meal").assertIsDisplayed()
        rule.onNodeWithText("Previous").performClick()
        rule.onNodeWithText("Write what you ate").assertIsDisplayed()
        repeat(4) { rule.onNodeWithText("Next").performClick() }
        rule.onNodeWithText("Understand your day").assertIsDisplayed()
        rule.onNodeWithText("Start journaling").performClick()
        rule.onNodeWithContentDescription("Settings").performClick()
        rule.onNodeWithText("Your profile").assertIsDisplayed()
        rule.onNodeWithText("Abhishek").assertIsDisplayed()
        rule.onNodeWithText("175 cm").assertIsDisplayed()
        rule.onNodeWithText("70 kg").assertIsDisplayed()
        shot("aru-settings-profile")
        rule.onNodeWithText("Edit profile").performClick()
        rule.onNodeWithContentDescription("Name profile field").performTextReplacement("Aru User")
        rule.onNodeWithText("Save profile").performClick()
        rule.onNodeWithText("Aru User").assertIsDisplayed()
        val savesBeforeReplay = saveCalls
        rule.onNodeWithText("Replay onboarding").performScrollTo().performClick()
        rule.onNodeWithText("Your name").assertIsDisplayed()
        rule.onNodeWithContentDescription("Aru and the name tag").assertIsDisplayed()
        rule.onNodeWithContentDescription("Name profile field").assertTextContains("Aru User")
        shot("aru-replay-name")
        rule.onNodeWithText("Next").performClick()
        rule.onNodeWithText("Date of birth").assertIsDisplayed()
        rule.onNodeWithContentDescription("Previous question").performClick()
        rule.onNodeWithText("Your name").assertIsDisplayed()
        repeat(3) { rule.onNodeWithText("Next").performClick() }
        rule.onNodeWithContentDescription("Weight profile field").assertIsDisplayed()
        rule.onNodeWithContentDescription("Aru on the weighing scale").assertIsDisplayed()
        shot("aru-replay-weight")
        rule.onNodeWithText("Explore features").performClick()
        rule.onNodeWithText("Write what you ate").assertIsDisplayed()
        rule.onNodeWithText("Previous").performClick()
        rule.onNodeWithContentDescription("Weight profile field").assertIsDisplayed()
        rule.onNodeWithText("Explore features").performClick()
        repeat(4) { rule.onNodeWithText("Next").performClick() }
        rule.onNodeWithText("Back to journal").performClick()
        rule.onNodeWithContentDescription("Settings").performClick()
        rule.onNodeWithText("Aru User").assertIsDisplayed()
        rule.onNodeWithText("175 cm").assertIsDisplayed()
        rule.onNodeWithText("70 kg").assertIsDisplayed()
        rule.onNodeWithText("Replay onboarding").performScrollTo().performClick()
        rule.onNodeWithText("Your name").assertIsDisplayed()
        rule.onNodeWithText("Skip").performClick()
        rule.onNodeWithContentDescription("Settings").assertIsDisplayed()
        rule.runOnIdle { Assert.assertEquals("Replay must not write profile data", savesBeforeReplay, saveCalls) }
    }
    private fun grantTourPermissions() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        listOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO).forEach {
            instrumentation.uiAutomation.grantRuntimePermission(instrumentation.targetContext.packageName, it)
        }
        instrumentation.targetContext.getSharedPreferences("aru_privacy", 0).edit().putBoolean("photo_ai_notice_accepted", true).commit()
    }

    /** Run with CAMERA and RECORD_AUDIO revoked and permission flags reset on the emulator. */
    @Test fun tourAutomaticallyRequestsPermissionsAndDenialsRemainSkippable() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        org.junit.Assume.assumeTrue(listOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO).all {
            context.checkSelfPermission(it) != android.content.pm.PackageManager.PERMISSION_GRANTED
        })
        fun denySystemPermission() {
            var button: android.view.accessibility.AccessibilityNodeInfo? = null
            rule.waitUntil(6000) {
                val root = instrumentation.uiAutomation.rootInActiveWindow
                button = root?.findAccessibilityNodeInfosByText("Don’t allow")?.firstOrNull()
                    ?: root?.findAccessibilityNodeInfosByText("Don't allow")?.firstOrNull()
                button != null
            }
            Assert.assertTrue(button!!.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK))
        }
        launch()
        var skipped = false
        rule.activity.runOnUiThread { rule.activity.setContent {
            androidx.compose.material3.MaterialTheme { FeatureTour(controller, false, null, onFinish = {}, onSkip = { skipped = true }) }
        } }
        rule.onNodeWithText("Next").performClick()
        denySystemPermission()
        rule.onNodeWithText("Microphone access was declined.", substring = true).performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("Next").performClick()
        denySystemPermission()
        rule.onNodeWithText("Camera access was declined.", substring = true).performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("Previous").performClick()
        rule.onNodeWithText("Dictate your meal").assertIsDisplayed()
        rule.onNodeWithText("Next").performClick()
        rule.onNodeWithText("Photograph your plate").assertIsDisplayed()
        rule.onNodeWithText("Skip").performClick()
        rule.runOnIdle { Assert.assertTrue(skipped) }
        Assert.assertTrue(repo.read().entries.isEmpty())
    }

    @Test fun interactiveTourLogsDictatesPhotographsSavesAndShowsRealTotals() {
        grantTourPermissions()
        val calls = java.util.concurrent.atomic.AtomicInteger()
        val photos = java.util.concurrent.atomic.AtomicInteger()
        val estimate = NutritionEstimate(listOf(EstimatedItem("Synthetic test meal", Portion(1.0, "serving", PortionKind.SERVING), Nutrients(310.0, 11.0, 54.0, 6.0, 7.0), listOf(SourceReference(SourceKind.USER, "Synthetic onboarding test fixture")))), false, "UI fixture only", 1)
        launch(estimator = NutritionEstimator { calls.incrementAndGet(); estimate }, photoAnalyzer = PhotoNutritionAnalyzer { _, bytes ->
            Assert.assertTrue(bytes.isNotEmpty()); photos.incrementAndGet(); delay(1800)
            PhotoMealAnalysis("Photo meal for tour test", estimate)
        })
        var starts = 0
        var stops = 0
        val fakeDictation = object : MealDictation {
            override fun start(onText: (String) -> Unit, onListening: (Boolean) -> Unit, onError: (String) -> Unit) {
                starts++; onListening(true); onText("one"); onText("one banana"); onListening(false)
            }
            override fun stop() { stops++ }
            override fun close() = Unit
        }
        var finished = false
        rule.activity.runOnUiThread { rule.activity.setContent {
            androidx.compose.material3.MaterialTheme { FeatureTour(controller, false, null, onFinish = { finished = true }, onSkip = {}, dictationOverride = fakeDictation) }
        } }
        Assert.assertTrue("A placeholder must not create a meal", repo.read().entries.isEmpty())
        rule.onNodeWithContentDescription("Tour meal text").performTextInput("2 idlis")
        rule.onNodeWithContentDescription("Tour meal text").performTextInput(" and sambar")
        closeKeyboard()
        rule.waitUntil(8000) { repo.read().entries.singleOrNull()?.estimate != null }
        Assert.assertEquals("2 idlis and sambar", repo.read().entries.single().text)
        Assert.assertEquals(1, calls.get())
        shot("aru-tour-write")
        rule.onNodeWithText("Next").performClick()
        rule.onNodeWithText("Dictate your meal").assertIsDisplayed()
        rule.onNodeWithContentDescription("Start tour dictation").performClick()
        rule.onNodeWithContentDescription("Tour dictated text").assertTextContains("one banana")
        rule.onNodeWithText("Add to journal").performScrollTo().performClick()
        rule.waitUntil(8000) { repo.read().entries.size == 2 && repo.read().entries.all { it.estimate != null } }
        Assert.assertEquals(1, starts)
        shot("aru-tour-dictate")
        rule.onNodeWithText("Next").performClick()
        Assert.assertTrue(stops > 0)
        rule.onNodeWithText("Open camera").performClick()
        rule.onNodeWithContentDescription("Take meal photo").assertIsDisplayed()
        Thread.sleep(1200)
        shot("aru-tour-camera")
        rule.onNodeWithContentDescription("Take meal photo").performClick()
        rule.waitUntil(20000) { repo.read().entries.size == 3 }
        rule.onNodeWithText("Photo meal for tour test").assertIsDisplayed()
        Assert.assertEquals(1, photos.get())
        rule.onNodeWithText("Next").performClick()
        rule.onNodeWithContentDescription("Save tour meal").performClick()
        rule.waitUntil(5000) { repo.read().savedMeals.size == 1 }
        rule.onNodeWithContentDescription("Save tour meal").assertIsNotEnabled()
        val saved = repo.read().savedMeals.single()
        rule.onNodeWithContentDescription("Reuse tour meal ${saved.name}").performClick()
        rule.waitUntil(5000) { repo.read().entries.size == 4 }
        Assert.assertEquals("Reusing a meal must not call AI", 2, calls.get())
        shot("aru-tour-saved")
        rule.onNodeWithText("Next").performClick()
        rule.mainClock.advanceTimeBy(2600)
        rule.onNodeWithContentDescription("Tour daily nutrition bar").assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Hint pulses: 2"))
        rule.onNodeWithContentDescription("Tour daily nutrition bar").performClick()
        rule.onNodeWithText("1240 kcal").assertIsDisplayed()
        rule.onNodeWithText("44 g").assertIsDisplayed()
        rule.onNodeWithText("216 g").assertIsDisplayed()
        rule.onNodeWithText("24 g").assertIsDisplayed()
        rule.onNodeWithText("28 g").assertIsDisplayed()
        shot("aru-tour-totals")
        rule.onNodeWithText("Close nutrition").performClick()
        rule.onNodeWithText("Previous").performClick()
        rule.onNodeWithText("Next").performClick()
        rule.mainClock.advanceTimeBy(3000)
        rule.onNodeWithContentDescription("Tour daily nutrition bar").assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Hint pulses: 2"))
        rule.onNodeWithText("Start journaling").performClick()
        rule.runOnIdle { Assert.assertTrue(finished) }
    }
    @Test fun profileIllustrationsAnimateReplayAndRespectReducedMotion() {
        rule.mainClock.autoAdvance = false
        var selectedStep by mutableIntStateOf(0)
        var motionEnabled by mutableStateOf(true)
        rule.activity.runOnUiThread {
            rule.activity.setContent {
                AruProfileIllustration(selectedStep, motionEnabled = motionEnabled)
            }
        }
        val names = listOf("Aru and the name tag", "Aru and the birthday cake", "Aru measuring height", "Aru on the weighing scale")
        val first = listOf("Holding a name tag", "Holding a birthday cake", "Looking at the measuring tape", "Getting ready to jump")
        val last = listOf("Waving hello", "Celebrating with confetti", "Smiling at the measurement", "Smiling as the needle settles")
        names.indices.forEach { index ->
            rule.runOnIdle { selectedStep = index }
            rule.mainClock.advanceTimeBy(32)
            val illustration = rule.onNodeWithContentDescription(names[index])
            illustration.assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, first[index]))
            val before = illustration.captureToImage()
            val seen = mutableSetOf<String>()
            repeat(48) {
                seen += illustration.fetchSemanticsNode().config[SemanticsProperties.StateDescription]
                rule.mainClock.advanceTimeBy(100, ignoreFrameDuration = true)
            }
            Assert.assertEquals("All eight poses play without a tap on scene $index: $seen", 8, seen.size)
            illustration.assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, last[index]))
            val after = illustration.captureToImage()
            val beforePixels = IntArray(before.width * before.height).also { before.readPixels(it) }
            val afterPixels = IntArray(after.width * after.height).also { after.readPixels(it) }
            Assert.assertFalse("The rendered pose must actually change", beforePixels.contentEquals(afterPixels))
            var waitedForRepeat = 0
            while (illustration.fetchSemanticsNode().config[SemanticsProperties.StateDescription] != first[index] && waitedForRepeat < 3000) {
                rule.mainClock.advanceTimeBy(100, ignoreFrameDuration = true)
                waitedForRepeat += 100
            }
            illustration.assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, first[index]))
            rule.mainClock.advanceTimeBy(900)
            illustration.performClick()
            rule.mainClock.advanceTimeBy(32)
            illustration.assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, first[index]))
        }
        rule.runOnIdle { motionEnabled = false }
        rule.mainClock.advanceTimeBy(32)
        rule.onNodeWithContentDescription(names.last()).assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, last.last()))
        rule.mainClock.advanceTimeBy(8000)
        rule.onNodeWithContentDescription(names.last()).assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, last.last()))
        rule.runOnIdle { motionEnabled = true }
        rule.mainClock.advanceTimeBy(1000)
        rule.activityRule.scenario.moveToState(androidx.lifecycle.Lifecycle.State.STARTED)
        rule.mainClock.advanceTimeBy(32)
        rule.activityRule.scenario.moveToState(androidx.lifecycle.Lifecycle.State.RESUMED)
        rule.mainClock.advanceTimeBy(32)
        rule.onNodeWithContentDescription(names.last()).assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, first.last()))
    }

    @Test fun illustratedProfileScreensCanAllBeSkipped() {
        var skipped by mutableStateOf(false)
        var saved: UserProfileDraft? = null
        rule.activity.runOnUiThread {
            rule.activity.setContent {
                androidx.compose.material3.MaterialTheme {
                    ProfileSetupFlow(null, false, null, { saved = it }, { skipped = true })
                }
            }
        }
        repeat(4) {
            rule.onNodeWithText("Skip this question").performClick()
        }
        rule.runOnIdle { Assert.assertEquals(UserProfileDraft(), saved) }
        rule.onNodeWithText("Skip for now").performClick()
        rule.runOnIdle { Assert.assertTrue(skipped) }
    }
    @Test fun editPersistsAndDeleteCanBeUndone() {
        launch()
        rule.onNodeWithText("What did you eat today?").performClick()
        rule.waitUntil(5000) { controller.state.value.journal.entries.size == 1 }
        rule.onNodeWithContentDescription("Food entry").performTextInput("2 idlis and a bowl of sambar")
        rule.onNodeWithContentDescription("Dictate meal").assertIsDisplayed()
        rule.onNodeWithContentDescription("Saved meals").assertIsDisplayed()
        rule.onNodeWithContentDescription("Photograph meal").assertIsDisplayed()
        rule.waitUntil(5000) { repo.read().entries.single().text == "2 idlis and a bowl of sambar" }
        rule.onNodeWithContentDescription("Nutrition for 2 idlis and a bowl of sambar").performClick()
        rule.onNodeWithText("AI calculation is not connected yet.").assertIsDisplayed()
        rule.onNodeWithText("Logged at", substring = true).assertIsDisplayed()
        rule.onNodeWithContentDescription("Meal options").performClick()
        rule.onNodeWithText("Delete meal").performClick()
        rule.waitUntil(5000) { repo.read().entries.isEmpty() }
        rule.onNodeWithText("Undo").performClick()
        rule.waitUntil(5000) { repo.read().entries.size == 1 }
        Assert.assertEquals("2 idlis and a bowl of sambar", repo.read().entries.single().text)
    }
    @Test fun repeatedAddKeepsOneOrganizedBlankLine() {
        launch(seed = true)
        rule.onNodeWithText("+ Add a line").performClick()
        rule.waitUntil(5000) { repo.read().entries.size == 2 }
        rule.onNodeWithText("+ Add a line").performClick()
        rule.waitForIdle()
        rule.waitUntil(5000) { repo.read().entries.count { it.text.isBlank() } == 1 }
        rule.onAllNodesWithText("Write what you ate…").assertCountEquals(1)
        shot("aru-organized-journal-lines")
    }
    @Test fun focusedEntryOpensCompactInAppMealCamera() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.uiAutomation.grantRuntimePermission(instrumentation.targetContext.packageName, Manifest.permission.CAMERA)
        instrumentation.targetContext.getSharedPreferences("aru_privacy", 0).edit().putBoolean("photo_ai_notice_accepted", true).commit()
        launch(photoAnalyzer = PhotoNutritionAnalyzer { _, jpeg ->
            Assert.assertTrue(jpeg.isNotEmpty())
            delay(1200)
            PhotoMealAnalysis(
                "Photo meal · masala dosa",
                NutritionEstimate(
                    listOf(EstimatedItem("Masala dosa", Portion(1.0,"serving",PortionKind.SERVING), Nutrients(310.0,8.0,48.0,10.0,4.0), listOf(SourceReference(SourceKind.USER,"Synthetic photo UI test fixture")))),
                    false,
                    "Synthetic photo result used only to verify the attachment flow.",
                    System.currentTimeMillis()
                )
            )
        })
        rule.onNodeWithText("What did you eat today?").performClick()
        rule.waitUntil(5000) { controller.state.value.journal.entries.size == 1 }
        rule.onNodeWithContentDescription("Food entry").performTextInput("one masala dosa")
        shot("aru-compact-input")
        rule.onNodeWithContentDescription("Photograph meal").performClick()
        rule.onNodeWithContentDescription("Take meal photo").assertIsDisplayed()
        rule.onNodeWithContentDescription("Choose meal photo").assertIsDisplayed()
        rule.onNodeWithText("Center the whole meal").assertIsDisplayed()
        Thread.sleep(1200) // Let the asynchronous CameraX preview draw before visual capture.
        shot("aru-meal-camera")
        rule.onNodeWithContentDescription("Take meal photo").performClick()
        rule.waitUntil(20000) { rule.onAllNodesWithText("Meal photo attached").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("Identifying dishes and portions…").assertIsDisplayed()
        rule.onNodeWithText("Identifying your meal…").assertDoesNotExist()
        shot("aru-photo-attached")
        rule.waitUntil(10000) { repo.read().entries.any { it.text == "Photo meal · masala dosa" } }
    }
    @Test fun detailsGoalsAndSavedMealReuse() {
        val calls = java.util.concurrent.atomic.AtomicInteger()
        launch(seed = true, estimator = NutritionEstimator { calls.incrementAndGet(); error("Saved meal reuse must not call AI") })
        rule.onNodeWithText("🔥 1").assertIsDisplayed()
        rule.onNodeWithContentDescription("Settings").assertIsDisplayed()
        shot("aru-journal")
        rule.onNodeWithContentDescription("Nutrition for Test meal · two idlis and sambar").performClick()
        rule.onAllNodesWithText("310").onLast().assertIsDisplayed()
        shot("aru-nutrition")
        rule.onNodeWithContentDescription("Meal options").performClick()
        rule.onNodeWithText("Delete meal").assertIsDisplayed()
        shot("aru-meal-options")
        rule.onNodeWithText("Save meal").performClick()
        rule.onNodeWithText("Meal name").performTextInput("Test breakfast")
        rule.onNodeWithText("Meal name").performImeAction()
        rule.waitUntil(5000) { controller.state.value.journal.savedMeals.size == 1 }
        rule.onNodeWithContentDescription("Close").performClick()
        rule.onNodeWithContentDescription("Food entry").performClick()
        rule.onNodeWithContentDescription("Saved meals").performClick()
        rule.onNodeWithText("Test breakfast").performScrollTo().assertIsDisplayed()
        shot("aru-saved-meals")
        rule.onNodeWithContentDescription("Add Test breakfast").performClick()
        rule.waitUntil(5000) { repo.read().entries.size == 2 }
        runBlocking { delay(2100) }
        Assert.assertEquals(0, calls.get())
        rule.onNodeWithContentDescription("Daily totals and goals").performClick()
        rule.onNodeWithText("620 / 2000 kcal").assertIsDisplayed()
        shot("aru-goals")
    }
    @Test fun editingAndManualCorrectionNeverCallPaidEstimator() {
        val calls = java.util.concurrent.atomic.AtomicInteger()
        launch(seed = true, estimator = NutritionEstimator { calls.incrementAndGet(); error("Should be cancelled") })
        val entry = repo.read().entries.single()
        controller.edit(entry.id, "Corrected test meal")
        controller.correct(entry.id, entry.estimate!!)
        rule.waitUntil(5000) { repo.read().entries.single().text == "Corrected test meal" && repo.read().entries.single().status == CalculationStatus.MANUAL }
        runBlocking { delay(2100) }
        Assert.assertEquals(0, calls.get())
        Assert.assertEquals(CalculationStatus.MANUAL, repo.read().entries.single().status)
    }
    @Test fun typingPauseCalculatesAutomaticallyAndUpdatesTotals() {
        val calls = java.util.concurrent.atomic.AtomicInteger()
        launch(seed = true, estimator = NutritionEstimator {
            calls.incrementAndGet()
            delay(100)
            NutritionEstimate(listOf(EstimatedItem("Dosa and sambar", Portion(1.0,"serving",PortionKind.SERVING), Nutrients(250.0), listOf(SourceReference(SourceKind.ARU_DATABASE,"Aru nutrition reference library")))),true,"Synthetic estimate; review portions",123,68, summary = "A classic South Indian breakfast featuring a golden dosa served with a bowl of lentil sambar")
        })
        val entry = repo.read().entries.single()
        controller.edit(entry.id,"Changed meal")
        rule.waitUntil(5000) { repo.read().entries.single().status == CalculationStatus.DRAFT }
        runBlocking { delay(1100) }
        Assert.assertEquals(0,calls.get())
        rule.waitUntil(5000) { repo.read().entries.single().status == CalculationStatus.NEEDS_REVIEW }
        rule.waitUntil(1000) { rule.onAllNodesWithText("✦ Summarizing meal…").fetchSemanticsNodes().isNotEmpty() }
        rule.mainClock.advanceTimeBy(1500)
        rule.waitForIdle()
        rule.onAllNodesWithText("Dosa and sambar").onLast().assertIsDisplayed()
        shot("aru-summarized-entry")
        Assert.assertEquals(1,calls.get())
        Assert.assertEquals(250.0,dailyTotals(repo.read().entries,date.toString()).values[0].knownAmount,0.0)
    }
    @Test fun manualNutrientsAndGoalsPersist() {
        launch(seed = true)
        rule.onNodeWithContentDescription("Nutrition for Test meal · two idlis and sambar").performClick()
        rule.onNodeWithText("Edit nutrition", substring = false).performScrollTo().performClick()
        rule.onNodeWithText("Calories (kcal)").performScrollTo().performTextReplacement("400")
        closeKeyboard()
        rule.onNodeWithText("Save nutrition").performScrollTo().assertIsDisplayed().performClick()
        rule.waitUntil(5000) { repo.read().entries.single().estimate!!.items.single().nutrients.caloriesKcal == 400.0 }
        rule.onNodeWithContentDescription("Close").performClick()
        rule.onNodeWithContentDescription("Daily totals and goals").performClick()
        rule.onNodeWithText("Edit goals").performScrollTo().performClick()
        rule.onNodeWithText("Calories (kcal)").performScrollTo().performTextReplacement("1800")
        closeKeyboard()
        rule.onNodeWithText("Save goals").performScrollTo().assertIsDisplayed().performClick()
        rule.waitUntil(5000) { repo.read().goals.targets.caloriesKcal == 1800.0 }
    }

}
