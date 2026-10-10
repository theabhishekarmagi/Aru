package com.aru.journal.ui

import com.aru.journal.auth.*
import com.aru.journal.domain.*
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.atomic.AtomicInteger

class AutoNutritionTest {
    private class Fixture(estimator: NutritionEstimator) {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        private var state = JournalState()
        val repo = JournalRepository(SessionProvider { AccountSession("test", Long.MAX_VALUE) }, object : JournalStore {
            override fun read(accountId:String) = state
            override fun write(accountId:String,state:JournalState) { this@Fixture.state=state }
        })
        val entry = repo.addDraft("initial",LocalDate.now(),ZoneId.of("UTC"))
        val controller = JournalController(repo,scope,estimator)
        suspend fun ready() = until { !controller.state.value.loading }
        fun current() = repo.read().entries.single()
    }
    companion object {
        private suspend fun until(predicate:()->Boolean) = withTimeout(7000) { while(!predicate()) delay(10) }
        private fun result() = NutritionEstimate(listOf(EstimatedItem("fixture",Portion(1.0,"piece",PortionKind.COUNT),Nutrients(100.0),listOf(SourceReference(SourceKind.AI_ESTIMATE,"Synthetic fixture")))),true,"Test only",1)
    }
    @Test fun typingBurstPersistsImmediatelyAndMakesOneCallAfterQuiet() = runBlocking {
        val calls=AtomicInteger(); val f=Fixture { calls.incrementAndGet(); result() }
        try {
            f.ready()
            f.controller.edit(f.entry.id,"2")
            until { f.current().text=="2" }
            delay(1000)
            f.controller.edit(f.entry.id,"2 idlis")
            until { f.current().text=="2 idlis" }
            delay(1100)
            assertEquals(0,calls.get())
            until { f.current().status==CalculationStatus.NEEDS_REVIEW }
            assertEquals(1,calls.get())
        } finally { f.scope.cancel() }
    }
    @Test fun newEditCancelsInflightAndBlankNeverCalls() = runBlocking {
        val calls=AtomicInteger();val cancelled=CompletableDeferred<Unit>()
        val f=Fixture { calls.incrementAndGet(); try { awaitCancellation() } finally { cancelled.complete(Unit) } }
        try {
            f.ready();f.controller.edit(f.entry.id,"meal")
            until { calls.get()==1 }
            f.controller.edit(f.entry.id,"")
            until { f.current().text.isEmpty() }
            withTimeout(2000) { cancelled.await() }
            delay(2100)
            assertEquals(1,calls.get());assertEquals(CalculationStatus.DRAFT,f.current().status)
        } finally { f.scope.cancel() }
    }
    @Test fun quotaFailureDoesNotAutoRetryButExplicitRetryWorks() = runBlocking {
        val calls=AtomicInteger();val f=Fixture { calls.incrementAndGet();throw NutritionServiceException("provider_quota") }
        try {
            f.ready();f.controller.edit(f.entry.id,"meal")
            until { f.current().status==CalculationStatus.FAILED }
            delay(2100)
            assertEquals(1,calls.get());assertEquals("provider_quota",f.current().failureCode)
            f.controller.calculate(f.entry.id)
            until { calls.get()==2 && f.current().status==CalculationStatus.FAILED }
            assertEquals(2,calls.get())
        } finally { f.scope.cancel() }
    }
    @Test fun manualCorrectionAndDeletionCancelQueuedCalculation() = runBlocking {
        val calls=AtomicInteger();val f=Fixture { calls.incrementAndGet();result() }
        try {
            f.ready();f.controller.edit(f.entry.id,"meal");f.controller.correct(f.entry.id,result())
            until { f.current().status==CalculationStatus.MANUAL }
            delay(2100);assertEquals(0,calls.get())
            f.controller.edit(f.entry.id,"different meal");f.controller.delete(f.entry.id)
            until { f.repo.read().entries.isEmpty() }
            delay(2100);assertEquals(0,calls.get())
        } finally { f.scope.cancel() }
    }
    @Test fun photoResultCreatesReadyJournalEntryWithoutTextEstimator() = runBlocking {
        val calls=AtomicInteger();val photoCalls=AtomicInteger()
        val f=Fixture { calls.incrementAndGet();result() }
        val controller=JournalController(f.repo,f.scope,{ calls.incrementAndGet();result() }, PhotoNutritionAnalyzer {
            _, jpeg -> photoCalls.incrementAndGet();assertArrayEquals(byteArrayOf(1,2,3),jpeg);PhotoMealAnalysis("one dosa",result())
        })
        try {
            f.ready();controller.analyzePhoto(byteArrayOf(1,2,3),LocalDate.now())
            until { f.repo.read().entries.size==2 }
            assertEquals(0,calls.get());assertEquals(1,photoCalls.get())
            assertEquals("one dosa",f.repo.read().entries.last().text)
        } finally { f.scope.cancel() }
    }
}
