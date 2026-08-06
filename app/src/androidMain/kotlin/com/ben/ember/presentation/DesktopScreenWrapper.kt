package com.ben.ember.presentation

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import com.ben.ember.domain.model.NoteBlock
import com.ben.ember.presentation.rag.RagViewModel

@Composable
actual fun DesktopMainScreenWrapper(
    isSidebarVisible: Boolean,
    sidebarWidth: Dp,
    onToggleSidebar: () -> Unit,
    onSelectionModeChange: (Boolean) -> Unit,
    onPickImage: ((String) -> Unit) -> Unit,
    onTakePhoto: ((String) -> Unit) -> Unit,
    onPickDocument: ((String) -> Unit) -> Unit,
    onOpenFile: (String, String) -> Unit,
    onExportMarkdown: (String, String) -> Unit,
    onExportPdf: (String, String, List<NoteBlock>) -> Unit,
    onExportBackup: (String) -> Unit,
    onImportBackupClick: () -> Unit,
    onAiIconTap: () -> Unit,
    isRagChatVisible: Boolean,
    ragViewModel: RagViewModel?,
    onDismissRagChat: () -> Unit
) {}