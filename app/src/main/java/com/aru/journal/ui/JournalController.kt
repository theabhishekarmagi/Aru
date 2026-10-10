package com.aru.journal.ui

import com.aru.journal.auth.AccountRequiredException
import com.aru.journal.domain.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.LocalDate
import java.time.ZoneId

/** Persist every edit before starting one estimate after 1.8 seconds without changes. */
class JournalController(
    private val repository: JournalRepository,
    private val scope: CoroutineScope,
    private val estimator: NutritionEstimator? = null,
    private val photoAnalyzer: PhotoNutritionAnalyzer? = null
) {
    private val mutable = MutableStateFlow(JournalUiState())
    val state = mutable.asStateFlow()
    val canEstimate get() = estimator != null
    val canAnalyzePhotos get() = photoAnalyzer != null
    private val work = Channel<suspend () -> Unit>(Channel.UNLIMITED)
    private val estimates = mutableMapOf<String, Job>()
    init {
        scope.launch(Dispatchers.IO) {
            for (action in work) try {
                action()
                mutable.value = mutable.value.copy(journal = repository.read(), loading = false, accountRequired = false)
            } catch (_: AccountRequiredException) {
                mutable.value = JournalUiState(loading = false, accountRequired = true)
            } catch (_: Exception) {
                mutable.value = mutable.value.copy(loading = false, error = "Couldn’t save this change. Your existing journal is safe. Please try again.")
            }
        }
        submit { repository.recoverInterruptedCalculations() }
    }
    private fun submit(action: suspend () -> Unit) { work.trySend(action) }
    fun clearError() { mutable.value = mutable.value.copy(error = null) }
    fun add(text: String, date: LocalDate, id: (String) -> Unit = {}) = submit {
        val entry = repository.addDraft(text, date, ZoneId.systemDefault())
        withContext(Dispatchers.Main) { id(entry.id) }
        schedule(entry)
    }
    fun edit(id: String, text: String) = submit {
        estimates.remove(id)?.cancel()
        schedule(repository.editText(id, text))
    }
    private fun schedule(entry: JournalEntry) {
        if (estimator == null || entry.text.isBlank()) return
        estimates[entry.id] = scope.launch {
            delay(1800)
            submit {
                val current = repository.read().entries.find { it.id == entry.id }
                if (current?.revision == entry.revision && current.status == CalculationStatus.DRAFT && current.text.isNotBlank()) startEstimate(entry.id)
            }
        }
    }
    fun calculate(id: String) = submit {
        val current = repository.read().entries.find { it.id == id }
        if (current != null && current.text.isNotBlank() && current.status !in setOf(CalculationStatus.CALCULATING, CalculationStatus.QUEUED)) startEstimate(id)
    }
    private fun startEstimate(id: String) {
        val service = estimator ?: return
        estimates.remove(id)?.cancel()
        repository.queue(id)
        val request = repository.beginEstimate(id)
        estimates[id] = scope.launch(Dispatchers.IO) {
            try {
                val result = service.estimate(request)
                submit { repository.acceptEstimate(request, result) }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (e: NutritionServiceException) { submit { repository.failEstimate(request, e.code) } }
            catch (_: Exception) { submit { repository.failEstimate(request, "unavailable") } }
        }
    }
    fun correct(id: String, estimate: NutritionEstimate) = submit {
        estimates.remove(id)?.cancel()
        repository.correct(id, estimate)
    }
    fun delete(id: String) = submit {
        estimates.remove(id)?.cancel()
        val token = repository.delete(id)
        mutable.value = mutable.value.copy(undoToken = token)
    }
    fun undo() = submit {
        mutable.value.undoToken?.let { repository.undoDelete(it) }
        mutable.value = mutable.value.copy(undoToken = null)
    }
    fun saveMeal(id: String, name: String) = submit { repository.saveMeal(id, name) }
    fun reuse(id: String, date: LocalDate) = submit { repository.reuseMeal(id, date, ZoneId.systemDefault()) }
    fun analyzePhoto(jpeg: ByteArray, date: LocalDate) {
        if (mutable.value.photoAnalyzing) return
        val analyzer = photoAnalyzer ?: run {
            mutable.value = mutable.value.copy(photoError = "Photo analysis is not connected yet.")
            return
        }
        if (jpeg.isEmpty()) return
        mutable.value = mutable.value.copy(photoAnalyzing = true, photoError = null)
        val requestId = java.util.UUID.randomUUID().toString()
        scope.launch(Dispatchers.IO) {
            try {
                val result = analyzer.analyze(requestId, jpeg)
                submit {
                    repository.addAnalyzedMeal(result.description, result.estimate, date, ZoneId.systemDefault())
                    mutable.value = mutable.value.copy(photoAnalyzing = false, photoError = null)
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (e: NutritionServiceException) {
                mutable.value = mutable.value.copy(photoAnalyzing = false, photoError = e.code)
            } catch (_: Exception) {
                mutable.value = mutable.value.copy(photoAnalyzing = false, photoError = "unavailable")
            }
        }
    }
    fun clearPhotoError() { mutable.value = mutable.value.copy(photoError = null) }
    fun goals(goals: NutritionGoals) = submit { repository.setGoals(goals) }
}

data class JournalUiState(
    val journal: JournalState = JournalState(),
    val loading: Boolean = true,
    val accountRequired: Boolean = false,
    val error: String? = null,
    val undoToken: String? = null,
    val photoAnalyzing: Boolean = false,
    val photoError: String? = null
)
