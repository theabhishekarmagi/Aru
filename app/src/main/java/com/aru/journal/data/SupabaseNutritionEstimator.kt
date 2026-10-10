package com.aru.journal.data

import com.aru.journal.auth.SessionProvider
import com.aru.journal.domain.*
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.exceptions.RestException
import io.github.jan.supabase.functions.functions
import io.ktor.client.statement.bodyAsText
import io.ktor.client.request.setBody
import io.ktor.client.plugins.timeout
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.http.*
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*

/** The SDK adds the current user's JWT; owner IDs and provider keys are never sent in the body. */
class SupabaseNutritionEstimator(private val client: SupabaseClient, private val session: SessionProvider) : NutritionEstimator {
    private val json = Json { ignoreUnknownKeys = true }
    override suspend fun estimate(request: EstimateRequest): NutritionEstimate = try { withTimeout(55000) {
        if(session.currentSession()?.accountId != request.accountId) throw NutritionServiceException("account_required")
        if(request.text.length > 2000) throw NutritionServiceException("invalid_request")
        val payload = buildJsonObject {
            put("entryId", request.entryId); put("requestId", request.requestId)
            put("revision", request.revision); put("text", request.text)
        }
        val response = client.functions.invoke("estimate-nutrition") {
            headers.append(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            setBody(payload.toString())
            timeout { requestTimeoutMillis = 50000; socketTimeoutMillis = 50000 }
        }
        val body = response.bodyAsText()
        if(response.status.value !in 200..299) {
            val code = runCatching { json.parseToJsonElement(body).jsonObject["error"]?.jsonPrimitive?.content }.getOrNull()
            throw NutritionServiceException(code ?: "unavailable")
        }
        decodeEstimateResponse(body, request)
    } } catch (_: TimeoutCancellationException) { throw NutritionServiceException("timeout") }
    catch (_: HttpRequestTimeoutException) { throw NutritionServiceException("timeout") }
    catch (e: RestException) {
        val code = runCatching { json.parseToJsonElement(e.error).jsonObject["error"]?.jsonPrimitive?.content }.getOrNull()
        throw NutritionServiceException(code ?: if(e.statusCode == 401) "account_required" else "unavailable")
    }
}

@Serializable private data class EstimateResponse(val entryId:String,val requestId:String,val revision:Long,val estimate:NutritionEstimate)
internal fun decodeEstimateResponse(body:String, request:EstimateRequest):NutritionEstimate {
    val response = Json { ignoreUnknownKeys = true }.decodeFromString<EstimateResponse>(body)
    require(response.entryId == request.entryId && response.requestId == request.requestId && response.revision == request.revision)
    return response.estimate
}
