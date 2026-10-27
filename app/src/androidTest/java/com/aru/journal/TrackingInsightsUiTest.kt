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

class TrackingInsightsUiTest {
 @get:Rule val rule=createAndroidComposeRule<MainActivity>()
 private val scope=CoroutineScope(SupervisorJob()+Dispatchers.Main)
 private lateinit var repo:JournalRepository
 private lateinit var controller:JournalController
 private val date=LocalDate.now()
 @Before fun clearSystemUiOnly(){val root=InstrumentationRegistry.getInstrumentation().uiAutomation.rootInActiveWindow?:return;if(root.findAccessibilityNodeInfosByText("System UI isn't responding").isNotEmpty())root.findAccessibilityNodeInfosByText("Wait").firstOrNull()?.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK)}
 @After fun done(){scope.cancel()}
 private fun lunch(text:String,day:LocalDate,cal:Double){val entry=repo.addDraft(text,day,ZoneId.systemDefault());repo.correct(entry.id,NutritionEstimate(listOf(EstimatedItem("Lunch",Portion(1.0,"plate",PortionKind.SERVING),Nutrients(cal,30.0,75.0,25.0),listOf(SourceReference(SourceKind.USER,"Fixture")))),false,"Synthetic test fixture",1))}
 private fun launch(history:Boolean=false){
  val context=InstrumentationRegistry.getInstrumentation().targetContext;val directory=File(context.cacheDir,"tracking-${System.nanoTime()}");val isolated=object:ContextWrapper(context){override fun getFilesDir()=directory}
  repo=JournalRepository(SessionProvider{AccountSession("tracking-test",Long.MAX_VALUE)},AtomicJournalStore(isolated))
  lunch("Synthetic lunch",date,1440.0);repo.addDraft("three glasses of water",date,ZoneId.systemDefault())
  if(history){lunch("Yesterday lunch",date.minusDays(1),500.0);repo.addDraft("500 ml water",date.minusDays(2),ZoneId.systemDefault())}
  controller=JournalController(repo,scope)
  rule.activity.runOnUiThread{rule.activity.setContent{AruApp(controller,profileUi=ProfileUiState(enabled=true,profile=UserProfile("tracking-test",profileSetupCompleted=true,onboardingCompleted=true)))}}
  rule.waitUntil(10000){!controller.state.value.loading&&rule.onAllNodesWithContentDescription("Daily water tracking").fetchSemanticsNodes().isNotEmpty()}
 }
 private fun shot(name:String){rule.waitForIdle();val inst=InstrumentationRegistry.getInstrumentation();val file=File(inst.targetContext.getExternalFilesDir(null),"$name.png");file.outputStream().use{inst.uiAutomation.takeScreenshot().compress(Bitmap.CompressFormat.PNG,100,it)};android.os.ParcelFileDescriptor.AutoCloseInputStream(inst.uiAutomation.executeShellCommand("cp ${file.absolutePath} /data/local/tmp/$name.png")).use{it.readBytes()}}
 private fun keyboard(){rule.activity.runOnUiThread{rule.activity.currentFocus?.let{view->(rule.activity.getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager).hideSoftInputFromWindow(view.windowToken,0);view.clearFocus()}};rule.waitForIdle()}
 @Test fun mealTextOpensEditableDescription(){
  launch();rule.onNodeWithContentDescription("Nutrition for Synthetic lunch").performClick();rule.onNodeWithText("Nutrition Details").assertIsDisplayed()
  rule.onNodeWithContentDescription("Meal description").performTextReplacement("Updated lunch")
  rule.onNodeWithText("Save description").performClick();rule.waitUntil(5000){repo.read().entries.any{it.text=="Updated lunch"}}
  Assert.assertNull(repo.read().entries.first{it.text=="Updated lunch"}.estimate)
  shot("aru-editable-meal-details")
 }
 @Test fun waterDropletShowsDailyProgressAndEditableGoal(){
  launch();rule.onNodeWithContentDescription("Water amount for three glasses of water").performClick()
  rule.onNodeWithText("Daily Water").assertIsDisplayed();rule.onNodeWithContentDescription("750 of 2000 ml, 38 percent").assertIsDisplayed()
  rule.onNodeWithText("+ 250 ml").performScrollTo().performClick();rule.waitUntil(5000){dailyWater(repo.read().entries,date.toString())==1000}
  rule.onNodeWithText("Daily water goal (ml)").performScrollTo().performTextReplacement("2500");keyboard()
  rule.onNodeWithText("Save water goal").performScrollTo().performClick();rule.waitUntil(5000){repo.read().goals.preferences.waterGoalMl==2500}
  rule.onNodeWithContentDescription("1000 of 2500 ml, 40 percent").performScrollTo().assertIsDisplayed();shot("aru-daily-water-tracker")
  rule.onNodeWithContentDescription("Close").performClick();rule.onNodeWithText("1000 / 2500 ml").assertIsDisplayed()
 }
 @Test fun settingsHasWeeklyInsightsWithFoodAndWaterHistory(){
  launch(history=true);rule.onNodeWithContentDescription("Settings").performClick();rule.onNodeWithText("Weekly Insights").performScrollTo().performClick()
  rule.onNodeWithText("3 / 7 days logged").assertIsDisplayed();rule.onNodeWithText("970 kcal").performScrollTo().assertIsDisplayed();shot("aru-weekly-insights")
  rule.onNodeWithText("179 ml / day · 1250 ml this week").performScrollTo().assertIsDisplayed()
 }
}
