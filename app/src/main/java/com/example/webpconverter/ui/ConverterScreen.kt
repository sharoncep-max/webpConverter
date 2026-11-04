package com.example.webpconverter.ui

import android.net.Uri
import android.text.format.Formatter
import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.triStateToggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TriStateCheckbox
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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.work.WorkInfo
import com.example.webpconverter.R
import com.example.webpconverter.conversion.ConversionWorker
import com.example.webpconverter.data.BatchOperation
import com.example.webpconverter.data.FileTypeFilter
import com.example.webpconverter.data.OutputFormat
import com.example.webpconverter.data.SourceFile
import com.example.webpconverter.data.isWebpName
import java.util.Locale

@Composable
fun ConverterScreen(
    state: ConverterState,
    onInput: () -> Unit,
    onOutput: () -> Unit,
    onRefresh: () -> Unit,
    onToggle: (String) -> Unit,
    onSelectAll: () -> Unit,
    onFormat: (OutputFormat) -> Unit,
    onConvert: () -> Unit,
    onCancel: () -> Unit,
    onDismissError: () -> Unit,
    onManagement: (Boolean) -> Unit = {},
    onFileAction: (BatchOperation) -> Unit = {},
    onFileType: (FileTypeFilter) -> Unit = {},
    onReset: () -> Unit = {}
) {
    val listState = rememberLazyListState()
    var confirming by remember { mutableStateOf(false) }
    val finished = state.work?.state?.isFinished == true && !state.submitting
    LaunchedEffect(finished) { if (finished) listState.animateScrollToItem(0) }

    Scaffold(
        topBar = {
            Column {
                AppHeader()
                PrimaryTabRow(selectedTabIndex = if (state.management) 1 else 0) {
                    Tab(
                        selected = !state.management, enabled = !state.busy,
                        onClick = { onManagement(false) }, text = { Text("Convert") }
                    )
                    Tab(
                        selected = state.management, enabled = !state.busy,
                        onClick = { onManagement(true) }, text = { Text("Manage files") }
                    )
                }
            }
        },
        bottomBar = { ActionBar(state, {
            if (state.operation in listOf(BatchOperation.MOVE, BatchOperation.DELETE)) confirming = true
            else onConvert()
        }, onCancel, {
            confirming = false
            onReset()
        }) },
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize()
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal)),
                contentPadding = PaddingValues(start = 16.dp, end = 20.dp, top = 16.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (finished) item(key = "result") { ResultPanel(state) }
                item(key = "source") { SourcePanel(state, onInput, onRefresh, onFileType) }
                item(key = "output") {
                    if (state.management) ManagementPanel(state, onOutput, onFileAction)
                    else OutputPanel(state, onOutput, onFormat)
                }
                item(key = "files") { FilesHeader(state, onSelectAll) }
                if (!state.scanning && state.files.isEmpty()) item(key = "empty") { EmptyState(state) }
                items(state.files, key = { it.uri }) { file ->
                    FileRow(file, file.uri in state.selected, enabled = !state.busy && !state.scanning) {
                        onToggle(file.uri)
                    }
                }
                item(key = "footer") {
                    Text(
                        if (state.management) "Only selected files are affected. Subfolders are not included."
                        else "Offline processing. No uploads. Metadata is not copied.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                    )
                }
            }
            VerticalScrollbar(
                listState,
                Modifier.align(Alignment.TopEnd).fillMaxHeight().padding(vertical = 10.dp, horizontal = 2.dp)
            )
        }
    }

    if (confirming && state.canRun) {
        val deleting = state.operation == BatchOperation.DELETE
        AlertDialog(
            onDismissRequest = { confirming = false },
            title = { Text("${state.operation.label} ${state.selected.size} selected files?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Input: ${folderLabel(state.input)}")
                    if (!deleting) Text("Destination: ${folderLabel(state.output)}")
                    Text(
                        if (state.fileType == FileTypeFilter.WEBP) "File type: WebP only"
                        else "File type: Other files (not .webp)"
                    )
                    Text(
                        if (deleting) "These selected files will be permanently deleted. There is no undo or recycle bin. Unselected files and subfolders are not affected."
                        else "Each selected file will be copied and verified before its source is deleted. This removes the originals from the input folder. Existing destination files are kept."
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { confirming = false; onConvert() }) {
                    Text(if (deleting) "Delete permanently" else "Move files", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirming = false }) { Text("Cancel") } }
        )
    }

    state.error?.let {
        AlertDialog(
            onDismissRequest = onDismissError,
            icon = { Icon(painterResource(R.drawable.ic_info), null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("Action could not be completed") },
            text = { Text(it) },
            confirmButton = { TextButton(onClick = onDismissError) { Text("OK") } }
        )
    }
}

