package com.aru.journal.domain

import java.time.LocalDate

data class InsightDay(val date:String,val logged:Boolean,val hasEstimates:Boolean,val totals:DailyTotals,val waterMl:Int)
data class InsightAverage(val value:Double?,val days:Int)
data class WeeklyInsights(val days:List<InsightDay>,val averages:List<InsightAverage>,val loggedDays:Int,val totalWaterMl:Int) {
 val averageWaterMl:Int get()=kotlin.math.round(totalWaterMl/7.0).toInt()
}
fun dailyWater(entries:List<JournalEntry>,date:String):Int=entries.filter {it.journalDate==date&&it.text.isNotBlank()}.sumOf {WaterText.forText(it.text,it.water)?.milliliters?:0}
fun weeklyInsights(entries:List<JournalEntry>,end:LocalDate):WeeklyInsights {
 val days=(6 downTo 0).map { offset->
  val date=end.minusDays(offset.toLong()).toString()
  val rows=entries.filter {it.journalDate==date&&it.text.isNotBlank()}.map {it.copy(water=WaterText.forText(it.text,it.water))}
  InsightDay(date,rows.isNotEmpty(),rows.any {it.water?.onlyWater!=true&&it.estimate!=null},dailyTotals(rows,date),dailyWater(rows,date))
 }
 val averages=(0..3).map {i->val complete=days.filter {it.hasEstimates&&it.totals.pendingEntryCount==0&&it.totals.values[i].missingItemCount==0};InsightAverage(if(complete.isEmpty())null else complete.sumOf {it.totals.values[i].knownAmount}/complete.size,complete.size)}
 return WeeklyInsights(days,averages,days.count {it.logged},days.sumOf {it.waterMl})
}
