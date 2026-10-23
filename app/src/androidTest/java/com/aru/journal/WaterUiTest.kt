package com.aru.journal

import android.graphics.Bitmap
import androidx.activity.compose.setContent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.aru.journal.auth.*
import com.aru.journal.domain.*
import com.aru.journal.ui.*
import kotlinx.coroutines.*
import org.junit.*
import org.junit.Assert.assertEquals
import java.io.File
import java.time.LocalDate
import java.time.ZoneId

class WaterUiTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var saved = JournalState()
    private val store = object : JournalStore {
        override fun read(accountId: String) = saved
        override fun write(accountId: String, state: JournalState) { saved = state }
    }
    private lateinit var controller: JournalController
    private lateinit var repo: JournalRepository
    private lateinit var entryId: String
    @Before fun clearEmulatorSystemUiDialog() {
        // API 37 preview emulator occasionally reports a System UI ANR on cold boot.
        // This fixture only acknowledges that exact OS dialog, never an Aru error.
        val root = InstrumentationRegistry.getInstrumentation().uiAutomation.rootInActiveWindow ?: return
        if (root.findAccessibilityNodeInfosByText("System UI isn't responding").isNotEmpty()) {
            root.findAccessibilityNodeInfosByText("Wait").firstOrNull()
                ?.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK)
        }
    }
    @After fun finish() { scope.cancel() }
    private fun launch(text: String) {
        repo = JournalRepository(SessionProvider { AccountSession("water-ui-fixture",Long.MAX_VALUE) },store)
        entryId = repo.addDraft(text,LocalDate.now(),ZoneId.systemDefault()).id
        render()
    }
    private fun render() {
        controller = JournalController(repo,scope)
        rule.activity.runOnUiThread { rule.activity.setContent { AruApp(controller) } }
        rule.waitUntil(10000) { !controller.state.value.loading && runCatching { rule.onAllNodes(isRoot()).fetchSemanticsNodes().isNotEmpty() }.getOrDefault(false) }
    }
    private fun shot(name: String) {
        rule.waitForIdle()
        val inst = InstrumentationRegistry.getInstrumentation()
        val file = File(inst.targetContext.getExternalFilesDir(null),"$name.png")
        file.outputStream().use { inst.uiAutomation.takeScreenshot().compress(Bitmap.CompressFormat.PNG,100,it) }
        android.os.ParcelFileDescriptor.AutoCloseInputStream(inst.uiAutomation.executeShellCommand("cp ${file.absolutePath} /data/local/tmp/$name.png")).use { it.readBytes() }
    }
    @Test fun waterTextOpensPortionsAndPresetAndCustomAmountsPersist() {
        launch("three glasses of water")
        rule.onNodeWithText("750 ml").assertIsDisplayed()
        rule.onNodeWithText("Thinking").assertDoesNotExist()
        rule.onNodeWithText("A little space for your everyday").assertDoesNotExist()
        rule.onNodeWithText("Make Aru yours").assertDoesNotExist()
        shot("aru-water-row")
        rule.onNodeWithContentDescription("Edit water entry").performClick()
        rule.onNodeWithContentDescription("Water description").assertIsDisplayed()
        rule.onNodeWithText("1 glass · 250 ml").performClick()
        rule.waitUntil(5000) { controller.state.value.journal.entries.single().water?.milliliters == 250 }
        rule.onNodeWithText("1 bottle · 500 ml").performClick()
        rule.waitUntil(5000) { controller.state.value.journal.entries.single().water?.milliliters == 500 }
        shot("aru-water-options")
        rule.onNodeWithContentDescription("Custom water amount in ml").performScrollTo().performTextReplacement("330")
        rule.onNodeWithText("Save water amount").performScrollTo().performClick()
        rule.waitUntil(5000) { controller.state.value.journal.entries.single().water?.milliliters == 330 }
        rule.onNodeWithContentDescription("Close").performClick()
        rule.onNodeWithText("330 ml").assertIsDisplayed()
        render()
        rule.onNodeWithText("330 ml").assertIsDisplayed()
        rule.onNodeWithContentDescription("Water amount for three glasses of water").performClick()
        rule.onNodeWithContentDescription("Water description").performTextReplacement("water")
        rule.onNodeWithText("Save description").performClick()
        rule.waitUntil(5000) { controller.state.value.journal.entries.single().text == "water" }
        rule.onNodeWithContentDescription("Close").performClick()
        rule.onNodeWithText("250 ml").assertIsDisplayed()
        rule.onNodeWithContentDescription("Water amount for water").assertIsDisplayed()
    }
    @Test fun mixedFoodAndWaterKeepCaloriesAndWaterTogether() {
        launch("rice and a glass of water")
        val estimate = NutritionEstimate(listOf(EstimatedItem("Rice",Portion(1.0,"serving",PortionKind.SERVING),Nutrients(200.0),listOf(SourceReference(SourceKind.USER,"Synthetic test")))),false,"Test fixture",1)
        controller.correct(entryId,estimate)
        rule.waitUntil(5000) { controller.state.value.journal.entries.single().estimate != null }
        rule.onNodeWithText("250 ml").assertIsDisplayed()
        rule.waitUntil(5000) { rule.onAllNodesWithText("200 cal").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithContentDescription("Edit water entry").performClick()
        rule.onNodeWithText("1 bottle · 500 ml").performClick()
        rule.waitUntil(5000) { controller.state.value.journal.entries.single().water?.milliliters == 500 }
        assertEquals(200.0,repo.read().entries.single().estimate!!.items.single().nutrients.caloriesKcal!!,0.0)
    }
}
