package com.aru.journal.sdui

import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException

class RemoteUiTest {
    private fun document(revision: Int = 1, cards: String = "[]", features: String = "{}", schema: Int = 1, minVersion: Int = 10) =
        """{"schemaVersion":$schema,"revision":$revision,"minVersionCode":$minVersion,"cards":$cards,"features":$features}"""
    private val card = """{"id":"welcome","type":"text","title":"Welcome","tone":"accent","actions":[{"label":"Goals","action":"goals"}]}"""
    private class MemoryCache(var value: CachedRemoteUi? = null) : RemoteUiCache {
        override fun read() = value
        override fun write(value: CachedRemoteUi) { this.value = value }
    }
    private fun rejects(raw: String) {
        try { RemoteUiContract.parse(raw, 10); fail("Should reject invalid configuration") }
        catch (_: IllegalArgumentException) { }
    }
    @Test fun publishedConfigurationMatchesThisApkAndPreservesCardOrder() {
        val file = listOf(File("../web/public/android-ui.json"), File("web/public/android-ui.json")).first { it.exists() }
        val parsed = RemoteUiContract.parse(file.readText(), 10)
        assertEquals(listOf("welcome", "shortcuts"), parsed.cards.map { it.id })
        assertTrue(parsed.cards.last().actions.any { it.action == "goals" })
    }
    @Test fun unknownComponentsAndPrivilegedActionsAreSkipped() {
        val raw = document(cards = """[{"id":"future","type":"executable","title":"Ignore"},$card,{"id":"safe","type":"actions","title":"Actions","actions":[{"label":"Not allowed","action":"delete_account"},{"label":"Not allowed","action":"https://evil.example"},{"label":"Add a meal","action":"add_meal"}]}]""")
        val parsed = RemoteUiContract.parse(raw, 10)
        assertEquals(listOf("welcome", "safe"), parsed.cards.map { it.id })
        assertEquals(listOf("add_meal"), parsed.cards.last().actions.map { it.action })
    }
    @Test fun flagsFilterSavedShortcutAndUnknownFieldsAllowForwardCompatibility() {
        val raw = document(cards = """[{"id":"saved","type":"actions","title":"Shortcuts","actions":[{"label":"Saved","action":"saved_meals"}]}]""", features = """{"savedMeals":false,"future":true}""")
        assertFalse(RemoteUiContract.parse(raw, 10).features.savedMeals)
        assertTrue(RemoteUiContract.parse(raw, 10).cards.single().actions.isEmpty())
    }
    @Test fun schemaVersionClientVersionDuplicateIdsAndPayloadLimitsAreEnforced() {
        rejects(document(schema = 2))
        rejects(document(minVersion = 11))
        rejects(document(cards = "[$card,$card]"))
        rejects(document(cards = """[{"id":"bad","type":"text","title":"${"x".repeat(101)}"}]"""))
        rejects(document(cards = """[{"id":"bad","type":"text","title":"Fine","body":"${"x".repeat(501)}"}]"""))
        rejects(document(cards = """[{"id":"bad","type":"text","title":"Fine","tone":"javascript"}]"""))
        rejects(document().dropLast(1) + ",\"padding\":\"${"界".repeat(23000)}\"}")
        assertEquals(RemoteUiDocument(), RemoteUiContract.parse("{}", 10))
    }
    @Test fun boundedReaderRejectsOversizedAndAcceptsExactLimit() {
        assertEquals(4, readBounded(ByteArrayInputStream(ByteArray(4)), 4).size)
        try { readBounded(ByteArrayInputStream(ByteArray(5)), 4); fail("Must bound download") }
        catch (_: IllegalArgumentException) { }
    }
    @Test fun cachedSettingsWorkOfflineAndExpireBackToDefaults() = runBlocking {
        var time = 1_000_000L
        val cache = MemoryCache(CachedRemoteUi(document(3, "[$card]"), time))
        val repo = RemoteUiRepository(10, cache, { throw IOException("offline") }, { time })
        repo.refresh()
        assertEquals(3, repo.document.value.revision)
        time += RemoteUiRepository.MAX_CACHE_AGE_MS + 1
        repo.refresh()
        assertEquals(RemoteUiDocument(), repo.document.value)
    }
    @Test fun invalidRemoteSettingsDoNotReplaceValidCacheAndGoodUpdateReplacesIt() = runBlocking {
        var response = document(schema = 99)
        val cache = MemoryCache(CachedRemoteUi(document(4), 1_000_000L))
        val repo = RemoteUiRepository(10, cache, { response }, { 1_000_000L })
        repo.refresh()
        assertEquals(4, repo.document.value.revision)
        response = document(5, "[$card]")
        repo.refresh(force = true)
        assertEquals(5, repo.document.value.revision)
        assertEquals(response, cache.value!!.raw)
    }
    @Test fun corruptOrFutureDatedCacheNeverBlocksDefaultJournal() = runBlocking {
        for (cached in listOf(CachedRemoteUi("not json", 100), CachedRemoteUi(document(6), 101))) {
            val repo = RemoteUiRepository(10, MemoryCache(cached), { throw IOException() }, { 100 })
            repo.refresh()
            assertEquals(RemoteUiDocument(), repo.document.value)
        }
    }
    @Test fun foregroundRefreshIsThrottledAndConcurrentRequestsAreCoalesced() = runBlocking {
        var time = 1_000_000L
        var requests = 0
        val repo = RemoteUiRepository(10, MemoryCache(), { requests++; document(requests) }, { time })
        repo.refresh(); repo.refresh()
        assertEquals(1, requests)
        time += RemoteUiRepository.REFRESH_INTERVAL_MS
        repo.refresh()
        assertEquals(2, requests)
        val started = CompletableDeferred<Unit>()
        val finish = CompletableDeferred<Unit>()
        val concurrent = RemoteUiRepository(10, MemoryCache(), { started.complete(Unit); finish.await(); document(9) })
        val first = launch { concurrent.refresh() }
        started.await()
        concurrent.refresh(force = true)
        finish.complete(Unit); first.join()
        assertEquals(9, concurrent.document.value.revision)
    }
    @Test fun cancellationPropagatesAndDiskFailureDoesNotDiscardServerDocument() = runBlocking {
        val cancelled = RemoteUiRepository(10, MemoryCache(), { throw CancellationException("cancelled") })
        try { cancelled.refresh(); fail("Must propagate coroutine cancellation") }
        catch (_: CancellationException) { }
        val repo = RemoteUiRepository(10, object : RemoteUiCache {
            override fun read(): CachedRemoteUi? = throw IOException()
            override fun write(value: CachedRemoteUi) { throw IOException() }
        }, { document(8) })
        repo.refresh()
        assertEquals(8, repo.document.value.revision)
    }
}
