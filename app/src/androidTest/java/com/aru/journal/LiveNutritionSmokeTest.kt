package com.aru.journal

import androidx.test.platform.app.InstrumentationRegistry
import com.aru.journal.auth.SupabaseAccount
import com.aru.journal.data.SupabaseNutritionEstimator
import com.aru.journal.domain.*
import io.github.jan.supabase.auth.auth
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.util.UUID

/** Explicit opt-in only: one free-model call using the user's existing real session; no journal writes. */
class LiveNutritionSmokeTest {
    @Test fun realSessionCalculatesMeal() = runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("runLiveNutrition") == "true")
        val account = SupabaseAccount(InstrumentationRegistry.getInstrumentation().targetContext)
        val client = requireNotNull(account.client)
        try {
            client.auth.awaitInitialization()
            val session = requireNotNull(account.currentSession()) { "Sign in to Aru on this emulator before the live smoke test." }
            val estimator = SupabaseNutritionEstimator(client, account)
            for(text in listOf("2 idlis with one katori sambar")) {
                val request=EstimateRequest(UUID.randomUUID().toString(),session.accountId,1,UUID.randomUUID().toString(),text,"2026-10-09","Asia/Kolkata")
                val result=estimator.estimate(request)
                assertTrue(result.items.isNotEmpty())
                assertTrue(result.needsReview)
                assertTrue(result.items.all { it.sources.any { source -> source.kind==SourceKind.ARU_DATABASE } })
                assertTrue(result.confidenceScore in 0..100)
                assertTrue(result.items.any { it.nutrients.caloriesKcal != null })
                assertEquals(result,estimator.estimate(request)) // cached replay must not incur another provider call
            }
        } finally { client.close() }
        Unit
    }
}
