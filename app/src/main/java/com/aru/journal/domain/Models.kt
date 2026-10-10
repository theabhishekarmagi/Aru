package com.aru.journal.domain

import kotlinx.serialization.Serializable
import java.time.LocalDate
import java.time.ZoneId

@Serializable
data class Nutrients(
    val caloriesKcal: Double? = null,
    val proteinG: Double? = null,
    val carbsG: Double? = null,
    val fatG: Double? = null,
    val fiberG: Double? = null
) {
    init { values().filterNotNull().forEach { require(it.isFinite() && it >= 0) } }
    fun values(): List<Double?> = listOf(caloriesKcal, proteinG, carbsG, fatG, fiberG)
}

@Serializable enum class PortionKind { COUNT, HOUSEHOLD, WEIGHT, VOLUME, SERVING }

/** Unit keys are extensible (e.g. g, ml, piece, katori). Never assume a universal bowl weight. */
@Serializable
data class Portion(
    val quantity: Double,
    val unitKey: String,
    val kind: PortionKind,
    val grams: Double? = null,
    val milliliters: Double? = null,
    val assumed: Boolean = false,
    val assumption: String? = null
) {
    init {
        require(quantity.isFinite() && quantity > 0 && unitKey.isNotBlank())
        listOfNotNull(grams, milliliters).forEach { require(it.isFinite() && it > 0) }
        require(!assumed || !assumption.isNullOrBlank())
    }
}

@Serializable enum class SourceKind { INDB, USDA, OFFICIAL_RESTAURANT, ARU_DATABASE, OTHER, USER, AI_ESTIMATE }
@Serializable
data class SourceReference(
    val kind: SourceKind,
    val title: String,
    val url: String? = null,
    val recordId: String? = null,
    val version: String? = null,
    val market: String? = null,
    val menuItem: String? = null,
    val menuSize: String? = null,
    val basis: String? = null
) {
    init {
        require(title.isNotBlank())
        require(url == null || url.startsWith("https://"))
        if (kind !in setOf(SourceKind.USER, SourceKind.AI_ESTIMATE, SourceKind.ARU_DATABASE)) require(!url.isNullOrBlank())
        if (kind == SourceKind.INDB) require(!recordId.isNullOrBlank() && !version.isNullOrBlank() && !basis.isNullOrBlank())
        if (kind == SourceKind.USDA) require(!recordId.isNullOrBlank() && !basis.isNullOrBlank())
        if (kind == SourceKind.OFFICIAL_RESTAURANT) {
            require(!market.isNullOrBlank() && !menuItem.isNullOrBlank() && !menuSize.isNullOrBlank())
        }
    }
}

@Serializable
data class EstimatedItem(
    val name: String,
    val portion: Portion,
    val nutrients: Nutrients,
    val sources: List<SourceReference>,
    val assumptions: List<String> = emptyList()
) {
    init { require(name.isNotBlank() && sources.isNotEmpty()) }
}

@Serializable
data class NutritionEstimate(
    val items: List<EstimatedItem>,
    val needsReview: Boolean,
    val explanation: String,
    val calculatedAtEpochMillis: Long,
    val confidenceScore: Int = 55
) {
    init {
        require(items.isNotEmpty() && explanation.isNotBlank())
        require(confidenceScore in 0..100)
        require(items.none { it.portion.assumed } || needsReview) {
            "Assumed portions must be marked for review"
        }
    }
}

@Serializable enum class CalculationStatus { DRAFT, QUEUED, CALCULATING, READY, NEEDS_REVIEW, FAILED, MANUAL }

@Serializable
data class JournalEntry(
    val id: String,
    val accountId: String,
    val journalDate: String,
    val timeZoneId: String,
    val text: String,
    val revision: Long = 1,
    val status: CalculationStatus = CalculationStatus.DRAFT,
    val requestId: String? = null,
    val estimate: NutritionEstimate? = null,
    val failureCode: String? = null,
    val savedMealId: String? = null
) {
    init {
        require(id.isNotBlank() && accountId.isNotBlank() && revision > 0)
        LocalDate.parse(journalDate)
        ZoneId.of(timeZoneId)
    }
}

@Serializable
data class SavedMeal(val id: String, val name: String, val text: String, val estimate: NutritionEstimate) {
    init { require(id.isNotBlank() && name.isNotBlank()) }
}

@Serializable
data class NutritionGoals(val targets: Nutrients = Nutrients()) {
    init { targets.values().filterNotNull().forEach { require(it > 0) } }
}

@Serializable data class DeletedEntry(val undoId: String, val entry: JournalEntry)
@Serializable
data class JournalState(
    val schemaVersion: Int = 1,
    val entries: List<JournalEntry> = emptyList(),
    val savedMeals: List<SavedMeal> = emptyList(),
    val deletedEntries: List<DeletedEntry> = emptyList(),
    val goals: NutritionGoals = NutritionGoals()
) {
    init { require(schemaVersion == 1) { "Unsupported journal schema" } }
}

data class EstimateRequest(
    val entryId: String, val accountId: String, val revision: Long, val requestId: String,
    val text: String, val journalDate: String, val timeZoneId: String
)

data class PhotoMealAnalysis(val description: String, val estimate: NutritionEstimate) {
    init { require(description.isNotBlank()) }
}

/** Server adapter must authenticate ownership. Provider/search keys must never reach the APK. */
fun interface NutritionEstimator { suspend fun estimate(request: EstimateRequest): NutritionEstimate }

/** Photos are transient input. Implementations must return only derived meal data. */
fun interface PhotoNutritionAnalyzer {
    suspend fun analyze(requestId: String, jpeg: ByteArray): PhotoMealAnalysis
}

data class NutrientTotal(val knownAmount: Double, val missingItemCount: Int)
/** Values follow Nutrients.values(): kcal, protein g, carbs g, fat g, fiber g. */
data class DailyTotals(val values: List<NutrientTotal>, val pendingEntryCount: Int, val reviewEntryCount: Int)

fun dailyTotals(entries: List<JournalEntry>, date: String): DailyTotals {
    val day = entries.filter { it.journalDate == date && it.text.isNotBlank() }
    val included = day.filter { it.status in setOf(CalculationStatus.READY, CalculationStatus.NEEDS_REVIEW, CalculationStatus.MANUAL) }
    val nutrients = included.flatMap { it.estimate?.items.orEmpty() }.map { it.nutrients.values() }
    return DailyTotals(
        (0..4).map { column ->
            NutrientTotal(nutrients.sumOf { it[column] ?: 0.0 }, nutrients.count { it[column] == null })
        },
        day.size - included.size,
        day.count { it.status == CalculationStatus.NEEDS_REVIEW }
    )
}

class NutritionServiceException(val code: String) : Exception(code)
