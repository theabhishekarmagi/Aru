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
import com.aru.journal.data.SupabaseUserProfileStore
import com.aru.journal.domain.JournalRepository
import com.aru.journal.domain.UserProfile
import com.aru.journal.domain.UserProfileDraft
import com.aru.journal.ui.*
import com.aru.journal.sdui.*
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
            val profile by model.profileUi.collectAsState()
            val remoteUi by model.remoteUi.document.collectAsState()
            key(controller) {
                AruApp(controller, auth, { model.signIn(this) }, model::signOut, profile, model::saveProfile, model::retryProfile, remoteUi)
            }
        }
    }
    override fun onResume() {
        super.onResume()
        ViewModelProvider(this)[JournalViewModel::class.java].refreshRemoteUi()
    }
}

class JournalViewModel(application: Application) : AndroidViewModel(application) {
    val remoteUi = RemoteUiRepository(BuildConfig.VERSION_CODE, AndroidRemoteUiCache(application), { RemoteUiHttp.fetch() })
    fun refreshRemoteUi() { viewModelScope.launch(Dispatchers.IO) { remoteUi.refresh() } }
    private val account = SupabaseAccount(application)
    private val store = AtomicJournalStore(application)
    private var journalJob = SupervisorJob(viewModelScope.coroutineContext[Job])
    private var profileJob: Job? = null
    private var owner: String? = null
    val authUi = MutableStateFlow(AuthUiState(configured = account.configured))
    val profileUi = MutableStateFlow(ProfileUiState(enabled = account.configured, loading = false))
    val controller = MutableStateFlow(createController(null))
    private val profileStore = account.client?.let { SupabaseUserProfileStore(it, account) }
    private fun createController(id: String?): JournalController {
        val boundSession = SessionProvider { account.currentSession()?.takeIf { it.accountId == id } }
        val nutrition = account.client?.let { SupabaseNutritionEstimator(it, boundSession) }
        return JournalController(JournalRepository(boundSession, store), CoroutineScope(viewModelScope.coroutineContext + journalJob),
            nutrition, nutrition)
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
                    if (id == null) {
                        profileJob?.cancel()
                        profileUi.value = ProfileUiState(enabled = account.configured)
                    } else loadProfile(id)
                }
            }
        } }
    }
    private fun loadProfile(id: String) {
        val store = profileStore ?: return
        profileJob?.cancel()
        profileJob = viewModelScope.launch(Dispatchers.IO) {
            profileUi.value = ProfileUiState(enabled = true, loading = true)
            try {
                val profile = store.load()
                if (owner == id) profileUi.value = ProfileUiState(enabled = true, profile = profile)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) {
                if (owner == id) profileUi.value = ProfileUiState(enabled = true, error = "Couldn’t load your profile. Check your connection and try again.")
            }
        }
    }
    fun retryProfile() { owner?.let(::loadProfile) }
    fun saveProfile(draft: UserProfileDraft, profileSetupCompleted: Boolean, onboardingCompleted: Boolean) {
        val id = owner ?: return
        val store = profileStore ?: return
        if (profileUi.value.saving) return
        profileJob = viewModelScope.launch(Dispatchers.IO) {
            val current = profileUi.value.profile
            val next = UserProfile(id, draft.displayName, draft.dateOfBirth, draft.heightCm, draft.weightKg,
                profileSetupCompleted, onboardingCompleted)
            profileUi.value = profileUi.value.copy(saving = true, error = null)
            try {
                store.save(next)
                if (owner == id) profileUi.value = ProfileUiState(enabled = true, profile = next)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) {
                if (owner == id) profileUi.value = ProfileUiState(
                    enabled = true,
                    profile = current ?: next.copy(profileSetupCompleted = false, onboardingCompleted = false),
                    error = "Couldn’t save your profile. Please try again."
                )
            }
        }
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
