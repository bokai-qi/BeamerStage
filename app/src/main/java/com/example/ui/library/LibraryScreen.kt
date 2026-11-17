package com.example.ui.library

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BrightnessAuto
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.CastConnected
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.IosShare
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Splitscreen
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.local.DeckEntity
import com.example.model.AppThemeMode
import com.example.model.BeamerLayoutMode
import com.example.model.DarkThemeStyle
import com.example.model.ExternalDisplayInfo
import com.example.model.LaserColor
import com.example.model.LibrarySortOrder
import com.example.model.UserPreferences
import com.example.ui.theme.OledBlack
import com.example.ui.theme.StageSurface

@Composable
fun LibraryScreen(
    viewModel: LibraryViewModel,
    onOpenDeck: (String) -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var showSettingsSheet by remember { mutableStateOf(false) }
    var showGuideSheet by remember { mutableStateOf(false) }
    var deckToRename by remember { mutableStateOf<DeckEntity?>(null) }
    var deckForDuration by remember { mutableStateOf<DeckEntity?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }

    val pdfPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            viewModel.importPdfUri(uri, onOpenDeck)
        }
    }

    LaunchedEffect(uiState.errorMessage) {
        val msg = uiState.errorMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(msg)
        viewModel.dismissError()
    }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { pdfPickerLauncher.launch(arrayOf("application/pdf")) },
                icon = {
                    if (uiState.isImporting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    } else {
                        Icon(Icons.Default.Add, contentDescription = null)
                    }
                },
                text = { Text(if (uiState.isImporting) "Opening…" else "Open PDF") },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.testTag("fab_open_pdf")
            )
        }
    ) { innerPadding ->
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.TopCenter
        ) {
            val isWide = maxWidth >= 680.dp
            val columns = if (maxWidth >= 960.dp) 3 else if (isWide) 2 else 1

            LazyVerticalGrid(
                columns = GridCells.Fixed(columns),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier
                    .widthIn(max = 1100.dp)
                    .fillMaxSize()
            ) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    LibraryTopHeader(
                        connectedDisplay = uiState.connectedDisplay,
                        themeMode = uiState.preferences.themeMode,
                        onCycleTheme = {
                            val next = when (uiState.preferences.themeMode) {
                                AppThemeMode.DARK -> AppThemeMode.LIGHT
                                AppThemeMode.LIGHT -> AppThemeMode.SYSTEM
                                AppThemeMode.SYSTEM -> AppThemeMode.DARK
                            }
                            viewModel.setThemeMode(next)
                        },
                        onOpenSettings = { showSettingsSheet = true },
                        onOpenGuide = { showGuideSheet = true }
                    )
                }

                item(span = { GridItemSpan(maxLineSpan) }) {
                    StageSignalBanner(connectedDisplay = uiState.connectedDisplay)
                }

                item(span = { GridItemSpan(maxLineSpan) }) {
                    LibrarySearchAndSortBar(
                        searchQuery = uiState.searchQuery,
                        sortOrder = uiState.sortOrder,
                        showDemoDecks = uiState.preferences.showDemoDecks,
                        deckCount = uiState.decks.size,
                        onSearchQueryChange = viewModel::setSearchQuery,
                        onSelectSortOrder = viewModel::setSortOrder,
                        onToggleDemoDecks = {
                            viewModel.setShowDemoDecks(!uiState.preferences.showDemoDecks)
                        }
                    )
                }

                if (uiState.decks.isEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        EmptyDecksCard(
                            hasSearchFilter = uiState.searchQuery.isNotBlank() || !uiState.preferences.showDemoDecks,
                            onClearFilter = {
                                viewModel.setSearchQuery("")
                                if (!uiState.preferences.showDemoDecks && uiState.totalDecksCount > 0) {
                                    viewModel.setShowDemoDecks(true)
                                }
                            },
                            onOpenPdfClick = { pdfPickerLauncher.launch(arrayOf("application/pdf")) }
                        )
                    }
                } else {
                    items(uiState.decks, key = { it.deck.id }) { item ->
                        DeckCard(
                            item = item,
                            isExporting = uiState.exportingDeckId == item.deck.id,
                            onClick = { onOpenDeck(item.deck.id) },
                            onRename = { deckToRename = item.deck },
                            onSetDuration = { deckForDuration = item.deck },
                            onExportPdf = {
                                viewModel.exportDeckAnnotatedPdf(context, item.deck.id) { uri, title ->
                                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                        type = "application/pdf"
                                        putExtra(Intent.EXTRA_STREAM, uri)
                                        putExtra(Intent.EXTRA_SUBJECT, "$title (Annotated)")
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    }
                                    runCatching {
                                        context.startActivity(
                                            Intent.createChooser(shareIntent, "Share Annotated PDF")
                                        )
                                    }
                                }
                            },
                            onResetProgress = {
                                viewModel.resetDeckProgressAndAnnotations(item.deck.id)
                            },
                            onChangeLayout = { mode ->
                                viewModel.updateDeckLayoutMode(item.deck.id, mode)
                            },
                            onDelete = { viewModel.deleteDeck(item.deck.id) }
                        )
                    }
                }

                item(span = { GridItemSpan(maxLineSpan) }) {
                    Spacer(modifier = Modifier.height(80.dp))
                }
            }
        }
    }

    if (showSettingsSheet) {
        StageSettingsSheet(
            preferences = uiState.preferences,
            onSelectThemeMode = viewModel::setThemeMode,
            onSelectDarkThemeStyle = viewModel::setDarkThemeStyle,
            onToggleSimplePresentingMode = viewModel::setSimplePresentingMode,
            onToggleVolumeKeys = viewModel::setVolumeKeysNavigate,
            onToggleKeepScreenOn = viewModel::setKeepScreenOn,
            onToggleHaptics = viewModel::setHapticFeedbackEnabled,
            onToggleShowDemos = viewModel::setShowDemoDecks,
            onSelectLaserColor = viewModel::setDefaultLaserColor,
            onChangeNoteFontSize = viewModel::setNoteFontSizeSp,
            onDismiss = { showSettingsSheet = false }
        )
    }

    if (showGuideSheet) {
        BeamerGuideSheet(onDismiss = { showGuideSheet = false })
    }

    val targetRename = deckToRename
    if (targetRename != null) {
        RenameDeckDialog(
            initialTitle = targetRename.title,
            onConfirm = { updated ->
                viewModel.renameDeck(targetRename.id, updated)
                deckToRename = null
            },
            onDismiss = { deckToRename = null }
        )
    }

    val targetDurationDeck = deckForDuration
    if (targetDurationDeck != null) {
        TargetDurationDialog(
            initialMinutes = targetDurationDeck.targetDurationMinutes,
            onConfirm = { mins ->
                viewModel.updateTargetDuration(targetDurationDeck.id, mins)
                deckForDuration = null
            },
            onDismiss = { deckForDuration = null }
        )
    }
}

