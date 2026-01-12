package app.revanced.manager.ui.component.morphe.home

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material3.*
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.morphe.manager.R
import app.revanced.manager.ui.component.morphe.shared.*

/**
 * Morphe-style dialog for adding patch bundles
 */
@Composable
fun MorpheAddBundleDialog(
    onDismiss: () -> Unit,
    onLocalSubmit: () -> Unit,
    onRemoteSubmit: (url: String, autoUpdate: Boolean) -> Unit,
    onLocalPick: () -> Unit,
    selectedLocalPath: String?
) {
    var remoteUrl by rememberSaveable { mutableStateOf("") }
    var autoUpdate by rememberSaveable { mutableStateOf(true) }
    var selectedTab by rememberSaveable { mutableStateOf(0) } // 0 = Remote, 1 = Local

    val isRemoteValid = remoteUrl.isNotBlank() &&
            (remoteUrl.startsWith("http://") || remoteUrl.startsWith("https://"))
    val isLocalValid = selectedLocalPath != null

    MorpheDialog(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.add_patch_bundle),
        footer = {
            MorpheDialogButtonRow(
                primaryText = stringResource(R.string.add),
                onPrimaryClick = {
                    when (selectedTab) {
                        0 -> if (isRemoteValid) onRemoteSubmit(remoteUrl, autoUpdate)
                        1 -> if (isLocalValid) onLocalSubmit()
                    }
                },
                primaryEnabled = if (selectedTab == 0) isRemoteValid else isLocalValid,
                secondaryText = stringResource(android.R.string.cancel),
                onSecondaryClick = onDismiss
            )
        }
    ) {
        val textColor = LocalDialogTextColor.current
        val secondaryColor = LocalDialogSecondaryTextColor.current

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Tabs
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                contentColor = textColor
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = {
                        Text(
                            text = stringResource(R.string.remote),
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = {
                        Text(
                            text = stringResource(R.string.local),
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                )
            }

            // Content based on selected tab
            when (selectedTab) {
                0 -> RemoteTabContent(
                    remoteUrl = remoteUrl,
                    onUrlChange = { remoteUrl = it },
                    autoUpdate = autoUpdate,
                    onAutoUpdateChange = { autoUpdate = it },
                    textColor = textColor,
                    secondaryColor = secondaryColor
                )
                1 -> LocalTabContent(
                    selectedPath = selectedLocalPath,
                    onPickFile = onLocalPick,
                    secondaryColor = secondaryColor
                )
            }
        }
    }
}

@Composable
private fun RemoteTabContent(
    remoteUrl: String,
    onUrlChange: (String) -> Unit,
    autoUpdate: Boolean,
    onAutoUpdateChange: (Boolean) -> Unit,
    textColor: androidx.compose.ui.graphics.Color,
    secondaryColor: androidx.compose.ui.graphics.Color
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // URL input
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = stringResource(R.string.remote_source_url),
                style = MaterialTheme.typography.labelLarge,
                color = textColor
            )

            OutlinedTextField(
                value = remoteUrl,
                onValueChange = onUrlChange,
                modifier = Modifier.fillMaxWidth(),
                placeholder = {
                    Text(
                        text = "https://example.com/patches.json",
                        color = secondaryColor.copy(alpha = 0.5f)
                    )
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = textColor,
                    unfocusedTextColor = textColor,
                    focusedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                    unfocusedBorderColor = secondaryColor.copy(alpha = 0.3f)
                )
            )
        }

        // Description
        Text(
            text = stringResource(R.string.remote_bundle_description),
            style = MaterialTheme.typography.bodySmall,
            color = secondaryColor
        )

        // Auto-update checkbox
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = autoUpdate,
                onCheckedChange = onAutoUpdateChange,
                colors = CheckboxDefaults.colors(
                    checkedColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                    uncheckedColor = secondaryColor.copy(alpha = 0.5f),
                    checkmarkColor = textColor
                )
            )
            Column {
                Text(
                    text = stringResource(R.string.auto_update),
                    style = MaterialTheme.typography.bodyMedium,
                    color = textColor
                )
                Text(
                    text = stringResource(R.string.auto_update_description),
                    style = MaterialTheme.typography.bodySmall,
                    color = secondaryColor
                )
            }
        }
    }
}

@Composable
private fun LocalTabContent(
    selectedPath: String?,
    onPickFile: () -> Unit,
    secondaryColor: androidx.compose.ui.graphics.Color
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // File picker button
        MorpheDialogButton(
            text = if (selectedPath == null) {
                stringResource(R.string.select_patch_bundle_file)
            } else {
                stringResource(R.string.change_file)
            },
            onClick = onPickFile,
            icon = Icons.Outlined.FolderOpen,
            modifier = Modifier.fillMaxWidth()
        )

        // Selected file path
        if (selectedPath != null) {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
            ) {
                Text(
                    text = selectedPath,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = secondaryColor
                )
            }
        }

        // Description
        Text(
            text = stringResource(R.string.local_bundle_description),
            style = MaterialTheme.typography.bodySmall,
            color = secondaryColor
        )
    }
}
