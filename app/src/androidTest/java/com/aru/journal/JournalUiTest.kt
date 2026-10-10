package com.aru.journal

import com.aru.journal.auth.UnconfiguredSessionProvider

import android.Manifest
import android.graphics.Bitmap
import androidx.activity.compose.setContent
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
    private fun launch(seed: Boolean = false, estimator: NutritionEstimator? = null) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = File(context.cacheDir, "ui-test-${System.nanoTime()}")
        val isolated = object : ContextWrapper(context) { override fun getFilesDir(): File = directory }
        repo = JournalRepository(SessionProvider { AccountSession("instrumentation-only", Long.MAX_VALUE) }, AtomicJournalStore(isolated))
        if(seed) {
            val entry = repo.addDraft("Test meal · two idlis and sambar", date, ZoneId.systemDefault())
            repo.correct(entry.id, NutritionEstimate(listOf(EstimatedItem("Test fixture — idli and sambar", Portion(1.0,"serving",PortionKind.SERVING), Nutrients(310.0,11.0,54.0,6.0,7.0), listOf(SourceReference(SourceKind.USER,"Synthetic UI test fixture")))), false, "Synthetic nutrition values used only to verify the interface.", 1))
            repo.setGoals(NutritionGoals(Nutrients(2000.0,90.0,250.0,65.0,30.0)))
        }
        controller = JournalController(repo,scope,estimator)
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
    @Test fun signedOutCannotAccessJournal() {
        val signedOut = JournalController(JournalRepository(UnconfiguredSessionProvider, object : JournalStore {
            override fun read(accountId: String) = error("Signed-out read")
            override fun write(accountId: String, state: JournalState) = error("Signed-out write")
        }), scope)
        rule.activity.runOnUiThread { rule.activity.setContent { AruApp(signedOut, AuthUiState(configured = true)) } }
        rule.waitUntil(5000) { !signedOut.state.value.loading }
        rule.onNodeWithText("Welcome to Aru 👋").assertIsDisplayed()
        rule.onNodeWithText("Sign in with Google").assertIsDisplayed()
        rule.onNodeWithContentDescription("Food entry").assertDoesNotExist()
        shot("aru-account")
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
        rule.onNodeWithText("Delete entry").performScrollTo().performClick()
        rule.waitUntil(5000) { repo.read().entries.isEmpty() }
        rule.onNodeWithText("Undo").performClick()
        rule.waitUntil(5000) { repo.read().entries.size == 1 }
        Assert.assertEquals("2 idlis and a bowl of sambar", repo.read().entries.single().text)
    }
    @Test fun focusedEntryOpensInAppMealCamera() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.uiAutomation.grantRuntimePermission(instrumentation.targetContext.packageName, Manifest.permission.CAMERA)
        instrumentation.targetContext.getSharedPreferences("aru_privacy", 0).edit().putBoolean("photo_ai_notice_accepted", true).commit()
        launch()
        rule.onNodeWithText("What did you eat today?").performClick()
        rule.waitUntil(5000) { controller.state.value.journal.entries.size == 1 }
        rule.onNodeWithContentDescription("Food entry").performTextInput("one masala dosa")
        shot("aru-compact-input")
        rule.onNodeWithContentDescription("Photograph meal").performClick()
        rule.onNodeWithContentDescription("Take meal photo").assertIsDisplayed()
        rule.onNodeWithContentDescription("Choose meal photo").assertIsDisplayed()
        shot("aru-meal-camera")
        rule.onNodeWithContentDescription("Close camera").performClick()
        rule.onNodeWithContentDescription("Food entry").assertIsDisplayed()
    }
    @Test fun detailsGoalsAndSavedMealReuse() {
        launch(seed = true)
        shot("aru-journal")
        rule.onNodeWithContentDescription("Nutrition for Test meal · two idlis and sambar").performClick()
        rule.onAllNodesWithText("310").onLast().assertIsDisplayed()
        shot("aru-nutrition")
        rule.onNodeWithText("Save as meal").performScrollTo().performClick()
        rule.onNodeWithText("Meal name").performScrollTo().performTextInput("Test breakfast")
        rule.onNodeWithText("Meal name").performImeAction()
        rule.waitUntil(5000) { repo.read().savedMeals.size == 1 }
        rule.onNodeWithContentDescription("Close").performClick()
        rule.onNodeWithContentDescription("Food entry").performClick()
        rule.onNodeWithContentDescription("Saved meals").performClick()
        rule.onNodeWithText("Test breakfast").assertIsDisplayed()
        shot("aru-saved-meals")
        rule.onNodeWithContentDescription("Add Test breakfast").performClick()
        rule.waitUntil(5000) { repo.read().entries.size == 2 }
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
            NutritionEstimate(listOf(EstimatedItem("Test food", Portion(1.0,"serving",PortionKind.SERVING), Nutrients(250.0), listOf(SourceReference(SourceKind.ARU_DATABASE,"Aru nutrition reference library")))),true,"Synthetic estimate; review portions",123,68)
        })
        val entry = repo.read().entries.single()
        controller.edit(entry.id,"Changed meal")
        rule.waitUntil(5000) { repo.read().entries.single().status == CalculationStatus.DRAFT }
        runBlocking { delay(1100) }
        Assert.assertEquals(0,calls.get())
        rule.waitUntil(5000) { repo.read().entries.single().status == CalculationStatus.NEEDS_REVIEW }
        Assert.assertEquals(1,calls.get())
        Assert.assertEquals(250.0,dailyTotals(repo.read().entries,date.toString()).values[0].knownAmount,0.0)
    }
    @Test fun manualNutrientsAndGoalsPersist() {
        launch(seed = true)
        rule.onNodeWithContentDescription("Nutrition for Test meal · two idlis and sambar").performClick()
        rule.onNodeWithText("Edit nutrition", substring = false).performScrollTo().performClick()
        rule.onNodeWithText("Calories (kcal)").performScrollTo().performTextReplacement("400")
        androidx.test.espresso.Espresso.closeSoftKeyboard()
        rule.onNodeWithText("Save nutrition").performScrollTo().assertIsDisplayed().performClick()
        rule.waitUntil(5000) { repo.read().entries.single().estimate!!.items.single().nutrients.caloriesKcal == 400.0 }
        rule.onNodeWithContentDescription("Close").performClick()
        rule.onNodeWithContentDescription("Daily totals and goals").performClick()
        rule.onNodeWithText("Edit goals").performScrollTo().performClick()
        rule.onNodeWithText("Calories (kcal)").performScrollTo().performTextReplacement("1800")
        androidx.test.espresso.Espresso.closeSoftKeyboard()
        rule.onNodeWithText("Save goals").performScrollTo().assertIsDisplayed().performClick()
        rule.waitUntil(5000) { repo.read().goals.targets.caloriesKcal == 1800.0 }
    }

}
