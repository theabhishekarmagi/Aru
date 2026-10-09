package com.aru.journal.auth

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import io.github.jan.supabase.auth.SessionManager
import io.github.jan.supabase.auth.user.UserSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Refresh/access tokens are encrypted with a non-exportable Android Keystore key. */
class SecureSessionManager(context: Context) : SessionManager {
    private val file = AtomicFile(File(context.noBackupFilesDir, "supabase-session"))
    private val alias = "aru.supabase.session.v1"
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        return (store.getKey(alias, null) as? SecretKey) ?: KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
    }
    override suspend fun saveSession(session: UserSession) = withContext(Dispatchers.IO) {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key()) }
        val encrypted = cipher.doFinal(json.encodeToString(session).toByteArray(Charsets.UTF_8))
        val output = file.startWrite()
        try { output.write(cipher.iv.size); output.write(cipher.iv); output.write(encrypted); file.finishWrite(output) }
        catch (e: Exception) { file.failWrite(output); throw e }
    }
    override suspend fun loadSession(): UserSession? = withContext(Dispatchers.IO) {
        if (!file.baseFile.exists() && !File(file.baseFile.path + ".bak").exists()) return@withContext null
        try {
            val bytes = file.readFully()
            val size = bytes[0].toInt() and 255
            require(size == 12 && bytes.size > size + 17)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, bytes.copyOfRange(1, size + 1))) }
            json.decodeFromString<UserSession>(cipher.doFinal(bytes.copyOfRange(size + 1, bytes.size)).toString(Charsets.UTF_8))
        } catch (_: Exception) {
            // A lost/invalidated key requires a fresh real sign-in; journal data remains intact.
            file.delete(); null
        }
    }
    override suspend fun deleteSession() = withContext(Dispatchers.IO) { file.delete() }
}
