package com.aru.journal.domain

import com.aru.journal.auth.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class WaterIntakeTest {
    private val date = LocalDate.of(2026,10,23)
    private val zone = ZoneId.of("Asia/Kolkata")
    private class Store : JournalStore {
        var state = JournalState()
        override fun read(accountId: String) = state
        override fun write(accountId: String, state: JournalState) { this.state = state }
    }
    private fun repo(store: Store) = JournalRepository(SessionProvider { AccountSession("water-test",Long.MAX_VALUE) },store)
    private fun food() = NutritionEstimate(listOf(EstimatedItem("Rice",Portion(1.0,"serving",PortionKind.SERVING),Nutrients(200.0,4.0,40.0,2.0,0.0),listOf(SourceReference(SourceKind.USER,"Test fixture")))),false,"Synthetic test nutrition",1)
    @Test fun glassesBottlesAndExplicitVolumesAreRecognized() {
        mapOf("water" to 250, "one glass of water" to 250, "three glasses of water" to 750,
            "I drank two bottles of water" to 1000, "a bottle of water" to 500,
            "330 ml water" to 330, "1.5 litres of water" to 1500, "water 500ml" to 500,
            "half glass of water" to 125, "half a glass of water" to 125, "one glass of water and a bottle of water" to 750).forEach { (text, ml) ->
            val water = requireNotNull(WaterText.detect(text)) { text }
            assertEquals(text, ml, water.milliliters)
            assertTrue(text, water.onlyWater)
        }
        assertTrue(WaterText.detect("water")!!.assumed)
        assertFalse(WaterText.detect("330ml water")!!.assumed)
    }
    @Test fun foodAndOtherDrinksAreNotMisclassifiedAsPlainWater() {
        for (text in listOf("watermelon", "coconut water", "one glass of lemon water", "water chestnuts", "no water", "500 ml water for cooking rice", "0 ml water", "999999 litres water")) {
            assertNull(text, WaterText.detect(text))
        }
        val mixed = requireNotNull(WaterText.detect("rice and a glass of water"))
        assertEquals(250, mixed.milliliters)
        assertFalse(mixed.onlyWater)
    }
    @Test fun manualAmountSurvivesUnrelatedEditsButQuantityChangesRedetectIt() {
        val original = WaterText.detect("rice and water")!!.copy(milliliters = 330, portionLabel = "Custom amount", assumed = false)
        assertEquals(330, WaterText.forText("dal and water", original)!!.milliliters)
        assertEquals(500, WaterText.forText("dal and two glasses of water", original)!!.milliliters)
        assertNull(WaterText.forText("dal", original))
    }
    @Test fun plainWaterHasNoPendingCaloriesAndDoesNotInflateMealTotals() {
        val r = repo(Store())
        val water = r.addDraft("three glasses of water", date, zone)
        assertEquals(750, water.water!!.milliliters)
        assertEquals(CalculationStatus.READY, water.status)
        assertNull(water.estimate)
        val meal = r.addDraft("rice", date, zone)
        r.correct(meal.id, food())
        val totals = dailyTotals(r.read().entries, date.toString())
        assertEquals(200.0,totals.values[0].knownAmount,0.0)
        assertEquals(0,totals.pendingEntryCount)
    }
    @Test fun portionCorrectionPersistsThroughSerializationRestartDeleteAndUndo() {
        val store = Store();val r = repo(store)
        val entry = r.addDraft("water",date,zone)
        r.setWater(entry.id,330,"Custom amount",false)
        store.state = Json.decodeFromString(Json.encodeToString(store.state))
        val restarted = repo(store)
        restarted.recoverInterruptedCalculations()
        assertEquals(330,restarted.read().entries.single().water!!.milliliters)
        val token = restarted.delete(entry.id)
        assertTrue(restarted.read().entries.isEmpty())
        assertEquals(330,restarted.undoDelete(token).water!!.milliliters)
        try { restarted.setWater(entry.id,0,null,false);fail("Invalid volume") } catch (_: IllegalArgumentException) { }
        assertEquals(330,restarted.read().entries.single().water!!.milliliters)
    }
    @Test fun legacyWaterRowsNormalizeWithoutDestroyingFoodOrDates() {
        val store = Store()
        val r = repo(store)
        val entry = r.addDraft("three glasses of water",date,zone)
        store.state = store.state.copy(entries = listOf(entry.copy(water=null,status=CalculationStatus.CALCULATING,requestId="old-request")))
        r.recoverInterruptedCalculations()
        val migrated = r.read().entries.single()
        assertEquals(750,migrated.water!!.milliliters)
        assertEquals(date.toString(),migrated.journalDate)
        assertEquals(CalculationStatus.READY,migrated.status)
        assertNull(migrated.requestId)
    }
    @Test fun waterCorrectionDoesNotLoseInflightFoodEstimateAndTextEditsRejectStaleResults() {
        val r = repo(Store())
        val mixed = r.addDraft("rice and water",date,zone)
        r.queue(mixed.id);val request = r.beginEstimate(mixed.id)
        r.setWater(mixed.id,330,"Custom amount",false)
        assertTrue(r.acceptEstimate(request,food()))
        assertEquals(330,r.read().entries.single().water!!.milliliters)
        r.editText(mixed.id,"rice");r.queue(mixed.id);val old = r.beginEstimate(mixed.id)
        r.editText(mixed.id,"water")
        assertFalse(r.acceptEstimate(old,food()))
        assertNull(r.read().entries.single().estimate)
    }
    @Test fun savedMixedMealRetainsItsCorrectedWaterPortionOnReuse() {
        val r = repo(Store())
        val entry = r.addDraft("rice and water",date,zone)
        r.correct(entry.id,food());r.setWater(entry.id,350,"Custom amount",false)
        val saved = r.saveMeal(entry.id,"Lunch")
        val reused = r.reuseMeal(saved.id,date.plusDays(1),zone)
        assertEquals(350,reused.water!!.milliliters)
        assertEquals(date.plusDays(1).toString(),reused.journalDate)
        assertNotNull(reused.estimate)
    }
}
