@file:OptIn(kotlin.time.ExperimentalTime::class)
package com.aru.journal

import android.content.ContextWrapper
import androidx.activity.compose.setContent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.aru.journal.auth.*
import com.aru.journal.domain.*
import com.aru.journal.ui.*
import io.github.jan.supabase.auth.user.UserSession
import kotlinx.coroutines.*
import org.junit.*
import java.io.File

class SupabaseAuthTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    @Test fun configuredGoogleButtonDoesNotBypassAuthentication() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
        try {
            val controller = JournalController(JournalRepository(UnconfiguredSessionProvider, object : JournalStore {
                override fun read(accountId: String) = error("Signed-out storage access")
                override fun write(accountId: String, state: JournalState) = error("Signed-out storage access")
            }),scope)
            var attempts = 0
            rule.activity.runOnUiThread { rule.activity.setContent { AruApp(controller, AuthUiState(configured=true), { attempts++ }) } }
            rule.waitUntil(5000) { !controller.state.value.loading }
            rule.onNodeWithText("Continue with Google").performClick()
            rule.runOnIdle { Assert.assertEquals(1, attempts) }
            rule.onNodeWithContentDescription("Food entry").assertDoesNotExist()
        } finally { scope.cancel() }
    }
    @Test fun sessionTokensAreEncryptedAndCanBeRestored() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = File(context.cacheDir,"auth-test-${System.nanoTime()}").apply { mkdirs() }
        val isolated = object : ContextWrapper(context) { override fun getNoBackupFilesDir() = directory }
        val session = UserSession("synthetic-access-token", "synthetic-refresh-token", expiresIn=3600, tokenType="bearer")
        val store = SecureSessionManager(isolated)
        store.saveSession(session)
        Assert.assertFalse(File(directory,"supabase-session").readBytes().toString(Charsets.ISO_8859_1).contains("synthetic"))
        Assert.assertEquals(session, SecureSessionManager(isolated).loadSession())
        store.deleteSession()
        Assert.assertNull(store.loadSession())
        directory.deleteRecursively()
        Unit
    }
}
