package com.audiotageditor.ui.editor

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.HideImage
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.InputChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.audiotageditor.data.AudioMetadata
import com.audiotageditor.data.SettingsManager

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(
    selectedUris: List<String>,
    onNavigateBack: () -> Unit,
    viewModel: EditorScreenViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val uiState by viewModel.uiState.collectAsState()

    val files = uiState.selectedFiles
    val isBatch = uiState.isBatchEdit
    val isLoading = uiState.isLoading
    val saveSuccess = uiState.saveSuccess
    val mixedFields = uiState.mixedFields
    val mixedFieldsAction = uiState.mixedFieldsAction

    // Load files initially
    LaunchedEffect(selectedUris) {
        viewModel.loadFiles(context, selectedUris)
    }

    // Handle save success
    LaunchedEffect(saveSuccess) {
        if (saveSuccess == true) {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            Toast.makeText(context, "Tags saved successfully!", Toast.LENGTH_SHORT).show()
            viewModel.resetSuccess()
            onNavigateBack()
        } else if (saveSuccess == false) {
            Toast.makeText(context, "Failed to save tags.", Toast.LENGTH_SHORT).show()
            viewModel.resetSuccess()
        }
    }

    BottomSheetScaffold(
        topBar = {
            TopAppBar(
                title = {
                    val fileWord = if (files.size == 1) "File" else "Files"
                    Text(
                        text = if (isBatch) "Batch Edit · ${files.size} $fileWord" else "Edit Tags",
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        },
        sheetContent = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .padding(bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Save Changes",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Button(
                    onClick = { viewModel.saveChanges(context) },
                    enabled = !isLoading && files.isNotEmpty(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Save,
                            contentDescription = null
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isBatch) "Save ${files.size} Changes" else "Save Changes",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                }
            }
        },
        sheetPeekHeight = 120.dp,
        containerColor = MaterialTheme.colorScheme.background,
        modifier = modifier.fillMaxSize()
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(MaterialTheme.colorScheme.background)
        ) {
            if (files.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
            } else {
                EditorFormContent(
                    uiState = uiState,
                    viewModel = viewModel,
                    isBatch = isBatch,
                    files = files
                )
            }
        }
    }
}

