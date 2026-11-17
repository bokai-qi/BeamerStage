package com.example.ui.presenter

import android.graphics.Bitmap
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.IosShare
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.model.AnnotationStroke
import com.example.model.BeamerLayoutMode
import com.example.model.ChalkboardState
import com.example.model.ExternalDisplayInfo
import com.example.model.InteractionTool
import com.example.model.LaserPointerState
import com.example.model.PdfOutlineEntry
import com.example.model.SlideViewportState
import com.example.model.SpotlightState
import com.example.ui.components.StageSlideCanvas

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SlideOverviewSheet(
    totalPages: Int,
    currentPageIndex: Int,
    frozenPageIndex: Int?,
    thumbnails: Map<Int, Bitmap?>,
    outlines: List<PdfOutlineEntry>,
    onSelectSlide: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var selectedTab by remember { mutableIntStateOf(0) }

    val activeOutlineIndex = remember(outlines, currentPageIndex) {
        outlines.indexOfLast { it.pageIndex <= currentPageIndex }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Slide & Section Jumper",
                        style = MaterialTheme.typography.titleLarge
                    )
                    Text(
                        text = if (outlines.isNotEmpty()) {
                            "$totalPages frames · ${outlines.size} Beamer sections"
                        } else {
                            "$totalPages frames · Tap any slide to jump"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.testTag("close_slide_grid_button")
                ) {
                    Icon(Icons.Default.Close, contentDescription = "Close slide overview")
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    label = { Text("Frames ($totalPages)") },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.GridView,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    },
                    modifier = Modifier.testTag("overview_tab_frames")
                )
                FilterChip(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    label = { Text("Sections (${outlines.size})") },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.FormatListBulleted,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    },
                    modifier = Modifier.testTag("overview_tab_sections")
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            if (selectedTab == 0) {
                val indices = remember(totalPages) { (0 until totalPages).toList() }
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 144.dp),
                    contentPadding = PaddingValues(vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 460.dp)
                ) {
                    items(indices, key = { it }) { pageIdx ->
                        val isCurrent = pageIdx == currentPageIndex
                        val isFrozenHere = pageIdx == frozenPageIndex
                        val bmp = thumbnails[pageIdx]

                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            border = BorderStroke(
                                width = if (isCurrent) 2.dp else 1.dp,
                                color = when {
                                    isCurrent -> MaterialTheme.colorScheme.primary
                                    isFrozenHere -> MaterialTheme.colorScheme.tertiary
                                    else -> MaterialTheme.colorScheme.outline
                                }
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onSelectSlide(pageIdx)
                                    onDismiss()
                                }
                                .testTag("grid_slide_${pageIdx + 1}")
                        ) {
                            Column(modifier = Modifier.padding(8.dp)) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .aspectRatio(16f / 9f)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color.Black),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (bmp != null && !bmp.isRecycled) {
                                        Image(
                                            bitmap = bmp.asImageBitmap(),
                                            contentDescription = "Slide ${pageIdx + 1}",
                                            contentScale = ContentScale.Fit,
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                    } else {
                                        Text(
                                            text = "${pageIdx + 1}",
                                            style = MaterialTheme.typography.titleMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Slide ${pageIdx + 1}",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                    )
                                    if (isFrozenHere) {
                                        Icon(
                                            imageVector = Icons.Default.AcUnit,
                                            contentDescription = "Frozen on projector",
                                            tint = MaterialTheme.colorScheme.tertiary,
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                if (outlines.isEmpty()) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(18.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "No PDF Section Bookmarks Found",
                                style = MaterialTheme.typography.titleSmall
                            )
                            Text(
                                text = "Compile your LaTeX Beamer deck with \\section{...} or \\appendix (enabled by default with hyperref) to populate instant section bookmarks here.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(vertical = 6.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 460.dp)
                    ) {
                        itemsIndexed(outlines) { idx, entry ->
                            val isActiveSection = idx == activeOutlineIndex
                            val indentDp = (entry.level.coerceIn(0, 3) * 16).dp
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (isActiveSection) {
                                    MaterialTheme.colorScheme.primaryContainer
                                } else {
                                    MaterialTheme.colorScheme.surfaceVariant
                                },
                                border = BorderStroke(
                                    width = if (isActiveSection) 1.5.dp else 1.dp,
                                    color = if (isActiveSection) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.outline
                                    }
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = indentDp)
                                    .clickable {
                                        onSelectSlide(entry.pageIndex)
                                        onDismiss()
                                    }
                                    .testTag("section_outline_$idx")
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 14.dp, vertical = 12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = entry.title,
                                        style = if (entry.level == 0) {
                                            MaterialTheme.typography.titleSmall
                                        } else {
                                            MaterialTheme.typography.bodyMedium
                                        },
                                        fontWeight = if (entry.level == 0) FontWeight.SemiBold else FontWeight.Normal,
                                        color = if (isActiveSection) {
                                            MaterialTheme.colorScheme.onPrimaryContainer
                                        } else {
                                            MaterialTheme.colorScheme.onSurface
                                        },
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = if (isActiveSection) {
                                            MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
                                        } else {
                                            MaterialTheme.colorScheme.surface
                                        }
                                    ) {
                                        Text(
                                            text = "Slide ${entry.pageIndex + 1}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = if (isActiveSection) {
                                                MaterialTheme.colorScheme.primary
                                            } else {
                                                MaterialTheme.colorScheme.onSurfaceVariant
                                            },
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DisplayAndBeamerConfigSheet(
    connectedDisplay: ExternalDisplayInfo?,
    requestedMode: BeamerLayoutMode,
    effectiveMode: BeamerLayoutMode,
    targetMinutes: Int,
    simplePresentingMode: Boolean,
    projectedBitmap: Bitmap?,
    projectedStrokes: List<AnnotationStroke>,
    laserState: LaserPointerState,
    spotlightState: SpotlightState,
    viewportState: SlideViewportState,
    chalkboardState: ChalkboardState,
    isBlackout: Boolean,
    isFrozen: Boolean,
    isExportingPdf: Boolean,
    onSelectLayoutMode: (BeamerLayoutMode) -> Unit,
    onSelectTargetMinutes: (Int) -> Unit,
    onToggleSimplePresentingMode: (Boolean) -> Unit,
    onExportAnnotatedPdf: () -> Unit,
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "DisplayPort & Beamer Setup",
                    style = MaterialTheme.typography.titleLarge
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close setup")
                }
            }

            // External DisplayPort signal status + Live Audience Preview
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Cast,
                            contentDescription = null,
                            tint = if (connectedDisplay != null) {
                                MaterialTheme.colorScheme.secondary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = connectedDisplay?.let {
                                    "Connected: ${it.name}"
                                } ?: "No External USB-C / DisplayPort Signal",
                                style = MaterialTheme.typography.titleSmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = connectedDisplay?.let {
                                    "${it.resolutionLabel} @ ${it.refreshRate.toInt()} Hz · Auto-presenting"
                                } ?: "Connect USB-C DisplayPort to cast cleanly. Preview below shows exact projector output.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Text(
                        text = buildString {
                            append("AUDIENCE PROJECTOR OUTPUT")
                            if (isBlackout) append(" · BLACKOUT")
                            else if (isFrozen) append(" · FROZEN")
                            else if (chalkboardState.isActive) append(" · CHALKBOARD")
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(16f / 9f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color.Black)
                    ) {
                        StageSlideCanvas(
                            slideBitmap = projectedBitmap,
                            strokes = projectedStrokes,
                            activeStroke = null,
                            laserState = laserState,
                            spotlightState = spotlightState,
                            viewportState = if (!isFrozen && viewportState.mirrorToProjector) viewportState else SlideViewportState(),
                            chalkboardState = chalkboardState,
                            activeTool = InteractionTool.NAVIGATE,
                            isBlackoutOverlay = isBlackout,
                            interactive = false,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }

            // Export & Share Annotated PDF
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Export Annotated Slides",
                            style = MaterialTheme.typography.titleSmall
                        )
                        Text(
                            text = "Save & share a PDF with your ink strokes and chalkboard pages baked in.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Button(
                        onClick = onExportAnnotatedPdf,
                        enabled = !isExportingPdf,
                        modifier = Modifier.testTag("export_annotated_pdf_button")
                    ) {
                        if (isExportingPdf) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.IosShare,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(if (isExportingPdf) "Exporting…" else "Share PDF")
                    }
                }
            }

            // Beamer Split-Page Layout Selector
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Beamer PDF Page Geometry",
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = "Active mode: ${effectiveMode.displayName}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )

                BeamerLayoutMode.entries.forEach { mode ->
                    val selected = requestedMode == mode
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (selected) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant
                        },
                        border = BorderStroke(
                            width = if (selected) 1.5.dp else 1.dp,
                            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelectLayoutMode(mode) }
                            .testTag("layout_mode_${mode.name}")
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = mode.displayName,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = mode.description,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (selected) {
                                Spacer(modifier = Modifier.width(8.dp))
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "Selected",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }

            // Simple Presenting Mode Toggle
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Simple Presenting Mode",
                            style = MaterialTheme.typography.titleSmall
                        )
                        Text(
                            text = "Show only the current slide with all tools; hide Next Slide and Speaker Notes.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Switch(
                        checked = simplePresentingMode,
                        onCheckedChange = onToggleSimplePresentingMode,
                        modifier = Modifier.testTag("sheet_switch_simple_mode")
                    )
                }
            }

            // Talk Pacing Target Duration
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Timer,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "Talk Pacing Target",
                        style = MaterialTheme.typography.titleMedium
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(5, 10, 15, 20, 30, 45).forEach { mins ->
                        FilterChip(
                            selected = targetMinutes == mins,
                            onClick = { onSelectTargetMinutes(mins) },
                            label = { Text("${mins}m", style = MaterialTheme.typography.labelMedium) }
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteEditorSheet(
    pageIndex: Int,
    initialText: String,
    onActiveChanged: (Boolean) -> Unit,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var draft by remember(pageIndex, initialText) { mutableStateOf(initialText) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    DisposableEffect(Unit) {
        onActiveChanged(true)
        onDispose { onActiveChanged(false) }
    }

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
            Text(
                text = "Speaker Notes · Slide ${pageIndex + 1}",
                style = MaterialTheme.typography.titleLarge
            )
            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it },
                label = { Text("Cues, equations, or timing reminders") },
                minLines = 5,
                maxLines = 10,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("note_editor_textfield")
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                OutlinedButton(onClick = onDismiss) {
                    Text("Cancel")
                }
                Spacer(modifier = Modifier.width(12.dp))
                Button(
                    onClick = {
                        onSave(draft)
                        onDismiss()
                    },
                    modifier = Modifier.testTag("save_note_button")
                ) {
                    Text("Save Note")
                }
            }
        }
    }
}
