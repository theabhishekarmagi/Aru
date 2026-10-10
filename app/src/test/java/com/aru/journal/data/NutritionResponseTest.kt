package com.aru.journal.data

import com.aru.journal.domain.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class NutritionResponseTest {
    private val request = EstimateRequest("entry", "owner", 2, "request", "idli", "2026-10-09", "Asia/Kolkata")
    private fun response(id: String = "request"): String {
        val estimate = NutritionEstimate(listOf(EstimatedItem("Idli", Portion(2.0,"piece",PortionKind.COUNT),Nutrients(120.0),listOf(SourceReference(SourceKind.ARU_DATABASE,"Aru nutrition reference library")))),true,"Review this estimate",123,72)
        return buildJsonObject {
            put("entryId","entry");put("requestId",id);put("revision",2)
            put("estimate",Json.encodeToJsonElement(estimate))
        }.toString()
    }
    @Test fun trustedSourceAndConfidenceRoundTrip() {
        val value=decodeEstimateResponse(response(),request)
        assertNull(value.items[0].sources[0].url)
        assertNull(value.items[0].nutrients.fiberG)
        assertTrue(value.needsReview)
        assertEquals(72, value.confidenceScore)
    }
    @Test fun mismatchedCompletionIsRejected() {
        assertThrows(IllegalArgumentException::class.java) { decodeEstimateResponse(response("old-request"),request) }
    }
}