@Composable
fun EditorFormContent(
    uiState: EditorUiState,
    viewModel: EditorScreenViewModel,
    isBatch: Boolean,
    files: List<AudioMetadata>
) {
    val mixedFields = uiState.mixedFields
    val mixedFieldsAction = uiState.mixedFieldsAction
    val title = uiState.title
    val artist = uiState.artist
    val album = uiState.album
    val year = uiState.year
    val genre = uiState.genre
    val track = uiState.track
    val albumArtist = uiState.albumArtist
    val comment = uiState.comment
    val description = uiState.description
    val composer = uiState.composer
    val discNumber = uiState.discNumber
    val removeCover = uiState.removeCover

    val showRemoveCoverOption = isBatch || uiState.albumArtBytes != null
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(androidx.compose.foundation.rememberScrollState())
            .padding(bottom = 32.dp, start = 16.dp, end = 16.dp, top = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        if (uiState.coverImageBitmap != null) {
                androidx.compose.foundation.Image(
                    bitmap = uiState.coverImageBitmap,
                    contentDescription = "Cover Art",
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .clip(RoundedCornerShape(16.dp)),
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop
                )
        } else if (!isBatch) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.MusicNote,
                        contentDescription = "No Cover Art",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(64.dp)
                    )
                }
        }

        // 1. Remove cover option at the top of metadata fields (if supported / applicable)
        // In batch mode, we always show it. In single mode, we show it if the file currently has cover art.
        if (showRemoveCoverOption) {
                OutlinedCard(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.HideImage,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(28.dp)
                                    )
                                    Spacer(modifier = Modifier.width(16.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "Remove Album Cover Art",
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            style = MaterialTheme.typography.titleMedium
                                        )
                                        Text(
                                            text = if (isBatch) "Strip cover photos from all selected audio files." else "Strip cover photo from this audio file.",
                                            fontSize = 12.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Switch(
                                        checked = removeCover,
                                        onCheckedChange = { viewModel.updateRemoveCover(it) }
                                    )
                                }
                            }
                    }

                    // Form Fields Header
                        Text(
                            text = "METADATA FIELDS",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                        )

                    // Title (Single only)
                    if (!isBatch) {
                            EditorTextField(
                                value = title,
                                onValueChange = { viewModel.updateTitle(it) },
                                label = "Title",
                                isBatch = false,
                                currentAction = "CHOOSE",
                                onActionChange = {}
                            )
                    }

                    // Artist
                        EditorTextField(
                            value = artist,
                            onValueChange = { viewModel.updateArtist(it) },
                            label = "Artist",
                            isBatch = isBatch,
                            sharedValues = if (isBatch) files.map { it.artist }.distinct().filter { it.isNotBlank() } else emptyList(),
                            currentAction = mixedFieldsAction["artist"] ?: if (isBatch) "KEEP" else "CHOOSE",
                            onActionChange = { action -> viewModel.setMixedFieldAction("artist", action) }
                        )

                    // Album
                        EditorTextField(
                            value = album,
                            onValueChange = { viewModel.updateAlbum(it) },
                            label = "Album",
                            isBatch = isBatch,
                            sharedValues = if (isBatch) files.map { it.album }.distinct().filter { it.isNotBlank() } else emptyList(),
                            currentAction = mixedFieldsAction["album"] ?: if (isBatch) "KEEP" else "CHOOSE",
                            onActionChange = { action -> viewModel.setMixedFieldAction("album", action) }
                        )

                    // Album Artist
                        EditorTextField(
                            value = albumArtist,
                            onValueChange = { viewModel.updateAlbumArtist(it) },
                            label = "Album Artist",
                            isBatch = isBatch,
                            sharedValues = if (isBatch) files.map { it.albumArtist }.distinct().filter { it.isNotBlank() } else emptyList(),
                            currentAction = mixedFieldsAction["albumArtist"] ?: if (isBatch) "KEEP" else "CHOOSE",
                            onActionChange = { action -> viewModel.setMixedFieldAction("albumArtist", action) }
                        )

                    // Genre
                        EditorTextField(
                            value = genre,
                            onValueChange = { viewModel.updateGenre(it) },
                            label = "Genre",
                            isBatch = isBatch,
                            sharedValues = if (isBatch) files.map { it.genre }.distinct().filter { it.isNotBlank() } else emptyList(),
                            currentAction = mixedFieldsAction["genre"] ?: if (isBatch) "KEEP" else "CHOOSE",
                            onActionChange = { action -> viewModel.setMixedFieldAction("genre", action) }
                        )

                    // Year
                        EditorTextField(
                            value = year,
                            onValueChange = { viewModel.updateYear(it) },
                            label = "Year",
                            isBatch = isBatch,
                            sharedValues = if (isBatch) files.map { it.year }.distinct().filter { it.isNotBlank() } else emptyList(),
                            currentAction = mixedFieldsAction["year"] ?: if (isBatch) "KEEP" else "CHOOSE",
                            onActionChange = { action -> viewModel.setMixedFieldAction("year", action) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                        )

                    // Track Number (Single only)
                    if (!isBatch) {
                            EditorTextField(
                                value = track,
                                onValueChange = { viewModel.updateTrack(it) },
                                label = "Track Number",
                                isBatch = false,
                                currentAction = "CHOOSE",
                                onActionChange = {},
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                            )
                    }

                    // Disc Number
                        EditorTextField(
                            value = discNumber,
                            onValueChange = { viewModel.updateDiscNumber(it) },
                            label = "Disc Number",
                            isBatch = isBatch,
                            sharedValues = if (isBatch) files.map { it.discNumber }.distinct().filter { it.isNotBlank() } else emptyList(),
                            currentAction = mixedFieldsAction["discNumber"] ?: if (isBatch) "KEEP" else "CHOOSE",
                            onActionChange = { action -> viewModel.setMixedFieldAction("discNumber", action) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                        )

                    // Composer
                        EditorTextField(
                            value = composer,
                            onValueChange = { viewModel.updateComposer(it) },
                            label = "Composer",
                            isBatch = isBatch,
                            sharedValues = if (isBatch) files.map { it.composer }.distinct().filter { it.isNotBlank() } else emptyList(),
                            currentAction = mixedFieldsAction["composer"] ?: if (isBatch) "KEEP" else "CHOOSE",
                            onActionChange = { action -> viewModel.setMixedFieldAction("composer", action) }
                        )

                    // Comment
                        EditorTextField(
                            value = comment,
                            onValueChange = { viewModel.updateComment(it) },
                            label = "Comment",
                            isBatch = isBatch,
                            sharedValues = if (isBatch) files.map { it.comment }.distinct().filter { it.isNotBlank() } else emptyList(),
                            currentAction = mixedFieldsAction["comment"] ?: if (isBatch) "KEEP" else "CHOOSE",
                            onActionChange = { action -> viewModel.setMixedFieldAction("comment", action) }
                        )

                    // Description
                        EditorTextField(
                            value = description,
                            onValueChange = { viewModel.updateDescription(it) },
                            label = "Description",
                            isBatch = isBatch,
                            sharedValues = if (isBatch) files.map { it.description }.distinct().filter { it.isNotBlank() } else emptyList(),
                            currentAction = mixedFieldsAction["description"] ?: if (isBatch) "KEEP" else "CHOOSE",
                            onActionChange = { action -> viewModel.setMixedFieldAction("description", action) }
                        )

                    // Advanced Technical Info Section at the bottom (Single only)
                    if (!isBatch && files.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            val file = files.first()
                            AdvancedTechnicalInfoCard(
                                file = file
                            )
                    }
                }
}

