package dev.radiocycle.llmhub.ui.chat

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import dev.radiocycle.llmhub.data.model.Provider

@Composable
fun ModelPickerDialog(
    providers: List<Provider>,
    selectedProviderId: String?,
    selectedModel: String?,
    onPick: (providerId: String?, model: String?) -> Unit,
    onDismiss: () -> Unit,
    onManageProviders: () -> Unit,
) {
    var searchQuery by remember { mutableStateOf("") }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            modifier = Modifier.width(620.dp).heightIn(max = 680.dp),
        ) {
            Column(Modifier.padding(top = 16.dp, bottom = 8.dp)) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Select Model or Route", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    TextButton(onClick = onManageProviders) { Text("Manage providers") }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Rounded.Close, contentDescription = "Close")
                    }
                }

                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search models (e.g. sonnet, 4o, r1, flash)…") },
                    leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = "Search") },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Rounded.Close, contentDescription = "Clear search")
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(24.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 8.dp),
                )

                LazyColumn(Modifier.weight(1f, fill = false)) {
                    if (searchQuery.isBlank()) {
                        item {
                            RouteRow(
                                title = "Automatic rotation",
                                subtitle = "Use the whole pool in the configured order",
                                selected = selectedProviderId == null,
                                leading = { Icon(Icons.Rounded.Shuffle, contentDescription = null) },
                                onClick = { onPick(null, null) },
                            )
                            HorizontalDivider(Modifier.padding(vertical = 8.dp, horizontal = 16.dp))
                        }

                        providers.forEach { provider ->
                            item {
                                Text(
                                    text = "${provider.name}  ·  ${provider.apiMode.label}",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(start = 24.dp, top = 12.dp, bottom = 4.dp),
                                )
                            }
                            val models = provider.models.ifEmpty { listOfNotNull(provider.defaultModel.takeIf { it.isNotBlank() }) }
                            if (models.isEmpty()) {
                                item {
                                    Text(
                                        "No models — open provider to fetch them",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(start = 24.dp, bottom = 8.dp),
                                    )
                                }
                            }
                            for (model in models) {
                                item(key = "${provider.id}:$model") {
                                    RouteRow(
                                        title = model,
                                        subtitle = if (provider.enabled) null else "provider disabled",
                                        selected = provider.id == selectedProviderId && model == selectedModel,
                                        leading = null,
                                        onClick = { onPick(provider.id, model) },
                                    )
                                }
                            }
                        }
                    } else {
                        val autoMatches = fuzzyMatchScore(searchQuery, "automatic rotation auto") > 0
                        if (autoMatches) {
                            item {
                                RouteRow(
                                    title = "Automatic rotation",
                                    subtitle = "Use the whole pool in the configured order",
                                    selected = selectedProviderId == null,
                                    leading = { Icon(Icons.Rounded.Shuffle, contentDescription = null) },
                                    onClick = { onPick(null, null) },
                                )
                                HorizontalDivider(Modifier.padding(vertical = 8.dp, horizontal = 16.dp))
                            }
                        }

                        // Filter and score models across all providers
                        val matchedModels = providers.flatMap { provider ->
                            val models = provider.models.ifEmpty { listOfNotNull(provider.defaultModel.takeIf { it.isNotBlank() }) }
                            val providerScore = fuzzyMatchScore(searchQuery, "${provider.name} ${provider.apiMode.label}")
                            models.mapNotNull { model ->
                                val modelScore = fuzzyMatchScore(searchQuery, model)
                                val finalScore = maxOf(modelScore, if (providerScore > 0) providerScore - 40 else -1)
                                if (finalScore > 0) ScoredModel(provider, model, finalScore) else null
                            }
                        }.sortedByDescending { it.score }

                        if (matchedModels.isEmpty() && !autoMatches) {
                            item {
                                Box(
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 40.dp, horizontal = 24.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        "No models matching \"$searchQuery\"",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        } else {
                            matchedModels.forEach { item ->
                                val provider = item.provider
                                val model = item.model
                                item(key = "${provider.id}:$model") {
                                    RouteRow(
                                        title = model,
                                        subtitle = buildString {
                                            append(provider.name)
                                            append(" · ")
                                            append(provider.apiMode.label)
                                            if (!provider.enabled) append(" · disabled")
                                        },
                                        selected = provider.id == selectedProviderId && model == selectedModel,
                                        leading = null,
                                        onClick = { onPick(provider.id, model) },
                                    )
                                }
                            }
                        }
                    }

                    item { Spacer(Modifier.height(16.dp)) }
                }
            }
        }
    }
}

private data class ScoredModel(
    val provider: Provider,
    val model: String,
    val score: Int,
)

private fun fuzzyMatchScore(query: String, target: String): Int {
    if (query.isBlank()) return 0
    val q = query.trim().lowercase()
    val t = target.lowercase()

    // 1. Exact match
    if (t == q) return 1000

    // 2. Prefix match
    if (t.startsWith(q)) return 500

    // 3. Substring match
    val subIndex = t.indexOf(q)
    if (subIndex >= 0) return 350 - subIndex.coerceAtMost(50)

    // 4. Token match (split by space, dash, slash, dot, underscore)
    val queryWords = q.split(' ', '-', '_', '.', '/').filter { it.isNotEmpty() }
    val targetWords = t.split(' ', '-', '_', '.', '/').filter { it.isNotEmpty() }
    if (queryWords.isNotEmpty() && queryWords.all { qw -> targetWords.any { tw -> tw.startsWith(qw) || tw.contains(qw) } }) {
        return 200
    }

    // 5. Subsequence fuzzy match (e.g. c37s -> claude-3-7-sonnet)
    var qIdx = 0
    var score = 0
    var prevMatch = -2
    for (i in t.indices) {
        if (qIdx < q.length && t[i] == q[qIdx]) {
            score += 10
            if (i == prevMatch + 1) score += 20 // Consecutive bonus
            if (i == 0 || !t[i - 1].isLetterOrDigit()) score += 20 // Word boundary bonus
            prevMatch = i
            qIdx++
        }
    }

    return if (qIdx == q.length) score else -1
}

@Composable
private fun RouteRow(
    title: String,
    subtitle: String?,
    selected: Boolean,
    leading: (@Composable () -> Unit)?,
    onClick: () -> Unit,
) {
    Surface(
        color = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 2.dp)
            .clickable(onClick = onClick),
    ) {
        Row(
            Modifier.padding(horizontal = 14.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            leading?.invoke()
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyLarge, maxLines = 1)
                subtitle?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (selected) Icon(Icons.Rounded.Check, contentDescription = null, Modifier.size(20.dp))
        }
    }
}
