package com.aru.journal.sdui

import android.content.Context
import android.util.AtomicFile
import kotlinx.serialization.encodeToString
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.io.InputStream
import java.io.ByteArrayOutputStream

class AndroidRemoteUiCache(context: Context) : RemoteUiCache {
    // This file never contains account information or meal data.
    private val file = AtomicFile(File(context.filesDir, "remote-ui-v1.json"))
    override fun read(): CachedRemoteUi? {
        if (!file.baseFile.exists() && !File(file.baseFile.path + ".bak").exists()) return null
        val bytes = file.openRead().use { readBounded(it, RemoteUiContract.MAX_BYTES * 2 + 4096) }
        require(bytes.size <= RemoteUiContract.MAX_BYTES * 2 + 4096)
        return RemoteUiContract.json.decodeFromString<CachedRemoteUi>(bytes.toString(Charsets.UTF_8))
    }
    override fun write(value: CachedRemoteUi) {
        val stream = file.startWrite()
        try {
            stream.write(RemoteUiContract.json.encodeToString(value).toByteArray(Charsets.UTF_8))
            file.finishWrite(stream)
        } catch (error: Exception) {
            file.failWrite(stream)
            throw error
        }
    }
}

object RemoteUiHttp {
    const val ENDPOINT = "https://arufoodjournal.vercel.app/android-ui.json"
    /** Only this app-owned HTTPS origin is contacted; no credentials are sent. */
    fun fetch(): String {
        val connection = URL(ENDPOINT).openConnection() as HttpURLConnection
        try {
            connection.instanceFollowRedirects = false
            connection.connectTimeout = 4000
            connection.readTimeout = 4000
            connection.useCaches = false
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("Cache-Control", "no-cache")
            require(connection.responseCode == 200)
            require(connection.contentType?.substringBefore(';')?.trim() == "application/json")
            require(connection.contentLengthLong <= RemoteUiContract.MAX_BYTES)
            val bytes = connection.inputStream.use { readBounded(it, RemoteUiContract.MAX_BYTES) }
            require(bytes.size <= RemoteUiContract.MAX_BYTES)
            return bytes.toString(Charsets.UTF_8)
        } finally { connection.disconnect() }
    }
}

internal fun readBounded(input: InputStream, limit: Int): ByteArray {
    val output = ByteArrayOutputStream()
    val buffer = ByteArray(4096)
    while (true) {
        val count = input.read(buffer, 0, minOf(buffer.size, limit + 1 - output.size()))
        if (count == -1) break
        output.write(buffer, 0, count)
        require(output.size() <= limit) { "UI response too large" }
    }
    return output.toByteArray()
}