@Composable
fun AdvancedTechnicalInfoCard(
    file: AudioMetadata
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp)),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Advanced Technical Info",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                TechnicalInfoItem(label = "Filename", value = file.fileName, modifier = Modifier.fillMaxWidth())
                TechnicalInfoItem(label = "Full File Path", value = android.net.Uri.parse(file.uriString).path ?: file.uriString, modifier = Modifier.fillMaxWidth())

                val isLossless = file.cleanFormat in listOf("FLAC", "ALAC", "WAV", "APE", "AIFF")

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    TechnicalInfoItem(label = "Codec", value = file.cleanFormat, modifier = Modifier.weight(1f))
                    TechnicalInfoItem(label = "Quality", value = if (isLossless) "Lossless" else "Lossy", modifier = Modifier.weight(1f))
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    TechnicalInfoItem(label = "Bitrate", value = file.bitrate, modifier = Modifier.weight(1f))
                    TechnicalInfoItem(label = "Sample Rate", value = file.sampleRate, modifier = Modifier.weight(1f))
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    TechnicalInfoItem(label = "File Size", value = file.sizeFormatted, modifier = Modifier.weight(1f))
                    TechnicalInfoItem(label = "Duration", value = file.durationFormatted, modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
fun TechnicalInfoItem(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value.ifBlank { "Unknown" },
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun EditorTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    isBatch: Boolean,
    sharedValues: List<String> = emptyList(),
    currentAction: String, // "KEEP", "BLANK", "CHOOSE"
    onActionChange: (String) -> Unit,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default
) {
    val showRightChips by SettingsManager.showRightChips.collectAsState()
    val showBottomStrip by SettingsManager.showBottomStrip.collectAsState()

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (isBatch && showRightChips) {
                Row(
                    modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh),
                ) {
                    val blankSelected = currentAction == "BLANK"
                    Box(
                        modifier = Modifier
                            .clickable { onActionChange(if (blankSelected) "KEEP" else "BLANK") }
                            .background(if (blankSelected) MaterialTheme.colorScheme.errorContainer else Color.Transparent)
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = "Blank",
                            fontSize = 12.sp,
                            fontWeight = if (blankSelected) FontWeight.Bold else FontWeight.Medium,
                            color = if (blankSelected) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Box(
                        modifier = Modifier
                            .clickable { onActionChange(if (currentAction == "CHOOSE") "KEEP" else "CHOOSE") }
                            .background(if (currentAction == "CHOOSE") MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = "Choose",
                            fontSize = 12.sp,
                            fontWeight = if (currentAction == "CHOOSE") FontWeight.Bold else FontWeight.Medium,
                            color = if (currentAction == "CHOOSE") MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        val placeholderText = when {
            isBatch && currentAction == "KEEP" -> "Keep existing values"
            isBatch && currentAction == "BLANK" -> "Field will be cleared"
            isBatch && currentAction == "CHOOSE" -> "Enter custom value or select below"
            else -> "Enter $label"
        }

        val handleChange = remember(onValueChange, onActionChange, isBatch, currentAction) {
            { newValue: String ->
                onValueChange(newValue)
                if (isBatch && currentAction != "CHOOSE") {
                    onActionChange("CHOOSE")
                }
            }
        }

        val textStyle = remember { androidx.compose.ui.text.TextStyle(textDirection = TextDirection.Content) }

        val surfaceContainerHigh = MaterialTheme.colorScheme.surfaceContainerHigh
        val surfaceContainerLow = MaterialTheme.colorScheme.surfaceContainerLow
        val onSurface = MaterialTheme.colorScheme.onSurface
        val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant
        val textFieldColors = TextFieldDefaults.colors(
            focusedContainerColor = surfaceContainerHigh,
            unfocusedContainerColor = surfaceContainerHigh,
            disabledContainerColor = surfaceContainerLow,
            focusedTextColor = onSurface,
            unfocusedTextColor = onSurface,
            disabledTextColor = onSurfaceVariant,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            disabledIndicatorColor = Color.Transparent
        )

        TextField(
            value = if (isBatch && currentAction != "CHOOSE") "" else value,
            onValueChange = handleChange,
            enabled = !isBatch || currentAction == "CHOOSE",
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp)),
            placeholder = {
                Text(
                    text = placeholderText,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 14.sp,
                    style = MaterialTheme.typography.bodyMedium.copy(textDirection = TextDirection.Content)
                )
            },
            textStyle = textStyle,
            keyboardOptions = keyboardOptions,
            colors = textFieldColors,
            singleLine = true
        )

        if (isBatch && showBottomStrip && currentAction == "CHOOSE" && sharedValues.isNotEmpty()) {
            androidx.compose.foundation.layout.FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                sharedValues.forEach { suggestion ->
                    val isSelected = suggestion == value
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh)
                            .clickable { handleChange(suggestion) }
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = suggestion.ifBlank { "(Empty)" },
                            fontSize = 12.sp,
                            color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }
    }
}
