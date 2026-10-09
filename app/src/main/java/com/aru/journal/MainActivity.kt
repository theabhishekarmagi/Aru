package com.aru.journal

import android.app.Activity
import android.app.Application
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.compose.runtime.*
import androidx.credentials.exceptions.GetCredentialCancellationException
import com.aru.journal.auth.*
import com.aru.journal.data.AtomicJournalStore
import com.aru.journal.data.SupabaseNutritionEstimator
import com.aru.journal.domain.JournalRepository
import com.aru.journal.ui.*
import io.github.jan.supabase.auth.auth
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val model = ViewModelProvider(this)[JournalViewModel::class.java]
        setContent {
            val controller by model.controller.collectAsState()
            val auth by model.authUi.collectAsState()
            key(controller) { AruApp(controller, auth, { model.signIn(this) }, model::signOut) }
        }
    }
}

class JournalViewModel(application: Application) : AndroidViewModel(application) {
    private val account = SupabaseAccount(application)
    private val store = AtomicJournalStore(application)
    private var journalJob = SupervisorJob(viewModelScope.coroutineContext[Job])
    private var owner: String? = null
    val authUi = MutableStateFlow(AuthUiState(configured = account.configured))
    val controller = MutableStateFlow(createController(null))
    private fun createController(id: String?): JournalController {
        val boundSession = SessionProvider { account.currentSession()?.takeIf { it.accountId == id } }
        return JournalController(JournalRepository(boundSession, store), CoroutineScope(viewModelScope.coroutineContext + journalJob),
            account.client?.let { SupabaseNutritionEstimator(it, boundSession) })
    }
    init {
        account.client?.let { client -> viewModelScope.launch {
            client.auth.sessionStatus.collect {
                val id = account.currentSession()?.accountId
                if(id != owner) {
                    journalJob.cancel()
                    journalJob = SupervisorJob(viewModelScope.coroutineContext[Job])
                    owner = id
                    controller.value = createController(id)
                }
            }
        } }
    }
    fun signIn(activity: Activity) {
        if(authUi.value.busy) return
        viewModelScope.launch {
            authUi.value = authUi.value.copy(busy = true, error = null)
            try { account.signIn(activity) }
            catch (_: GetCredentialCancellationException) { }
            catch (e: CancellationException) { throw e }
            catch (_: Exception) { authUi.value = authUi.value.copy(error = "Google sign-in couldn’t finish. Please try again.") }
            finally { authUi.value = authUi.value.copy(busy = false) }
        }
    }
    fun signOut() {
        viewModelScope.launch {
            try { account.signOut() }
            catch (e: CancellationException) { throw e }
            catch (_: Exception) { authUi.value = authUi.value.copy(error = "Couldn’t sign out. Please try again.") }
        }
    }
}
