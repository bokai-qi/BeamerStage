package com.example.ui.presenter

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material.icons.filled.BorderColor
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.CastConnected
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Flare
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Highlight
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Swipe
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.content.Intent
import android.net.Uri
import androidx.compose.material.icons.filled.Link
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.TextButton
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.model.InteractionTool
import com.example.model.LaserColor
import com.example.model.PenColor
import com.example.ui.components.StageSlideCanvas
import com.example.ui.theme.AmberTimer
import java.util.Locale

@Composable
fun PresenterScreen(
    viewModel: PresenterViewModel,
    onNavigateBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val view = LocalView.current
    val context = LocalContext.current

    var showSlideGrid by remember { mutableStateOf(false) }
    var showDisplayConfig by remember { mutableStateOf(false) }
    var showNoteEditor by remember { mutableStateOf(false) }

    // Keep screen awake on Pixel while presenting if preference enabled
    DisposableEffect(uiState.preferences.keepScreenOn) {
        val previous = view.keepScreenOn
        view.keepScreenOn = uiState.preferences.keepScreenOn
        onDispose { view.keepScreenOn = previous }
    }

    LaunchedEffect(showSlideGrid) {
        if (showSlideGrid) {
            viewModel.loadAllGridThumbnails()
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        if (uiState.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("Preparing Beamer Stage…", style = MaterialTheme.typography.bodyMedium)
                }
            }
            return@Scaffold
        }

        if (uiState.errorMessage != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("Unable to Open Presentation", style = MaterialTheme.typography.titleLarge)
                    Text(
                        text = uiState.errorMessage.orEmpty(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Button(onClick = onNavigateBack) {
                        Text("Back to Library")
                    }
                }
            }
            return@Scaffold
        }

        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            val isLandscapeOrWide = maxWidth >= 660.dp

            Column(modifier = Modifier.fillMaxSize()) {
                TopStageControlBar(
                    uiState = uiState,
                    onBack = onNavigateBack,
                    onOpenDisplayConfig = { showDisplayConfig = true },
                    onToggleTimer = viewModel::toggleTimer,
                    onResetTimer = viewModel::resetTimer,
                    onToggleFreeze = viewModel::toggleFreeze,
                    onToggleBlackout = viewModel::toggleBlackout
                )

                TalkPacingBar(
                    elapsedSeconds = uiState.elapsedSeconds,
                    targetMinutes = uiState.targetMinutes,
                    currentPage = uiState.currentPageIndex,
                    totalPages = uiState.totalPages
                )

                if (isLandscapeOrWide) {
                    // Wide / Landscape Broadcast Studio 2-column layout
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .weight(0.60f)
                                .fillMaxHeight(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            InteractiveSlideStage(
                                uiState = uiState,
                                viewModel = viewModel,
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth()
                            )
                            ToolDockBar(
                                uiState = uiState,
                                viewModel = viewModel
                            )
                        }

                        Column(
                            modifier = Modifier
                                .weight(0.40f)
                                .fillMaxHeight(),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            NextSlideCard(
                                uiState = uiState,
                                onNextClick = viewModel::nextSlide,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(132.dp)
                            )
                            PresenterNotesPanel(
                                uiState = uiState,
                                onFontScaleDelta = viewModel::adjustNoteFontSize,
                                onEditNote = { showNoteEditor = true },
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth()
                            )
                            BottomTransportBar(
                                currentPageIndex = uiState.currentPageIndex,
                                totalPages = uiState.totalPages,
                                onPrevious = viewModel::previousSlide,
                                onNext = viewModel::nextSlide,
                                onOpenGrid = { showSlideGrid = true }
                            )
                        }
                    }
                } else {
                    // Compact Portrait Handheld Console
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        InteractiveSlideStage(
                            uiState = uiState,
                            viewModel = viewModel,
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(16f / 9f)
                        )

                        ToolDockBar(
                            uiState = uiState,
                            viewModel = viewModel
                        )

                        // Next Slide + Notes split deck
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            PresenterNotesPanel(
                                uiState = uiState,
                                onFontScaleDelta = viewModel::adjustNoteFontSize,
                                onEditNote = { showNoteEditor = true },
                                modifier = Modifier
                                    .weight(0.64f)
                                    .fillMaxHeight()
                            )
                            NextSlideCard(
                                uiState = uiState,
                                onNextClick = viewModel::nextSlide,
                                modifier = Modifier
                                    .weight(0.36f)
                                    .fillMaxHeight()
                            )
                        }

                        BottomTransportBar(
                            currentPageIndex = uiState.currentPageIndex,
                            totalPages = uiState.totalPages,
                            onPrevious = viewModel::previousSlide,
                            onNext = viewModel::nextSlide,
                            onOpenGrid = { showSlideGrid = true }
                        )
                    }
                }
            }
        }
    }

    if (showSlideGrid) {
        SlideOverviewSheet(
            totalPages = uiState.totalPages,
            currentPageIndex = uiState.currentPageIndex,
            frozenPageIndex = uiState.frozenPageIndex,
            thumbnails = uiState.slideGridThumbnails,
            onSelectSlide = viewModel::goToSlide,
            onDismiss = { showSlideGrid = false }
        )
    }

    if (showDisplayConfig) {
        DisplayAndBeamerConfigSheet(
            connectedDisplay = uiState.connectedDisplay,
            requestedMode = uiState.requestedLayoutMode,
            effectiveMode = uiState.effectiveLayoutMode,
            targetMinutes = uiState.targetMinutes,
            projectedBitmap = uiState.projectedBitmap,
            projectedStrokes = uiState.projectedStrokes,
            laserState = uiState.laserState,
            isBlackout = uiState.isBlackout,
            isFrozen = uiState.isFrozen,
            onSelectLayoutMode = viewModel::setLayoutMode,
            onSelectTargetMinutes = viewModel::updateTargetDuration,
            onDismiss = { showDisplayConfig = false }
        )
    }

    if (showNoteEditor) {
        NoteEditorSheet(
            pageIndex = uiState.currentPageIndex,
            initialText = uiState.currentNoteText,
            onSave = viewModel::saveNoteForCurrentSlide,
            onDismiss = { showNoteEditor = false }
        )
    }

    val pendingUri = uiState.pendingExternalUri
    if (pendingUri != null) {
        AlertDialog(
            onDismissRequest = viewModel::dismissExternalUri,
            title = { Text("Open External PDF Link?") },
            text = {
                Text(
                    text = pendingUri,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.dismissExternalUri()
                        runCatching {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(pendingUri))
                            context.startActivity(intent)
                        }
                    },
                    modifier = Modifier.testTag("confirm_open_external_link")
                ) {
                    Text("Open URL")
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissExternalUri) {
                    Text("Stay on Slides")
                }
            }
        )
    }
}

