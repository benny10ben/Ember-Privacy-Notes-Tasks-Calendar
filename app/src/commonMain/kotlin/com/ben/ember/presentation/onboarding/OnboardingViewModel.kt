package com.ben.ember.presentation.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ben.ember.data.local.prefs.SettingsManager
import com.ben.ember.data.local.prefs.SyncConstants
import com.ben.ember.data.local.room.NoteMetadataEntity
import com.ben.ember.domain.model.NoteContent
import com.ben.ember.domain.repository.NoteRepository
import com.ben.ember.domain.util.MediaStorageHelper
import ember.app.generated.resources.Res
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID

class OnboardingViewModel(
    private val settingsManager: SettingsManager,
    private val noteRepository: NoteRepository,
    private val mediaStorageHelper: MediaStorageHelper
) : ViewModel() {

    private val _previewNoteId = MutableStateFlow<String?>(null)
    val previewNoteId: StateFlow<String?> = _previewNoteId.asStateFlow()

    private var previewNoteFilePath: String? = null

    val fontStylePreference: StateFlow<String> = settingsManager.fontStylePreferenceFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = SyncConstants.DEFAULT_FONT_STYLE_PREFERENCE
    )

    val fontSizePreference: StateFlow<String> = settingsManager.fontSizePreferenceFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = SyncConstants.DEFAULT_FONT_SIZE_PREFERENCE
    )

    val subNoteOpenMode: StateFlow<String> = settingsManager.subNoteOpenModeFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = SyncConstants.DEFAULT_SUBNOTE_OPEN_MODE
    )

    val showScrollbar: StateFlow<Boolean> = settingsManager.showScrollbarFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = SyncConstants.DEFAULT_SHOW_SCROLLBAR
    )

    val aiFeaturesDisabled: StateFlow<Boolean> = settingsManager.aiFeaturesDisabledFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = SyncConstants.DEFAULT_AI_FEATURES_DISABLED
    )

    fun setFontStylePreference(preference: String) {
        settingsManager.saveFontStylePreference(preference)
    }

    fun setFontSizePreference(preference: String) {
        settingsManager.saveFontSizePreference(preference)
    }

    fun setSubNoteOpenMode(mode: String) {
        settingsManager.saveSubNoteOpenMode(mode)
    }

    fun setShowScrollbar(enabled: Boolean) {
        settingsManager.saveShowScrollbar(enabled)
    }

    fun setAiFeaturesDisabled(disabled: Boolean) {
        settingsManager.saveAiFeaturesDisabled(disabled)
    }

    fun completeOnboarding() {
        settingsManager.saveOnboardingCompleted(true)
    }

    fun ensurePreviewNoteCreated() {
        if (_previewNoteId.value != null) return

        val newNoteId = UUID.randomUUID().toString()
        val fileName = "note_$newNoteId.json"

        viewModelScope.launch {
            val coverImagePath = runCatching {
                val bytes = Res.readBytes("files/onboarding_cover_img.jpg")
                mediaStorageHelper.saveBytesToInternalStorage(bytes, "jpg", "image/jpeg")?.localFileName
            }.getOrNull()

            val metadata = NoteMetadataEntity(
                noteId = newNoteId,
                title = "My First Note",
                icon = "📝",
                folderId = null,
                isDaily = false,
                dateString = null,
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis(),
                filePath = fileName,
                snippet = "",
                coverImagePath = coverImagePath
            )
            noteRepository.saveNote(metadata, NoteContent(blocks = emptyList()))
            previewNoteFilePath = fileName
            _previewNoteId.value = newNoteId
        }
    }

    fun deletePreviewNoteIfExists() {
        val noteId = _previewNoteId.value ?: return
        val filePath = previewNoteFilePath ?: return
        _previewNoteId.value = null
        previewNoteFilePath = null

        viewModelScope.launch {
            noteRepository.deleteNote(noteId, filePath)
        }
    }
}
