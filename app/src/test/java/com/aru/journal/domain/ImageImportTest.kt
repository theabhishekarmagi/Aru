package com.aru.journal.domain

import com.aru.journal.auth.*
import kotlinx.serialization.json.Json
import org.junit.*
import java.time.LocalDate

class ImageImportTest {
 @Test fun recipeChoicesRetainPrintedAmountsWithoutNutrition(){
  val result=Json.decodeFromString<ImageScanAnalysis>("""{"kind":"ingredients","description":"Choose what you ate","options":[{"id":"0","name":"Rice","description":"Cooked rice","category":"Ingredients","portionHint":"100 g"}],"estimate":null}""")
  Assert.assertEquals("100 g",result.options.single().portionHint);Assert.assertNull(result.estimate)
 }
 @Test fun imageSelectionPreviewNeverCreatesAnEntry(){
  var stored=JournalState();val store=object:JournalStore{override fun read(accountId:String)=stored;override fun write(accountId:String,state:JournalState){stored=state}}
  val repo=JournalRepository(SessionProvider{AccountSession("image-preview",Long.MAX_VALUE)},store)
  val request=repo.imagePreviewRequest("2 servings Chicken Bowl",LocalDate.parse("2026-10-27"))
  Assert.assertEquals("image-preview",request.accountId);Assert.assertEquals("2 servings Chicken Bowl",request.text);Assert.assertTrue(repo.read().entries.isEmpty())
 }
 @Test fun malformedImageClassificationIsRejected(){
  Assert.assertThrows(IllegalArgumentException::class.java){ImageScanAnalysis("dish","Dish",estimate=null)}
  Assert.assertThrows(IllegalArgumentException::class.java){ImageScanAnalysis("menu","Menu")}
  Assert.assertThrows(IllegalArgumentException::class.java){ImageScanAnalysis("other","No food",listOf(ImageFoodOption("0","Rice")))}
 }
}
