@file:OptIn(kotlin.time.ExperimentalTime::class)
package com.aru.journal.auth

import android.app.Activity
import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.CustomCredential
import androidx.credentials.ClearCredentialStateRequest
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.aru.journal.BuildConfig
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.Google
import io.github.jan.supabase.auth.providers.builtin.IDToken
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.functions.Functions
import java.security.MessageDigest
import java.util.UUID

class SupabaseAccount(context: Context) : SessionProvider {
    val configured = BuildConfig.SUPABASE_URL.isNotBlank() && BuildConfig.SUPABASE_PUBLISHABLE_KEY.isNotBlank() && BuildConfig.GOOGLE_WEB_CLIENT_ID.isNotBlank()
    val client = if (configured) createSupabaseClient(BuildConfig.SUPABASE_URL, BuildConfig.SUPABASE_PUBLISHABLE_KEY) {
        install(Auth) { sessionManager = SecureSessionManager(context) }
        install(Postgrest)
        install(Functions)
    } else null
    private val credentials = CredentialManager.create(context)
    override fun currentSession(): AccountSession? {
        val session = client?.auth?.currentSessionOrNull() ?: return null
        val id = session.user?.id ?: return null
        val expiry = session.expiresAt.toEpochMilliseconds()
        if (expiry <= System.currentTimeMillis()) return null
        return AccountSession(id, expiry)
    }
    suspend fun signIn(activity: Activity) {
        val supabase = requireNotNull(client)
        val rawNonce = UUID.randomUUID().toString()
        val hashedNonce = MessageDigest.getInstance("SHA-256").digest(rawNonce.toByteArray()).joinToString("") { "%02x".format(it) }
        val option = GetSignInWithGoogleOption.Builder(BuildConfig.GOOGLE_WEB_CLIENT_ID).setNonce(hashedNonce).build()
        val result = credentials.getCredential(activity, GetCredentialRequest.Builder().addCredentialOption(option).build()).credential
        require(result is CustomCredential && result.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL)
        val google = GoogleIdTokenCredential.createFrom(result.data)
        supabase.auth.signInWith(IDToken) { idToken = google.idToken; provider = Google; nonce = rawNonce }
    }
    suspend fun signOut() {
        client?.auth?.signOut()
        credentials.clearCredentialState(ClearCredentialStateRequest())
    }
}
