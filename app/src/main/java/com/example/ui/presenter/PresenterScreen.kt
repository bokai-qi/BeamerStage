package com.example.ui.presenter

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.HapticFeedbackConstants
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoFixNormal
import androidx.compose.material.icons.filled.BorderColor
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.CastConnected
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DashboardCustomize
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Flare
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Highlight
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Swipe
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.ZoomOutMap
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.model.ChalkboardStyle
import com.example.model.InteractionTool
import com.example.model.LaserColor
import com.example.model.PenColor
import com.example.model.PenStrokeWidth
import com.example.model.TimerDisplayMode
import com.example.ui.components.StageSlideCanvas
import com.example.ui.theme.AmberTimer
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

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
    var showExitConfirm by remember { mutableStateOf(false) }

    val handleRequestExit = {
        when {
            uiState.chalkboardState.isActive -> viewModel.toggleChalkboard()
            uiState.viewportState.isZoomed -> viewModel.resetViewport()
            uiState.connectedDisplay != null || uiState.elapsedSeconds > 0L -> {
                showExitConfirm = true
            }
            else -> onNavigateBack()
        }
    }

    BackHandler(onBack = handleRequestExit)

    // Keep screen awake while presenting if preference enabled
    DisposableEffect(uiState.preferences.keepScreenOn) {
        val previous = view.keepScreenOn
        view.keepScreenOn = uiState.preferences.keepScreenOn
        onDispose { view.keepScreenOn = previous }
    }

    // Tactile haptic feedback dispatcher
    LaunchedEffect(viewModel) {
        viewModel.hapticEvents.collect { cue ->
            triggerHapticCue(context, view, cue)
        }
    }

    LaunchedEffect(showSlideGrid) {
        if (showSlideGrid) {
            viewModel.loadAllGridThumbnails()
        } else {
            viewModel.clearGridThumbnails()
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
            val containerMaxHeight = maxHeight
            val isHorizontal = maxWidth > maxHeight
            val isCompactHeader = maxWidth < 560.dp && !isHorizontal
            val simpleMode = uiState.preferences.simplePresentingMode
            val bmp = uiState.currentSlideBitmap
            val slideAspectRatio = if (!uiState.chalkboardState.isActive && bmp != null && !bmp.isRecycled && bmp.height > 0) {
                (bmp.width.toFloat() / bmp.height.toFloat()).coerceIn(1.15f, 2.35f)
            } else {
                16f / 9f
            }

            Column(modifier = Modifier.fillMaxSize()) {
                TopStageControlBar(
                    uiState = uiState,
                    isCompactHeader = isCompactHeader,
                    onBack = handleRequestExit,
                    onOpenDisplayConfig = { showDisplayConfig = true },
                    onToggleTimer = viewModel::toggleTimer,
                    onResetTimer = viewModel::resetTimer,
                    onCycleTimerMode = viewModel::cycleTimerDisplayMode,
                    onToggleChalkboard = viewModel::toggleChalkboard,
                    onToggleSimpleMode = viewModel::toggleSimplePresentingMode,
                    onToggleFreeze = viewModel::toggleFreeze,
                    onToggleBlackout = viewModel::toggleBlackout
                )

                if (uiState.preferences.showTimerBar) {
                    TalkPacingBar(
                        elapsedSeconds = uiState.elapsedSeconds,
                        targetMinutes = uiState.targetMinutes,
                        currentPage = uiState.currentPageIndex,
                        totalPages = uiState.totalPages
                    )
                }

                if (simpleMode && isHorizontal) {
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        VerticalToolRail(
                            uiState = uiState,
                            viewModel = viewModel,
                            modifier = Modifier.fillMaxHeight()
                        )
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight(),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            InteractiveSlideStage(
                                uiState = uiState,
                                viewModel = viewModel,
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth()
                            )
                            BottomTransportBar(
                                uiState = uiState,
                                compact = true,
                                onPrevious = viewModel::previousSlide,
                                onNext = viewModel::nextSlide,
                                onOpenGrid = { showSlideGrid = true }
                            )
                        }
                    }
                } else if (simpleMode) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        InteractiveSlideStage(
                            uiState = uiState,
                            viewModel = viewModel,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                        )
                        HorizontalToolDock(
                            uiState = uiState,
                            viewModel = viewModel
                        )
                        BottomTransportBar(
                            uiState = uiState,
                            compact = false,
                            onPrevious = viewModel::previousSlide,
                            onNext = viewModel::nextSlide,
                            onOpenGrid = { showSlideGrid = true }
                        )
                    }
                } else if (isHorizontal) {
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        VerticalToolRail(
                            uiState = uiState,
                            viewModel = viewModel,
                            modifier = Modifier.fillMaxHeight()
                        )

                        InteractiveSlideStage(
                            uiState = uiState,
                            viewModel = viewModel,
                            modifier = Modifier
                                .weight(0.65f)
                                .fillMaxHeight()
                        )

                        Column(
                            modifier = Modifier
                                .weight(0.35f)
                                .fillMaxHeight(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            NextSlideCard(
                                uiState = uiState,
                                onNextClick = viewModel::nextSlide,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(108.dp)
                            )
                            PresenterNotesPanel(
                                uiState = uiState,
                                isPortraitSplit = false,
                                onSelectPortraitWeight = viewModel::setPortraitNotesWeight,
                                onFontScaleDelta = viewModel::adjustNoteFontSize,
                                onEditNote = { showNoteEditor = true },
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth()
                            )
                            BottomTransportBar(
                                uiState = uiState,
                                compact = true,
                                onPrevious = viewModel::previousSlide,
                                onNext = viewModel::nextSlide,
                                onOpenGrid = { showSlideGrid = true }
                            )
                        }
                    }
                } else {
                    val notesWeight = uiState.preferences.portraitNotesWeight.coerceIn(0.42f, 0.85f)
                    val nextWeight = (1f - notesWeight).coerceAtLeast(0.15f)
                    val maxStageHeight = containerMaxHeight * 0.46f

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        InteractiveSlideStage(
                            uiState = uiState,
                            viewModel = viewModel,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = maxStageHeight)
                                .aspectRatio(slideAspectRatio)
                                .align(Alignment.CenterHorizontally)
                        )

                        HorizontalToolDock(
                            uiState = uiState,
                            viewModel = viewModel
                        )

                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            PresenterNotesPanel(
                                uiState = uiState,
                                isPortraitSplit = true,
                                onSelectPortraitWeight = viewModel::setPortraitNotesWeight,
                                onFontScaleDelta = viewModel::adjustNoteFontSize,
                                onEditNote = { showNoteEditor = true },
                                modifier = Modifier
                                    .weight(notesWeight)
                                    .fillMaxHeight()
                            )
                            if (notesWeight < 0.82f) {
                                NextSlideCard(
                                    uiState = uiState,
                                    onNextClick = viewModel::nextSlide,
                                    modifier = Modifier
                                        .weight(nextWeight)
                                        .fillMaxHeight()
                                )
                            }
                        }

                        BottomTransportBar(
                            uiState = uiState,
                            compact = false,
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
            outlines = uiState.outlines,
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
            simplePresentingMode = uiState.preferences.simplePresentingMode,
            projectedBitmap = uiState.projectedBitmap,
            projectedStrokes = uiState.projectedStrokes,
            laserState = uiState.laserState,
            spotlightState = uiState.spotlightState,
            viewportState = uiState.viewportState,
            chalkboardState = uiState.projectedChalkboardState,
            isBlackout = uiState.isBlackout,
            isFrozen = uiState.isFrozen,
            isExportingPdf = uiState.isExportingPdf,
            onSelectLayoutMode = viewModel::setLayoutMode,
            onSelectTargetMinutes = viewModel::updateTargetDuration,
            onToggleSimplePresentingMode = viewModel::setSimplePresentingMode,
            onExportAnnotatedPdf = {
                viewModel.exportAnnotatedPdf(context) { uri ->
                    sharePdfUri(context, uri, uiState.deck?.title ?: "Slides")
                }
            },
            onDismiss = { showDisplayConfig = false }
        )
    }

    if (showNoteEditor) {
        NoteEditorSheet(
            pageIndex = uiState.currentPageIndex,
            initialText = uiState.currentNoteText,
            onActiveChanged = { active -> viewModel.isModalTextInputActive = active },
            onSave = viewModel::saveNoteForCurrentSlide,
            onDismiss = { showNoteEditor = false }
        )
    }

    if (showExitConfirm) {
        AlertDialog(
            onDismissRequest = { showExitConfirm = false },
            title = { Text("End Presentation Session?") },
            text = {
                Text(
                    text = if (uiState.connectedDisplay != null) {
                        "Your slides are currently live on ${uiState.connectedDisplay?.name}. Exiting will close the projector output."
                    } else {
                        "Your presentation timer (${formatElapsedTime(uiState.elapsedSeconds)}) is active. Exit back to the Library?"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showExitConfirm = false
                        onNavigateBack()
                    },
                    modifier = Modifier.testTag("confirm_exit_presenter")
                ) {
                    Text("End Session")
                }
            },
            dismissButton = {
                TextButton(onClick = { showExitConfirm = false }) {
                    Text("Keep Presenting")
                }
            }
        )
    }

    val exportErr = uiState.exportMessage
    if (exportErr != null) {
        AlertDialog(
            onDismissRequest = viewModel::dismissExportMessage,
            title = { Text("Export Notice") },
            text = { Text(exportErr) },
            confirmButton = {
                Button(onClick = viewModel::dismissExportMessage) {
                    Text("OK")
                }
            }
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
    isCompactHeader: Boolean,
    onBack: () -> Unit,
    onOpenDisplayConfig: () -> Unit,
    onToggleTimer: () -> Unit,
    onResetTimer: () -> Unit,
    onCycleTimerMode: () -> Unit,
    onToggleChalkboard: () -> Unit,
    onToggleSimpleMode: () -> Unit,
    onToggleFreeze: () -> Unit,
    onToggleBlackout: () -> Unit
) {
    val targetSeconds = uiState.targetMinutes * 60L
    val timerRatio = if (targetSeconds > 0) uiState.elapsedSeconds.toFloat() / targetSeconds.toFloat() else 0f
    val timerMode = uiState.preferences.timerDisplayMode
    val timerColor = when {
        timerMode == TimerDisplayMode.CLOCK -> MaterialTheme.colorScheme.onSurface
        timerRatio >= 1.0f -> MaterialTheme.colorScheme.tertiary
        timerRatio >= 0.85f -> AmberTimer
        uiState.isTimerRunning -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurface
    }

    val primaryTimeText = when (timerMode) {
        TimerDisplayMode.ELAPSED -> formatElapsedTime(uiState.elapsedSeconds)
        TimerDisplayMode.REMAINING -> formatRemainingTime(targetSeconds - uiState.elapsedSeconds)
        TimerDisplayMode.CLOCK -> uiState.wallClockText.ifBlank { formatElapsedTime(uiState.elapsedSeconds) }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier
                    .size(48.dp)
                    .testTag("back_to_library_button")
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
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
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
                            text = uiState.connectedDisplay?.resolutionLabel ?: "Stage Setup",
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
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            // Timer + Countdown + Wall Clock Pill
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                modifier = Modifier
                    .height(48.dp)
                    .testTag("timer_toggle_button")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .clickable(onClick = onToggleTimer)
                            .padding(horizontal = 4.dp, vertical = 6.dp)
                    ) {
                        Icon(
                            imageVector = if (uiState.isTimerRunning) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (uiState.isTimerRunning) "Pause Timer" else "Start Timer",
                            tint = timerColor,
                            modifier = Modifier.size(17.dp)
                        )
                        Text(
                            text = primaryTimeText,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = timerColor,
                            maxLines = 1
                        )
                    }

                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.surface,
                        modifier = Modifier
                            .clickable(onClick = onCycleTimerMode)
                            .testTag("timer_mode_chip")
                    ) {
                        Text(
                            text = timerMode.label.uppercase(Locale.US),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp)
                        )
                    }

                    if (uiState.elapsedSeconds > 0 && !uiState.isTimerRunning) {
                        IconButton(
                            onClick = onResetTimer,
                            modifier = Modifier.size(32.dp)
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

            if (!isCompactHeader) {
                StageQuickActionButtons(
                    uiState = uiState,
                    compactRow = false,
                    onToggleChalkboard = onToggleChalkboard,
                    onToggleFreeze = onToggleFreeze,
                    onToggleBlackout = onToggleBlackout,
                    onToggleSimpleMode = onToggleSimpleMode
                )
            }
        }

        if (isCompactHeader) {
            StageQuickActionButtons(
                uiState = uiState,
                compactRow = true,
                onToggleChalkboard = onToggleChalkboard,
                onToggleFreeze = onToggleFreeze,
                onToggleBlackout = onToggleBlackout,
                onToggleSimpleMode = onToggleSimpleMode
            )
        }
    }
}

