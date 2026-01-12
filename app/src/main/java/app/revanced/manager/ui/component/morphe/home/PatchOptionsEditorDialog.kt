package app.revanced.manager.ui.component.morphe.home

import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.morphe.manager.R
import app.revanced.manager.domain.bundles.PatchBundleSource
import app.revanced.manager.domain.manager.PatchOptionsPreferencesManager
import app.revanced.manager.patcher.patch.Option
import app.revanced.manager.ui.component.morphe.settings.*
import app.revanced.manager.ui.component.morphe.shared.*
import app.revanced.manager.ui.component.morphe.utils.rememberFolderPickerWithPermission
import app.revanced.manager.ui.viewmodel.DashboardViewModel
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel

/**
 * Patch options editor dialog
 * Shows tabs for each compatible package if multiple packages exist
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PatchOptionsEditorDialog(
    bundle: PatchBundleSource,
    patchWithPackages: PatchWithPackages,
    onDismiss: () -> Unit,
    dashboardViewModel: DashboardViewModel = koinViewModel()
) {
    val scope = rememberCoroutineScope()
    val patch = patchWithPackages.patch
    val compatiblePackages = patchWithPackages.compatiblePackages

    // Selected package tab
    var selectedPackageIndex by remember { mutableIntStateOf(0) }
    val currentPackage = compatiblePackages.getOrNull(selectedPackageIndex) ?: compatiblePackages.firstOrNull() ?: ""

    // Load saved options for current package
    val savedOptions by dashboardViewModel.getSavedOptionsForBundle(
        bundleUid = bundle.uid,
        packageName = currentPackage
    ).collectAsStateWithLifecycle(initialValue = emptyMap())

    val patchOptions = patch.options ?: emptyList()
    val currentPatchOptions = savedOptions[patch.name] ?: emptyMap()

    // Local state for editing
    val optionValues = remember(currentPatchOptions, patchOptions, currentPackage) {
        mutableStateMapOf<String, Any?>().apply {
            patchOptions.forEach { option ->
                val savedValue = currentPatchOptions[option.key]
                val defaultValue = option.default
                this[option.key] = savedValue ?: defaultValue
            }
        }
    }

    // Update option values when package changes
    LaunchedEffect(currentPackage, savedOptions) {
        val packageOptions = savedOptions[patch.name] ?: emptyMap()
        patchOptions.forEach { option ->
            val savedValue = packageOptions[option.key]
            val defaultValue = option.default
            optionValues[option.key] = savedValue ?: defaultValue
        }
    }

    // Get package display name
    @Composable
    fun getPackageDisplayName(packageName: String): String {
        return when (packageName) {
            PatchOptionsPreferencesManager.PACKAGE_YOUTUBE -> stringResource(R.string.morphe_home_youtube)
            PatchOptionsPreferencesManager.PACKAGE_YOUTUBE_MUSIC -> stringResource(R.string.morphe_home_youtube_music)
            else -> packageName.substringAfterLast('.').replaceFirstChar { it.uppercase() }
        }
    }

    MorpheDialog(
        onDismissRequest = onDismiss,
        title = patch.name,
        footer = {
            MorpheDialogButtonRow(
                primaryText = stringResource(R.string.save),
                onPrimaryClick = {
                    scope.launch {
                        dashboardViewModel.savePatchOptionsForBundle(
                            bundleUid = bundle.uid,
                            patchName = patch.name,
                            options = optionValues.toMap(),
                            packageName = currentPackage
                        )
                        onDismiss()
                    }
                },
                secondaryText = stringResource(R.string.reset),
                onSecondaryClick = {
                    scope.launch {
                        dashboardViewModel.resetPatchOptionsForBundle(
                            bundleUid = bundle.uid,
                            patchName = patch.name,
                            packageName = currentPackage
                        )
                        // Reset local state to defaults
                        optionValues.clear()
                        patchOptions.forEach { option ->
                            optionValues[option.key] = option.default
                        }
                    }
                }
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 600.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Package tabs (only show if multiple packages)
            if (compatiblePackages.size > 1) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        compatiblePackages.forEachIndexed { index, packageName ->
                            val isSelected = selectedPackageIndex == index

                            Surface(
                                onClick = { selectedPackageIndex = index },
                                shape = RoundedCornerShape(6.dp),
                                color = if (isSelected)
                                    MaterialTheme.colorScheme.primary
                                else
                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.0f),
                                modifier = Modifier.weight(1f)
                            ) {
                                Box(
                                    modifier = Modifier.padding(8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = getPackageDisplayName(packageName),
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isSelected)
                                            MaterialTheme.colorScheme.onPrimary
                                        else
                                            LocalDialogTextColor.current
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Single package indicator
            if (compatiblePackages.size == 1) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = stringResource(R.string.morphe_configuring_for_package, getPackageDisplayName(currentPackage)),
                            style = MaterialTheme.typography.bodySmall,
                            color = LocalDialogTextColor.current
                        )
                    }
                }
            }

            // Description
            if (patch.description?.isNotBlank() == true) {
                Text(
                    text = patch.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = LocalDialogSecondaryTextColor.current
                )
            }

            // Options
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                if (patchOptions.isEmpty()) {
                    Text(
                        text = stringResource(R.string.morphe_patch_options_no_available),
                        style = MaterialTheme.typography.bodyMedium,
                        color = LocalDialogSecondaryTextColor.current.copy(alpha = 0.7f),
                        fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                    )
                } else {
                    // Filter options based on package compatibility
                    val filteredOptions = remember(patchOptions, currentPackage) {
                        patchOptions.filter { option ->
                            // Check if option key suggests package-specific filtering
                            val isLightThemeOption = option.key.lowercase().contains("light") &&
                                    option.key.lowercase().contains("theme")
                            val isYouTubeMusic = currentPackage == PatchOptionsPreferencesManager.PACKAGE_YOUTUBE_MUSIC

                            // Filter out light theme option for YouTube Music
                            if (isLightThemeOption && isYouTubeMusic) {
                                false
                            } else {
                                true
                            }
                        }
                    }

                    if (filteredOptions.isEmpty()) {
                        Text(
                            text = stringResource(R.string.morphe_patch_options_no_available),
                            style = MaterialTheme.typography.bodyMedium,
                            color = LocalDialogSecondaryTextColor.current.copy(alpha = 0.7f),
                            fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                        )
                    } else {
                        filteredOptions.forEach { option ->
                            PatchOptionField(
                                option = option,
                                currentValue = optionValues[option.key],
                                onValueChange = { newValue ->
                                    optionValues[option.key] = newValue
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Renders appropriate UI field based on option type
 */
