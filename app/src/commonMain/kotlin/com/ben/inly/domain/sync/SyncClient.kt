package com.ben.inly.domain.sync

import com.ben.inly.core.security.SyncEncryptionManager
import com.ben.inly.core.security.SyncHmacSigner
import com.ben.inly.data.local.prefs.SettingsManager
import com.ben.inly.data.local.prefs.SyncConstants
import com.ben.inly.domain.sync.SyncEnvelope
import com.ben.inly.domain.sync.SyncPayload
import io.ktor.client.*
import io.ktor.client.call.body
import io.ktor.client.plugins.api.createClientPlugin
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.ContentType
import io.ktor.http.content.OutgoingContent
import io.ktor.http.contentType
import io.ktor.http.encodedPath
import io.ktor.serialization.kotlinx.json.*
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.jvm.javaio.toByteReadChannel
import io.ktor.utils.io.jvm.javaio.toInputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock
import kotlinx.serialization.json.Json
import java.io.File

class SyncClient(
    private val settingsManager: SettingsManager,
    private val hmacSigner: SyncHmacSigner,
    private val syncEncryptionManager: SyncEncryptionManager
) {
    private val client = HttpClient {
        expectSuccess = true
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true; coerceInputValues = true })
        }
        // Signs every outgoing request with HMAC-SHA256 over the path + timestamp instead of a
        // static bearer token, so a sniffed request can't be replayed once its 30s window lapses.
        install(createClientPlugin("HmacAuthPlugin") {
            onRequest { request, _ ->
                val timestampMillis = Clock.System.now().toEpochMilliseconds()
                val signature = hmacSigner.sign(
                    path = request.url.encodedPath,
                    timestampMillis = timestampMillis,
                    secretKey = settingsManager.getSyncEncryptionKey()
                )
                request.headers.append(SyncConstants.HEADER_SYNC_TIMESTAMP, timestampMillis.toString())
                request.headers.append(SyncConstants.HEADER_SYNC_SIGNATURE, signature)
            }
        })
    }

    // Helper to dynamically get the URL
    private val serverUrl: String
        get() {
            val ip = settingsManager.getSyncIpAddress()
            val port = settingsManager.getSyncPort()
            return "http://$ip:$port"
        }

    suspend fun pushChanges(changes: List<SyncEnvelope>) {
        if (changes.isEmpty()) return

        client.post("$serverUrl${SyncConstants.ROUTE_PUSH}") {
            contentType(ContentType.Application.Json)
            setBody(SyncPayload(changes))
        }
    }

    suspend fun fetchChanges(since: Long): List<SyncEnvelope> {
        val response = client.get("$serverUrl${SyncConstants.ROUTE_FETCH}") {
            parameter("since", since)
        }
        val payload: SyncPayload = response.body()
        return payload.changes
    }

    suspend fun requestUnpair(): Boolean {
        return try {
            val response = client.post("$serverUrl${SyncConstants.ROUTE_UNPAIR}")
            response.status.value in 200..299
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    // MEDIA ROUTES - streamed through AES/GCM so a large file is never fully buffered in memory

    suspend fun downloadMedia(fileName: String, destinationFile: File): Boolean {
        return try {
            client.prepareGet("$serverUrl/sync/media/$fileName").execute { response ->
                if (response.status.value !in 200..299) return@execute false
                response.bodyAsChannel().toInputStream().use { encryptedInput ->
                    destinationFile.outputStream().use { plainOutput ->
                        syncEncryptionManager.decryptStream(encryptedInput, plainOutput, settingsManager.getSyncEncryptionKey())
                    }
                }
                true
            }
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    suspend fun listRemoteMedia(): List<com.ben.inly.domain.sync.RemoteMediaEntry> {
        return try {
            client.get("$serverUrl/sync/media/list").body<com.ben.inly.domain.sync.RemoteMediaList>().entries
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    suspend fun deleteRemoteMedia(fileName: String): Boolean {
        return try {
            val response = client.delete("$serverUrl/sync/media/$fileName")
            response.status.value in 200..299
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    suspend fun uploadMedia(fileName: String, file: File): Boolean {
        val tempEncryptedFile = File(file.parentFile, "$fileName.enc.tmp")
        return try {
            withContext(Dispatchers.IO) {
                file.inputStream().use { plainInput ->
                    tempEncryptedFile.outputStream().use { encryptedOutput ->
                        syncEncryptionManager.encryptStream(plainInput, encryptedOutput, settingsManager.getSyncEncryptionKey())
                    }
                }
            }

            val response = client.post("$serverUrl/sync/media/$fileName") {
                contentType(ContentType.Application.OctetStream)
                setBody(object : OutgoingContent.ReadChannelContent() {
                    override val contentType = ContentType.Application.OctetStream
                    override val contentLength = tempEncryptedFile.length()
                    override fun readFrom(): ByteReadChannel = tempEncryptedFile.inputStream().toByteReadChannel()
                })
            }
            response.status.value in 200..299
        } catch (e: Exception) {
            e.printStackTrace()
            false
        } finally {
            tempEncryptedFile.delete()
        }
    }
}