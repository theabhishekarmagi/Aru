package com.aru.journal.ui

import com.aru.journal.auth.AccountRequiredException
import com.aru.journal.domain.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.LocalDate
import java.time.ZoneId

/** One ordered IO queue persists every edit; only estimation is debounced. */
class JournalController(
    private val repository: JournalRepository,
    private val scope: CoroutineScope,
    private val estimator: NutritionEstimator? = null
) {
    private val mutable = MutableStateFlow(JournalUiState())
    val state = mutable.asStateFlow()
    val canEstimate get() = estimator != null
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
        if (text.isNotBlank()) schedule(entry.id, entry.revision)
    }
    fun edit(id: String, text: String) = submit {
        val entry = repository.editText(id, text)
        if (text.isNotBlank()) schedule(id, entry.revision) else estimates.remove(id)?.cancel()
    }
    private fun schedule(id: String, revision: Long) {
        if (estimator == null) return
        estimates.remove(id)?.cancel()
        estimates[id] = scope.launch {
            delay(900)
            submit {
                val current = repository.read().entries.find { it.id == id }
                if (current?.revision == revision && current.status == CalculationStatus.DRAFT) startEstimate(id)
            }
        }
    }
    fun calculate(id: String) = submit {
        estimates.remove(id)?.cancel()
        startEstimate(id)
    }
    private fun startEstimate(id: String) {
        val service = estimator ?: return
        repository.queue(id)
        val request = repository.beginEstimate(id)
        scope.launch(Dispatchers.IO) {
            try {
                val result = service.estimate(request)
                submit { repository.acceptEstimate(request, result) }
            } catch (cancelled: CancellationException) { throw cancelled }
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
    fun goals(goals: NutritionGoals) = submit { repository.setGoals(goals) }
}

data class JournalUiState(
    val journal: JournalState = JournalState(),
    val loading: Boolean = true,
    val accountRequired: Boolean = false,
    val error: String? = null,
    val undoToken: String? = null
)