@Composable
private fun PatchOptionField(
    option: Option<*>,
    currentValue: Any?,
    onValueChange: (Any?) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Label
        Text(
            text = option.title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = LocalDialogTextColor.current
        )

        // Description with collapsible support for long text
        if (option.description.isNotBlank()) {
            var isDescriptionExpanded by remember { mutableStateOf(false) }
            val shouldTruncate = option.description.length > 200

            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                modifier = Modifier
                    .fillMaxWidth()
                    .then(
                        if (shouldTruncate) Modifier.clickable { isDescriptionExpanded = !isDescriptionExpanded }
                        else Modifier
                    )
            ) {
                Box(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = option.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = LocalDialogSecondaryTextColor.current,
                            maxLines = if (shouldTruncate && !isDescriptionExpanded) 4 else Int.MAX_VALUE
                        )
                    }

                    // Gradient fade at bottom when collapsed
                    if (shouldTruncate && !isDescriptionExpanded) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .fillMaxWidth()
                                .height(40.dp)
                                .background(
                                    brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                                        colors = listOf(
                                            androidx.compose.ui.graphics.Color.Transparent,
                                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                                        )
                                    )
                                )
                        )
                    }

                    // Expand/Collapse indicator
                    if (shouldTruncate) {
                        Row(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(8.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (isDescriptionExpanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        }

        // Required indicator
        if (option.required) {
            Surface(
                shape = RoundedCornerShape(4.dp),
                color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Warning,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(12.dp)
                    )
                    Text(
                        text = stringResource(R.string.morphe_required),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }

        // Input field based on type
        when {
            // Boolean
            option.type.toString().contains("Boolean", ignoreCase = true) -> {
                BooleanOptionField(
                    option = option,
                    currentValue = currentValue as? Boolean ?: false,
                    onValueChange = { onValueChange(it) }
                )
            }
            // String with presets (dropdown)
            option.type.toString().contains("String", ignoreCase = true) && !option.presets.isNullOrEmpty() -> {
                SelectOptionField(
                    option = option,
                    currentValue = currentValue?.toString() ?: "",
                    onValueChange = { onValueChange(it) }
                )
            }
            // String with color detection
            option.type.toString().contains("String", ignoreCase = true) && isColorOption(option) -> {
                ColorOptionField(
                    option = option,
                    currentValue = currentValue?.toString() ?: "",
                    onValueChange = { onValueChange(it) }
                )
            }
            // String with path detection
            option.type.toString().contains("String", ignoreCase = true) && isPathOption(option) -> {
                PathOptionField(
                    option = option,
                    currentValue = currentValue?.toString() ?: "",
                    onValueChange = { onValueChange(it) }
                )
            }
            // Regular String
            option.type.toString().contains("String", ignoreCase = true) -> {
                StringOptionField(
                    option = option,
                    currentValue = currentValue?.toString() ?: "",
                    onValueChange = { onValueChange(it) }
                )
            }
            // Integer
            option.type.toString().contains("Int", ignoreCase = true) -> {
                IntegerOptionField(
                    option = option,
                    currentValue = when (currentValue) {
                        is Int -> currentValue
                        is Long -> currentValue.toInt()
                        is String -> currentValue.toIntOrNull() ?: 0
                        else -> 0
                    },
                    onValueChange = { onValueChange(it) }
                )
            }
            // Long
            option.type.toString().contains("Long", ignoreCase = true) -> {
                LongOptionField(
                    option = option,
                    currentValue = when (currentValue) {
                        is Long -> currentValue
                        is Int -> currentValue.toLong()
                        is String -> currentValue.toLongOrNull() ?: 0L
                        else -> 0L
                    },
                    onValueChange = { onValueChange(it) }
                )
            }
            // Float
            option.type.toString().contains("Float", ignoreCase = true) -> {
                FloatOptionField(
                    option = option,
                    currentValue = when (currentValue) {
                        is Float -> currentValue
                        is Double -> currentValue.toFloat()
                        is String -> currentValue.toFloatOrNull() ?: 0f
                        else -> 0f
                    },
                    onValueChange = { onValueChange(it) }
                )
            }
            // Fallback - treat as string
            else -> {
                StringOptionField(
                    option = option,
                    currentValue = currentValue?.toString() ?: "",
                    onValueChange = { onValueChange(it) }
                )
            }
        }
    }
}

@Composable
private fun BooleanOptionField(
    option: Option<*>,
    currentValue: Boolean,
    onValueChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable { onValueChange(!currentValue) }
            .padding(12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = if (currentValue) stringResource(R.string.morphe_enabled) else stringResource(R.string.morphe_disabled),
            style = MaterialTheme.typography.bodyMedium,
            color = LocalDialogTextColor.current
        )
        Switch(
            checked = currentValue,
            onCheckedChange = null
        )
    }
}

@Composable
private fun StringOptionField(
    option: Option<*>,
    currentValue: String,
    onValueChange: (String) -> Unit
) {
    MorpheDialogTextField(
        value = currentValue,
        onValueChange = onValueChange,
        placeholder = {
            Text(
                option.default?.toString() ?: "String",
                color = LocalDialogSecondaryTextColor.current.copy(alpha = 0.6f)
            )
        },
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun IntegerOptionField(
    option: Option<*>,
    currentValue: Int,
    onValueChange: (Int) -> Unit
) {
    MorpheDialogTextField(
        value = currentValue.toString(),
        onValueChange = {
            it.toIntOrNull()?.let(onValueChange)
        },
        placeholder = {
            Text(
                option.default?.toString() ?: "0",
                color = LocalDialogSecondaryTextColor.current.copy(alpha = 0.6f)
            )
        },
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun LongOptionField(
    option: Option<*>,
    currentValue: Long,
    onValueChange: (Long) -> Unit
) {
    MorpheDialogTextField(
        value = currentValue.toString(),
        onValueChange = {
            it.toLongOrNull()?.let(onValueChange)
        },
        placeholder = {
            Text(
                option.default?.toString() ?: "0",
                color = LocalDialogSecondaryTextColor.current.copy(alpha = 0.6f)
            )
        },
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun FloatOptionField(
    option: Option<*>,
    currentValue: Float,
    onValueChange: (Float) -> Unit
) {
    MorpheDialogTextField(
        value = currentValue.toString(),
        onValueChange = {
            it.toFloatOrNull()?.let(onValueChange)
        },
        placeholder = {
            Text(
                option.default?.toString() ?: "0.0",
                color = LocalDialogSecondaryTextColor.current.copy(alpha = 0.6f)
            )
        },
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun ColorOptionField(
    option: Option<*>,
    currentValue: String,
    onValueChange: (String) -> Unit
) {
    var showColorPicker by remember { mutableStateOf(false) }

    // Check if option has presets
    val hasPresets = !option.presets.isNullOrEmpty()

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Show preset selector if available
        if (hasPresets) {
            SelectOptionField(
                option = option,
                currentValue = currentValue,
                onValueChange = onValueChange
            )
        } else {
            // Show text field for custom color
            MorpheDialogTextField(
                value = currentValue,
                onValueChange = onValueChange,
                placeholder = {
                    Text(
                        option.default?.toString() ?: "#RRGGBB",
                        color = LocalDialogSecondaryTextColor.current.copy(alpha = 0.6f)
                    )
                },
                modifier = Modifier.fillMaxWidth()
            )

            // Custom color picker button
            MorpheClickableCard(
                onClick = { showColorPicker = true },
                cornerRadius = 8.dp,
                alpha = 0.05f
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (currentValue.isNotBlank()) {
                            ColorPreviewDot(colorValue = currentValue, size = 24)
                        }
                        Text(
                            text = stringResource(R.string.morphe_custom_color_picker),
                            style = MaterialTheme.typography.bodyMedium,
                            color = LocalDialogTextColor.current
                        )
                    }

                    Icon(
                        imageVector = Icons.Outlined.Palette,
                        contentDescription = null,
                        tint = LocalDialogTextColor.current.copy(alpha = 0.5f),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }

    if (showColorPicker) {
        ColorPickerDialog(
            title = option.title,
            currentColor = currentValue.ifBlank { option.default?.toString() ?: "#000000" },
            onColorSelected = { color ->
                onValueChange(color)
                showColorPicker = false
            },
            onDismiss = { showColorPicker = false }
        )
    }
}

@Composable
private fun PathOptionField(
    option: Option<*>,
    currentValue: String,
    onValueChange: (String) -> Unit
) {
    val openFolderPicker = rememberFolderPickerWithPermission(
        onFolderPicked = { path -> onValueChange(path) }
    )

    MorpheDialogTextField(
        value = currentValue,
        onValueChange = onValueChange,
        placeholder = {
            Text(
                option.default?.toString() ?: "/storage/emulated/0/icons",
                color = LocalDialogSecondaryTextColor.current.copy(alpha = 0.6f)
            )
        },
        trailingIcon = {
            IconButton(
                onClick = openFolderPicker,
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.FolderOpen,
                    contentDescription = "Pick folder",
                    tint = LocalDialogTextColor.current.copy(alpha = 0.7f),
                    modifier = Modifier.size(20.dp)
                )
            }
        },
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun SelectOptionField(
    option: Option<*>,
    currentValue: String,
    onValueChange: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    var showCustomInput by remember { mutableStateOf(false) }
    val rotationAngle by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = tween(durationMillis = 300),
        label = "dropdown_rotation"
    )

    val presets = option.presets ?: emptyMap()
    val isColorOption = isColorOption(option)
    val isPackageNameOption = option.key.lowercase().contains("packagename") ||
            option.title.lowercase().contains("package name")

    // Check if current value is custom
    val isCustomValue = presets.values.none { it?.toString() == currentValue } && currentValue.isNotBlank()

    // Determine if we should show custom input options
    val supportsCustomInput = isColorOption || isPackageNameOption

    Column(modifier = Modifier.fillMaxWidth()) {
        // Current selection button
        MorpheClickableCard(
            onClick = { expanded = !expanded },
            cornerRadius = 8.dp,
            alpha = 0.05f
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    // Show color preview if it's a color option
                    if (isColorOption && currentValue.isNotBlank()) {
                        ColorPreviewDot(colorValue = currentValue, size = 20)
                    }

                    Column {
                        val displayText = if (isCustomValue) {
                            stringResource(R.string.morphe_custom)
                        } else {
                            presets.entries.find { it.value?.toString() == currentValue }?.key
                                ?: currentValue.ifBlank { option.default?.toString() ?: stringResource(R.string.morphe_select) }
                        }

                        Text(
                            text = displayText,
                            style = MaterialTheme.typography.bodyMedium,
                            color = LocalDialogTextColor.current
                        )
                        if (currentValue.isNotBlank() && (isCustomValue || presets.entries.find { it.value?.toString() == currentValue }?.key != null)) {
                            Text(
                                text = currentValue,
                                style = MaterialTheme.typography.bodySmall,
                                color = LocalDialogSecondaryTextColor.current
                            )
                        }
                    }
                }
                Icon(
                    imageVector = Icons.Outlined.ExpandMore,
                    contentDescription = null,
                    modifier = Modifier.rotate(rotationAngle),
                    tint = LocalDialogTextColor.current.copy(alpha = 0.7f)
                )
            }
        }

        // Dropdown options
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(animationSpec = tween(300)) + fadeIn(),
            exit = shrinkVertically(animationSpec = tween(300)) + fadeOut()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // Preset options
                presets.forEach { (label, value) ->
                    val valueStr = value?.toString() ?: ""
                    val isSelected = currentValue == valueStr && !isCustomValue

                    OutlinedCard(
                        onClick = {
                            onValueChange(valueStr)
                            expanded = false
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        colors = CardDefaults.outlinedCardColors(
                            containerColor = if (isSelected)
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                            else
                                MaterialTheme.colorScheme.surface.copy(alpha = 0.0f)
                        ),
                        border = BorderStroke(
                            width = if (isSelected) 2.dp else 1.dp,
                            color = if (isSelected)
                                MaterialTheme.colorScheme.primary
                            else
                                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                // Color preview if applicable
                                if (isColorOption && valueStr.isNotBlank()) {
                                    ColorPreviewDot(colorValue = valueStr, size = 20)
                                }

                                Column {
                                    Text(
                                        text = label,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = LocalDialogTextColor.current,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                    )
                                    if (valueStr.isNotBlank() && valueStr != label) {
                                        Text(
                                            text = valueStr,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = LocalDialogSecondaryTextColor.current
                                        )
                                    }
                                }
                            }
                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.Outlined.Check,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }

                // Custom option for supported types
                if (supportsCustomInput) {
                    OutlinedCard(
                        onClick = {
                            showCustomInput = true
                            expanded = false
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        colors = CardDefaults.outlinedCardColors(
                            containerColor = if (isCustomValue)
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                            else
                                MaterialTheme.colorScheme.surface.copy(alpha = 0.0f)
                        ),
                        border = BorderStroke(
                            width = if (isCustomValue) 2.dp else 1.dp,
                            color = if (isCustomValue)
                                MaterialTheme.colorScheme.primary
                            else
                                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    imageVector = if (isColorOption) Icons.Outlined.Palette else Icons.Outlined.Edit,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )

                                Text(
                                    text = stringResource(R.string.morphe_custom),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = LocalDialogTextColor.current,
                                    fontWeight = if (isCustomValue) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                            if (isCustomValue) {
                                Icon(
                                    imageVector = Icons.Outlined.Check,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Custom input dialogs
    if (showCustomInput) {
        when {
            isColorOption -> {
                ColorPickerDialog(
                    title = option.title,
                    currentColor = if (isCustomValue) currentValue else (option.default?.toString() ?: "#000000"),
                    onColorSelected = { color ->
                        onValueChange(color)
                        showCustomInput = false
                    },
                    onDismiss = { showCustomInput = false }
                )
            }
            isPackageNameOption -> {
                CustomTextInputDialog(
                    title = option.title,
                    currentValue = if (isCustomValue) currentValue else "",
                    placeholder = option.default?.toString() ?: "",
                    onValueConfirmed = { text ->
                        onValueChange(text)
                        showCustomInput = false
                    },
                    onDismiss = { showCustomInput = false }
                )
            }
        }
    }
}

/**
 * Custom text input dialog for entering custom values
 */
@Composable
private fun CustomTextInputDialog(
    title: String,
    currentValue: String,
    placeholder: String,
    onValueConfirmed: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var textValue by remember { mutableStateOf(currentValue) }

    MorpheDialog(
        onDismissRequest = onDismiss,
        title = title,
        footer = {
            MorpheDialogButtonRow(
                primaryText = stringResource(R.string.save),
                onPrimaryClick = {
                    onValueConfirmed(textValue)
                },
                secondaryText = stringResource(android.R.string.cancel),
                onSecondaryClick = onDismiss
            )
        }
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = stringResource(R.string.morphe_enter_custom_value),
                style = MaterialTheme.typography.bodyMedium,
                color = LocalDialogSecondaryTextColor.current
            )

            MorpheDialogTextField(
                value = textValue,
                onValueChange = { textValue = it },
                placeholder = {
                    Text(
                        placeholder,
                        color = LocalDialogSecondaryTextColor.current.copy(alpha = 0.6f)
                    )
                },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

private fun isColorOption(option: Option<*>): Boolean {
    val key = option.key.lowercase()
    val title = option.title.lowercase()
    val description = option.description.lowercase()

    return key.contains("color") ||
            title.contains("color") ||
            description.contains("color") ||
            description.contains("#rrggbb") ||
            description.contains("hex") ||
            option.presets?.values?.any {
                it?.toString()?.startsWith("#") == true ||
                        it?.toString()?.startsWith("@android:color") == true
            } == true
}

private fun isPathOption(option: Option<*>): Boolean {
    val key = option.key.lowercase()
    val title = option.title.lowercase()
    val description = option.description.lowercase()

    return key.contains("path") ||
            key.contains("folder") ||
            key.contains("directory") ||
            key.contains("custom") && (key.contains("icon") || key.contains("header")) ||
            title.contains("folder") ||
            description.contains("folder") ||
            description.contains("path") ||
            description.contains("directory") ||
            description.contains("mipmap-") ||
            description.contains("drawable-")
}
