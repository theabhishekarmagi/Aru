package com.aru.journal.domain

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class GoalPreferencesTest {
    @Test fun `burn needs complete adult profile and explicit equation sex`() {
        val profile=UserProfile("test",dateOfBirth="1996-10-26",heightCm=175.0,weightKg=70.0)
        val prefs=GoalPreferences(equationSex="male")
        val today=LocalDate.of(2026,10,26)
        assertEquals(1979,estimatedDailyBurn(profile,prefs,today))
        assertEquals(1779,estimatedDailyBurn(profile,prefs.copy(equationSex="female"),today))
        assertNull(estimatedDailyBurn(profile,GoalPreferences(),today))
        assertNull(estimatedDailyBurn(profile.copy(weightKg=null),prefs,today))
        assertNull(estimatedDailyBurn(profile.copy(dateOfBirth="2010-01-01"),prefs,today))
        assertNull(estimatedDailyBurn(profile.copy(dateOfBirth="1940-01-01"),prefs,today))
    }
    @Test fun `settings survive serialization without changing nutrition targets`() {
        val goal=NutritionGoals(Nutrients(2000.0,sodiumMg=2300.0),GoalPreferences("lose",65.0,"2027-01-01","lb",listOf("Vegetarian"),"female","moderate",true,false,true))
        assertEquals(goal,Json.decodeFromString<NutritionGoals>(Json.encodeToString(goal)))
        assertEquals(2000.0,goal.targets.caloriesKcal!!,0.0)
        val legacy=Json.decodeFromString<NutritionGoals>("""{"targets":{"fiberG":30.0}}""")
        assertTrue(legacy.preferences.trackFiber)
        assertNull(legacy.targets.sugarG)
        assertNull(legacy.targets.sodiumMg)
        assertThrows(IllegalArgumentException::class.java) {GoalPreferences(targetWeightKg=Double.NaN)}
        assertThrows(IllegalArgumentException::class.java) {GoalPreferences(dietaryPreferences=listOf("other"))}
    }
    @Test fun `progress clamps display and unknown micros remain incomplete`() {
        assertEquals(1f,goalProgress(200.0,100.0))
        assertEquals(0f,goalProgress(200.0,null))
        assertEquals(0f,goalProgress(Double.POSITIVE_INFINITY,100.0))
        val date=LocalDate.of(2026,10,26).toString()
        val estimate=NutritionEstimate(listOf(EstimatedItem("Test",Portion(1.0,"serving",PortionKind.SERVING),Nutrients(100.0,sodiumMg=600.0),listOf(SourceReference(SourceKind.USER,"Fixture")))),false,"Fixture",1)
        val total=dailyTotals(listOf(JournalEntry("a","user",date,"UTC","Test",status=CalculationStatus.READY,estimate=estimate)),date)
        assertEquals(600.0,total.values[6].knownAmount,0.0)
        assertEquals(1,total.values[5].missingItemCount)
    }
}