@Composable
private fun AppHeader() {
    Box(
        Modifier.fillMaxWidth().background(headerBrush)
            .statusBarsPadding()
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
            .padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(52.dp).clip(MaterialTheme.shapes.medium)
                    .background(Color.White.copy(alpha = 0.16f))
                    .border(1.dp, Color.White.copy(alpha = 0.35f), MaterialTheme.shapes.medium),
                contentAlignment = Alignment.Center
            ) {
                Image(painterResource(R.drawable.ic_launcher_foreground), null, Modifier.requiredSize(84.dp))
            }
            Column(Modifier.padding(start = 14.dp)) {
                Text(
                    "WebP Converter", color = Color.White,
                    style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold
                )
                Text(
                    "Convert WebP images and organize files", color = Color.White.copy(alpha = 0.85f),
                    style = MaterialTheme.typography.bodyMedium
                )
                Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("Offline", "Batch", "On-device").forEach { HeaderPill(it) }
                }
            }
        }
    }
}

@Composable
private fun HeaderPill(text: String) {
    Text(
        text, color = Color.White, style = MaterialTheme.typography.labelSmall,
        modifier = Modifier.clip(CircleShape).background(Color.White.copy(alpha = 0.18f))
            .border(1.dp, Color.White.copy(alpha = 0.3f), CircleShape)
            .padding(horizontal = 10.dp, vertical = 3.dp)
    )
}

@Composable
private fun Panel(
    step: Int,
    @DrawableRes icon: Int,
    title: String,
    subtitle: String? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val colors = MaterialTheme.colorScheme
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = colors.surface,
        border = BorderStroke(1.dp, colors.outlineVariant),
        shadowElevation = 1.dp
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(40.dp).clip(MaterialTheme.shapes.small).background(colors.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(painterResource(icon), null, Modifier.size(22.dp), tint = colors.onPrimaryContainer)
                }
                Column(Modifier.padding(start = 12.dp).weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    subtitle?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                    }
                }
                Text(
                    "STEP $step", style = MaterialTheme.typography.labelSmall, color = colors.primary,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.border(1.dp, colors.primary.copy(alpha = 0.5f), CircleShape)
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                )
            }
            HorizontalDivider(color = colors.outlineVariant)
            content()
        }
    }
}

