package com.aru.journal

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
        rule.onNodeWithText("An account is required to use Aru.").assertIsDisplayed()
        rule.onNodeWithContentDescription("Food entry").assertDoesNotExist()
        shot("aru-account")
    }
    @Test fun editPersistsAndDeleteCanBeUndone() {
        launch()
        rule.onNodeWithText("What did you eat today?").performClick()
        rule.waitUntil(5000) { controller.state.value.journal.entries.size == 1 }
        rule.onNodeWithContentDescription("Food entry").performTextInput("2 idlis and a bowl of sambar")
        rule.waitUntil(5000) { repo.read().entries.single().text == "2 idlis and a bowl of sambar" }
        rule.onNodeWithContentDescription("Nutrition for 2 idlis and a bowl of sambar").performClick()
        rule.onNodeWithText("AI calculation is not connected yet.").assertIsDisplayed()
        rule.onNodeWithText("Delete entry").performScrollTo().performClick()
        rule.waitUntil(5000) { repo.read().entries.isEmpty() }
        rule.onNodeWithText("Undo").performClick()
        rule.waitUntil(5000) { repo.read().entries.size == 1 }
        Assert.assertEquals("2 idlis and a bowl of sambar", repo.read().entries.single().text)
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
        rule.onNodeWithText("Close").performScrollTo().performClick()
        rule.onNodeWithContentDescription("Saved meals").performClick()
        rule.onNodeWithText("Test breakfast").assertIsDisplayed()
        shot("aru-saved-meals")
        rule.onNodeWithText("Add to today").performClick()
        rule.waitUntil(5000) { repo.read().entries.size == 2 }
        rule.onNodeWithContentDescription("Daily totals and goals").performClick()
        rule.onNodeWithText("620 / 2000 kcal").assertIsDisplayed()
        shot("aru-goals")
    }
    @Test fun manualCorrectionCancelsPendingEstimate() {
        val calls = java.util.concurrent.atomic.AtomicInteger()
        launch(seed = true, estimator = NutritionEstimator { calls.incrementAndGet(); error("Should be cancelled") })
        val entry = repo.read().entries.single()
        controller.edit(entry.id, "Corrected test meal")
        controller.correct(entry.id, entry.estimate!!)
        rule.waitUntil(5000) { repo.read().entries.single().text == "Corrected test meal" && repo.read().entries.single().status == CalculationStatus.MANUAL }
        runBlocking { delay(1300) }
        Assert.assertEquals(0, calls.get())
        Assert.assertEquals(CalculationStatus.MANUAL, repo.read().entries.single().status)
    }
    @Test fun manualNutrientsAndGoalsPersist() {
        launch(seed = true)
        rule.onNodeWithContentDescription("Nutrition for Test meal · two idlis and sambar").performClick()
        rule.onNodeWithText("Edit nutrition", substring = false).performScrollTo().performClick()
        rule.onNodeWithText("Calories (kcal)").performScrollTo().performTextReplacement("400")
        androidx.test.espresso.Espresso.closeSoftKeyboard()
        rule.onNodeWithText("Save nutrition").performScrollTo().assertIsDisplayed().performClick()
        rule.waitUntil(5000) { repo.read().entries.single().estimate!!.items.single().nutrients.caloriesKcal == 400.0 }
        rule.onNodeWithText("Close").performScrollTo().performClick()
        rule.onNodeWithContentDescription("Daily totals and goals").performClick()
        rule.onNodeWithText("Edit goals").performScrollTo().performClick()
        rule.onNodeWithText("Calories (kcal)").performScrollTo().performTextReplacement("1800")
        androidx.test.espresso.Espresso.closeSoftKeyboard()
        rule.onNodeWithText("Save goals").performScrollTo().assertIsDisplayed().performClick()
        rule.waitUntil(5000) { repo.read().goals.targets.caloriesKcal == 1800.0 }
    }

}
