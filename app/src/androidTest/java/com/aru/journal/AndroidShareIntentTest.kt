package com.aru.journal

import android.content.ContentValues
import android.content.Intent
import android.graphics.Bitmap
import android.provider.MediaStore
import androidx.lifecycle.ViewModelProvider
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import kotlinx.coroutines.*
import org.junit.*

/** A real OS launch, kept separate from ActivityScenario's synthetic task. */
class AndroidShareIntentTest {
 @Test fun androidShareIntentIsResolvedAndReceivedWithoutUploading(){
  val inst=InstrumentationRegistry.getInstrumentation();val context=inst.targetContext
  val share=Intent(Intent.ACTION_SEND).setType("image/jpeg")
  Assert.assertTrue(context.packageManager.queryIntentActivities(share,android.content.pm.PackageManager.MATCH_DEFAULT_ONLY).any{it.activityInfo.packageName==context.packageName})
  inst.runOnMainSync{listOf(Stage.RESUMED,Stage.PAUSED,Stage.STOPPED).flatMap{ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(it)}.distinct().forEach{it.finish()}}
  val uri=context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,ContentValues().apply{put(MediaStore.Images.Media.DISPLAY_NAME,"aru-test-share-${System.nanoTime()}.jpg");put(MediaStore.Images.Media.MIME_TYPE,"image/jpeg")})!!
  val monitor=inst.addMonitor(MainActivity::class.java.name,null,false)
  var activity:MainActivity?=null
  try{
   context.contentResolver.openOutputStream(uri)!!.use{output->val b=Bitmap.createBitmap(600,400,Bitmap.Config.ARGB_8888);b.eraseColor(android.graphics.Color.WHITE);b.compress(Bitmap.CompressFormat.JPEG,90,output);b.recycle()}
   context.startActivity(Intent(context,MainActivity::class.java).setAction(Intent.ACTION_SEND).setType("image/jpeg").putExtra(Intent.EXTRA_STREAM,uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
   activity=inst.waitForMonitorWithTimeout(monitor,15000) as? MainActivity
   Assert.assertNotNull("Android launched Aru for ACTION_SEND",activity)
   var model:JournalViewModel?=null;inst.runOnMainSync{model=ViewModelProvider(activity!!)[JournalViewModel::class.java]}
   runBlocking{withTimeout(10000){while(model!!.sharedImage.value==null)delay(50)}}
   Assert.assertTrue(model!!.sharedImage.value!!.size<=1_500_000)
  }finally{inst.removeMonitor(monitor);activity?.let{a->inst.runOnMainSync{a.finishAndRemoveTask()}};context.contentResolver.delete(uri,null,null)}
 }
}
