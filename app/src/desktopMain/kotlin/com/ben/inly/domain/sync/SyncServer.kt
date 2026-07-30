package com.ben.inly.domain.sync

import com.ben.inly.core.security.SyncEncryptionManager
import com.ben.inly.core.security.SyncHmacSigner
import com.ben.inly.data.local.prefs.SettingsManager
import com.ben.inly.data.local.prefs.SyncConstants
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.routing.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.serialization.kotlinx.json.*
import com.ben.inly.domain.sync.SyncEnvelope
import com.ben.inly.domain.sync.SyncPayload
import com.ben.inly.domain.sync.SyncRepository
import io.ktor.http.ContentType
import io.ktor.utils.io.jvm.javaio.toInputStream
import kotlinx.serialization.json.Json
import java.security.MessageDigest

// Tracks active file uploads so GET requests return HTTP 425 (Too Early)
// when a requested file is currently being uploaded by another device.
private object InFlightUploadsTracker {
    private val uploading = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
    fun markStarted(fileName: String) {
        uploading.add(fileName)
    }
    fun markFinished(fileName: String) {
        uploading.remove(fileName)
    }
    fun isUploading(fileName: String): Boolean = fileName in uploading
}

// Verifies request headers against an HMAC signature to reject expired or tampered requests.
private fun ApplicationCall.hasValidSyncSignature(settingsManager: SettingsManager, hmacSigner: SyncHmacSigner): Boolean {
    val timestampMillis = request.headers[SyncConstants.HEADER_SYNC_TIMESTAMP]?.toLongOrNull() ?: return false
    val signature = request.headers[SyncConstants.HEADER_SYNC_SIGNATURE] ?: return false

    val age = System.currentTimeMillis() - timestampMillis
    if (age > SyncConstants.MAX_REQUEST_AGE_MS || age < -SyncConstants.MAX_REQUEST_AGE_MS) return false

    val expectedSignature = hmacSigner.sign(
        path = request.path(),
        timestampMillis = timestampMillis,
        secretKey = settingsManager.getSyncEncryptionKey()
    )
    // Uses constant-time comparison to prevent timing attacks.
    return MessageDigest.isEqual(expectedSignature.toByteArray(), signature.toByteArray())
}

