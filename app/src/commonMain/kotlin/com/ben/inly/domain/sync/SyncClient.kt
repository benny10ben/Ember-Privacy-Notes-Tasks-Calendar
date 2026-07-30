package com.ben.inly.domain.sync

import com.ben.inly.core.security.SyncEncryptionManager
import com.ben.inly.core.security.SyncHmacSigner
import com.ben.inly.data.local.prefs.SettingsManager
import com.ben.inly.data.local.prefs.SyncConstants
import com.ben.inly.domain.sync.SyncEnvelope
import com.ben.inly.domain.sync.SyncPayload
import io.ktor.client.*
import io.ktor.client.call.body
import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.plugins.HttpTimeout
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
import java.nio.file.Files
import java.nio.file.StandardCopyOption

// PEER_NOT_READY indicates a 425 Too Early response (another device is currently uploading the file).
// FAILED indicates a standard transfer failure (404, timeout, or connection error).
enum class MediaTransferOutcome { SUCCESS, PEER_NOT_READY, FAILED }

class SyncClient(
    private val settingsManager: SettingsManager,
    private val hmacSigner: SyncHmacSigner,
    private val syncEncryptionManager: SyncEncryptionManager
) {
    private companion object {
        const val CONNECT_TIMEOUT_MS = 15_000L
        // Extended timeouts to accommodate large file uploads/downloads over slow Wi-Fi.
        const val REQUEST_TIMEOUT_MS = 10 * 60_000L
        const val SOCKET_TIMEOUT_MS = 10 * 60_000L
    }

    private val client = HttpClient {
        expectSuccess = true
        install(HttpTimeout) {
            connectTimeoutMillis = CONNECT_TIMEOUT_MS
            requestTimeoutMillis = REQUEST_TIMEOUT_MS
            socketTimeoutMillis = SOCKET_TIMEOUT_MS
        }
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true; coerceInputValues = true })
        }
        // Signs outgoing requests using HMAC-SHA256 based on the request path and current timestamp.
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

    // Closes the underlying HTTP client and releases its connection pool and thread resources.
    fun close() {
        client.close()
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

    suspend fun downloadMedia(fileName: String, destinationFile: File): MediaTransferOutcome {
        // Unique temporary file path to prevent concurrent sync operations from overwriting each other.
        val tempFile = File(destinationFile.parentFile, "${destinationFile.name}.${java.util.UUID.randomUUID()}.tmp")
        val url = "$serverUrl/sync/media/$fileName"
        val startedAt = Clock.System.now().toEpochMilliseconds()
        return try {
            val downloaded = client.prepareGet(url).execute { response ->
                if (response.status.value !in 200..299) {
                    LanSyncLog.e("downloadMedia: $fileName request failed with status ${response.status.value}")
                    return@execute false
                }
                response.bodyAsChannel().toInputStream().use { encryptedInput ->
                    tempFile.outputStream().use { plainOutput ->
                        syncEncryptionManager.decryptStream(encryptedInput, plainOutput, settingsManager.getSyncEncryptionKey())
                    }
                }
                true
            }
            // Atomically moves the temporary file to its final destination after a successful download.
            if (downloaded) {
                Files.move(tempFile.toPath(), destinationFile.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
                MediaTransferOutcome.SUCCESS
            } else {
                MediaTransferOutcome.FAILED
            }
        } catch (e: ClientRequestException) {
            val elapsedMs = Clock.System.now().toEpochMilliseconds() - startedAt
            if (e.response.status.value == 425) {
                MediaTransferOutcome.PEER_NOT_READY
            } else {
                LanSyncLog.e("downloadMedia: $fileName failed after ${elapsedMs}ms with ${e::class.simpleName}: ${e.message}", e)
                MediaTransferOutcome.FAILED
            }
        } catch (e: Exception) {
            val elapsedMs = Clock.System.now().toEpochMilliseconds() - startedAt
            LanSyncLog.e("downloadMedia: $fileName failed after ${elapsedMs}ms with ${e::class.simpleName}: ${e.message}", e)
            MediaTransferOutcome.FAILED
        } finally {
            tempFile.delete()
        }
    }

    suspend fun listRemoteMedia(): List<com.ben.inly.domain.sync.RemoteMediaEntry> {
        return try {
            client.get("$serverUrl/sync/media/list").body<com.ben.inly.domain.sync.RemoteMediaList>().entries
        } catch (e: Exception) {
            LanSyncLog.e("listRemoteMedia: failed with ${e::class.simpleName}: ${e.message}", e)
            emptyList()
        }
    }

    suspend fun deleteRemoteMedia(fileName: String): Boolean {
        return try {
            val response = client.delete("$serverUrl/sync/media/$fileName")
            response.status.value in 200..299
        } catch (e: Exception) {
            LanSyncLog.e("deleteRemoteMedia: $fileName failed with ${e::class.simpleName}: ${e.message}", e)
            false
        }
    }

    suspend fun uploadMedia(fileName: String, file: File): MediaTransferOutcome {
        // Unique temporary file path to isolate concurrent uploads.
        val tempEncryptedFile = File(file.parentFile, "$fileName.${java.util.UUID.randomUUID()}.enc.tmp")
        val startedAt = Clock.System.now().toEpochMilliseconds()
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
            val succeeded = response.status.value in 200..299
            if (!succeeded) {
                val elapsedMs = Clock.System.now().toEpochMilliseconds() - startedAt
                LanSyncLog.e("uploadMedia: $fileName rejected with status ${response.status.value} after ${elapsedMs}ms")
            }
            if (succeeded) MediaTransferOutcome.SUCCESS else MediaTransferOutcome.FAILED
        } catch (e: Exception) {
            val elapsedMs = Clock.System.now().toEpochMilliseconds() - startedAt
            LanSyncLog.e("uploadMedia: $fileName failed after ${elapsedMs}ms with ${e::class.simpleName}: ${e.message}", e)
            MediaTransferOutcome.FAILED
        } finally {
            tempEncryptedFile.delete()
        }
    }
}