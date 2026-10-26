package com.aru.journal

import android.content.ContextWrapper
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

class NutritionGoalsUiTest {
 @get:Rule val rule=createAndroidComposeRule<MainActivity>()
 private val scope=CoroutineScope(SupervisorJob()+Dispatchers.Main)
 private lateinit var repo:JournalRepository
 private lateinit var controller:JournalController
 @Before fun dismissEmulatorSystemUiOnly() {
  val root=InstrumentationRegistry.getInstrumentation().uiAutomation.rootInActiveWindow?:return
  if(root.findAccessibilityNodeInfosByText("System UI isn't responding").isNotEmpty())root.findAccessibilityNodeInfosByText("Wait").firstOrNull()?.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK)
 }
 @After fun done(){scope.cancel()}
 private fun launch(){
  val context=InstrumentationRegistry.getInstrumentation().targetContext
  val directory=File(context.cacheDir,"goals-test-${System.nanoTime()}")
  val isolated=object:ContextWrapper(context){override fun getFilesDir()=directory}
  repo=JournalRepository(SessionProvider {AccountSession("goal-test",Long.MAX_VALUE)},AtomicJournalStore(isolated))
  val entry=repo.addDraft("Synthetic lunch",LocalDate.now(),ZoneId.systemDefault())
  repo.correct(entry.id,NutritionEstimate(listOf(EstimatedItem("Synthetic lunch",Portion(1.0,"plate",PortionKind.SERVING),Nutrients(1440.0,82.0,137.0,62.0,12.0,20.0,800.0),listOf(SourceReference(SourceKind.USER,"Test fixture")))),false,"Synthetic UI fixture",1))
  repo.setGoals(NutritionGoals(Nutrients(2000.0,150.0,250.0,65.0)))
  controller=JournalController(repo,scope)
  rule.activity.runOnUiThread{rule.activity.setContent{AruApp(controller,profileUi=ProfileUiState(enabled=true,profile=UserProfile("goal-test",dateOfBirth="1996-01-01",heightCm=175.0,weightKg=70.0,profileSetupCompleted=true,onboardingCompleted=true)))}}
  rule.waitUntil(10000){!controller.state.value.loading&&rule.onAllNodesWithContentDescription("Daily totals and goals").fetchSemanticsNodes().isNotEmpty()}
 }
 private fun shot(name:String){
  rule.waitForIdle();val inst=InstrumentationRegistry.getInstrumentation();val file=File(inst.targetContext.getExternalFilesDir(null),"$name.png")
  file.outputStream().use{inst.uiAutomation.takeScreenshot().compress(Bitmap.CompressFormat.PNG,100,it)}
  android.os.ParcelFileDescriptor.AutoCloseInputStream(inst.uiAutomation.executeShellCommand("cp ${file.absolutePath} /data/local/tmp/$name.png")).use{it.readBytes()}
 }
 private fun keyboard(){rule.activity.runOnUiThread{rule.activity.currentFocus?.let{view->(rule.activity.getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager).hideSoftInputFromWindow(view.windowToken,0);view.clearFocus()}};rule.waitForIdle()}
 @Test fun goalsCardAndPreferencesSaveAndCancel(){
  launch()
  rule.onNodeWithContentDescription("Open nutrition goals").assertDoesNotExist()
  shot("aru-goals-collapsed")
  rule.onNodeWithContentDescription("Daily totals and goals").performClick()
  rule.onNodeWithContentDescription("Open nutrition goals").assertIsDisplayed()
  shot("aru-goals-expanded")
  rule.onNodeWithContentDescription("Daily totals and goals").performClick()
  rule.onNodeWithContentDescription("Open nutrition goals").assertDoesNotExist()
  rule.onNodeWithContentDescription("Daily totals and goals").performClick()
  shot("aru-goals-card-new")
  rule.onNodeWithContentDescription("Open nutrition goals").performClick()
  rule.onNodeWithText("Nutrition Goals").assertIsDisplayed()
  shot("aru-goals-settings-new")
  rule.onNodeWithText("Target Weight").performScrollTo().performTextReplacement("65")
  keyboard();rule.onNodeWithText("Cancel",useUnmergedTree=false).performScrollTo().performClick()
  Assert.assertNull(repo.read().goals.preferences.targetWeightKg)
  rule.onNodeWithContentDescription("Open nutrition goals").performClick()
  rule.onNodeWithText("Target Weight").performScrollTo().performTextReplacement("75")
  keyboard()
  rule.onNodeWithContentDescription("Track Sugar").performScrollTo().performClick()
  rule.onNodeWithText("Sugar target").performScrollTo().performTextReplacement("40")
  keyboard()
  rule.onNodeWithContentDescription("Track Sodium").performScrollTo().performClick()
  rule.onNodeWithText("Sodium target").performScrollTo().performTextReplacement("2300")
  keyboard();shot("aru-goals-micros-new")
  rule.onNodeWithText("Save goals").performScrollTo().performClick()
  rule.waitUntil(5000){repo.read().goals.targets.sodiumMg==2300.0}
  Assert.assertEquals(75.0,repo.read().goals.preferences.targetWeightKg!!,0.0)
  Assert.assertTrue(repo.read().goals.preferences.trackSugar)
  Assert.assertTrue(repo.read().goals.preferences.trackSodium)
  rule.waitUntil(5000){rule.onAllNodesWithText("Nutrition Goals").fetchSemanticsNodes().isEmpty()}
  rule.onNodeWithContentDescription("Open nutrition goals").assertIsDisplayed()
 }
 @Test fun burnIsOptionalAndInvalidTargetsCannotSave(){
  launch();rule.onNodeWithContentDescription("Daily totals and goals").performClick();rule.onNodeWithContentDescription("Open nutrition goals").performClick()
  rule.onNodeWithText("Not calculated").assertIsDisplayed()
  rule.onNodeWithText("Daily burn calculation details ⌄").performScrollTo().performClick()
  rule.onNodeWithText("Not set ⌄").performScrollTo().performClick()
  rule.onNodeWithText("Male").performClick()
  rule.onNodeWithText("1979 kcal / day").assertIsDisplayed()
  rule.onNodeWithText("Target Weight").performScrollTo().performTextReplacement("2")
  keyboard();rule.onNodeWithText("Save goals").performScrollTo().performClick()
  Assert.assertNull(repo.read().goals.preferences.targetWeightKg)
  rule.onNodeWithText("Nutrition Goals").assertIsDisplayed()
 }
}