fun startSyncServer(
    settingsManager: SettingsManager,
    syncRepository: SyncRepository,
    hmacSigner: SyncHmacSigner,
    syncEncryptionManager: SyncEncryptionManager,
    pairingState: SyncPairingState
) {
    val port = settingsManager.getSyncPort().let { if (it <= 0) SyncConstants.DEFAULT_PORT else it }

    embeddedServer(Netty, host = "0.0.0.0", port = port) {
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true; coerceInputValues = true })
        }

        routing {
            get(SyncConstants.ROUTE_FETCH) {
                if (!call.hasValidSyncSignature(settingsManager, hmacSigner)) {
                    call.respond(io.ktor.http.HttpStatusCode.Unauthorized, "Invalid or expired sync signature")
                    return@get
                }

                // Fetches changes since the client's provided timestamp (idempotent snapshot).
                val since = call.request.queryParameters["since"]?.toLongOrNull() ?: 0L
                val changes = syncRepository.collectLocalChanges(since)
                call.respond(SyncPayload(changes))
            }

            post(SyncConstants.ROUTE_PUSH) {
                if (!call.hasValidSyncSignature(settingsManager, hmacSigner)) {
                    call.respond(io.ktor.http.HttpStatusCode.Unauthorized, "Invalid or expired sync signature")
                    return@post
                }

                try {
                    val payload = call.receive<SyncPayload>()
                    // Applies incoming changes per envelope. If any envelope fails or is skipped due to a lock,
                    // returns a non-2xx status so the client knows to retry the push.
                    val appliedCleanly = syncRepository.applyRemoteChanges(payload.changes)
                    if (appliedCleanly) {
                        call.respond(io.ktor.http.HttpStatusCode.OK)
                    } else {
                        call.respond(io.ktor.http.HttpStatusCode.Conflict, "Some changes could not be applied, retry")
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    call.respond(io.ktor.http.HttpStatusCode.BadRequest, e.message ?: "Sync push failed")
                }
            }

            post(SyncConstants.ROUTE_UNPAIR) {
                if (!call.hasValidSyncSignature(settingsManager, hmacSigner)) {
                    call.respond(io.ktor.http.HttpStatusCode.Unauthorized, "Invalid or expired sync signature")
                    return@post
                }

                pairingState.unpairLocally()
                call.respond(io.ktor.http.HttpStatusCode.OK)
            }

            get("/sync/media/{fileName}") {
                if (!call.hasValidSyncSignature(settingsManager, hmacSigner)) {
                    call.respond(io.ktor.http.HttpStatusCode.Unauthorized, "Invalid or expired sync signature")
                    return@get
                }

                val fileName = call.parameters["fileName"]
                if (fileName == null) {
                    call.respond(io.ktor.http.HttpStatusCode.BadRequest)
                    return@get
                }

                val mediaDir = java.io.File(System.getProperty("user.home"), ".inly/media")
                val file = java.io.File(mediaDir, fileName)

                if (!file.exists()) {
                    if (InFlightUploadsTracker.isUploading(fileName)) {
                        call.respond(io.ktor.http.HttpStatusCode(425, "Too Early"))
                    } else {
                        LanSyncLog.e("GET /sync/media: $fileName not found at ${file.absolutePath}, responding 404")
                        call.respond(io.ktor.http.HttpStatusCode.NotFound)
                    }
                    return@get
                }
                val startedAt = System.currentTimeMillis()
                try {
                    call.respondOutputStream(ContentType.Application.OctetStream) {
                        this.use { responseOutput ->
                            file.inputStream().use { plainInput ->
                                syncEncryptionManager.encryptStream(plainInput, responseOutput, settingsManager.getSyncEncryptionKey())
                            }
                        }
                    }
                } catch (e: Exception) {
                    LanSyncLog.e(
                        "GET /sync/media: streaming $fileName failed after ${System.currentTimeMillis() - startedAt}ms with ${e::class.simpleName}: ${e.message}",
                        e
                    )
                    throw e
                }
            }

            post("/sync/media/{fileName}") {
                if (!call.hasValidSyncSignature(settingsManager, hmacSigner)) {
                    call.respond(io.ktor.http.HttpStatusCode.Unauthorized, "Invalid or expired sync signature")
                    return@post
                }

                val fileName = call.parameters["fileName"]
                if (fileName == null) {
                    call.respond(io.ktor.http.HttpStatusCode.BadRequest)
                    return@post
                }

                val mediaDir = java.io.File(System.getProperty("user.home"), ".inly/media").apply { mkdirs() }
                val file = java.io.File(mediaDir, fileName)
                // Saves to a temporary file first and moves it atomically upon completion to avoid incomplete files.
                val tempFile = java.io.File(mediaDir, "$fileName.${java.util.UUID.randomUUID()}.tmp")

                val startedAt = System.currentTimeMillis()
                InFlightUploadsTracker.markStarted(fileName)
                try {
                    call.receiveChannel().toInputStream().use { encryptedInput ->
                        tempFile.outputStream().use { plainOutput ->
                            syncEncryptionManager.decryptStream(encryptedInput, plainOutput, settingsManager.getSyncEncryptionKey())
                        }
                    }
                    java.nio.file.Files.move(
                        tempFile.toPath(), file.toPath(),
                        java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                        java.nio.file.StandardCopyOption.ATOMIC_MOVE
                    )
                    call.respond(io.ktor.http.HttpStatusCode.OK)
                } catch (e: Exception) {
                    LanSyncLog.e(
                        "POST /sync/media: $fileName failed after ${System.currentTimeMillis() - startedAt}ms with ${e::class.simpleName}: ${e.message}",
                        e
                    )
                    tempFile.delete()
                    call.respond(io.ktor.http.HttpStatusCode.BadRequest, e.message ?: "Media upload failed")
                } finally {
                    InFlightUploadsTracker.markFinished(fileName)
                }
            }

            get("/sync/media/list") {
                if (!call.hasValidSyncSignature(settingsManager, hmacSigner)) {
                    call.respond(io.ktor.http.HttpStatusCode.Unauthorized, "Invalid or expired sync signature")
                    return@get
                }

                val mediaDir = java.io.File(System.getProperty("user.home"), ".inly/media")
                val entries = (mediaDir.listFiles() ?: emptyArray())
                    .filter { it.isFile }
                    .map { com.ben.inly.domain.sync.RemoteMediaEntry(fileName = it.name, lastModified = it.lastModified()) }
                call.respond(com.ben.inly.domain.sync.RemoteMediaList(entries))
            }

            delete("/sync/media/{fileName}") {
                if (!call.hasValidSyncSignature(settingsManager, hmacSigner)) {
                    call.respond(io.ktor.http.HttpStatusCode.Unauthorized, "Invalid or expired sync signature")
                    return@delete
                }

                val fileName = call.parameters["fileName"]
                if (fileName == null) {
                    call.respond(io.ktor.http.HttpStatusCode.BadRequest)
                    return@delete
                }

                val mediaDir = java.io.File(System.getProperty("user.home"), ".inly/media")
                val file = java.io.File(mediaDir, fileName)
                if (file.exists()) file.delete()
                call.respond(io.ktor.http.HttpStatusCode.OK)
            }
        }
    }.start(wait = false)
}