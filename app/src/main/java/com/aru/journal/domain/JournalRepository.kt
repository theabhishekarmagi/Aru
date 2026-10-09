package com.aru.journal.domain

import com.aru.journal.auth.AccountRequiredException
import com.aru.journal.auth.SessionProvider
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

/** Writes must atomically replace the previous state or throw, never silently lose edits. */
interface JournalStore {
    fun read(accountId: String): JournalState
    fun write(accountId: String, state: JournalState)
}

/** One instance per store. Disk IO is synchronous: invoke from an IO dispatcher in the future UI. */
class JournalRepository(
    private val sessions: SessionProvider,
    private val store: JournalStore,
    private val now: () -> Long = System::currentTimeMillis,
    private val newId: () -> String = { UUID.randomUUID().toString() }
) {
    private val json = Json { encodeDefaults = true }

    private fun account(): String {
        val session = sessions.currentSession() ?: throw AccountRequiredException()
        if (session.expiresAtEpochMillis <= now()) throw AccountRequiredException()
        return session.accountId
    }

    // Serialization snapshots detach caller-supplied mutable lists from stored state.
    private fun snapshot(state: JournalState): JournalState = json.decodeFromString(json.encodeToString(state))

    private fun persist(owner: String, state: JournalState) {
        if (account() != owner) throw AccountRequiredException()
        store.write(owner, snapshot(state))
    }

    @Synchronized fun read(): JournalState = snapshot(store.read(account()))

    @Synchronized fun addDraft(text: String, date: LocalDate, zone: ZoneId): JournalEntry {
        val owner = account()
        val state = store.read(owner)
        val entry = JournalEntry(newId(), owner, date.toString(), zone.id, text)
        persist(owner, state.copy(entries = state.entries + entry))
        return entry
    }

    /** Persist each text change before scheduling AI. An edit invalidates any in-flight request. */
    @Synchronized fun editText(id: String, text: String): JournalEntry = change(id) {
        it.copy(text = text, revision = it.revision + 1, status = CalculationStatus.DRAFT,
            requestId = null, estimate = null, failureCode = null)
    }

    @Synchronized fun queue(id: String): JournalEntry = change(id) {
        require(it.text.isNotBlank())
        it.copy(revision = it.revision + 1, status = CalculationStatus.QUEUED,
            requestId = null, estimate = null, failureCode = null)
    }

    @Synchronized fun beginEstimate(id: String): EstimateRequest {
        val entry = change(id) {
            require(it.status == CalculationStatus.QUEUED)
            it.copy(status = CalculationStatus.CALCULATING, requestId = newId())
        }
        return EstimateRequest(entry.id, entry.accountId, entry.revision, entry.requestId!!,
            entry.text, entry.journalDate, entry.timeZoneId)
    }

    /** False means stale, deleted, retried, manually corrected, or a different signed-in account. */
    @Synchronized fun acceptEstimate(request: EstimateRequest, estimate: NutritionEstimate): Boolean =
        finish(request) { it.copy(status = if (estimate.needsReview) CalculationStatus.NEEDS_REVIEW else CalculationStatus.READY,
            requestId = null, estimate = estimate, failureCode = null) }

    @Synchronized fun failEstimate(request: EstimateRequest, code: String): Boolean {
        require(code.isNotBlank())
        return finish(request) { it.copy(status = CalculationStatus.FAILED, requestId = null, failureCode = code) }
    }

    @Synchronized fun correct(id: String, estimate: NutritionEstimate): JournalEntry = change(id) {
        it.copy(revision = it.revision + 1, status = CalculationStatus.MANUAL,
            requestId = null, estimate = estimate, failureCode = null)
    }

    /** Call after process restart; durable pending text is retained for later retry. */
    @Synchronized fun recoverInterruptedCalculations() {
        val owner = account()
        val state = store.read(owner)
        persist(owner, state.copy(entries = state.entries.map {
            if (it.status == CalculationStatus.CALCULATING)
                it.copy(revision = it.revision + 1, status = CalculationStatus.QUEUED, requestId = null)
            else it
        }))
    }

    @Synchronized fun delete(id: String): String {
        val owner = account()
        val state = store.read(owner)
        val entry = state.entries.single { it.id == id }
        val undo = DeletedEntry(newId(), entry)
        persist(owner, state.copy(entries = state.entries.filterNot { it.id == id },
            deletedEntries = (state.deletedEntries + undo).takeLast(20)))
        return undo.undoId
    }

    @Synchronized fun undoDelete(undoId: String): JournalEntry {
        val owner = account()
        val state = store.read(owner)
        val deleted = state.deletedEntries.single { it.undoId == undoId }
        require(state.entries.none { it.id == deleted.entry.id })
        val prior = deleted.entry
        val restored = prior.copy(revision = prior.revision + 1, requestId = null,
            status = if (prior.status == CalculationStatus.CALCULATING) CalculationStatus.QUEUED else prior.status)
        persist(owner, state.copy(entries = state.entries + restored,
            deletedEntries = state.deletedEntries.filterNot { it.undoId == undoId }))
        return restored
    }

    @Synchronized fun saveMeal(entryId: String, name: String): SavedMeal {
        val owner = account()
        val state = store.read(owner)
        val entry = state.entries.single { it.id == entryId }
        require(entry.status in setOf(CalculationStatus.READY, CalculationStatus.MANUAL, CalculationStatus.NEEDS_REVIEW))
        val meal = SavedMeal(newId(), name, entry.text, requireNotNull(entry.estimate))
        persist(owner, state.copy(savedMeals = state.savedMeals + meal))
        return read().savedMeals.single { it.id == meal.id }
    }

    @Synchronized fun reuseMeal(mealId: String, date: LocalDate, zone: ZoneId): JournalEntry {
        val owner = account()
        val state = store.read(owner)
        val meal = state.savedMeals.single { it.id == mealId }
        val entry = JournalEntry(newId(), owner, date.toString(), zone.id, meal.text,
            status = if (meal.estimate.needsReview) CalculationStatus.NEEDS_REVIEW else CalculationStatus.READY,
            estimate = meal.estimate, savedMealId = mealId)
        persist(owner, state.copy(entries = state.entries + entry))
        return read().entries.single { it.id == entry.id }
    }

    @Synchronized fun setGoals(goals: NutritionGoals) {
        val owner = account()
        persist(owner, store.read(owner).copy(goals = goals))
    }

    private fun change(id: String, transform: (JournalEntry) -> JournalEntry): JournalEntry {
        val owner = account()
        val state = store.read(owner)
        val entry = transform(state.entries.single { it.id == id && it.accountId == owner })
        persist(owner, state.copy(entries = state.entries.map { if (it.id == id) entry else it }))
        return read().entries.single { it.id == id }
    }

    private fun finish(request: EstimateRequest, transform: (JournalEntry) -> JournalEntry): Boolean {
        val owner = account()
        if (owner != request.accountId) return false
        val state = store.read(owner)
        val current = state.entries.singleOrNull { it.id == request.entryId } ?: return false
        if (current.accountId != owner || current.revision != request.revision ||
            current.requestId != request.requestId || current.status != CalculationStatus.CALCULATING) return false
        persist(owner, state.copy(entries = state.entries.map { if (it.id == current.id) transform(it) else it }))
        return true
    }
}
