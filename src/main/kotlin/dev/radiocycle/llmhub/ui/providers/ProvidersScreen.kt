package dev.radiocycle.llmhub.ui.providers

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.FileUpload
import androidx.compose.material.icons.rounded.HealthAndSafety
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import dev.radiocycle.llmhub.data.model.ApiMode
import dev.radiocycle.llmhub.data.model.BuiltInPresets
import dev.radiocycle.llmhub.data.model.Endpoint
import dev.radiocycle.llmhub.data.model.EndpointHealth
import dev.radiocycle.llmhub.data.model.KeyParser
import dev.radiocycle.llmhub.data.model.Provider
import dev.radiocycle.llmhub.ui.common.DesktopFilePicker

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProvidersScreen(viewModel: ProvidersViewModel, onEdit: () -> Unit) {
    val providers by viewModel.providers.collectAsState()
    val health by viewModel.health.collectAsState()
    var showPresets by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<Provider?>(null) }
    var pendingImportForProvider by remember { mutableStateOf<Provider?>(null) }
    var pendingImportKeys by remember { mutableStateOf<List<String>?>(null) }
    var importError by remember { mutableStateOf<String?>(null) }

    val handleFileImport = { target: Provider ->
        val file = DesktopFilePicker.pickFile("Select API Keys File")
        if (file != null) {
            val content = runCatching { file.readText() }.getOrNull()
            if (content.isNullOrBlank()) {
                importError = "The selected file is empty or could not be read"
            } else if (content.contains('\u0000')) {
                importError = "The selected file appears to be binary, not text"
            } else {
                val keys = KeyParser.parse(content)
                if (keys.isEmpty()) {
                    importError = "No valid keys found in file. Ensure keys are on separate lines or comma-separated."
                } else if (target.keys.isEmpty()) {
                    viewModel.addKeysToProvider(target.id, keys, append = false)
                } else {
                    pendingImportForProvider = target
                    pendingImportKeys = keys
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Providers & Endpoints") },
                actions = {
                    IconButton(onClick = viewModel::clearHealth) {
                        Icon(Icons.Rounded.HealthAndSafety, contentDescription = "Reset health stats")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                ),
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showPresets = true },
                icon = { Icon(Icons.Rounded.Add, contentDescription = null) },
                text = { Text("Add provider") },
            )
        },
    ) { padding ->
        if (providers.isEmpty()) {
            Column(
                Modifier.padding(padding).fillMaxSize().padding(32.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("No providers yet", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(8.dp))
                Text(
                    "Add one from a preset or define a fully custom endpoint with your own base URL, " +
                        "headers and API mode.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.padding(padding).fillMaxSize(),
                contentPadding = PaddingValues(20.dp, 12.dp, 20.dp, 96.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    Text(
                        "Requests try providers top to bottom. Order is adjusted with the arrows.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 4.dp),
                    )
                }
                items(providers.sortedBy { it.priority }, key = { it.id }) { provider ->
                    ProviderCard(
                        provider = provider,
                        health = (0 until provider.keySlotCount)
                            .map { slot -> health[Endpoint(provider, slot).id] },
                        onToggle = { viewModel.setEnabled(provider.id, it) },
                        onEdit = {
                            viewModel.startEdit(provider.id)
                            onEdit()
                        },
                        onDelete = { pendingDelete = provider },
                        onMoveUp = { viewModel.move(provider.id, -1) },
                        onMoveDown = { viewModel.move(provider.id, +1) },
                        onImportKeys = { handleFileImport(provider) },
                    )
                }
            }
        }
    }

    if (showPresets) {
        Dialog(onDismissRequest = { showPresets = false }) {
            Surface(
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 6.dp,
                modifier = Modifier.width(560.dp).heightIn(max = 620.dp),
            ) {
                Column(Modifier.padding(20.dp)) {
                    Row(
                        Modifier.fillMaxWidth().padding(bottom = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "Start from a preset",
                            style = MaterialTheme.typography.titleMedium,
                        )
                        IconButton(onClick = { showPresets = false }) {
                            Icon(Icons.Rounded.Close, contentDescription = "Close")
                        }
                    }

                    LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(BuiltInPresets.all, key = { it.id }) { preset ->
                            Surface(
                                shape = RoundedCornerShape(18.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerLow,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        viewModel.startFromPreset(preset)
                                        showPresets = false
                                        onEdit()
                                    },
                            ) {
                                Column(Modifier.padding(16.dp)) {
                                    Text(preset.name, style = MaterialTheme.typography.bodyLarge)
                                    Text(
                                        text = "${preset.apiMode.label} · ${preset.baseUrl.ifBlank { "your endpoint" }}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    pendingDelete?.let { provider ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Remove ${provider.name}?") },
            text = { Text("The endpoint and its API keys will be deleted from this device.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.delete(provider.id)
                    pendingDelete = null
                }) { Text("Remove", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("Cancel") } },
        )
    }

    pendingImportKeys?.let { importedKeys ->
        val target = pendingImportForProvider
        if (target != null) {
            AlertDialog(
                onDismissRequest = {
                    pendingImportKeys = null
                    pendingImportForProvider = null
                },
                title = { Text("Import ${importedKeys.size} keys to ${target.name}") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Found ${importedKeys.size} keys in file.")
                        Text(
                            "Current key pool has ${target.keys.size} keys. Would you like to append or replace?",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Text(
                            text = "Preview: " + importedKeys.take(2).joinToString {
                                if (it.length > 10) it.take(4) + "…" + it.takeLast(4) else it
                            } + if (importedKeys.size > 2) " (+${importedKeys.size - 2} more)" else "",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                confirmButton = {
                    Button(onClick = {
                        viewModel.addKeysToProvider(target.id, importedKeys, append = true)
                        pendingImportKeys = null
                        pendingImportForProvider = null
                    }) {
                        Text("Append")
                    }
                },
                dismissButton = {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = {
                            pendingImportKeys = null
                            pendingImportForProvider = null
                        }) {
                            Text("Cancel")
                        }
                        TextButton(onClick = {
                            viewModel.addKeysToProvider(target.id, importedKeys, append = false)
                            pendingImportKeys = null
                            pendingImportForProvider = null
                        }) {
                            Text("Replace")
                        }
                    }
                },
            )
        }
    }

    importError?.let { err ->
        AlertDialog(
            onDismissRequest = { importError = null },
            title = { Text("Import error") },
            text = { Text(err) },
            confirmButton = {
                TextButton(onClick = { importError = null }) { Text("OK") }
            },
        )
    }
}

@Composable
private fun ProviderCard(
    provider: Provider,
    health: List<EndpointHealth?>,
    onToggle: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onImportKeys: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = if (provider.enabled) MaterialTheme.colorScheme.surfaceContainer
        else MaterialTheme.colorScheme.surfaceContainerLowest,
        modifier = Modifier.fillMaxWidth().animateContentSize(),
    ) {
        Column(Modifier.padding(start = 20.dp, end = 12.dp, top = 16.dp, bottom = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(provider.name.ifBlank { "Unnamed" }, style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = buildString {
                            if (provider.apiMode == ApiMode.OPENAI) {
                                append("OpenAI (${provider.openAiMode.shortLabel})")
                            } else {
                                append(provider.apiMode.label)
                            }
                            val effortVal = provider.effectiveEffortValue
                            if (!effortVal.isNullOrBlank()) {
                                append(" · ")
                                if (provider.effortParameter.isNotBlank()) {
                                    append("${provider.effortParameter}: $effortVal")
                                } else {
                                    append("effort: $effortVal")
                                }
                            }
                            append(" · ")
                            append(provider.baseUrl.ifBlank { "no base URL" })
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Switch(checked = provider.enabled, onCheckedChange = onToggle)
            }

            Spacer(Modifier.height(8.dp))
            HealthRow(provider, health)

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onImportKeys) {
                    Icon(Icons.Rounded.FileUpload, contentDescription = "Import keys from file", Modifier.size(18.dp))
                }
                IconButton(onClick = onMoveUp) {
                    Icon(Icons.Rounded.ArrowUpward, contentDescription = "Move up", Modifier.size(18.dp))
                }
                IconButton(onClick = onMoveDown) {
                    Icon(Icons.Rounded.ArrowDownward, contentDescription = "Move down", Modifier.size(18.dp))
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Rounded.Delete, contentDescription = "Delete", Modifier.size(18.dp))
                }
                IconButton(onClick = onEdit) {
                    Icon(Icons.Rounded.Edit, contentDescription = "Edit", Modifier.size(18.dp))
                }
            }
        }
    }
}

@Composable
private fun HealthRow(provider: Provider, health: List<EndpointHealth?>) {
    val slots = health.filterNotNull()
    val coolingCount = slots.count { it.isCoolingDown() }
    val allCooling = slots.isNotEmpty() && coolingCount == provider.keySlotCount
    val successes = slots.sumOf { it.successes }
    val failures = slots.sumOf { it.failures }
    val lastLatency = slots.maxOfOrNull { it.lastLatencyMs } ?: 0L
    val statusColor = when {
        !provider.enabled -> MaterialTheme.colorScheme.outline
        allCooling -> MaterialTheme.colorScheme.error
        coolingCount > 0 -> MaterialTheme.colorScheme.secondary
        successes > 0 -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.outline
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            Modifier
                .size(8.dp)
                .background(statusColor, RoundedCornerShape(50))
        )
        Text(
            text = buildString {
                when {
                    !provider.enabled -> append("disabled")
                    allCooling -> {
                        val soonest = slots.minOf { it.cooldownUntil }
                        val seconds = ((soonest - System.currentTimeMillis()) / 1000).coerceAtLeast(0)
                        append("all keys cooling down ${seconds}s")
                    }
                    coolingCount > 0 -> append("ready · $coolingCount of ${provider.keySlotCount} keys cooling")
                    else -> append("ready")
                }
                provider.keys.size.takeIf { it > 1 }?.let { append(" · $it keys") }
                if (successes > 0 || failures > 0) {
                    append(" · $successes ok / $failures fail")
                    if (lastLatency > 0) append(" · $lastLatency ms")
                }
                provider.models.size.takeIf { it > 0 }?.let { append(" · $it models") }
            },
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
        )
    }
    slots.lastOrNull { it.lastError != null }?.lastError?.takeIf { provider.enabled && allCooling }?.let { error ->
        Text(
            text = error,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.error,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}
