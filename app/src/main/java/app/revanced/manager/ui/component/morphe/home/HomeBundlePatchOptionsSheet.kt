package app.revanced.manager.ui.component.morphe.home

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.morphe.manager.R
import app.revanced.manager.domain.bundles.PatchBundleSource
import app.revanced.manager.patcher.patch.PatchInfo
import app.revanced.manager.ui.component.morphe.shared.MorpheCard
import app.revanced.manager.ui.component.morphe.shared.MorpheClickableCard
import app.revanced.manager.ui.viewmodel.DashboardViewModel
import kotlinx.collections.immutable.toImmutableList
import org.koin.androidx.compose.koinViewModel

/**
 * Bottom sheet showing patches with configurable options for a specific bundle
 * Shows all patches with deduplicated options across packages
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeBundlePatchOptionsSheet(
    bundle: PatchBundleSource,
    onDismiss: () -> Unit,
    dashboardViewModel: DashboardViewModel = koinViewModel()
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // Get patches for this bundle
    val bundleInfo by dashboardViewModel.patchBundleRepository.bundleInfoFlow
        .collectAsStateWithLifecycle(emptyMap())

    val patches = remember(bundleInfo, bundle.uid) {
        bundleInfo[bundle.uid]?.patches ?: emptyList()
    }

    // Get saved options for all compatible packages
    val allCompatiblePackages = remember(patches) {
        patches.flatMap { patch ->
            patch.compatiblePackages?.map { it.packageName } ?: emptyList()
        }.distinct().sorted()
    }

    val savedOptionsByPackage = allCompatiblePackages.associateWith { packageName ->
        dashboardViewModel.getSavedOptionsForBundle(
            bundleUid = bundle.uid,
            packageName = packageName
        ).collectAsStateWithLifecycle(emptyMap()).value
    }

    // State for patch options editor
    var selectedPatchInfo by remember { mutableStateOf<PatchWithPackages?>(null) }

    // Filter patches that have options and deduplicate
    val deduplicatedPatches = remember(patches) {
        // Group patches by name (since same patch can appear for multiple packages)
        val patchGroups = patches
            .filter { !it.options.isNullOrEmpty() }
            .groupBy { it.name }

        // For each group, merge compatible packages and all unique options
        patchGroups.map { (_, patchList) ->
            val firstPatch = patchList.first()
            val allPackages = patchList.flatMap { patch ->
                patch.compatiblePackages?.map { it.packageName } ?: emptyList()
            }.distinct().sorted()

            // Merge all options from all patches with same name
            val allOptions = patchList.flatMap { it.options ?: emptyList() }
                .distinctBy { it.key } // Remove duplicate options by key
                .toList()

            PatchWithPackages(
                patch = firstPatch.copy(options = allOptions.toImmutableList()),
                compatiblePackages = allPackages
            )
        }.sortedBy { it.patch.name }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        dragHandle = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    modifier = Modifier.size(width = 32.dp, height = 4.dp),
                    shape = RoundedCornerShape(2.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                ) {}
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        ) {
            // Header
            Text(
                text = stringResource(R.string.morphe_patch_options),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            Text(
                text = bundle.displayTitle,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            // Content
            if (deduplicatedPatches.isEmpty()) {
                // No patches with options
                MorpheCard(
                    cornerRadius = 12.dp,
                    alpha = 0.1f,
                    modifier = Modifier.padding(bottom = 16.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Text(
                            text = stringResource(R.string.morphe_no_configurable_patches),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            } else {
                // List of patches with options
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(bottom = 16.dp)
                ) {
                    items(deduplicatedPatches) { patchWithPackages ->
                        val customOptionsCount = patchWithPackages.compatiblePackages.count { packageName ->
                            savedOptionsByPackage[packageName]?.containsKey(patchWithPackages.patch.name) == true
                        }

                        PatchOptionCard(
                            patchWithPackages = patchWithPackages,
                            customOptionsCount = customOptionsCount,
                            onConfigureClick = { selectedPatchInfo = patchWithPackages }
                        )
                    }
                }
            }
        }
    }

    // Patch Options Editor Dialog
    selectedPatchInfo?.let { patchInfo ->
        PatchOptionsEditorDialog(
            bundle = bundle,
            patchWithPackages = patchInfo,
            onDismiss = { selectedPatchInfo = null },
            dashboardViewModel = dashboardViewModel
        )
    }
}

/**
 * Data class combining patch with all its compatible packages
 */
data class PatchWithPackages(
    val patch: PatchInfo,
    val compatiblePackages: List<String>
)

/**
 * Card for a single patch with options
 * Shows patch name, number of options, and configure button
 */
@Composable
private fun PatchOptionCard(
    patchWithPackages: PatchWithPackages,
    customOptionsCount: Int,
    onConfigureClick: () -> Unit
) {
    MorpheCard(
        cornerRadius = 12.dp,
        alpha = 0.1f
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            // Patch name and status
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = patchWithPackages.patch.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Text(
                        text = stringResource(
                            R.string.morphe_options_count,
                            patchWithPackages.patch.options?.size ?: 0
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Status indicator that show count if custom options are set
                if (customOptionsCount > 0) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Check,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = stringResource(R.string.morphe_custom_options_set_count, customOptionsCount),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }
            }

            // Compatible packages info
            if (patchWithPackages.compatiblePackages.isNotEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(
                        R.string.morphe_compatible_with_packages,
                        patchWithPackages.compatiblePackages.joinToString(", ") { pkg ->
                            pkg.substringAfterLast('.').replaceFirstChar { it.uppercase() }
                        }
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }

            // Configure button
            Spacer(modifier = Modifier.height(8.dp))

            MorpheClickableCard(
                onClick = onConfigureClick,
                cornerRadius = 8.dp,
                alpha = 0.2f,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(10.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Settings,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.morphe_configure_options),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}
