package com.aru.journal

import android.content.ContextWrapper
import android.content.ContentValues
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.provider.MediaStore
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.aru.journal.auth.*
import com.aru.journal.data.AtomicJournalStore
import com.aru.journal.domain.*
import com.aru.journal.ui.*
import kotlinx.coroutines.*
import org.junit.*
import java.io.File
import java.time.LocalDate

class ImageImportUiTest {
 @get:Rule val rule=createAndroidComposeRule<MainActivity>()
 private val scope=CoroutineScope(SupervisorJob()+Dispatchers.Main)
 private lateinit var repo:JournalRepository
 private var capturedText=""
 private var scanned=0
 private val jpeg:ByteArray get(){val b=Bitmap.createBitmap(600,400,Bitmap.Config.ARGB_8888);b.eraseColor(Color.WHITE);return java.io.ByteArrayOutputStream().use{b.compress(Bitmap.CompressFormat.JPEG,90,it);b.recycle();it.toByteArray()}}
 @Before fun systemUiOnly(){val root=InstrumentationRegistry.getInstrumentation().uiAutomation.rootInActiveWindow?:return;if(root.findAccessibilityNodeInfosByText("System UI isn't responding").isNotEmpty())root.findAccessibilityNodeInfosByText("Wait").firstOrNull()?.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK)}
 @After fun done(){scope.cancel()}
 private fun estimate(name:String)=NutritionEstimate(listOf(EstimatedItem(name,Portion(1.0,"serving",PortionKind.SERVING),Nutrients(520.0,31.0,63.0,16.0),listOf(SourceReference(SourceKind.USER,"Synthetic fixture")))),true,"Synthetic fixture; portions estimated",1,65,name)
 private fun launch(kind:String){
  val context=InstrumentationRegistry.getInstrumentation().targetContext;val dir=File(context.cacheDir,"import-${System.nanoTime()}")
  val isolated=object:ContextWrapper(context){override fun getFilesDir()=dir}
  repo=JournalRepository(SessionProvider{AccountSession("image-test",Long.MAX_VALUE)},AtomicJournalStore(isolated))
  val scan=ImageImportAnalyzer{_,_->scanned++;when(kind){"dish"->ImageScanAnalysis("dish","Mixed sushi rolls",estimate=estimate("Mixed sushi rolls"));"other"->ImageScanAnalysis("other","No food found");else->ImageScanAnalysis(kind,"Choose what you ate",listOf(ImageFoodOption("0","Chicken Bowl","Rice and chicken","Bowls"),ImageFoodOption("1","Lemonade","Lemon drink","Drinks")))}}
  val estimator=NutritionEstimator{request->capturedText=request.text;estimate("Chicken Bowl")}
  val controller=JournalController(repo,scope,estimator,imageAnalyzer=scan)
  rule.activity.runOnUiThread{rule.activity.setContent{MaterialTheme{ImageImportSheet(jpeg,LocalDate.now(),controller){}}}}
  rule.onNodeWithText("Analyze image").assertIsDisplayed()
 }
 private fun shot(name:String){rule.waitForIdle();val inst=InstrumentationRegistry.getInstrumentation();val f=File(inst.targetContext.getExternalFilesDir(null),"$name.png");f.outputStream().use{inst.uiAutomation.takeScreenshot().compress(Bitmap.CompressFormat.PNG,100,it)};android.os.ParcelFileDescriptor.AutoCloseInputStream(inst.uiAutomation.executeShellCommand("cp ${f.absolutePath} /data/local/tmp/$name.png")).use{it.readBytes()}}
 @Test fun dishPreviewRequiresExplicitAdd(){launch("dish");Assert.assertEquals(0,scanned);rule.onNodeWithText("Analyze image").performClick();rule.onNodeWithText("+ Add to Aru").assertIsDisplayed();Assert.assertTrue(repo.read().entries.isEmpty());shot("aru-shared-dish-preview");rule.onNodeWithText("+ Add to Aru").performClick();rule.waitUntil(5000){repo.read().entries.size==1};Assert.assertEquals("Mixed sushi rolls",repo.read().entries.single().text)}
 @Test fun menuOnlyEstimatesTheChosenFood(){launch("menu");rule.onNodeWithText("Analyze image").performClick();rule.onNodeWithText("Continue · 0 selected").assertIsNotEnabled();rule.onNodeWithContentDescription("Select Chicken Bowl").performScrollTo().performClick();rule.onNodeWithText("Amount for Chicken Bowl").performScrollTo().performTextReplacement("2 servings");shot("aru-shared-menu-choices");rule.onNodeWithText("Continue · 1 selected").performClick();rule.onNodeWithText("+ Add to Aru").assertIsDisplayed();Assert.assertTrue(repo.read().entries.isEmpty());Assert.assertTrue(capturedText.contains("2 servings Chicken Bowl"));Assert.assertFalse(capturedText.contains("Lemonade"));rule.onNodeWithText("+ Add to Aru").performClick();rule.waitUntil(5000){repo.read().entries.size==1};Assert.assertFalse(repo.read().entries.single().text.contains("Lemonade"))}
 @Test fun unrelatedImageHasNoAddAction(){launch("other");rule.onNodeWithText("Analyze image").performClick();rule.onNodeWithText("No readable food found").assertIsDisplayed();rule.onNodeWithText("+ Add to Aru").assertDoesNotExist();Assert.assertTrue(repo.read().entries.isEmpty())}
}