@Composable
private fun LibraryTopHeader(
    connectedDisplay: ExternalDisplayInfo?,
    themeMode: AppThemeMode,
    onCycleTheme: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenGuide: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "BeamerStage",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(2.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(
                            if (connectedDisplay != null) {
                                MaterialTheme.colorScheme.secondary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                            }
                        )
                )
                Text(
                    text = connectedDisplay?.let {
                        "DisplayPort Active · ${it.resolutionLabel}"
                    } ?: "Ready for USB-C DisplayPort",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (connectedDisplay != null) {
                        MaterialTheme.colorScheme.secondary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            IconButton(
                onClick = onCycleTheme,
                modifier = Modifier.testTag("theme_toggle_button")
            ) {
                Icon(
                    imageVector = when (themeMode) {
                        AppThemeMode.DARK -> Icons.Default.DarkMode
                        AppThemeMode.LIGHT -> Icons.Default.LightMode
                        AppThemeMode.SYSTEM -> Icons.Default.BrightnessAuto
                    },
                    contentDescription = "Theme: ${themeMode.label}. Tap to switch"
                )
            }
            IconButton(
                onClick = onOpenGuide,
                modifier = Modifier.testTag("beamer_guide_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Splitscreen,
                    contentDescription = "Beamer Split-Notes Guide"
                )
            }
            IconButton(
                onClick = onOpenSettings,
                modifier = Modifier.testTag("settings_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = "Presenter Settings"
                )
            }
        }
    }
}

