package com.askew137.gcwatch.mobile.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.foundation.clickable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.askew137.gcwatch.mobile.routing.SegmentRepository
import java.io.File
import java.text.DecimalFormat

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapsScreen(
    viewModel: MobileViewModel,
    onNavigateUp: () -> Unit,
) {
    val mapFiles by viewModel.mapFiles.collectAsState()
    val copyProgress by viewModel.copyProgress.collectAsState()

    // SAF pickers
    val mapPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? ->
        uri?.let { viewModel.copyMapFile(it) }
    }

    Scaffold(
        containerColor = Color.Black,
        topBar = {
            TopAppBar(
                title = { Text("Maps & Routing", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateUp) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Black,
                    titleContentColor = MaterialTheme.colorScheme.primary,
                    navigationIconContentColor = MaterialTheme.colorScheme.primary,
                ),
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { mapPicker.launch(arrayOf("*/*")) },
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("Add map file") },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            // ── Map files section ─────────────────────────────────────────────
            SectionHeader("Map files (.map)")

            if (mapFiles.isEmpty()) {
                EmptyHint("No .map files yet. Tap '+' to add one.")
            } else {
                mapFiles.forEach { file ->
                    MapFileCard(file = file, onSendToWatch = { viewModel.sendMapToWatch(file) })
                }
            }

            // Copy progress
            copyProgress?.let { progress ->
                LinearProgressIndicator(
                    progress = {
                        if (progress.progressPercent >= 0) progress.progressPercent / 100f else 0f
                    },
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    "Copying ${progress.fileName}…",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // ── Info card ─────────────────────────────────────────────────────
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "Where to get map files",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    ExternalLinkText(
                        label = "OpenAndroMaps.org",
                        url   = "https://www.openandromaps.org",
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    ExternalLinkText(
                        label = "BBBike.org",
                        url   = "https://extract.bbbike.org",
                    )
                }
            }

            // ── Routing data ───────────────────────────────────────────────────
            RoutingDataSection(viewModel)

            Spacer(modifier = Modifier.height(80.dp)) // FAB clearance
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(vertical = 4.dp),
    )
}

@Composable
private fun EmptyHint(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(vertical = 4.dp),
    )
}

@Composable
private fun MapFileCard(file: File, onSendToWatch: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .padding(12.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    file.name,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    formatFileSize(file.length()),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Button(onClick = onSendToWatch) {
                Text("Send to watch")
            }
        }
    }
}

@Composable
private fun ExternalLinkText(label: String, url: String) {
    val context = LocalContext.current
    Text(
        text = label,
        style = MaterialTheme.typography.bodyMedium.copy(
            textDecoration = TextDecoration.Underline,
        ),
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .padding(vertical = 2.dp)
            .clickable {
                val intent = android.content.Intent(
                    android.content.Intent.ACTION_VIEW,
                    android.net.Uri.parse(url),
                )
                context.startActivity(intent)
            },
    )
}

private val sizeFormat = DecimalFormat("#.##")

private fun formatFileSize(bytes: Long): String = when {
    bytes >= 1_073_741_824L -> "${sizeFormat.format(bytes / 1_073_741_824.0)} GB"
    bytes >= 1_048_576L     -> "${sizeFormat.format(bytes / 1_048_576.0)} MB"
    bytes >= 1_024L         -> "${sizeFormat.format(bytes / 1_024.0)} KB"
    else                    -> "$bytes B"
}


// ── Routing data (BRouter tiles) ──────────────────────────────────────────────

@Composable
private fun RoutingDataSection(viewModel: MobileViewModel) {
    val installed by viewModel.installedSegments.collectAsState()
    val download by viewModel.segmentDownload.collectAsState()
    val downloadError by viewModel.segmentDownloadError.collectAsState()
    val missing by viewModel.missingSegment.collectAsState()
    val sizes by viewModel.segmentRemoteSizes.collectAsState()

    HorizontalDivider(color = MaterialTheme.colorScheme.outline)
    SectionHeader("Navigation data (routes)")
    Text(
        "Routes on foot are computed on the phone from BRouter data. Download the tiles for " +
            "the areas you play in. Only the phone needs them – nothing is sent to the watch.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    val installedNames = installed.map { it.name.removeSuffix(".rd5") }.toSet()
    val missingTile = missing
    if (missingTile != null && missingTile !in installedNames) {
        LaunchedEffect(missingTile) { viewModel.fetchSegmentSize(missingTile) }
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    "The last route needs data that is not installed: $missingTile",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
                Spacer(modifier = Modifier.height(8.dp))
                Button(onClick = { viewModel.downloadSegment(missingTile) }, enabled = download == null) {
                    Text("Download $missingTile" + (sizes[missingTile]?.let { " (${formatFileSize(it)})" } ?: ""))
                }
            }
        }
    }

    // Known tiles first, then any other installed ones.
    val knownNames = SegmentRepository.knownTiles.map { it.name }
    val rows = SegmentRepository.knownTiles +
        installedNames.filter { it !in knownNames }.map { SegmentRepository.Tile(it, "") }

    rows.forEach { tile ->
        val file = installed.firstOrNull { it.name == "${tile.name}.rd5" }
        val active = download?.takeIf { it.tile == tile.name }
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                modifier = Modifier.padding(12.dp).fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        tile.name,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Medium,
                    )
                    if (tile.area.isNotEmpty()) {
                        Text(
                            tile.area,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(
                        when {
                            active != null && active.total > 0 ->
                                "Downloading ${active.bytes * 100 / active.total} % (${formatFileSize(active.total)})"
                            active != null -> "Downloading…"
                            file != null -> "Installed, ${formatFileSize(file.length())}"
                            else -> sizes[tile.name]?.let { "Not installed, ${formatFileSize(it)}" } ?: "Not installed"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (active != null && active.total > 0) {
                        LinearProgressIndicator(
                            progress = { active.bytes.toFloat() / active.total },
                            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
                when {
                    active != null -> Unit
                    file != null -> OutlinedButton(onClick = { viewModel.deleteSegment(tile.name) }) { Text("Delete") }
                    else -> Button(onClick = { viewModel.downloadSegment(tile.name) }, enabled = download == null) {
                        Text("Download")
                    }
                }
            }
        }
    }

    downloadError?.let {
        Text(
            "Download failed: $it",
            style = MaterialTheme.typography.bodySmall,
            color = Color.Red,
        )
    }
}
