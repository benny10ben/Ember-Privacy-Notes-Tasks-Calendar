package com.ben.ember.domain.util

import com.ben.ember.domain.model.NoteBlock
import com.ben.ember.presentation.settings.SettingsViewModel
import com.ben.ember.util.DesktopBackupExporter
import kotlinx.coroutines.runBlocking
import org.koin.core.context.GlobalContext
import java.awt.FileDialog
import java.awt.Frame
import javax.swing.JOptionPane
import javax.swing.SwingUtilities

fun handleExportMarkdown(window: Frame, fileName: String, content: String) {
    try {
        val dialog = FileDialog(window, "Export Markdown", FileDialog.SAVE)
        dialog.file = fileName
        dialog.isVisible = true

        val chosenFileStr = dialog.file ?: return
        val chosenDirStr = dialog.directory

        var saveFile = if (chosenDirStr != null) java.io.File(chosenDirStr, chosenFileStr)
        else java.io.File(chosenFileStr)

        if (saveFile.name.endsWith(".txt", ignoreCase = true)) {
            saveFile = java.io.File(saveFile.absolutePath.removeSuffix(".txt").removeSuffix(".TXT"))
        }
        if (!saveFile.name.endsWith(".md", ignoreCase = true)) {
            saveFile = java.io.File(saveFile.absolutePath + ".md")
        }

        saveFile.writeText(content)

        SwingUtilities.invokeLater {
            JOptionPane.showMessageDialog(window, "Markdown saved successfully:\n${saveFile.name}", "Success", JOptionPane.INFORMATION_MESSAGE)
        }
    } catch (e: Throwable) {
        e.printStackTrace()
        SwingUtilities.invokeLater {
            JOptionPane.showMessageDialog(window, "Error saving Markdown:\n${e.message}", "Error", JOptionPane.ERROR_MESSAGE)
        }
    }
}

fun handleExportPdf(window: Frame, fileName: String, title: String, blocks: List<NoteBlock>) {
    try {
        val dialog = FileDialog(window, "Export PDF", FileDialog.SAVE)
        dialog.file = fileName
        dialog.isVisible = true

        val chosenFileStr = dialog.file ?: return
        val chosenDirStr = dialog.directory

        var saveFile = if (chosenDirStr != null) java.io.File(chosenDirStr, chosenFileStr)
        else java.io.File(chosenFileStr)

        if (saveFile.name.endsWith(".txt", ignoreCase = true)) {
            saveFile = java.io.File(saveFile.absolutePath.removeSuffix(".txt").removeSuffix(".TXT"))
        }
        if (!saveFile.name.endsWith(".pdf", ignoreCase = true)) {
            saveFile = java.io.File(saveFile.absolutePath + ".pdf")
        }

        generateDesktopPdf(saveFile, title, blocks)

        SwingUtilities.invokeLater {
            JOptionPane.showMessageDialog(window, "PDF saved successfully:\n${saveFile.name}", "Success", JOptionPane.INFORMATION_MESSAGE)
        }
    } catch (e: Throwable) {
        e.printStackTrace()
        SwingUtilities.invokeLater {
            JOptionPane.showMessageDialog(window, "Error saving PDF:\n${e.message}", "Error", JOptionPane.ERROR_MESSAGE)
        }
    }
}

fun handleExportBackup(window: Frame, jsonContent: String) {
    try {
        val fileName = "EmberBackup_${System.currentTimeMillis()}.ember"
        val dialog = FileDialog(window, "Export Ember Backup", FileDialog.SAVE)
        dialog.file = fileName
        dialog.isVisible = true

        val chosenFileStr = dialog.file ?: return
        val chosenDirStr = dialog.directory
        val saveFile = if (chosenDirStr != null) java.io.File(chosenDirStr, chosenFileStr)
        else java.io.File(chosenFileStr)

        val mediaDir = java.io.File(System.getProperty("user.home"), ".ember/media")
        DesktopBackupExporter().exportToZip(saveFile, jsonContent, mediaDir)

        SwingUtilities.invokeLater {
            JOptionPane.showMessageDialog(window, "Backup saved successfully!", "Success", JOptionPane.INFORMATION_MESSAGE)
        }
    } catch (e: Throwable) {
        e.printStackTrace()
        SwingUtilities.invokeLater {
            JOptionPane.showMessageDialog(window, "Export failed:\n${e.message}", "Error", JOptionPane.ERROR_MESSAGE)
        }
    }
}

fun handleImportBackup(window: Frame) {
    try {
        val dialog = FileDialog(window, "Import Ember Backup", FileDialog.LOAD)
        dialog.file = "*.ember"
        dialog.isVisible = true

        val chosenFileStr = dialog.file ?: return
        val chosenDirStr = dialog.directory
        val sourceFile = if (chosenDirStr != null) java.io.File(chosenDirStr, chosenFileStr)
        else java.io.File(chosenFileStr)

        val mediaDir = java.io.File(System.getProperty("user.home"), ".ember/media")
        val jsonString = DesktopBackupExporter().importFromZip(sourceFile, mediaDir)

        if (jsonString != null) {
            val settingsViewModel = GlobalContext.get().get<SettingsViewModel>()
            runBlocking { settingsViewModel.mergeBackupJson(jsonString) }

            SwingUtilities.invokeLater {
                JOptionPane.showMessageDialog(window, "Backup restored successfully!", "Success", JOptionPane.INFORMATION_MESSAGE)
            }
        } else {
            SwingUtilities.invokeLater {
                JOptionPane.showMessageDialog(window, "Invalid or corrupted backup file.", "Error", JOptionPane.ERROR_MESSAGE)
            }
        }
    } catch (e: Throwable) {
        e.printStackTrace()
        SwingUtilities.invokeLater {
            JOptionPane.showMessageDialog(window, "Import failed:\n${e.message}", "Error", JOptionPane.ERROR_MESSAGE)
        }
    }
}