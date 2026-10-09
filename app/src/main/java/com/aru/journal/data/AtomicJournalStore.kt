package com.aru.journal.data

import android.content.Context
import android.util.AtomicFile
import com.aru.journal.domain.JournalState
import com.aru.journal.domain.JournalStore
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.security.MessageDigest

/** Private app storage, separate file per account, backups disabled in the manifest. */
class AtomicJournalStore(context: Context) : JournalStore {
    private val directory = File(context.filesDir, "journals")
    private val json = Json { encodeDefaults = true }

    private fun file(owner: String): AtomicFile {
        val key = MessageDigest.getInstance("SHA-256").digest(owner.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
        check(directory.exists() || directory.mkdirs()) { "Cannot create journal storage" }
        return AtomicFile(File(directory, "$key.json"))
    }

    @Synchronized override fun read(accountId: String): JournalState {
        val file = file(accountId)
        if (!file.baseFile.exists() && !File(file.baseFile.path + ".bak").exists()) return JournalState()
        // Corruption/schema failures propagate; never replace real journal data with an empty state.
        return json.decodeFromString<JournalState>(file.readFully().toString(Charsets.UTF_8)).also { state ->
            check(state.entries.all { it.accountId == accountId })
            check(state.deletedEntries.all { it.entry.accountId == accountId })
        }
    }

    @Synchronized override fun write(accountId: String, state: JournalState) {
        require(state.entries.all { it.accountId == accountId })
        require(state.deletedEntries.all { it.entry.accountId == accountId })
        val file = file(accountId)
        val stream = file.startWrite()
        try {
            stream.write(json.encodeToString(state).toByteArray(Charsets.UTF_8))
            file.finishWrite(stream)
        } catch (error: Exception) {
            file.failWrite(stream)
            throw error
        }
    }
}
