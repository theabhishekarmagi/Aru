package com.aru.journal.domain

import kotlinx.serialization.Serializable
import java.util.Locale
import kotlin.math.roundToInt

@Serializable
data class WaterIntake(
    val milliliters: Int,
    val detectionKey: String,
    val onlyWater: Boolean,
    val assumed: Boolean = true,
    val portionLabel: String? = null
) {
    init {
        require(milliliters in 1..20000 && detectionKey.isNotBlank())
        require(portionLabel == null || portionLabel.length in 1..60)
    }
}

/** Conservative, local parsing for drinking water, not ingredients or caloric beverages. */
object WaterText {
    const val GLASS_ML = 250
    const val BOTTLE_ML = 500
    private val numberWords = mapOf("a" to 1.0, "an" to 1.0, "one" to 1.0, "two" to 2.0, "three" to 3.0,
        "four" to 4.0, "five" to 5.0, "six" to 6.0, "seven" to 7.0, "eight" to 8.0, "nine" to 9.0, "ten" to 10.0,
        "half" to .5)
    private val count = "(?:[0-9]+(?:\\.[0-9]+)?|a|an|one|two|three|four|five|six|seven|eight|nine|ten|half(?:\\s+a)?)"
    private val units = "(?:ml|millilitres?|milliliters?|litres?|liters?|l|glasses|glass|bottles?|cups?)"
    private val adjective = "(?:(?:plain|drinking|warm|cold|hot|filtered|mineral|sparkling|still)\\s+)*"
    private val before = Regex("\\b($count)\\s*($units)\\s+(?:of\\s+)?${adjective}water\\b")
    private val vessel = Regex("\\b(glasses|glass|bottles?|cups?)\\s+(?:of\\s+)?${adjective}water\\b")
    private val after = Regex("\\b${adjective}water\\s*[,:(-]?\\s*($count)\\s*($units)\\b")
    private val waterWord = Regex("\\b${adjective}water\\b")
    private val nonWater = Regex("\\b(coconut|tonic|sugar|sugared|sweetened|honey|rose|lemon|lime|flavou?red|juice)\\s+(?:\\w+\\s+)?water\\b|\\bwater\\s+(?:chestnuts?|melon|ice)\\b")
    private val preparation = Regex("\\b(?:cook(?:ed|ing)?|boil(?:ed|ing)?|recipe|ingredient|mix(?:ed|ing)?)\\b")
    private val negated = Regex("\\b(?:no|without|not)\\s+(?:any\\s+)?water\\b")
    private val filler = setOf("i","we","drank","drink","drinking","had","have","having","some","and","plus","with","of","today","just","a","an")
    private data class Match(val range: IntRange, val ml: Int, val assumed: Boolean)
    private fun amount(quantity: String, unit: String): Pair<Int, Boolean>? {
        val normalized = quantity.removeSuffix(" a")
        val n = numberWords[normalized] ?: normalized.toDoubleOrNull() ?: return null
        if (!n.isFinite() || n <= 0) return null
        val perUnit = when {
            unit.startsWith("glass") || unit.startsWith("cup") -> GLASS_ML
            unit.startsWith("bottle") -> BOTTLE_ML
            unit == "l" || unit.startsWith("lit") -> 1000
            else -> 1
        }
        val total = n * perUnit
        if (total < 1 || total > 20000) return null
        return total.roundToInt() to (unit.startsWith("glass") || unit.startsWith("cup") || unit.startsWith("bottle"))
    }
    fun detect(text: String): WaterIntake? {
        if (text.length > 10000) return null
        val lower = text.lowercase(Locale.ROOT).trim()
        // Preparations and flavoured drinks are left to regular nutrition estimation.
        if (nonWater.containsMatchIn(lower) || preparation.containsMatchIn(lower) || negated.containsMatchIn(lower)) return null
        val matches = mutableListOf<Match>()
        fun add(range: IntRange, ml: Int, assumed: Boolean) {
            if (matches.none { it.range.first <= range.last && range.first <= it.range.last }) matches += Match(range, ml, assumed)
        }
        for (pattern in listOf(before, after)) for (m in pattern.findAll(lower)) {
            val parsed = amount(m.groupValues[1], m.groupValues[2]) ?: return null
            add(m.range, parsed.first, parsed.second)
        }
        for (m in vessel.findAll(lower)) {
            val parsed = amount("one", m.groupValues[1]) ?: continue
            add(m.range, parsed.first, true)
        }
        for (m in waterWord.findAll(lower)) add(m.range, GLASS_ML, true)
        if (matches.isEmpty()) return null
        val total = matches.sumOf { it.ml }
        if (total !in 1..20000) return null
        val remaining = StringBuilder(lower)
        matches.forEach { m -> for (i in m.range) remaining.setCharAt(i, ' ') }
        val remainingWords = Regex("[a-z0-9]+").findAll(remaining).map { it.value }.toList()
        val onlyWater = remainingWords.all { it in filler }
        val key = matches.sortedBy { it.range.first }.joinToString("|") { lower.substring(it.range) }
        return WaterIntake(total, key, onlyWater, matches.any { it.assumed })
    }
    fun forText(text: String, prior: WaterIntake? = null): WaterIntake? {
        val detected = detect(text) ?: return null
        return if (prior?.detectionKey == detected.detectionKey) prior.copy(onlyWater = detected.onlyWater) else detected
    }
}
