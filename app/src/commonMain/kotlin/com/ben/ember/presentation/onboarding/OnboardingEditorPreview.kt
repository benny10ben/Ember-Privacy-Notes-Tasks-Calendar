package com.ben.ember.presentation.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.unit.dp
import com.ben.ember.presentation.mobile.home.note.NoteScreen

@Composable
fun OnboardingEditorPreview(viewModel: OnboardingViewModel, modifier: Modifier = Modifier) {
    val noteId by viewModel.previewNoteId.collectAsState()

    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(top = 24.dp, bottom = 24.dp, end = 24.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .shadow(elevation = 16.dp, shape = RoundedCornerShape(20.dp))
                .clip(RoundedCornerShape(20.dp))
                .background(MaterialTheme.colorScheme.surface)
        ) {
            val currentNoteId = noteId
            if (currentNoteId != null) {
                NoteScreen(
                    noteId = currentNoteId,
                    onNavigateBack = {},
                    showBackButton = false
                )
            } else {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center),
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}
