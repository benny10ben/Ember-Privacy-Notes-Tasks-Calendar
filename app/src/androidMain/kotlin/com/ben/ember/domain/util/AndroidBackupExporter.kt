package com.ben.ember.domain.util

import android.content.Context
import android.net.Uri
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class AndroidBackupExporter(private val context: Context) {

    fun exportToZip(uri: Uri, jsonContent: String, filesDir: File) {
        context.contentResolver.openOutputStream(uri)?.use { outputStream ->
            ZipOutputStream(outputStream).use { zipOut ->

                val jsonEntry = ZipEntry("database.json")
                zipOut.putNextEntry(jsonEntry)
                zipOut.write(jsonContent.toByteArray(Charsets.UTF_8))
                zipOut.closeEntry()

                val mediaDir = File(filesDir, "media")
                if (mediaDir.exists() && mediaDir.isDirectory) {
                    mediaDir.listFiles()?.forEach { file ->
                        if (file.isFile) {
                            val mediaEntry = ZipEntry("media/${file.name}")
                            zipOut.putNextEntry(mediaEntry)
                            file.inputStream().use { input -> input.copyTo(zipOut) }
                            zipOut.closeEntry()
                        }
                    }
                }

                // FALLBACK: Include "voice_" to capture legacy audio recordings still sitting in the root folder from previous versions of the app.
                filesDir.listFiles()?.forEach { file ->
                    if (file.isFile) {
                        val name = file.name
                        if (name.startsWith("media_") || name.startsWith("voice_")) {
                            val mediaEntry = ZipEntry("media/${name}")
                            zipOut.putNextEntry(mediaEntry)
                            file.inputStream().use { input -> input.copyTo(zipOut) }
                            zipOut.closeEntry()
                        }
                    }
                }
            }
        }
    }

    /**
     * Unzips the .ember backup file.
     * Returns the raw JSON string of the database, or null if it fails.
     */
    fun importFromZip(uri: Uri, filesDir: File): String? {
        var jsonContent: String? = null

        try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                java.util.zip.ZipInputStream(inputStream).use { zipIn ->
                    var entry = zipIn.nextEntry

                    while (entry != null) {
                        if (entry.name == "database.json") {
                            jsonContent = zipIn.readBytes().decodeToString()

                        } else if (entry.name.startsWith("media/")) {
                            val mediaFile = File(filesDir, entry.name)
                            mediaFile.parentFile?.mkdirs()

                            mediaFile.outputStream().use { output ->
                                zipIn.copyTo(output)
                            }
                        }
                        zipIn.closeEntry()
                        entry = zipIn.nextEntry
                    }
                }
            }
            return jsonContent
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }
}