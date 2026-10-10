package com.aru.journal.domain

import com.aru.journal.auth.AccountRequiredException
import com.aru.journal.auth.AccountSession
import com.aru.journal.auth.SessionProvider
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class JournalRepositoryTest {
    private class MemoryStore : JournalStore {
        val states = mutableMapOf<String, JournalState>()
        var failWrite = false
        override fun read(accountId: String) = states[accountId] ?: JournalState()
        override fun write(accountId: String, state: JournalState) {
            if (failWrite) throw java.io.IOException("disk full")
            states[accountId] = state
        }
    }
    private var session: AccountSession? = AccountSession("account-a", 1000)
    private val store = MemoryStore()
    private val repo = JournalRepository(SessionProvider { session }, store, now = { 100 })
    private val date = LocalDate.of(2026, 10, 8)
    private val zone = ZoneId.of("Asia/Kolkata")

    // Synthetic fixture solely for state-invariant tests, not app nutrition reference data.
    private fun estimate(kcal: Double = 100.0, review: Boolean = false) = NutritionEstimate(
        listOf(EstimatedItem("test item", Portion(1.0, "piece", PortionKind.COUNT),
            Nutrients(kcal, 2.0, 3.0, 4.0, null),
            listOf(SourceReference(SourceKind.USER, "Unit-test fixture")))),
        review, "Synthetic fixture", 100
    )
    private fun request(): EstimateRequest {
        val entry = repo.addDraft("test meal", date, zone)
        repo.queue(entry.id)
        return repo.beginEstimate(entry.id)
    }

    @Test fun `signed out and expired sessions cannot access journal`() {
        session = null
        assertThrows(AccountRequiredException::class.java) { repo.read() }
        assertThrows(AccountRequiredException::class.java) { repo.addDraft("meal", date, zone) }
        session = AccountSession("account-a", 100)
        assertThrows(AccountRequiredException::class.java) { repo.read() }
    }

    @Test fun `text persists immediately across repository restart`() {
        val entry = repo.addDraft("2 idli", date, zone)
        repo.editText(entry.id, "3 idli")
        val reopened = JournalRepository(SessionProvider { session }, store, now = { 100 })
        assertEquals("3 idli", reopened.read().entries.single().text)
        assertEquals(entry.id, reopened.read().entries.single().id)
    }

    @Test fun `late response after text edit is rejected`() {
        val request = request()
        repo.editText(request.entryId, "changed meal")
        assertFalse(repo.acceptEstimate(request, estimate()))
        assertNull(repo.read().entries.single().estimate)
    }

    @Test fun `retry invalidates old request and duplicate response`() {
        val first = request()
        repo.queue(first.entryId)
        val second = repo.beginEstimate(first.entryId)
        assertFalse(repo.acceptEstimate(first, estimate(200.0)))
        assertTrue(repo.acceptEstimate(second, estimate()))
        assertFalse(repo.acceptEstimate(second, estimate(300.0)))
    }

    @Test fun `manual correction cannot be overwritten by pending AI`() {
        val request = request()
        repo.correct(request.entryId, estimate(80.0))
        assertFalse(repo.acceptEstimate(request, estimate()))
        assertEquals(CalculationStatus.MANUAL, repo.read().entries.single().status)
    }

    @Test fun `deleted and restored entry rejects stale completion`() {
        val request = request()
        val token = repo.delete(request.entryId)
        assertFalse(repo.acceptEstimate(request, estimate()))
        val restored = repo.undoDelete(token)
        assertEquals(request.entryId, restored.id)
        assertTrue(restored.revision > request.revision)
        assertFalse(repo.acceptEstimate(request, estimate()))
        assertEquals(CalculationStatus.QUEUED, restored.status)
    }

    @Test fun `saved meal reuse creates independent entries`() {
        val request = request()
        repo.acceptEstimate(request, estimate())
        val meal = repo.saveMeal(request.entryId, "usual meal")
        val first = repo.reuseMeal(meal.id, date, zone)
        val second = repo.reuseMeal(meal.id, date, zone)
        assertNotEquals(first.id, second.id)
        repo.correct(second.id, estimate(200.0))
        val state = repo.read()
        assertEquals(100.0, state.entries.single { it.id == first.id }.estimate!!.items.single().nutrients.caloriesKcal!!, 0.0)
        assertEquals(100.0, state.savedMeals.single().estimate.items.single().nutrients.caloriesKcal!!, 0.0)
    }

    @Test fun `photo analysis persists only derived meal data`() {
        val added = repo.addAnalyzedMeal("one masala dosa", estimate(420.0, review = true), date, zone)
        assertEquals("one masala dosa", added.text)
        assertEquals(CalculationStatus.NEEDS_REVIEW, added.status)
        assertEquals(420.0, added.estimate!!.items.single().nutrients.caloriesKcal!!, 0.0)
    }

    @Test fun `caller mutation cannot change stored estimates`() {
        val request = request()
        val items = estimate().items.toMutableList()
        repo.acceptEstimate(request, estimate().copy(items = items))
        items.clear()
        assertEquals(1, repo.read().entries.single().estimate!!.items.size)
    }

    @Test fun `account changes isolate reads and reject old result`() {
        val request = request()
        session = AccountSession("account-b", 1000)
        assertTrue(repo.read().entries.isEmpty())
        assertFalse(repo.acceptEstimate(request, estimate()))
        session = AccountSession("account-a", 1000)
        assertEquals(1, repo.read().entries.size)
    }

    @Test fun `interrupted requests recover to queue and reject old response`() {
        val request = request()
        repo.recoverInterruptedCalculations()
        assertEquals(CalculationStatus.QUEUED, repo.read().entries.single().status)
        assertFalse(repo.acceptEstimate(request, estimate()))
    }

    @Test fun `totals disclose missing fiber review and pending entries`() {
        val request = request()
        repo.acceptEstimate(request, estimate(review = true))
        repo.addDraft("unestimated", date, zone)
        repo.addDraft("previous day", date.minusDays(1), zone)
        val totals = dailyTotals(repo.read().entries, date.toString())
        assertEquals(100.0, totals.values[0].knownAmount, 0.0)
        assertEquals(1, totals.values[4].missingItemCount)
        assertEquals(1, totals.pendingEntryCount)
        assertEquals(1, totals.reviewEntryCount)
    }

    @Test fun `failed persistence throws without destroying saved text`() {
        val entry = repo.addDraft("original", date, zone)
        store.failWrite = true
        assertThrows(java.io.IOException::class.java) { repo.editText(entry.id, "unsaved") }
        assertEquals("original", repo.read().entries.single().text)
    }

    @Test fun `invalid portions nutrients and goals rejected`() {
        assertThrows(IllegalArgumentException::class.java) { Portion(0.0, "piece", PortionKind.COUNT) }
        assertThrows(IllegalArgumentException::class.java) { Portion(1.0, "bowl", PortionKind.HOUSEHOLD, assumed = true) }
        assertThrows(IllegalArgumentException::class.java) { Nutrients(caloriesKcal = Double.NaN) }
        assertThrows(IllegalArgumentException::class.java) { NutritionGoals(Nutrients(proteinG = 0.0)) }
        assertThrows(IllegalArgumentException::class.java) {
            SourceReference(SourceKind.OFFICIAL_RESTAURANT, "Missing market and size", "https://example.com")
        }
        assertThrows(IllegalArgumentException::class.java) {
            val assumed = estimate().items.single().copy(portion = Portion(1.0, "bowl", PortionKind.HOUSEHOLD,
                assumed = true, assumption = "Source serving, size unknown"))
            estimate().copy(items = listOf(assumed), needsReview = false)
        }
        repo.setGoals(NutritionGoals(Nutrients(proteinG = 75.0)))
        assertEquals(75.0, repo.read().goals.targets.proteinG!!, 0.0)
    }
}
