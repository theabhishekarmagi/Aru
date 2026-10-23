package com.aru.journal.sdui

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.serialization.Serializable

@Serializable
data class CachedRemoteUi(val raw: String, val fetchedAtMillis: Long)
interface RemoteUiCache {
    fun read(): CachedRemoteUi?
    fun write(value: CachedRemoteUi)
}

/** Call on IO. Networking never blocks account restoration or the journal. */
class RemoteUiRepository(
    private val appVersionCode: Int,
    private val cache: RemoteUiCache,
    private val fetch: suspend () -> String,
    private val now: () -> Long = System::currentTimeMillis
) {
    companion object {
        const val REFRESH_INTERVAL_MS = 5 * 60 * 1000L
        const val MAX_CACHE_AGE_MS = 7 * 24 * 60 * 60 * 1000L
    }
    private val mutableDocument = MutableStateFlow(RemoteUiDocument())
    val document: StateFlow<RemoteUiDocument> = mutableDocument.asStateFlow()
    private val mutex = Mutex()
    private var initialized = false
    private var accepted: CachedRemoteUi? = null
    private var lastAttempt: Long? = null

    private fun fresh(value: CachedRemoteUi): Boolean = now() - value.fetchedAtMillis in 0..MAX_CACHE_AGE_MS

    suspend fun refresh(force: Boolean = false) {
        if (!mutex.tryLock()) return
        try {
            if (!initialized) {
                initialized = true
                try {
                    cache.read()?.takeIf(::fresh)?.let {
                        mutableDocument.value = RemoteUiContract.parse(it.raw, appVersionCode)
                        accepted = it
                    }
                } catch (_: Exception) { /* A damaged UI cache must not affect journal storage. */ }
            }
            if (accepted?.let(::fresh) == false) {
                accepted = null
                mutableDocument.value = RemoteUiDocument()
            }
            val time = now()
            if (!force && lastAttempt?.let { time - it in 0 until REFRESH_INTERVAL_MS } == true) return
            lastAttempt = time
            try {
                val raw = fetch()
                val parsed = RemoteUiContract.parse(raw, appVersionCode)
                val value = CachedRemoteUi(raw, now())
                accepted = value
                mutableDocument.value = parsed
                try { cache.write(value) } catch (_: Exception) { /* Keep usable remote content in memory. */ }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { /* Keep last valid configuration, otherwise built-in defaults. */ }
        } finally { mutex.unlock() }
    }
}
