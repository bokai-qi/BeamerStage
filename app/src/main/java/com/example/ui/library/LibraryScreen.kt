package com.example.ui.library

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Splitscreen
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.model.AppThemeMode
import com.example.model.BeamerLayoutMode
import com.example.model.DarkThemeStyle
import com.example.model.ExternalDisplayInfo
import com.example.model.LaserColor
import com.example.model.UserPreferences
import com.example.ui.theme.OledBlack
import com.example.ui.theme.StageSurface

@Composable
fun LibraryScreen(
    viewModel: LibraryViewModel,
    onOpenDeck: (String) -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var showSettingsSheet by remember { mutableStateOf(false) }
    var showGuideSheet by remember { mutableStateOf(false) }
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
            FloatingActionButton(
                onClick = { pdfPickerLauncher.launch(arrayOf("application/pdf")) },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.testTag("fab_open_pdf")
            ) {
                Icon(Icons.Default.Add, contentDescription = "Open PDF Presentation")
            }
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
                    StageHeroCard(
                        connectedDisplay = uiState.connectedDisplay,
                        isImporting = uiState.isImporting,
                        onOpenPdfClick = { pdfPickerLauncher.launch(arrayOf("application/pdf")) }
                    )
                }

                item(span = { GridItemSpan(maxLineSpan) }) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Presentations & Beamer Demos",
                            style = MaterialTheme.typography.titleLarge
                        )
                        Text(
                            text = "${uiState.decks.size} decks",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                if (uiState.decks.isEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        EmptyDecksCard(
                            onOpenPdfClick = { pdfPickerLauncher.launch(arrayOf("application/pdf")) }
                        )
                    }
                } else {
                    items(uiState.decks, key = { it.deck.id }) { item ->
                        DeckCard(
                            item = item,
                            onClick = { onOpenDeck(item.deck.id) },
                            onChangeLayout = { mode ->
                                viewModel.updateDeckLayoutMode(item.deck.id, mode)
                            },
                            onDelete = { viewModel.deleteDeck(item.deck.id) }
                        )
                    }
                }

                item(span = { GridItemSpan(maxLineSpan) }) {
                    Spacer(modifier = Modifier.height(72.dp))
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
            onSelectLaserColor = viewModel::setDefaultLaserColor,
            onChangeNoteFontSize = viewModel::setNoteFontSizeSp,
            onDismiss = { showSettingsSheet = false }
        )
    }

    if (showGuideSheet) {
        BeamerGuideSheet(onDismiss = { showGuideSheet = false })
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
private fun StageHeroCard(
    connectedDisplay: ExternalDisplayInfo?,
    isImporting: Boolean,
    onOpenPdfClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
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
                .padding(16.dp),
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
                modifier = Modifier.size(48.dp)
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
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Text(
                    text = connectedDisplay?.name ?: "USB-C DisplayPort / HDMI",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = connectedDisplay?.let {
                        "${it.resolutionLabel} @ ${it.refreshRate.toInt()} Hz · Ready to cast"
                    } ?: "Import any LaTeX Beamer or PDF slide deck",
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

            Button(
                onClick = onOpenPdfClick,
                enabled = !isImporting,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ),
                modifier = Modifier.testTag("open_pdf_button")
            ) {
                Icon(
                    imageVector = Icons.Default.PictureAsPdf,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(if (isImporting) "Loading…" else "Open PDF")
            }
        }
    }
}

@Composable
private fun DeckCard(
    item: LibraryDeckItem,
    onClick: () -> Unit,
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
                        text = "Slide ${deck.lastPageIndex + 1}/${deck.pageCount} · ${deck.targetDurationMinutes}m",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
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
private fun EmptyDecksCard(onOpenPdfClick: () -> Unit) {
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
                text = "No PDF Slides Loaded",
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = "Open any compiled LaTeX Beamer PDF or standard slide deck from your device.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedButton(onClick = onOpenPdfClick) {
                Text("Browse Device PDFs")
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
                        "Use hardware Volume Down for Next and Volume Up for Previous while presenting.",
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
                "Connect a USB-C to DisplayPort or HDMI adapter on your Pixel device. BeamerStage automatically routes clean, borderless slides, laser pointer, and annotations to the external projector while keeping your timer and notes private on your phone.",
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
                "When Auto-Detect is enabled, double-wide 32:9 PDFs automatically project only the slide half and show your compiled Beamer \\note{} half on the presenter console.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