@Composable
private fun StageSignalBanner(connectedDisplay: ExternalDisplayInfo?) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            width = 1.dp,
            color = if (connectedDisplay != null) {
                MaterialTheme.colorScheme.secondary.copy(alpha = 0.5f)
            } else {
                MaterialTheme.colorScheme.outlineVariant
            }
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = if (connectedDisplay != null) {
                    MaterialTheme.colorScheme.secondaryContainer
                } else {
                    MaterialTheme.colorScheme.primaryContainer
                },
                modifier = Modifier.size(44.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = if (connectedDisplay != null) {
                            Icons.Default.CastConnected
                        } else {
                            Icons.Default.Cast
                        },
                        contentDescription = null,
                        tint = if (connectedDisplay != null) {
                            MaterialTheme.colorScheme.secondary
                        } else {
                            MaterialTheme.colorScheme.primary
                        },
                        modifier = Modifier.size(21.dp)
                    )
                }
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = connectedDisplay?.name ?: "USB-C DisplayPort / HDMI Stage",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = connectedDisplay?.let {
                        "${it.resolutionLabel} @ ${it.refreshRate.toInt()} Hz · Projector locked & ready"
                    } ?: "Auto-splits 32:9 Beamer notes · Supports clickers, hyperlinks & chalkboard",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (connectedDisplay != null) {
                        MaterialTheme.colorScheme.secondary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun LibrarySearchAndSortBar(
    searchQuery: String,
    sortOrder: LibrarySortOrder,
    showDemoDecks: Boolean,
    deckCount: Int,
    onSearchQueryChange: (String) -> Unit,
    onSelectSortOrder: (LibrarySortOrder) -> Unit,
    onToggleDemoDecks: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchQueryChange,
            singleLine = true,
            placeholder = {
                Text(
                    text = "Search talks & Beamer decks…",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = null,
                    modifier = Modifier.size(19.dp)
                )
            },
            trailingIcon = if (searchQuery.isNotEmpty()) {
                {
                    IconButton(onClick = { onSearchQueryChange("") }) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Clear search",
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            } else null,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("library_search_field")
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                LibrarySortOrder.entries.forEach { order ->
                    FilterChip(
                        selected = sortOrder == order,
                        onClick = { onSelectSortOrder(order) },
                        label = { Text(order.label, style = MaterialTheme.typography.labelSmall) },
                        modifier = Modifier.testTag("sort_chip_${order.name.lowercase()}")
                    )
                }
                FilterChip(
                    selected = showDemoDecks,
                    onClick = onToggleDemoDecks,
                    label = { Text("Demos", style = MaterialTheme.typography.labelSmall) },
                    modifier = Modifier.testTag("toggle_demo_decks_chip")
                )
            }

            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "$deckCount ${if (deckCount == 1) "deck" else "decks"}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun DeckCard(
    item: LibraryDeckItem,
    isExporting: Boolean,
    onClick: () -> Unit,
    onRename: () -> Unit,
    onSetDuration: () -> Unit,
    onExportPdf: () -> Unit,
    onResetProgress: () -> Unit,
    onChangeLayout: (BeamerLayoutMode) -> Unit,
    onDelete: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val deck = item.deck
    val progress = if (deck.pageCount > 0) {
        (deck.lastPageIndex + 1).toFloat() / deck.pageCount.toFloat()
    } else 0f

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag("deck_card_${deck.id}")
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .width(118.dp)
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color.Black),
                contentAlignment = Alignment.Center
            ) {
                if (item.thumbnail != null && !item.thumbnail.isRecycled) {
                    Image(
                        bitmap = item.thumbnail.asImageBitmap(),
                        contentDescription = "${deck.title} cover slide",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.PictureAsPdf,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = deck.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Text(
                            text = item.layoutMode.badgeLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                    Text(
                        text = if (isExporting) {
                            "Exporting PDF…"
                        } else {
                            "Slide ${deck.lastPageIndex + 1}/${deck.pageCount} · ${deck.targetDurationMinutes}m"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isExporting) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }

                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(CircleShape),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
            }

            Box {
                IconButton(
                    onClick = { menuExpanded = true },
                    modifier = Modifier.testTag("deck_menu_${deck.id}")
                ) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "Deck options"
                    )
                }
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("Present Now") },
                        leadingIcon = { Icon(Icons.Default.PlayArrow, contentDescription = null) },
                        onClick = {
                            menuExpanded = false
                            onClick()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Rename Deck…") },
                        leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                        onClick = {
                            menuExpanded = false
                            onRename()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Set Target Duration (${deck.targetDurationMinutes}m)…") },
                        leadingIcon = { Icon(Icons.Default.Timer, contentDescription = null) },
                        onClick = {
                            menuExpanded = false
                            onSetDuration()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Export Annotated PDF") },
                        leadingIcon = { Icon(Icons.Default.IosShare, contentDescription = null) },
                        onClick = {
                            menuExpanded = false
                            onExportPdf()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Reset Progress & Clear Ink") },
                        leadingIcon = { Icon(Icons.Default.RestartAlt, contentDescription = null) },
                        onClick = {
                            menuExpanded = false
                            onResetProgress()
                        }
                    )
                    HorizontalDivider()
                    BeamerLayoutMode.entries.forEach { mode ->
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = if (item.layoutMode == mode) "✓ ${mode.displayName}" else mode.displayName
                                )
                            },
                            onClick = {
                                menuExpanded = false
                                onChangeLayout(mode)
                            }
                        )
                    }
                    if (!deck.isBundledSample) {
                        HorizontalDivider()
                        DropdownMenuItem(
                            text = { Text("Remove from Library", color = MaterialTheme.colorScheme.error) },
                            leadingIcon = {
                                Icon(
                                    Icons.Default.DeleteOutline,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error
                                )
                            },
                            onClick = {
                                menuExpanded = false
                                onDelete()
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RenameDeckDialog(
    initialTitle: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var draft by remember(initialTitle) { mutableStateOf(initialTitle) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rename Presentation") },
        text = {
            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it },
                singleLine = true,
                label = { Text("Presentation title") },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("rename_deck_textfield")
            )
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(draft) },
                enabled = draft.trim().isNotEmpty(),
                modifier = Modifier.testTag("confirm_rename_deck_button")
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun TargetDurationDialog(
    initialMinutes: Int,
    onConfirm: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedMinutes by remember(initialMinutes) { mutableIntStateOf(initialMinutes.coerceIn(1, 180)) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Talk Pacing Target") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "Target duration: $selectedMinutes min",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(5, 10, 15, 20, 30, 45, 60, 90).forEach { mins ->
                        FilterChip(
                            selected = selectedMinutes == mins,
                            onClick = { selectedMinutes = mins },
                            label = { Text("${mins}m") }
                        )
                    }
                }
                Slider(
                    value = selectedMinutes.toFloat(),
                    onValueChange = { selectedMinutes = it.toInt().coerceIn(1, 120) },
                    valueRange = 1f..120f
                )
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(selectedMinutes) }) {
                Text("Apply")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun EmptyDecksCard(
    hasSearchFilter: Boolean,
    onClearFilter: () -> Unit,
    onOpenPdfClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = Icons.Default.PictureAsPdf,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(40.dp)
            )
            Text(
                text = if (hasSearchFilter) "No Matching Presentations" else "No PDF Slides Loaded",
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = if (hasSearchFilter) {
                    "Try clearing your search filter or enabling bundled demo decks."
                } else {
                    "Open any compiled LaTeX Beamer PDF or standard slide deck from your device."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (hasSearchFilter) {
                OutlinedButton(onClick = onClearFilter) {
                    Text("Show All Decks")
                }
            } else {
                OutlinedButton(onClick = onOpenPdfClick) {
                    Text("Browse Device PDFs")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StageSettingsSheet(
    preferences: UserPreferences,
    onSelectThemeMode: (AppThemeMode) -> Unit,
    onSelectDarkThemeStyle: (DarkThemeStyle) -> Unit,
    onToggleSimplePresentingMode: (Boolean) -> Unit,
    onToggleVolumeKeys: (Boolean) -> Unit,
    onToggleKeepScreenOn: (Boolean) -> Unit,
    onToggleHaptics: (Boolean) -> Unit,
    onToggleShowDemos: (Boolean) -> Unit,
    onSelectLaserColor: (LaserColor) -> Unit,
    onChangeNoteFontSize: (Float) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("Stage Preferences", style = MaterialTheme.typography.titleLarge)

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Appearance", style = MaterialTheme.typography.titleSmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AppThemeMode.entries.forEach { mode ->
                        FilterChip(
                            selected = preferences.themeMode == mode,
                            onClick = { onSelectThemeMode(mode) },
                            label = { Text(mode.label) },
                            leadingIcon = {
                                Icon(
                                    imageVector = when (mode) {
                                        AppThemeMode.DARK -> Icons.Default.DarkMode
                                        AppThemeMode.LIGHT -> Icons.Default.LightMode
                                        AppThemeMode.SYSTEM -> Icons.Default.BrightnessAuto
                                    },
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            },
                            modifier = Modifier.testTag("theme_chip_${mode.name.lowercase()}")
                        )
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Dark Mode Style", style = MaterialTheme.typography.titleSmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DarkThemeStyle.entries.forEach { style ->
                        val selected = preferences.darkThemeStyle == style
                        FilterChip(
                            selected = selected,
                            onClick = { onSelectDarkThemeStyle(style) },
                            label = { Text(style.label) },
                            leadingIcon = {
                                Box(
                                    modifier = Modifier
                                        .size(12.dp)
                                        .clip(CircleShape)
                                        .background(
                                            if (style == DarkThemeStyle.OLED) OledBlack else StageSurface
                                        )
                                )
                            },
                            modifier = Modifier.testTag("dark_style_chip_${style.name.lowercase()}")
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Simple Presenting Mode", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "Show only the current slide and tools on your device without Next Slide or Speaker Notes.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Switch(
                    checked = preferences.simplePresentingMode,
                    onCheckedChange = onToggleSimplePresentingMode,
                    modifier = Modifier.testTag("switch_simple_mode")
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Volume Buttons Advance Slides", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "Use hardware Volume Down for Next and Volume Up for Previous (wireless clickers always work).",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Switch(
                    checked = preferences.volumeKeysNavigate,
                    onCheckedChange = onToggleVolumeKeys,
                    modifier = Modifier.testTag("switch_volume_keys")
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Tactile Haptics & Pacing Alerts", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "Subtle ticks on slide steps, straight-line snap, and 85% / 100% talk timer milestones.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Switch(
                    checked = preferences.hapticFeedbackEnabled,
                    onCheckedChange = onToggleHaptics,
                    modifier = Modifier.testTag("switch_haptics")
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Keep Device Screen Awake", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "Prevents screen timeout on your Pixel during long lectures.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Switch(
                    checked = preferences.keepScreenOn,
                    onCheckedChange = onToggleKeepScreenOn,
                    modifier = Modifier.testTag("switch_keep_screen_on")
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Show Bundled Demo Decks", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "Display the two built-in LaTeX Beamer sample talks in your library.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Switch(
                    checked = preferences.showDemoDecks,
                    onCheckedChange = onToggleShowDemos,
                    modifier = Modifier.testTag("switch_show_demos")
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Default Laser Pointer Color", style = MaterialTheme.typography.titleSmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    LaserColor.entries.forEach { color ->
                        FilterChip(
                            selected = preferences.defaultLaserColor == color,
                            onClick = { onSelectLaserColor(color) },
                            label = { Text(color.label) },
                            leadingIcon = {
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .clip(CircleShape)
                                        .background(Color(color.colorHex))
                                )
                            }
                        )
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Speaker Note Text Size", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "${preferences.noteFontSizeSp.toInt()} sp",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Slider(
                    value = preferences.noteFontSizeSp,
                    onValueChange = onChangeNoteFontSize,
                    valueRange = 12f..28f
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("settings_credits_section"),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "Thank you for using, happy presenting!",
                    style = MaterialTheme.typography.titleSmall
                )
                Text(
                    text = "By Bokai Qi, built with Google Gemini.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BeamerGuideSheet(onDismiss: () -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("LaTeX Beamer & DisplayPort Guide", style = MaterialTheme.typography.titleLarge)
            Text(
                "1. USB-C DisplayPort Casting",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                "Connect a USB-C to DisplayPort or HDMI adapter on your Pixel device. BeamerStage automatically routes clean, borderless slides, laser pointer, spotlight, zoom, and chalkboard ink to the external projector while keeping your timer and notes private.",
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                "2. Compiling Beamer Notes on Second Screen",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary
            )
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "\\usepackage{pgfpages}\n\\setbeameroption{show notes on second screen=right}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(12.dp)
                )
            }
            Text(
                "When Auto-Detect is enabled, double-wide 32:9 PDFs automatically project only the slide half and show your compiled Beamer \\note{} half (with pinch-to-zoom) on the presenter console.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                "3. Wireless Clicker & Keyboard Shortcuts",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                "PageDown / Right / Space: Next · PageUp / Left: Prev · B or '.': Blackout · F: Freeze · W: Chalkboard · Esc: Resume.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
