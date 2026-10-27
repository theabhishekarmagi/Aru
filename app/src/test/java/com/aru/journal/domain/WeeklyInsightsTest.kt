package com.aru.journal.domain
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import kotlinx.serialization.json.Json
class WeeklyInsightsTest {
 private fun meal(id:String,date:String,cal:Double):JournalEntry {
  val estimate=NutritionEstimate(listOf(EstimatedItem("Lunch",Portion(1.0,"plate",PortionKind.SERVING),Nutrients(cal,10.0,50.0,10.0),listOf(SourceReference(SourceKind.USER,"Fixture")))),false,"Fixture",1)
  return JournalEntry(id,"owner",date,"UTC","lunch",status=CalculationStatus.READY,estimate=estimate)
 }
 @Test fun `weekly averages exclude unfinished days and keep water-only consistency`() {
  val entries=listOf(meal("a","2026-10-27",600.0),meal("b","2026-10-26",400.0),JournalEntry("c","owner","2026-10-25","UTC","500 ml water"),meal("d","2026-10-24",100.0),JournalEntry("e","owner","2026-10-24","UTC","pending rice"),meal("f","2026-10-19",2000.0))
  val week=weeklyInsights(entries,LocalDate.of(2026,10,27));assertEquals(7,week.days.size);assertEquals(4,week.loggedDays);assertEquals(500,week.totalWaterMl);assertEquals(71,week.averageWaterMl);assertEquals(500.0,week.averages[0].value!!,0.0);assertEquals(2,week.averages[0].days)
  assertNull(weeklyInsights(emptyList(),LocalDate.of(2026,10,27)).averages[0].value)
 }
 @Test fun `daily water counts corrected portions and default goal survives old stored settings`() {
  val date="2026-10-27";val detected=WaterText.detect("one glass of water")!!
  val entry=JournalEntry("a","owner",date,"UTC","one glass of water",water=detected.copy(milliliters=330,assumed=false))
  assertEquals(330,dailyWater(listOf(entry),date));assertEquals(0,dailyWater(listOf(entry),"2026-10-26"))
  assertEquals(2000,Json.decodeFromString<GoalPreferences>("{}").waterGoalMl)
  assertThrows(IllegalArgumentException::class.java){GoalPreferences(waterGoalMl=0)}
  assertThrows(IllegalArgumentException::class.java){GoalPreferences(waterGoalMl=20001)}
 }
}
