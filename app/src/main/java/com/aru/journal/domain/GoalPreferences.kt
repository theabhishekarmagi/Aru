package com.aru.journal.domain

import kotlinx.serialization.Serializable
import java.time.LocalDate
import java.time.Period
import kotlin.math.roundToInt

@Serializable
data class GoalPreferences(
    val goalType: String = "maintain",
    val targetWeightKg: Double? = null,
    val targetDate: String? = null,
    val weightUnit: String = "kg",
    val dietaryPreferences: List<String> = emptyList(),
    val equationSex: String? = null,
    val activityLevel: String = "sedentary",
    val trackSugar: Boolean = false,
    val trackFiber: Boolean = false,
    val trackSodium: Boolean = false
) {
    init {
        require(goalType in listOf("maintain", "lose", "gain"))
        require(targetWeightKg == null || targetWeightKg in 20.0..500.0)
        require(targetDate == null || runCatching { LocalDate.parse(targetDate) }.isSuccess)
        require(weightUnit in listOf("kg", "lb"))
        require(equationSex == null || equationSex in listOf("female", "male"))
        require(activityLevel in activityMultipliers)
        require(dietaryPreferences.distinct().size == dietaryPreferences.size && dietaryPreferences.all { it in dietOptions })
    }
    companion object {
        val activityMultipliers = linkedMapOf("sedentary" to 1.2, "light" to 1.375, "moderate" to 1.55, "active" to 1.725)
        val dietOptions = listOf("Vegetarian", "Vegan", "Pescatarian", "Halal", "Gluten-free", "Dairy-free")
    }
}

/** Mifflin–St Jeor resting energy × selected activity factor. Never sets intake goals. */
fun estimatedDailyBurn(profile: UserProfile?, preferences: GoalPreferences, today: LocalDate = LocalDate.now()): Int? {
    val birth = profile?.dateOfBirth?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: return null
    val age = Period.between(birth, today).years
    if (age !in 19..78) return null
    val weight = profile.weightKg ?: return null
    val height = profile.heightCm ?: return null
    val offset = when (preferences.equationSex) { "male" -> 5; "female" -> -161; else -> return null }
    val resting = 10 * weight + 6.25 * height - 5 * age + offset
    return (resting * GoalPreferences.activityMultipliers.getValue(preferences.activityLevel)).takeIf { it > 0 }?.roundToInt()
}

fun goalProgress(amount: Double, target: Double?): Float =
    if (target == null || !target.isFinite() || target <= 0 || !amount.isFinite()) 0f else (amount / target).toFloat().coerceIn(0f, 1f)
