package com.ben.emberr.util

import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

class DesktopBackupExporter {

    /**
     * Packed structure:
     * - database.json
     * - media/
     * - media_123.jpg
     * - media_456.png
     */
    fun exportToZip(destinationFile: File, jsonContent: String, mediaDir: File) {
        ZipOutputStream(FileOutputStream(destinationFile)).use { zipOut ->
            // 1. Write the main database JSON
            val jsonEntry = ZipEntry("database.json")
            zipOut.putNextEntry(jsonEntry)
            zipOut.write(jsonContent.toByteArray(Charsets.UTF_8))
            zipOut.closeEntry()

            // 2. Pack the desktop media directory files into the "media/" zip path
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
        }
    }

    /**
     * Unpacks the archive and returns the database JSON string.
     */
    fun importFromZip(sourceFile: File, mediaDir: File): String? {
        var jsonContent: String? = null
        try {
            sourceFile.inputStream().use { inputStream ->
                ZipInputStream(inputStream).use { zipIn ->
                    var entry = zipIn.nextEntry
                    while (entry != null) {
                        if (entry.name == "database.json") {
                            // Read JSON text directly without closing the outer stream
                            jsonContent = zipIn.readBytes().decodeToString()
                        } else if (entry.name.startsWith("media/")) {
                            // Extract file name from the zip's virtual directory entry
                            val fileName = entry.name.substringAfter("media/")
                            if (fileName.isNotEmpty()) {
                                val mediaFile = File(mediaDir, fileName)
                                mediaFile.parentFile?.mkdirs()
                                mediaFile.outputStream().use { output ->
                                    zipIn.copyTo(output)
                                }
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