@Composable
private fun StageQuickActionButtons(
    uiState: PresenterUiState,
    compactRow: Boolean,
    onToggleChalkboard: () -> Unit,
    onToggleFreeze: () -> Unit,
    onToggleBlackout: () -> Unit,
    onToggleSimpleMode: () -> Unit
) {
    val simpleActive = uiState.preferences.simplePresentingMode
    val boardActive = uiState.chalkboardState.isActive

    if (compactRow) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            StageTogglePill(
                icon = Icons.Default.DashboardCustomize,
                label = "Board",
                active = boardActive,
                activeColor = MaterialTheme.colorScheme.secondary,
                onClick = onToggleChalkboard,
                modifier = Modifier
                    .weight(1f)
                    .testTag("chalkboard_button")
            )
            StageTogglePill(
                icon = Icons.Default.AcUnit,
                label = "Freeze",
                active = uiState.isFrozen,
                activeColor = MaterialTheme.colorScheme.primary,
                onClick = onToggleFreeze,
                modifier = Modifier
                    .weight(1f)
                    .testTag("freeze_button")
            )
            StageTogglePill(
                icon = Icons.Default.VisibilityOff,
                label = "Black",
                active = uiState.isBlackout,
                activeColor = MaterialTheme.colorScheme.tertiary,
                onClick = onToggleBlackout,
                modifier = Modifier
                    .weight(1f)
                    .testTag("blackout_button")
            )
            StageTogglePill(
                icon = if (simpleActive) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                label = "Focus",
                active = simpleActive,
                activeColor = MaterialTheme.colorScheme.primary,
                onClick = onToggleSimpleMode,
                modifier = Modifier
                    .weight(1f)
                    .testTag("simple_mode_button")
            )
        }
    } else {
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            FilledTonalIconButton(
                onClick = onToggleChalkboard,
                modifier = Modifier
                    .size(48.dp)
                    .testTag("chalkboard_button")
            ) {
                Icon(
                    imageVector = Icons.Default.DashboardCustomize,
                    contentDescription = if (boardActive) "Exit Chalkboard" else "Open Chalkboard",
                    tint = if (boardActive) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            FilledTonalIconButton(
                onClick = onToggleSimpleMode,
                modifier = Modifier
                    .size(48.dp)
                    .testTag("simple_mode_button")
            ) {
                Icon(
                    imageVector = if (simpleActive) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                    contentDescription = if (simpleActive) "Exit Focus Mode" else "Focus Presenting Mode",
                    tint = if (simpleActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
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
}

@Composable
private fun StageTogglePill(
    icon: ImageVector,
    label: String,
    active: Boolean,
    activeColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = if (active) activeColor.copy(alpha = 0.18f) else MaterialTheme.colorScheme.surfaceVariant,
        border = BorderStroke(
            1.dp,
            if (active) activeColor.copy(alpha = 0.75f) else MaterialTheme.colorScheme.outline
        ),
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
                tint = if (active) activeColor else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = if (active) activeColor else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
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
    val isPenOrMark = uiState.activeTool == InteractionTool.PEN ||
        uiState.activeTool == InteractionTool.HIGHLIGHTER
    val stageMatColor = if (uiState.chalkboardState.isActive) {
        Color(uiState.chalkboardState.style.bgHex)
    } else {
        MaterialTheme.colorScheme.surfaceContainerLowest
    }

    Surface(
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(
            width = if (uiState.activeTool == InteractionTool.LASER || uiState.chalkboardState.isActive) 2.dp else 1.dp,
            color = when {
                uiState.isBlackout -> MaterialTheme.colorScheme.tertiary
                uiState.chalkboardState.isActive -> MaterialTheme.colorScheme.secondary
                uiState.isFrozen -> MaterialTheme.colorScheme.primary
                uiState.activeTool == InteractionTool.LASER -> Color(uiState.laserColor.colorHex)
                uiState.activeTool == InteractionTool.SPOTLIGHT -> MaterialTheme.colorScheme.primary
                isPenOrMark && uiState.isEraserActive -> MaterialTheme.colorScheme.primary
                isPenOrMark -> Color(uiState.penColor.colorHex)
                else -> MaterialTheme.colorScheme.outlineVariant
            }
        ),
        color = stageMatColor,
        modifier = modifier
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val stageW = constraints.maxWidth.toFloat().coerceAtLeast(1f)
            val stageH = constraints.maxHeight.toFloat().coerceAtLeast(1f)
            val bmp = uiState.currentSlideBitmap
            val aspectW = if (bmp != null && !bmp.isRecycled && bmp.width > 0) bmp.width.toFloat() else stageW
            val aspectH = if (bmp != null && !bmp.isRecycled && bmp.height > 0) bmp.height.toFloat() else stageH
            val fitScale = min(stageW / aspectW, stageH / aspectH)
            val coverScale = max(stageW / aspectW, stageH / aspectH)
            val fillZoomRatio = if (fitScale > 0f) (coverScale / fitScale).coerceIn(1f, 5f) else 1f
            val canFillStage = !uiState.chalkboardState.isActive && fillZoomRatio > 1.07f

            StageSlideCanvas(
                slideBitmap = uiState.currentSlideBitmap,
                strokes = uiState.currentSlideStrokes,
                activeStroke = uiState.activeStroke,
                laserState = uiState.laserState,
                activeTool = uiState.activeTool,
                spotlightState = uiState.spotlightState,
                viewportState = uiState.viewportState,
                chalkboardState = uiState.chalkboardState,
                links = uiState.currentSlideLinks,
                showLinkHints = uiState.showLinkHints,
                isEraserActive = uiState.isEraserActive,
                eraserPosition = uiState.eraserPosition,
                isBlackoutOverlay = false,
                backdropColor = stageMatColor,
                interactive = true,
                onNextSlide = viewModel::nextSlide,
                onPreviousSlide = viewModel::previousSlide,
                onLinkClick = viewModel::onPdfLinkClicked,
                onLaserMove = viewModel::updateLaserPointer,
                onLaserEnd = viewModel::endLaserPointer,
                onSpotlightMove = viewModel::updateSpotlight,
                onSpotlightEnd = viewModel::endSpotlight,
                onStrokeStart = viewModel::startStroke,
                onStrokeMove = viewModel::appendStrokePoint,
                onSnapStrokeToLine = viewModel::snapActiveStrokeToLine,
                onCancelActiveStroke = viewModel::cancelActiveStroke,
                onStrokeEnd = viewModel::finishStroke,
                onEraseStart = viewModel::startErase,
                onEraseMove = viewModel::moveErase,
                onEraseEnd = viewModel::endErase,
                onViewportChange = viewModel::setViewport,
                modifier = Modifier.fillMaxSize()
            )

            // Status Badges overlaid in top-left corner
            Row(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (uiState.chalkboardState.isActive) {
                    StatusBadge(
                        text = "BOARD ${uiState.chalkboardState.boardIndex + 1}/${uiState.chalkboardState.totalBoards}",
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.secondary
                    )
                }
                if (uiState.isFrozen) {
                    StatusBadge(
                        text = "FROZEN · SLIDE ${(uiState.frozenPageIndex ?: 0) + 1}",
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.primary
                    )
                }
                if (uiState.isBlackout) {
                    StatusBadge(
                        text = "BLACKOUT",
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                        contentColor = MaterialTheme.colorScheme.tertiary
                    )
                }
                if (isPenOrMark && uiState.isEraserActive) {
                    StatusBadge(
                        text = "ERASER",
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.primary
                    )
                }
            }

            // Top-right: Jump-back pill after following a PDF hyperlink
            val jumpBackIdx = uiState.jumpBackPageIndex
            if (jumpBackIdx != null && !uiState.chalkboardState.isActive) {
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

            // Bottom-right: Live Zoom / Stage Fill pill + Projector Sync toggle + Reset
            if (!uiState.chalkboardState.isActive && (uiState.viewportState.isZoomed || canFillStage)) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                    border = BorderStroke(
                        1.dp,
                        if (uiState.viewportState.isZoomed) {
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                        } else {
                            MaterialTheme.colorScheme.outlineVariant
                        }
                    ),
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (uiState.viewportState.isZoomed) {
                            Text(
                                text = String.format(Locale.US, "%.1f×", uiState.viewportState.scale),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = if (uiState.viewportState.mirrorToProjector) "Cast: ON" else "Cast: OFF",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (uiState.viewportState.mirrorToProjector) {
                                    MaterialTheme.colorScheme.secondary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .clickable { viewModel.toggleMirrorZoomToProjector() }
                                    .padding(horizontal = 4.dp, vertical = 2.dp)
                                    .testTag("toggle_mirror_zoom")
                            )
                            if (canFillStage && abs(uiState.viewportState.scale - fillZoomRatio) > 0.08f) {
                                Text(
                                    text = "Fill",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .clickable { viewModel.setViewport(fillZoomRatio, 0f, 0f) }
                                        .padding(horizontal = 4.dp, vertical = 2.dp)
                                        .testTag("fill_stage_zoom_pill")
                                )
                            }
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .clickable { viewModel.resetViewport() }
                                    .padding(horizontal = 4.dp, vertical = 2.dp)
                                    .testTag("reset_zoom_pill")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ZoomOutMap,
                                    contentDescription = "Reset Zoom",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(3.dp))
                                Text(
                                    text = "1×",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        } else if (canFillStage) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .clickable { viewModel.setViewport(fillZoomRatio, 0f, 0f) }
                                    .padding(horizontal = 4.dp, vertical = 2.dp)
                                    .testTag("fill_stage_zoom_pill")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ZoomOutMap,
                                    contentDescription = "Fill viewer zone",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(13.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Fill",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            // Bottom overlay when Chalkboard is active: Board background styles + Add Board Page + Exit
            if (uiState.chalkboardState.isActive) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.93f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        ChalkboardStyle.entries.forEach { style ->
                            val selected = uiState.chalkboardState.style == style
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = if (selected) {
                                    MaterialTheme.colorScheme.secondaryContainer
                                } else {
                                    Color.Transparent
                                },
                                border = if (selected) {
                                    BorderStroke(1.dp, MaterialTheme.colorScheme.secondary)
                                } else null,
                                modifier = Modifier
                                    .clickable { viewModel.setChalkboardStyle(style) }
                                    .testTag("chalkboard_style_${style.name.lowercase()}")
                            ) {
                                Text(
                                    text = style.label,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (selected) {
                                        MaterialTheme.colorScheme.secondary
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }

                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier
                                .clickable { viewModel.addChalkboardPage() }
                                .testTag("add_chalkboard_page_button")
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = "New Board Page",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(2.dp))
                                Text(
                                    text = "Page",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }

                        IconButton(
                            onClick = viewModel::toggleChalkboard,
                            modifier = Modifier
                                .size(28.dp)
                                .testTag("exit_chalkboard_pill")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Return to Slide",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
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

/**
 * Compact horizontal toolbar used in vertical/portrait orientation.
 * Uses expanding pills (icon + label on active tool, icon-only on inactive tools)
 * so Swipe, Laser, and Spotlight fit their contextual controls on the same single row.
 */
@Composable
private fun HorizontalToolDock(
    uiState: PresenterUiState,
    viewModel: PresenterViewModel
) {
    val isPenOrMark = uiState.activeTool == InteractionTool.PEN ||
        uiState.activeTool == InteractionTool.HIGHLIGHTER

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("horizontal_tool_dock")
    ) {
        Column(modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CompactHorizontalToolPill(
                    icon = Icons.Default.Swipe,
                    label = "Swipe",
                    selected = uiState.activeTool == InteractionTool.NAVIGATE,
                    accent = MaterialTheme.colorScheme.primary,
                    onClick = { viewModel.setInteractionTool(InteractionTool.NAVIGATE) },
                    modifier = Modifier.testTag("tool_navigate")
                )
                CompactHorizontalToolPill(
                    icon = Icons.Default.Flare,
                    label = "Laser",
                    selected = uiState.activeTool == InteractionTool.LASER,
                    accent = Color(uiState.laserColor.colorHex),
                    onClick = { viewModel.setInteractionTool(InteractionTool.LASER) },
                    modifier = Modifier.testTag("tool_laser")
                )
                CompactHorizontalToolPill(
                    icon = Icons.Default.CenterFocusStrong,
                    label = "Spot",
                    selected = uiState.activeTool == InteractionTool.SPOTLIGHT,
                    accent = MaterialTheme.colorScheme.primary,
                    onClick = { viewModel.setInteractionTool(InteractionTool.SPOTLIGHT) },
                    modifier = Modifier.testTag("tool_spotlight")
                )
                CompactHorizontalToolPill(
                    icon = Icons.Default.BorderColor,
                    label = "Pen",
                    selected = uiState.activeTool == InteractionTool.PEN,
                    accent = Color(uiState.penColor.colorHex),
                    onClick = { viewModel.setInteractionTool(InteractionTool.PEN) },
                    modifier = Modifier.testTag("tool_pen")
                )
                CompactHorizontalToolPill(
                    icon = Icons.Default.Highlight,
                    label = "Mark",
                    selected = uiState.activeTool == InteractionTool.HIGHLIGHTER,
                    accent = Color(uiState.penColor.colorHex),
                    onClick = { viewModel.setInteractionTool(InteractionTool.HIGHLIGHTER) },
                    modifier = Modifier.testTag("tool_highlighter")
                )

                // Inline contextual controls for Swipe Links, Laser colors, or Spotlight radius
                if (uiState.activeTool == InteractionTool.NAVIGATE &&
                    !uiState.chalkboardState.isActive &&
                    uiState.currentSlideLinks.isNotEmpty()
                ) {
                    VerticalDivider(modifier = Modifier.height(22.dp).padding(horizontal = 2.dp))
                    MiniToggleChip(
                        selected = uiState.showLinkHints,
                        label = "${uiState.currentSlideLinks.size}",
                        icon = Icons.Default.Link,
                        contentDescription = "Toggle PDF link highlights",
                        onClick = viewModel::toggleLinkHints,
                        modifier = Modifier.testTag("toggle_link_hints_chip")
                    )
                } else if (uiState.activeTool == InteractionTool.LASER) {
                    VerticalDivider(modifier = Modifier.height(22.dp).padding(horizontal = 2.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        LaserColor.entries.forEach { lc ->
                            val selected = uiState.laserColor == lc
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(CircleShape)
                                    .clickable { viewModel.setLaserColor(lc) }
                                    .testTag("laser_color_${lc.name.lowercase()}")
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(20.dp)
                                        .clip(CircleShape)
                                        .background(Color(lc.colorHex))
                                        .border(
                                            width = if (selected) 2.5.dp else 1.dp,
                                            color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline,
                                            shape = CircleShape
                                        )
                                )
                            }
                        }
                    }
                } else if (uiState.activeTool == InteractionTool.SPOTLIGHT) {
                    VerticalDivider(modifier = Modifier.height(22.dp).padding(horizontal = 2.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        listOf("S" to 0.11f, "M" to 0.16f, "L" to 0.24f).forEach { (lbl, rad) ->
                            val selected = abs(uiState.spotlightState.radiusRatio - rad) < 0.02f
                            MiniOptionPill(
                                label = lbl,
                                selected = selected,
                                onClick = { viewModel.setSpotlightRadius(rad) }
                            )
                        }
                    }
                }

                if (uiState.currentSlideStrokes.isNotEmpty() || uiState.canRedo) {
                    VerticalDivider(modifier = Modifier.height(22.dp).padding(horizontal = 2.dp))
                    IconButton(
                        onClick = viewModel::undoLastStroke,
                        enabled = uiState.currentSlideStrokes.isNotEmpty(),
                        modifier = Modifier
                            .size(36.dp)
                            .testTag("undo_stroke_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Undo,
                            contentDescription = "Undo stroke",
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    if (uiState.canRedo) {
                        IconButton(
                            onClick = viewModel::redoLastStroke,
                            modifier = Modifier
                                .size(36.dp)
                                .testTag("redo_stroke_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Redo,
                                contentDescription = "Redo stroke",
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                    if (uiState.currentSlideStrokes.isNotEmpty()) {
                        IconButton(
                            onClick = viewModel::clearCurrentSlideAnnotations,
                            modifier = Modifier
                                .size(36.dp)
                                .testTag("clear_strokes_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.DeleteSweep,
                                contentDescription = "Clear annotations",
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }

            // Ultra-slim palette strip shown ONLY when Pen or Highlighter is active
            if (isPenOrMark) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(top = 4.dp, start = 2.dp, end = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    MiniToggleChip(
                        selected = uiState.isEraserActive,
                        label = "Erase",
                        icon = Icons.Default.AutoFixNormal,
                        contentDescription = "Toggle Eraser",
                        onClick = viewModel::toggleEraser,
                        modifier = Modifier.testTag("tool_eraser_toggle")
                    )

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        PenStrokeWidth.entries.forEach { sw ->
                            val selected = !uiState.isEraserActive && uiState.penStrokeWidth == sw
                            MiniOptionPill(
                                label = sw.label.take(1),
                                selected = selected,
                                onClick = { viewModel.setPenStrokeWidth(sw) },
                                modifier = Modifier.testTag("stroke_width_${sw.name.lowercase()}")
                            )
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        PenColor.entries.forEach { pc ->
                            val selected = !uiState.isEraserActive && uiState.penColor == pc
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(CircleShape)
                                    .clickable { viewModel.setPenColor(pc) }
                                    .testTag("pen_color_${pc.name.lowercase()}")
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(21.dp)
                                        .clip(CircleShape)
                                        .background(Color(pc.colorHex))
                                        .border(
                                            width = if (selected) 2.5.dp else 1.dp,
                                            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                                            shape = CircleShape
                                        )
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Space-saving vertical tool rail used in horizontal/landscape orientation
 * so the PDF slide stage gets 100% of available vertical height.
 */
@Composable
private fun VerticalToolRail(
    uiState: PresenterUiState,
    viewModel: PresenterViewModel,
    modifier: Modifier = Modifier
) {
    val isPenOrMark = uiState.activeTool == InteractionTool.PEN ||
        uiState.activeTool == InteractionTool.HIGHLIGHTER
    var showInkPaletteMenu by remember { mutableStateOf(false) }

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier
            .width(52.dp)
            .testTag("vertical_tool_rail")
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(vertical = 6.dp, horizontal = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            VerticalRailToolButton(
                icon = Icons.Default.Swipe,
                contentDescription = "Swipe",
                selected = uiState.activeTool == InteractionTool.NAVIGATE,
                accent = MaterialTheme.colorScheme.primary,
                onClick = { viewModel.setInteractionTool(InteractionTool.NAVIGATE) },
                modifier = Modifier.testTag("tool_navigate")
            )
            VerticalRailToolButton(
                icon = Icons.Default.Flare,
                contentDescription = "Laser",
                selected = uiState.activeTool == InteractionTool.LASER,
                accent = Color(uiState.laserColor.colorHex),
                onClick = { viewModel.setInteractionTool(InteractionTool.LASER) },
                modifier = Modifier.testTag("tool_laser")
            )
            VerticalRailToolButton(
                icon = Icons.Default.CenterFocusStrong,
                contentDescription = "Spotlight",
                selected = uiState.activeTool == InteractionTool.SPOTLIGHT,
                accent = MaterialTheme.colorScheme.primary,
                onClick = { viewModel.setInteractionTool(InteractionTool.SPOTLIGHT) },
                modifier = Modifier.testTag("tool_spotlight")
            )
            VerticalRailToolButton(
                icon = Icons.Default.BorderColor,
                contentDescription = "Pen",
                selected = uiState.activeTool == InteractionTool.PEN,
                accent = Color(uiState.penColor.colorHex),
                onClick = { viewModel.setInteractionTool(InteractionTool.PEN) },
                modifier = Modifier.testTag("tool_pen")
            )
            VerticalRailToolButton(
                icon = Icons.Default.Highlight,
                contentDescription = "Highlighter",
                selected = uiState.activeTool == InteractionTool.HIGHLIGHTER,
                accent = Color(uiState.penColor.colorHex),
                onClick = { viewModel.setInteractionTool(InteractionTool.HIGHLIGHTER) },
                modifier = Modifier.testTag("tool_highlighter")
            )

            // Contextual compact controls in rail
            if (uiState.activeTool == InteractionTool.NAVIGATE &&
                !uiState.chalkboardState.isActive &&
                uiState.currentSlideLinks.isNotEmpty()
            ) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp, horizontal = 4.dp))
                VerticalRailToolButton(
                    icon = Icons.Default.Link,
                    contentDescription = "Toggle PDF link highlights",
                    selected = uiState.showLinkHints,
                    accent = MaterialTheme.colorScheme.primary,
                    onClick = viewModel::toggleLinkHints,
                    modifier = Modifier.testTag("toggle_link_hints_chip")
                )
            } else if (uiState.activeTool == InteractionTool.LASER) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp, horizontal = 4.dp))
                LaserColor.entries.forEach { lc ->
                    val selected = uiState.laserColor == lc
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(30.dp)
                            .clip(CircleShape)
                            .clickable { viewModel.setLaserColor(lc) }
                            .testTag("laser_color_${lc.name.lowercase()}")
                    ) {
                        Box(
                            modifier = Modifier
                                .size(18.dp)
                                .clip(CircleShape)
                                .background(Color(lc.colorHex))
                                .border(
                                    width = if (selected) 2.5.dp else 1.dp,
                                    color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline,
                                    shape = CircleShape
                                )
                        )
                    }
                }
            } else if (uiState.activeTool == InteractionTool.SPOTLIGHT) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp, horizontal = 4.dp))
                val curRad = uiState.spotlightState.radiusRatio
                val spotLabel = when {
                    curRad < 0.135f -> "S"
                    curRad < 0.20f -> "M"
                    else -> "L"
                }
                MiniOptionPill(
                    label = spotLabel,
                    selected = true,
                    onClick = {
                        val nextRad = when {
                            curRad < 0.135f -> 0.16f
                            curRad < 0.20f -> 0.24f
                            else -> 0.11f
                        }
                        viewModel.setSpotlightRadius(nextRad)
                    }
                )
            } else if (isPenOrMark) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp, horizontal = 4.dp))
                VerticalRailToolButton(
                    icon = Icons.Default.AutoFixNormal,
                    contentDescription = "Toggle Eraser",
                    selected = uiState.isEraserActive,
                    accent = MaterialTheme.colorScheme.primary,
                    onClick = viewModel::toggleEraser,
                    modifier = Modifier.testTag("tool_eraser_toggle")
                )

                Box {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                        modifier = Modifier
                            .size(width = 42.dp, height = 38.dp)
                            .clickable { showInkPaletteMenu = true }
                            .testTag("ink_palette_button")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(16.dp)
                                    .clip(CircleShape)
                                    .background(Color(uiState.penColor.colorHex))
                                    .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
                            )
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(
                                text = uiState.penStrokeWidth.label.take(1),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    DropdownMenu(
                        expanded = showInkPaletteMenu,
                        onDismissRequest = { showInkPaletteMenu = false }
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                PenColor.entries.forEach { pc ->
                                    val selected = !uiState.isEraserActive && uiState.penColor == pc
                                    Box(
                                        modifier = Modifier
                                            .size(28.dp)
                                            .clip(CircleShape)
                                            .background(Color(pc.colorHex))
                                            .border(
                                                width = if (selected) 2.5.dp else 1.dp,
                                                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                                                shape = CircleShape
                                            )
                                            .clickable {
                                                viewModel.setPenColor(pc)
                                                showInkPaletteMenu = false
                                            }
                                            .testTag("pen_color_${pc.name.lowercase()}")
                                    )
                                }
                            }
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                PenStrokeWidth.entries.forEach { sw ->
                                    val selected = !uiState.isEraserActive && uiState.penStrokeWidth == sw
                                    MiniOptionPill(
                                        label = sw.label,
                                        selected = selected,
                                        onClick = {
                                            viewModel.setPenStrokeWidth(sw)
                                            showInkPaletteMenu = false
                                        },
                                        modifier = Modifier.testTag("stroke_width_${sw.name.lowercase()}")
                                    )
                                }
                            }
                        }
                    }
                }
            }

            if (uiState.currentSlideStrokes.isNotEmpty() || uiState.canRedo) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp, horizontal = 4.dp))
                IconButton(
                    onClick = viewModel::undoLastStroke,
                    enabled = uiState.currentSlideStrokes.isNotEmpty(),
                    modifier = Modifier
                        .size(38.dp)
                        .testTag("undo_stroke_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Undo,
                        contentDescription = "Undo stroke",
                        modifier = Modifier.size(18.dp)
                    )
                }
                if (uiState.canRedo) {
                    IconButton(
                        onClick = viewModel::redoLastStroke,
                        modifier = Modifier
                            .size(38.dp)
                            .testTag("redo_stroke_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Redo,
                            contentDescription = "Redo stroke",
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
                if (uiState.currentSlideStrokes.isNotEmpty()) {
                    IconButton(
                        onClick = viewModel::clearCurrentSlideAnnotations,
                        modifier = Modifier
                            .size(38.dp)
                            .testTag("clear_strokes_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteSweep,
                            contentDescription = "Clear annotations",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CompactHorizontalToolPill(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    accent: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(9.dp),
        color = if (selected) accent.copy(alpha = 0.16f) else Color.Transparent,
        border = if (selected) BorderStroke(1.dp, accent.copy(alpha = 0.72f)) else null,
        modifier = modifier
            .height(38.dp)
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = if (selected) 9.dp else 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (selected) accent else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(17.dp)
            )
            if (selected) {
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = accent,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun VerticalRailToolButton(
    icon: ImageVector,
    contentDescription: String,
    selected: Boolean,
    accent: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = if (selected) accent.copy(alpha = 0.18f) else Color.Transparent,
        border = if (selected) BorderStroke(1.dp, accent.copy(alpha = 0.75f)) else null,
        modifier = modifier
            .size(width = 42.dp, height = 38.dp)
            .clickable(onClick = onClick)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = if (selected) accent else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
private fun MiniToggleChip(
    selected: Boolean,
    label: String,
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = if (selected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceVariant
        },
        border = BorderStroke(
            1.dp,
            if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
        ),
        modifier = modifier
            .height(28.dp)
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(14.dp)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun MiniOptionPill(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = if (selected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceVariant
        },
        border = BorderStroke(
            1.dp,
            if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
        ),
        modifier = modifier
            .height(28.dp)
            .clickable(onClick = onClick)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.padding(horizontal = 8.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
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
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerLowest),
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
    isPortraitSplit: Boolean,
    onSelectPortraitWeight: (Float) -> Unit,
    onFontScaleDelta: (Float) -> Unit,
    onEditNote: () -> Unit,
    modifier: Modifier = Modifier
) {
    var beamerNoteZoom by remember(uiState.currentPageIndex) { mutableFloatStateOf(1f) }
    var beamerNotePan by remember(uiState.currentPageIndex) { mutableStateOf(Offset.Zero) }
    var noteBoxSize by remember { mutableStateOf(IntSize.Zero) }

    val noteTransformState = rememberTransformableState { zoomChange, panChange, _ ->
        val nextZoom = (beamerNoteZoom * zoomChange).coerceIn(1f, 3.5f)
        beamerNoteZoom = nextZoom
        beamerNotePan = if (nextZoom <= 1.01f) {
            Offset.Zero
        } else {
            val maxPanX = ((noteBoxSize.width * (nextZoom - 1f)) / 2f).coerceAtLeast(0f)
            val maxPanY = ((noteBoxSize.height * (nextZoom - 1f)) / 2f).coerceAtLeast(0f)
            Offset(
                x = (beamerNotePan.x + panChange.x).coerceIn(-maxPanX, maxPanX),
                y = (beamerNotePan.y + panChange.y).coerceIn(-maxPanY, maxPanY)
            )
        }
    }

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
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = if (uiState.beamerNotesBitmap != null) "BEAMER NOTES" else "NOTES",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    if (isPortraitSplit) {
                        val curW = uiState.preferences.portraitNotesWeight
                        val nextLabel = when {
                            curW >= 0.82f -> "Full"
                            curW >= 0.68f -> "Wide"
                            else -> "Split"
                        }
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier
                                .clickable {
                                    val nextW = when {
                                        curW < 0.68f -> 0.72f
                                        curW < 0.82f -> 0.85f
                                        else -> 0.58f
                                    }
                                    onSelectPortraitWeight(nextW)
                                }
                                .testTag("cycle_portrait_split_button")
                        ) {
                            Text(
                                text = nextLabel,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (uiState.beamerNotesBitmap != null) {
                        Text(
                            text = if (beamerNoteZoom > 1.05f) "1×" else "1.8×",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .clickable {
                                    if (beamerNoteZoom > 1.05f) {
                                        beamerNoteZoom = 1f
                                        beamerNotePan = Offset.Zero
                                    } else {
                                        beamerNoteZoom = 1.8f
                                    }
                                }
                                .padding(horizontal = 6.dp, vertical = 4.dp)
                                .testTag("beamer_notes_zoom_button")
                        )
                    } else {
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
                    .verticalScroll(rememberScrollState(), enabled = beamerNoteZoom <= 1.05f)
                    .padding(horizontal = 10.dp)
                    .padding(bottom = 10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val beamerBmp = uiState.beamerNotesBitmap
                if (beamerBmp != null && !beamerBmp.isRecycled) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clipToBounds()
                            .onSizeChanged { noteBoxSize = it }
                            .transformable(state = noteTransformState)
                    ) {
                        Image(
                            bitmap = beamerBmp.asImageBitmap(),
                            contentDescription = "Compiled Beamer notes page",
                            contentScale = ContentScale.FillWidth,
                            modifier = Modifier
                                .fillMaxWidth()
                                .graphicsLayer {
                                    scaleX = beamerNoteZoom
                                    scaleY = beamerNoteZoom
                                    translationX = beamerNotePan.x
                                    translationY = beamerNotePan.y
                                }
                        )
                    }
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
    uiState: PresenterUiState,
    compact: Boolean = false,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onOpenGrid: () -> Unit
) {
    val isBoard = uiState.chalkboardState.isActive
    val prevEnabled = if (isBoard) {
        uiState.chalkboardState.boardIndex > 0
    } else {
        uiState.currentPageIndex > 0
    }
    val nextEnabled = if (isBoard) {
        uiState.chalkboardState.boardIndex + 1 < uiState.chalkboardState.totalBoards
    } else {
        uiState.currentPageIndex < uiState.totalPages - 1
    }
    val centerLabel = if (isBoard) {
        "B${uiState.chalkboardState.boardIndex + 1}/${uiState.chalkboardState.totalBoards}"
    } else {
        "${uiState.currentPageIndex + 1}/${uiState.totalPages}"
    }
    val barHeight = if (compact) 48.dp else 50.dp

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        OutlinedButton(
            onClick = onPrevious,
            enabled = prevEnabled,
            contentPadding = PaddingValues(horizontal = if (compact) 10.dp else 14.dp),
            modifier = Modifier
                .height(barHeight)
                .weight(0.28f)
                .testTag("prev_slide_button")
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                contentDescription = "Previous"
            )
            if (!compact) {
                Text("Prev", maxLines = 1)
            }
        }

        OutlinedButton(
            onClick = onOpenGrid,
            contentPadding = PaddingValues(horizontal = 8.dp),
            modifier = Modifier
                .height(barHeight)
                .weight(0.34f)
                .testTag("slide_grid_button")
        ) {
            Icon(
                imageVector = Icons.Default.GridView,
                contentDescription = "Open slide & section overview",
                modifier = Modifier.size(17.dp)
            )
            Spacer(modifier = Modifier.width(5.dp))
            Text(
                text = centerLabel,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1
            )
        }

        Button(
            onClick = onNext,
            enabled = nextEnabled,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ),
            contentPadding = PaddingValues(horizontal = if (compact) 12.dp else 16.dp),
            modifier = Modifier
                .height(barHeight)
                .weight(0.38f)
                .testTag("next_slide_button")
        ) {
            Text("Next", fontWeight = FontWeight.Bold, maxLines = 1)
            Spacer(modifier = Modifier.width(4.dp))
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = "Next"
            )
        }
    }
}

private fun sharePdfUri(context: Context, uri: Uri, title: String) {
    val shareIntent = Intent(Intent.ACTION_SEND).apply {
        type = "application/pdf"
        putExtra(Intent.EXTRA_STREAM, uri)
        putExtra(Intent.EXTRA_SUBJECT, "$title (Annotated)")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    runCatching {
        context.startActivity(Intent.createChooser(shareIntent, "Share Annotated PDF"))
    }
}

private fun triggerHapticCue(context: Context, view: android.view.View, cue: HapticCue) {
    when (cue) {
        HapticCue.STEP, HapticCue.LINK, HapticCue.ERASE -> {
            view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
        }
        HapticCue.SNAP -> {
            view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        }
        HapticCue.TIMER_WARNING, HapticCue.TIMER_EXPIRED -> {
            runCatching {
                val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                    vm?.defaultVibrator
                } else {
                    @Suppress("DEPRECATION")
                    context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                }
                if (vibrator?.hasVibrator() == true) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        val timings = if (cue == HapticCue.TIMER_EXPIRED) {
                            longArrayOf(0L, 90L, 80L, 140L)
                        } else {
                            longArrayOf(0L, 65L, 70L, 65L)
                        }
                        vibrator.vibrate(VibrationEffect.createWaveform(timings, -1))
                    } else {
                        @Suppress("DEPRECATION")
                        vibrator.vibrate(120L)
                    }
                } else {
                    view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                }
            }
        }
    }
}

private fun formatElapsedTime(seconds: Long): String {
    val mins = seconds / 60
    val secs = seconds % 60
    return String.format(Locale.US, "%02d:%02d", mins, secs)
}

private fun formatRemainingTime(remainingSeconds: Long): String {
    val isOvertime = remainingSeconds < 0L
    val absSecs = abs(remainingSeconds)
    val mins = absSecs / 60
    val secs = absSecs % 60
    val prefix = if (isOvertime) "+" else "-"
    return String.format(Locale.US, "%s%02d:%02d", prefix, mins, secs)
}