@Composable
private fun TopStageControlBar(
    uiState: PresenterUiState,
    onBack: () -> Unit,
    onOpenDisplayConfig: () -> Unit,
    onToggleTimer: () -> Unit,
    onResetTimer: () -> Unit,
    onToggleFreeze: () -> Unit,
    onToggleBlackout: () -> Unit
) {
    val targetSeconds = uiState.targetMinutes * 60L
    val timerRatio = if (targetSeconds > 0) uiState.elapsedSeconds.toFloat() / targetSeconds.toFloat() else 0f
    val timerColor = when {
        timerRatio >= 1.0f -> MaterialTheme.colorScheme.tertiary
        timerRatio >= 0.85f -> AmberTimer
        uiState.isTimerRunning -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurface
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        IconButton(
            onClick = onBack,
            modifier = Modifier.testTag("back_to_library_button")
        ) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to Library")
        }

        // External DisplayPort + Beamer Mode Pill
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = if (uiState.connectedDisplay != null) {
                MaterialTheme.colorScheme.secondaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            },
            border = BorderStroke(
                1.dp,
                if (uiState.connectedDisplay != null) {
                    MaterialTheme.colorScheme.secondary.copy(alpha = 0.6f)
                } else {
                    MaterialTheme.colorScheme.outline
                }
            ),
            modifier = Modifier
                .weight(1f)
                .height(48.dp)
                .clickable(onClick = onOpenDisplayConfig)
                .testTag("display_config_chip")
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = if (uiState.connectedDisplay != null) Icons.Default.CastConnected else Icons.Default.Cast,
                    contentDescription = "DisplayPort Output",
                    tint = if (uiState.connectedDisplay != null) {
                        MaterialTheme.colorScheme.secondary
                    } else {
                        MaterialTheme.colorScheme.primary
                    },
                    modifier = Modifier.size(18.dp)
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = uiState.connectedDisplay?.resolutionLabel ?: "Stage Preview",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (uiState.connectedDisplay != null) {
                            MaterialTheme.colorScheme.secondary
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = uiState.effectiveLayoutMode.badgeLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }
            }
        }

        // Timer Pill
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            modifier = Modifier
                .height(48.dp)
                .clickable(onClick = onToggleTimer)
                .testTag("timer_toggle_button")
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    imageVector = if (uiState.isTimerRunning) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (uiState.isTimerRunning) "Pause Timer" else "Start Timer",
                    tint = timerColor,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = formatElapsedTime(uiState.elapsedSeconds),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = timerColor
                )
                if (uiState.elapsedSeconds > 0 && !uiState.isTimerRunning) {
                    IconButton(
                        onClick = onResetTimer,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Reset Timer",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(15.dp)
                        )
                    }
                }
            }
        }

        // Freeze Projector Button
        FilledTonalIconButton(
            onClick = onToggleFreeze,
            modifier = Modifier
                .size(48.dp)
                .testTag("freeze_button")
        ) {
            Icon(
                imageVector = Icons.Default.AcUnit,
                contentDescription = if (uiState.isFrozen) "Unfreeze Projector" else "Freeze Projector",
                tint = if (uiState.isFrozen) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // Blackout Projector Button
        FilledTonalIconButton(
            onClick = onToggleBlackout,
            modifier = Modifier
                .size(48.dp)
                .testTag("blackout_button")
        ) {
            Icon(
                imageVector = Icons.Default.VisibilityOff,
                contentDescription = if (uiState.isBlackout) "Resume Projector" else "Blackout Projector",
                tint = if (uiState.isBlackout) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun TalkPacingBar(
    elapsedSeconds: Long,
    targetMinutes: Int,
    currentPage: Int,
    totalPages: Int
) {
    val slideFraction = if (totalPages > 0) (currentPage + 1).toFloat() / totalPages.toFloat() else 0f
    val timeFraction = if (targetMinutes > 0) {
        (elapsedSeconds.toFloat() / (targetMinutes * 60f)).coerceIn(0f, 1f)
    } else 0f

    Column(modifier = Modifier.fillMaxWidth()) {
        LinearProgressIndicator(
            progress = { slideFraction },
            modifier = Modifier
                .fillMaxWidth()
                .height(3.dp),
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.surfaceVariant
        )
        LinearProgressIndicator(
            progress = { timeFraction },
            modifier = Modifier
                .fillMaxWidth()
                .height(2.dp),
            color = when {
                elapsedSeconds >= targetMinutes * 60L -> MaterialTheme.colorScheme.tertiary
                timeFraction >= 0.85f -> AmberTimer
                else -> MaterialTheme.colorScheme.secondary.copy(alpha = 0.7f)
            },
            trackColor = Color.Transparent
        )
    }
}

@Composable
private fun InteractiveSlideStage(
    uiState: PresenterUiState,
    viewModel: PresenterViewModel,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(
            width = if (uiState.activeTool == InteractionTool.LASER) 2.dp else 1.dp,
            color = when {
                uiState.isBlackout -> MaterialTheme.colorScheme.tertiary
                uiState.isFrozen -> MaterialTheme.colorScheme.primary
                uiState.activeTool == InteractionTool.LASER -> Color(uiState.laserColor.colorHex)
                uiState.activeTool == InteractionTool.PEN || uiState.activeTool == InteractionTool.HIGHLIGHTER ->
                    Color(uiState.penColor.colorHex)
                else -> MaterialTheme.colorScheme.outline
            }
        ),
        color = Color.Black,
        modifier = modifier
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            StageSlideCanvas(
                slideBitmap = uiState.currentSlideBitmap,
                strokes = uiState.currentSlideStrokes,
                activeStroke = uiState.activeStroke,
                laserState = uiState.laserState,
                activeTool = uiState.activeTool,
                links = uiState.currentSlideLinks,
                showLinkHints = uiState.showLinkHints,
                isBlackoutOverlay = false,
                interactive = true,
                onNextSlide = viewModel::nextSlide,
                onPreviousSlide = viewModel::previousSlide,
                onLinkClick = viewModel::onPdfLinkClicked,
                onLaserMove = viewModel::updateLaserPointer,
                onLaserEnd = viewModel::endLaserPointer,
                onStrokeStart = viewModel::startStroke,
                onStrokeMove = viewModel::appendStrokePoint,
                onStrokeEnd = viewModel::finishStroke,
                modifier = Modifier.fillMaxSize()
            )

            // Subtle Status Chips overlaid in top-left corner
            Row(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (uiState.isFrozen) {
                    StatusBadge(
                        text = "PROJECTOR FROZEN ON SLIDE ${(uiState.frozenPageIndex ?: 0) + 1}",
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.primary
                    )
                }
                if (uiState.isBlackout) {
                    StatusBadge(
                        text = "AUDIENCE BLACKOUT",
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                        contentColor = MaterialTheme.colorScheme.tertiary
                    )
                }
            }

            // One-tap return chip after following a non-sequential PDF hyperlink
            val jumpBackIdx = uiState.jumpBackPageIndex
            if (jumpBackIdx != null) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.94f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)),
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .clickable { viewModel.returnFromJump() }
                        .testTag("jump_back_pill")
                ) {
                    Text(
                        text = "↩ Slide ${jumpBackIdx + 1}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun StatusBadge(text: String, containerColor: Color, contentColor: Color) {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = containerColor.copy(alpha = 0.92f)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = contentColor,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

@Composable
private fun ToolDockBar(
    uiState: PresenterUiState,
    viewModel: PresenterViewModel
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ToolButton(
                    icon = Icons.Default.Swipe,
                    label = "Swipe",
                    selected = uiState.activeTool == InteractionTool.NAVIGATE,
                    accent = MaterialTheme.colorScheme.primary,
                    onClick = { viewModel.setInteractionTool(InteractionTool.NAVIGATE) },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("tool_navigate")
                )
                ToolButton(
                    icon = Icons.Default.Flare,
                    label = "Laser",
                    selected = uiState.activeTool == InteractionTool.LASER,
                    accent = Color(uiState.laserColor.colorHex),
                    onClick = { viewModel.setInteractionTool(InteractionTool.LASER) },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("tool_laser")
                )
                ToolButton(
                    icon = Icons.Default.BorderColor,
                    label = "Pen",
                    selected = uiState.activeTool == InteractionTool.PEN,
                    accent = Color(uiState.penColor.colorHex),
                    onClick = { viewModel.setInteractionTool(InteractionTool.PEN) },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("tool_pen")
                )
                ToolButton(
                    icon = Icons.Default.Highlight,
                    label = "Mark",
                    selected = uiState.activeTool == InteractionTool.HIGHLIGHTER,
                    accent = Color(uiState.penColor.colorHex),
                    onClick = { viewModel.setInteractionTool(InteractionTool.HIGHLIGHTER) },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("tool_highlighter")
                )

                if (uiState.currentSlideStrokes.isNotEmpty()) {
                    IconButton(
                        onClick = viewModel::undoLastStroke,
                        modifier = Modifier.testTag("undo_stroke_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Undo,
                            contentDescription = "Undo stroke"
                        )
                    }
                    IconButton(
                        onClick = viewModel::clearCurrentSlideAnnotations,
                        modifier = Modifier.testTag("clear_strokes_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteSweep,
                            contentDescription = "Clear slide annotations",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }

            // Contextual Sub-row for Navigate Links, Laser, or Pen/Highlighter
            if (uiState.activeTool == InteractionTool.NAVIGATE && uiState.currentSlideLinks.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp, start = 4.dp, end = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    val count = uiState.currentSlideLinks.size
                    Text(
                        text = "$count PDF ${if (count == 1) "link" else "links"} active · Tap link or swipe",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )
                    FilterChip(
                        selected = uiState.showLinkHints,
                        onClick = viewModel::toggleLinkHints,
                        label = {
                            Text(
                                text = "Link Boxes",
                                style = MaterialTheme.typography.labelSmall
                            )
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Link,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp)
                            )
                        },
                        modifier = Modifier.testTag("toggle_link_hints_chip")
                    )
                }
            } else if (uiState.activeTool == InteractionTool.LASER) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp, start = 4.dp, end = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Touch & drag slide for live beam",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )
                    LaserColor.entries.forEach { lc ->
                        val selected = uiState.laserColor == lc
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(Color(lc.colorHex))
                                .border(
                                    width = if (selected) 2.5.dp else 1.dp,
                                    color = if (selected) Color.White else Color.Transparent,
                                    shape = CircleShape
                                )
                                .clickable { viewModel.setLaserColor(lc) }
                                .testTag("laser_color_${lc.name.lowercase()}")
                        )
                    }
                }
            } else if (uiState.activeTool == InteractionTool.PEN || uiState.activeTool == InteractionTool.HIGHLIGHTER) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp, start = 4.dp, end = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Ink syncs live to projector",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )
                    PenColor.entries.forEach { pc ->
                        val selected = uiState.penColor == pc
                        Box(
                            modifier = Modifier
                                .size(26.dp)
                                .clip(CircleShape)
                                .background(Color(pc.colorHex))
                                .border(
                                    width = if (selected) 2.5.dp else 1.dp,
                                    color = if (selected) Color.White else MaterialTheme.colorScheme.outline,
                                    shape = CircleShape
                                )
                                .clickable { viewModel.setPenColor(pc) }
                                .testTag("pen_color_${pc.name.lowercase()}")
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ToolButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    selected: Boolean,
    accent: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = if (selected) accent.copy(alpha = 0.16f) else Color.Transparent,
        border = if (selected) BorderStroke(1.dp, accent.copy(alpha = 0.7f)) else null,
        modifier = modifier
            .height(48.dp)
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (selected) accent else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = if (selected) accent else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun NextSlideCard(
    uiState: PresenterUiState,
    onNextClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val hasNext = uiState.currentPageIndex + 1 < uiState.totalPages
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier
            .clickable(enabled = hasNext, onClick = onNextClick)
            .testTag("next_slide_preview_card")
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = if (hasNext) "NEXT · ${uiState.currentPageIndex + 2}" else "END OF DECK",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.Black),
                contentAlignment = Alignment.Center
            ) {
                val nextBmp = uiState.nextSlideBitmap
                if (hasNext && nextBmp != null && !nextBmp.isRecycled) {
                    Image(
                        bitmap = nextBmp.asImageBitmap(),
                        contentDescription = "Next slide preview",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Text(
                        text = if (hasNext) "Rendering…" else "Final Slide",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun PresenterNotesPanel(
    uiState: PresenterUiState,
    onFontScaleDelta: (Float) -> Unit,
    onEditNote: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier.testTag("presenter_notes_panel")
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (uiState.beamerNotesBitmap != null) "BEAMER NOTES" else "SPEAKER NOTES",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (uiState.beamerNotesBitmap == null) {
                        Text(
                            text = "A-",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .clickable { onFontScaleDelta(-2f) }
                                .padding(horizontal = 6.dp, vertical = 4.dp)
                        )
                        Text(
                            text = "A+",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .clickable { onFontScaleDelta(2f) }
                                .padding(horizontal = 6.dp, vertical = 4.dp)
                        )
                    }
                    IconButton(
                        onClick = onEditNote,
                        modifier = Modifier
                            .size(36.dp)
                            .testTag("edit_note_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Edit slide note",
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 10.dp)
                    .padding(bottom = 10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val beamerBmp = uiState.beamerNotesBitmap
                if (beamerBmp != null && !beamerBmp.isRecycled) {
                    Image(
                        bitmap = beamerBmp.asImageBitmap(),
                        contentDescription = "Compiled Beamer notes page",
                        contentScale = ContentScale.FillWidth,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                    )
                }

                if (uiState.currentNoteText.isNotBlank()) {
                    Text(
                        text = uiState.currentNoteText,
                        fontSize = uiState.preferences.noteFontSizeSp.sp,
                        lineHeight = (uiState.preferences.noteFontSizeSp * 1.4f).sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                } else if (beamerBmp == null) {
                    Text(
                        text = "No notes for Slide ${uiState.currentPageIndex + 1}. Tap the pencil icon to add speaking cues.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun BottomTransportBar(
    currentPageIndex: Int,
    totalPages: Int,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onOpenGrid: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        OutlinedButton(
            onClick = onPrevious,
            enabled = currentPageIndex > 0,
            contentPadding = PaddingValues(horizontal = 14.dp),
            modifier = Modifier
                .height(52.dp)
                .weight(0.28f)
                .testTag("prev_slide_button")
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                contentDescription = "Previous slide"
            )
            Text("Prev", maxLines = 1)
        }

        OutlinedButton(
            onClick = onOpenGrid,
            contentPadding = PaddingValues(horizontal = 10.dp),
            modifier = Modifier
                .height(52.dp)
                .weight(0.32f)
                .testTag("slide_grid_button")
        ) {
            Icon(
                imageVector = Icons.Default.GridView,
                contentDescription = "Open slide grid",
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "${currentPageIndex + 1}/$totalPages",
                style = MaterialTheme.typography.labelLarge
            )
        }

        Button(
            onClick = onNext,
            enabled = currentPageIndex < totalPages - 1,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ),
            contentPadding = PaddingValues(horizontal = 16.dp),
            modifier = Modifier
                .height(52.dp)
                .weight(0.40f)
                .testTag("next_slide_button")
        ) {
            Text("Next", fontWeight = FontWeight.Bold, maxLines = 1)
            Spacer(modifier = Modifier.width(4.dp))
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = "Next slide"
            )
        }
    }
}

private fun formatElapsedTime(seconds: Long): String {
    val mins = seconds / 60
    val secs = seconds % 60
    return String.format(Locale.US, "%02d:%02d", mins, secs)
}