@Composable
private fun SourcePanel(
    state: ConverterState,
    onInput: () -> Unit,
    onRefresh: () -> Unit,
    onFileType: (FileTypeFilter) -> Unit
) {
    val subtitle = if (state.management) "Only files directly inside this folder are listed."
        else "Only .webp files directly inside this folder are listed."
    Panel(1, R.drawable.ic_folder, "Input folder", subtitle) {
        FolderField(state.input)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Button(onClick = onInput, enabled = !state.busy, modifier = Modifier.weight(1f)) {
                Icon(painterResource(R.drawable.ic_folder), null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Choose input folder", maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            OutlinedButton(onClick = onRefresh, enabled = state.input != null && !state.busy && !state.scanning) {
                Icon(painterResource(R.drawable.ic_refresh), null, Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Refresh")
            }
        }
        if (state.management) {
            Text("Show files", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
            Row(Modifier.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FileTypeFilter.entries.forEach { filter ->
                    Surface(
                        selected = state.fileType == filter,
                        onClick = { onFileType(filter) },
                        enabled = !state.busy,
                        modifier = Modifier.weight(1f).semantics { role = Role.RadioButton },
                        shape = MaterialTheme.shapes.medium,
                        color = if (state.fileType == filter) MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.surface,
                        border = BorderStroke(
                            if (state.fileType == filter) 2.dp else 1.dp,
                            if (state.fileType == filter) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.outlineVariant
                        )
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(
                                selected = state.fileType == filter, onClick = null,
                                enabled = !state.busy, modifier = Modifier.padding(4.dp)
                            )
                            Text(filter.label, style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
            }
            Text(
                if (state.fileType == FileTypeFilter.WEBP) "Files ending in .webp, ignoring letter case."
                else "All files except .webp, including files without an extension.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (state.scanning) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                Text("Scanning folder...", Modifier.padding(start = 10.dp), style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun OutputPanel(state: ConverterState, onOutput: () -> Unit, onFormat: (OutputFormat) -> Unit) {
    Panel(2, R.drawable.ic_output, "Output", "Destination folder and image format.") {
        FolderField(state.output)
        FilledTonalButton(onClick = onOutput, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) {
            Icon(painterResource(R.drawable.ic_output), null, Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Choose destination folder")
        }
        Text("Output format", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
        Row(Modifier.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutputFormat.entries.forEach { format ->
                FormatOption(
                    format = format,
                    description = if (format == OutputFormat.PNG) "Lossless · keeps transparency"
                    else "Smaller files · white background",
                    selected = state.format == format,
                    enabled = !state.busy,
                    onClick = { onFormat(format) },
                    modifier = Modifier.weight(1f)
                )
            }
        }
        InfoNote(
            (if (state.format == OutputFormat.JPG)
                "JPG uses quality 95 with a white background behind transparent pixels."
            else "PNG preserves transparency and uses lossless compression.") +
                "\nExisting files are kept; new files get unique names. Animated WebP is not supported."
        )
    }
}

@Composable
private fun ManagementPanel(
    state: ConverterState, onOutput: () -> Unit, onAction: (BatchOperation) -> Unit
) {
    Panel(2, R.drawable.ic_output, "File operation", "Copy, move or delete the selected files.") {
        Row(Modifier.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(BatchOperation.COPY, BatchOperation.MOVE, BatchOperation.DELETE).forEach { action ->
                Surface(
                    selected = state.fileAction == action,
                    onClick = { onAction(action) }, enabled = !state.busy,
                    modifier = Modifier.weight(1f).semantics { role = Role.RadioButton },
                    shape = MaterialTheme.shapes.medium,
                    color = if (state.fileAction == action) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Text(action.label, Modifier.padding(vertical = 14.dp), textAlign = TextAlign.Center,
                        fontWeight = FontWeight.SemiBold)
                }
            }
        }
        if (state.fileAction != BatchOperation.DELETE) {
            FolderField(state.output)
            FilledTonalButton(onClick = onOutput, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) {
                Text("Choose destination folder")
            }
        }
        InfoNote(when (state.fileAction) {
            BatchOperation.COPY -> "Copy keeps the originals. File bytes and embedded data are unchanged; provider metadata such as timestamps may differ. Existing files are kept; copies get unique names."
            BatchOperation.MOVE -> "Move copies and verifies each file before deleting its source. Write access to the input folder is required. If deletion fails, the verified copy is kept and an error is shown."
            else -> "Delete permanently removes only the selected files from the input folder. There is no undo. A confirmation is required."
        })
    }
}

@Composable
private fun FormatOption(
    format: OutputFormat,
    description: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    Surface(
        selected = selected,
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.semantics { role = Role.RadioButton },
        shape = MaterialTheme.shapes.medium,
        color = if (selected) colors.primaryContainer else colors.surface,
        border = BorderStroke(if (selected) 2.dp else 1.dp, if (selected) colors.primary else colors.outlineVariant)
    ) {
        Column(Modifier.padding(start = 4.dp, end = 12.dp, top = 6.dp, bottom = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(selected = selected, onClick = null, enabled = enabled, modifier = Modifier.padding(8.dp))
                Text(format.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            Text(
                description, style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant, modifier = Modifier.padding(start = 12.dp)
            )
        }
    }
}

@Composable
private fun FolderField(value: String?) {
    val colors = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium)
            .background(colors.surfaceVariant.copy(alpha = 0.6f))
            .border(1.dp, colors.outlineVariant, MaterialTheme.shapes.medium)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            painterResource(R.drawable.ic_folder), null, Modifier.size(22.dp),
            tint = if (value == null) colors.onSurfaceVariant else colors.secondary
        )
        Column(Modifier.padding(start = 10.dp)) {
            Text(
                folderLabel(value), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium,
                maxLines = 1, overflow = TextOverflow.Ellipsis
            )
            Text(
                if (value == null) "Tap the button below to pick a folder" else "Access granted",
                style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun InfoNote(text: String) {
    val colors = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium)
            .background(colors.secondaryContainer.copy(alpha = 0.55f))
            .border(1.dp, colors.secondary.copy(alpha = 0.25f), MaterialTheme.shapes.medium)
            .padding(12.dp)
    ) {
        Icon(painterResource(R.drawable.ic_info), null, Modifier.size(18.dp), tint = colors.secondary)
        Text(
            text, style = MaterialTheme.typography.bodySmall, color = colors.onSecondaryContainer,
            modifier = Modifier.padding(start = 10.dp)
        )
    }
}

@Composable
private fun FilesHeader(state: ConverterState, onSelectAll: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val title = if (state.management) "Select files" else "Select images"
    val subtitle = if (state.management) "Tap a file to include it in the batch."
        else "Tap an image to include it in the batch."
    Panel(3, R.drawable.ic_photos, title, subtitle) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "${state.selected.size} of ${state.files.size} selected",
                style = MaterialTheme.typography.labelLarge, color = colors.onPrimaryContainer,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.clip(CircleShape).background(colors.primaryContainer)
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            )
            Spacer(Modifier.weight(1f))
            val toggle = when {
                state.selected.isEmpty() -> ToggleableState.Off
                state.selected.size == state.files.size -> ToggleableState.On
                else -> ToggleableState.Indeterminate
            }
            val enabled = !state.busy && !state.scanning && state.files.isNotEmpty()
            Row(
                Modifier.clip(CircleShape)
                    .triStateToggleable(toggle, enabled = enabled, role = Role.Checkbox, onClick = onSelectAll)
                    .padding(end = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TriStateCheckbox(state = toggle, onClick = null, enabled = enabled, modifier = Modifier.padding(10.dp))
                Text("Select all", style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

@Composable
private fun FileRow(file: SourceFile, selected: Boolean, enabled: Boolean, onToggle: () -> Unit) {
    val context = LocalContext.current
    val colors = MaterialTheme.colorScheme
    val dot = file.name.lastIndexOf('.')
    val extension = if (dot > 0) file.name.substring(dot + 1) else ""
    val badge = if (isWebpName(file.name)) "WEBP"
        else extension.takeIf { it.isNotBlank() && it.length <= 8 && it.all(Char::isLetterOrDigit) }
            ?.uppercase(Locale.ROOT) ?: "FILE"
    Surface(
        checked = selected,
        onCheckedChange = { onToggle() },
        enabled = enabled,
        modifier = Modifier.fillMaxWidth().semantics { role = Role.Checkbox },
        shape = MaterialTheme.shapes.medium,
        color = if (selected) colors.primaryContainer.copy(alpha = 0.45f) else colors.surface,
        border = BorderStroke(if (selected) 1.5.dp else 1.dp, if (selected) colors.primary else colors.outlineVariant)
    ) {
        Row(Modifier.padding(horizontal = 6.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = selected, onCheckedChange = null, enabled = enabled, modifier = Modifier.padding(8.dp))
            Box(
                Modifier.size(40.dp).clip(MaterialTheme.shapes.small).background(colors.secondaryContainer),
                contentAlignment = Alignment.Center
            ) {
                val icon = if (isWebpName(file.name) || file.mimeType?.startsWith("image/") == true)
                    R.drawable.ic_image else R.drawable.ic_file
                Icon(painterResource(icon), null, Modifier.size(22.dp), tint = colors.onSecondaryContainer)
            }
            Column(Modifier.padding(horizontal = 12.dp).weight(1f)) {
                Text(
                    file.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium,
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
                Text(
                    file.size?.let { Formatter.formatFileSize(context, it) } ?: "Size unavailable",
                    style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant
                )
            }
            Text(
                badge, style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant,
                modifier = Modifier.padding(end = 6.dp)
                    .border(1.dp, colors.outline, MaterialTheme.shapes.extraSmall)
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            )
        }
    }
}

@Composable
private fun EmptyState(state: ConverterState) {
    val colors = MaterialTheme.colorScheme
    Column(
        Modifier.fillMaxWidth().clip(MaterialTheme.shapes.large)
            .border(1.dp, colors.outline.copy(alpha = 0.6f), MaterialTheme.shapes.large)
            .padding(vertical = 28.dp, horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(painterResource(R.drawable.ic_photos), null, Modifier.size(36.dp), tint = colors.outline)
        Text(
            when {
                state.input == null -> "Choose an input folder to begin."
                state.visibleFileType == FileTypeFilter.WEBP -> "No .webp files found."
                else -> "No non-WebP files found."
            },
            style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp)
        )
    }
}

@Composable
private fun ResultPanel(state: ConverterState) {
    val work = state.work ?: return
    val report = state.report
    val operation = report?.operation ?: state.activeOperation
    val success = work.state == WorkInfo.State.SUCCEEDED && report != null &&
        report.failed == 0 && report.completed == report.entries.size
    val message = when (work.state) {
        WorkInfo.State.SUCCEEDED -> if (success)
            "Success! ${operation.completedLabel} ${report.succeeded} of ${report.entries.size} files."
        else if (report != null) "Finished: ${report.succeeded} ${operation.completedLabel.lowercase()}, ${report.failed} failed."
        else "${operation.label} finished, but its report is unavailable. Check your folders."
        WorkInfo.State.CANCELLED -> "${operation.label} cancelled. ${report?.succeeded ?: 0} files completed. Completed changes are not undone; check your folders."
        else -> "${operation.label} stopped: ${work.outputData.getString(ConversionWorker.ERROR) ?: "Unknown error."}"
    }
    val palette = when {
        success -> successColors
        work.state == WorkInfo.State.CANCELLED ->
            StatusColors(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.onSurfaceVariant)
        else -> StatusColors(MaterialTheme.colorScheme.errorContainer, MaterialTheme.colorScheme.onErrorContainer)
    }
    val errors = report?.entries?.filter { it.error != null }.orEmpty()
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = palette.container,
        contentColor = palette.content,
        border = BorderStroke(1.dp, palette.content.copy(alpha = 0.25f))
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    painterResource(if (success) R.drawable.ic_check_circle else R.drawable.ic_info),
                    null, Modifier.size(26.dp)
                )
                Text(
                    message, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(start = 10.dp)
                )
            }
            if (errors.isNotEmpty()) {
                HorizontalDivider(color = palette.content.copy(alpha = 0.2f))
                errors.forEach { Text("${it.source.name}: ${it.error}", style = MaterialTheme.typography.bodySmall) }
            }
        }
    }
}

@Composable
private fun ActionBar(state: ConverterState, onConvert: () -> Unit, onCancel: () -> Unit, onReset: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Surface(color = colors.surface, shadowElevation = 12.dp) {
        Column {
            HorizontalDivider(color = colors.outlineVariant)
            Column(
                Modifier.fillMaxWidth().navigationBarsPadding()
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (state.busy) ProgressPanel(state, onCancel)
                TextButton(
                    onClick = onReset,
                    enabled = !state.busy,
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Icon(painterResource(R.drawable.ic_refresh), null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Reset")
                }
                Button(
                    onClick = onConvert,
                    enabled = state.canRun,
                    modifier = Modifier.fillMaxWidth().height(54.dp),
                    shape = MaterialTheme.shapes.medium,
                    colors = if (state.operation == BatchOperation.DELETE)
                        ButtonDefaults.buttonColors(containerColor = colors.error, contentColor = colors.onError)
                    else ButtonDefaults.buttonColors()
                ) {
                    Icon(
                        painterResource(if (state.operation == BatchOperation.DELETE) R.drawable.ic_close else R.drawable.ic_sync),
                        null, Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        "${state.operation.label} ${state.selected.size} selected files",
                        style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

@Composable
private fun ProgressPanel(state: ConverterState, onCancel: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val work = state.work
    val completed = work?.progress?.getInt(ConversionWorker.COMPLETED, 0) ?: 0
    val total = work?.progress?.getInt(ConversionWorker.TOTAL, 0) ?: 0
    val operation = state.activeOperation
    val status = when {
        state.restoring -> "Restoring operation status..."
        state.submitting -> "Preparing ${operation.label.lowercase()}..."
        work?.state == WorkInfo.State.RUNNING && total > 0 ->
            "${operation.ongoing} ${minOf(completed + 1, total)} of $total files... ($completed finished)"
        else -> "${operation.label} queued. Waiting for Android to start the worker..."
    }
    Column(
        Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium)
            .background(colors.surfaceVariant.copy(alpha = 0.6f))
            .border(1.dp, colors.outlineVariant, MaterialTheme.shapes.medium)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
            Text(
                status, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(start = 10.dp).weight(1f)
            )
            if (total > 0) {
                Text(
                    "${completed * 100 / total}%", style = MaterialTheme.typography.labelLarge,
                    color = colors.primary, fontWeight = FontWeight.Bold
                )
            }
        }
        val barModifier = Modifier.fillMaxWidth().height(8.dp).clip(CircleShape)
        if (total > 0) {
            LinearProgressIndicator(
                progress = { completed.toFloat() / total }, modifier = barModifier, strokeCap = StrokeCap.Round
            )
        } else {
            LinearProgressIndicator(modifier = barModifier, strokeCap = StrokeCap.Round)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Work continues when you leave this screen. Cancelling does not undo completed changes.",
                style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
            if (work != null && !work.state.isFinished) {
                TextButton(onClick = onCancel) {
                    Icon(painterResource(R.drawable.ic_close), null, Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(if (operation == BatchOperation.CONVERT) "Cancel conversion" else "Cancel operation")
                }
            }
        }
    }
}

private fun folderLabel(value: String?): String {
    if (value == null) return "No folder selected"
    val decoded = Uri.decode(value.substringAfterLast('/'))
    val path = decoded.substringAfter(':', decoded)
    return path.ifBlank { "Storage root" }
}
