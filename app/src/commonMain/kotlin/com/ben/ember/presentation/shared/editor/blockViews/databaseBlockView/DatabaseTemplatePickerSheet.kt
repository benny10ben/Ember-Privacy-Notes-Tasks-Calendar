package com.ben.ember.presentation.shared.editor.blockViews.databaseBlockView

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ben.ember.data.local.room.DatabaseTemplateEntity
import com.ben.ember.presentation.shared.components.EmberBottomSheet
import ember.app.generated.resources.Res
import ember.app.generated.resources.files
import ember.app.generated.resources.hash
import org.jetbrains.compose.resources.painterResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DatabaseTemplatePickerSheet(
    expanded: Boolean,
    templates: List<DatabaseTemplateEntity>,
    onDismiss: () -> Unit,
    onCreateBlank: () -> Unit,
    onSelectTemplate: (DatabaseTemplateEntity) -> Unit
) {
    EmberBottomSheet(expanded = expanded, onDismiss = onDismiss, title = "Add Database") { closeAnd ->
        MuteRippleOnMobile {
            Column(modifier = Modifier.fillMaxWidth().padding(bottom = 22.dp)) {
                SheetMenuRow(icon = painterResource(Res.drawable.hash), text = "Create Blank Database") {
                    closeAnd {
                        onDismiss()
                        onCreateBlank()
                    }
                }

                if (templates.isNotEmpty()) {
                    SheetDivider()
                    SheetSectionLabel("Saved Templates", Modifier.padding(vertical = 6.dp))
                    templates.forEach { template ->
                        SheetMenuRow(icon = painterResource(Res.drawable.files), text = template.name) {
                            closeAnd {
                                onDismiss()
                                onSelectTemplate(template)
                            }
                        }
                    }
                }
            }
        }
    }
}
