package com.aru.journal

import android.graphics.Bitmap
import androidx.activity.compose.setContent
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.aru.journal.auth.*
import com.aru.journal.domain.*
import com.aru.journal.sdui.*
import com.aru.journal.ui.*
import kotlinx.coroutines.*
import org.junit.*
import java.io.File
import java.time.LocalDate
import java.time.ZoneId

/** Synthetic accounts only; no production authentication or journal records are touched. */
class RemoteUiUiTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    @After fun finish() { scope.cancel() }
    private val cache = object : RemoteUiCache {
        private var value: CachedRemoteUi? = null
        override fun read() = value
        override fun write(value: CachedRemoteUi) { this.value = value }
    }
    private fun controller(signedIn: Boolean = true): JournalController {
        val store = object : JournalStore {
            private var state = JournalState()
            override fun read(accountId: String) = state
            override fun write(accountId: String, state: JournalState) { this.state = state }
        }
        val sessions = if (signedIn) SessionProvider { AccountSession("sdui-ui-test-only", Long.MAX_VALUE) } else UnconfiguredSessionProvider
        val repo = JournalRepository(sessions, store)
        if (signedIn) repo.addDraft("My existing journal entry", LocalDate.now(), ZoneId.systemDefault())
        return JournalController(repo, scope)
    }
    private fun capture(name: String) {
        rule.waitForIdle()
        val inst = InstrumentationRegistry.getInstrumentation()
        val file = File(inst.targetContext.getExternalFilesDir(null), "$name.png")
        file.outputStream().use { inst.uiAutomation.takeScreenshot().compress(Bitmap.CompressFormat.PNG, 100, it) }
        android.os.ParcelFileDescriptor.AutoCloseInputStream(inst.uiAutomation.executeShellCommand("cp ${file.absolutePath} /data/local/tmp/$name.png")).use { it.readBytes() }
    }
    @Test fun serverRefreshChangesCardsAndActionsWithoutReinstallingOrLosingJournal() {
        var response = """{"revision":1,"minVersionCode":10,"cards":[{"id":"first","type":"text","title":"First server layout"}]}"""
        val remote = RemoteUiRepository(10, cache, { response })
        val journal = controller()
        runBlocking(Dispatchers.IO) { remote.refresh() }
        rule.activity.runOnUiThread {
            rule.activity.setContent {
                val document by remote.document.collectAsState()
                AruApp(journal, remoteUi = document)
            }
        }
        rule.waitUntil(10000) { !journal.state.value.loading && runCatching { rule.onAllNodes(isRoot()).fetchSemanticsNodes().isNotEmpty() }.getOrDefault(false) }
        rule.onNodeWithText("First server layout").assertIsDisplayed()
        rule.onNodeWithText("My existing journal entry").assertIsDisplayed()
        response = """{"revision":2,"minVersionCode":10,"accent":"green","features":{"camera":false,"microphone":false,"savedMeals":false},"cards":[{"id":"replacement","type":"actions","title":"Updated from the server","actions":[{"label":"Set daily goals","action":"goals"},{"label":"Should be hidden","action":"saved_meals"}]}]}"""
        runBlocking(Dispatchers.IO) { remote.refresh(force = true) }
        rule.waitForIdle()
        rule.onNodeWithText("First server layout").assertDoesNotExist()
        rule.onNodeWithText("Updated from the server").assertIsDisplayed()
        rule.onNodeWithText("Should be hidden").assertDoesNotExist()
        rule.onNodeWithText("Set daily goals").performClick()
        rule.onNodeWithText("Daily goals").assertIsDisplayed()
        rule.onNodeWithContentDescription("Close").performClick()
        rule.waitUntil(5000) { rule.onAllNodesWithText("Daily goals").fetchSemanticsNodes().isEmpty() }
        rule.waitForIdle()
        rule.onNodeWithContentDescription("Food entry").performClick()
        rule.onNodeWithContentDescription("Photograph meal").assertDoesNotExist()
        rule.onNodeWithContentDescription("Dictate meal").assertDoesNotExist()
        rule.onNodeWithContentDescription("Food entry").performTextInput(" remains editable")
        rule.waitUntil(10000) { journal.state.value.journal.entries.any { it.text.contains("remains editable") } }
        capture("aru-sdui-updated")
        response = """{"revision":3,"minVersionCode":10,"cards":[]}"""
        runBlocking(Dispatchers.IO) { remote.refresh(force = true) }
        rule.waitForIdle()
        rule.onNodeWithContentDescription("Photograph meal").assertIsDisplayed()
        rule.onNodeWithContentDescription("Dictate meal").assertIsDisplayed()
        rule.onNodeWithText("Updated from the server").assertDoesNotExist()
    }
    @Test fun remoteContentCannotBypassAccountGate() {
        val journal = controller(false)
        val config = RemoteUiContract.parse("""{"cards":[{"id":"private","type":"text","title":"Must stay behind sign-in"}]}""", 10)
        rule.activity.runOnUiThread { rule.activity.setContent { AruApp(journal, AuthUiState(configured = true), remoteUi = config) } }
        rule.waitUntil(10000) { !journal.state.value.loading && runCatching { rule.onAllNodes(isRoot()).fetchSemanticsNodes().isNotEmpty() }.getOrDefault(false) }
        rule.onNodeWithText("Welcome to Aru 👋").assertIsDisplayed()
        rule.onNodeWithText("Must stay behind sign-in").assertDoesNotExist()
    }
    @Test fun dailySummaryUsesJournalDataAndMarksPendingNutrition() {
        val journal = controller()
        val config = RemoteUiContract.parse("""{"cards":[{"id":"summary","type":"daily_summary","title":"Selected day summary","tone":"accent"}]}""", 10)
        rule.activity.runOnUiThread { rule.activity.setContent { AruApp(journal, remoteUi = config) } }
        rule.waitUntil(10000) { !journal.state.value.loading && runCatching { rule.onAllNodes(isRoot()).fetchSemanticsNodes().isNotEmpty() }.getOrDefault(false) }
        rule.onNodeWithText("Selected day summary").assertIsDisplayed()
        rule.onNodeWithText("0 cal logged").assertIsDisplayed()
        rule.onNodeWithText("Totals are incomplete; some nutrition is not calculated.").assertIsDisplayed()
    }
    @Test fun livePublicConfigurationRendersInInstalledNativeApp() {
        val raw = runBlocking(Dispatchers.IO) { RemoteUiHttp.fetch() }
        val config = RemoteUiContract.parse(raw, BuildConfig.VERSION_CODE)
        val journal = controller()
        rule.activity.runOnUiThread { rule.activity.setContent { AruApp(journal, remoteUi = config) } }
        rule.waitUntil(10000) { !journal.state.value.loading && runCatching { rule.onAllNodes(isRoot()).fetchSemanticsNodes().isNotEmpty() }.getOrDefault(false) }
        config.cards.firstOrNull()?.let { rule.onNodeWithText(it.title).assertIsDisplayed() }
        rule.onNodeWithContentDescription("Food entry").assertIsDisplayed()
        capture("aru-sdui-live")
    }
}
