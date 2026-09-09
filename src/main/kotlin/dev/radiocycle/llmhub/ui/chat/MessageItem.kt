package dev.radiocycle.llmhub.ui.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.NoteAdd
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.radiocycle.llmhub.data.model.ChatMessage
import dev.radiocycle.llmhub.data.model.Role
import dev.radiocycle.llmhub.data.model.ToolResult
import dev.radiocycle.llmhub.ui.common.MarkdownText

@Composable
fun MessageItem(
    message: ChatMessage,
    isLast: Boolean,
    isStreaming: Boolean,
) {
    when (message.role) {
        Role.USER -> UserMessage(message)
        Role.TOOL -> ToolMessage(message)
        Role.ASSISTANT -> AssistantMessage(
            message = message,
            showCursor = isLast && isStreaming,
        )
        Role.SYSTEM -> Unit
    }
}

@Composable
private fun UserMessage(message: ChatMessage) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        Surface(
            color = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp, bottomStart = 24.dp, bottomEnd = 6.dp),
            modifier = Modifier.widthIn(max = 680.dp),
        ) {
            Text(
                text = message.content,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 13.dp),
            )
        }
    }
}

@Composable
private fun AssistantMessage(message: ChatMessage, showCursor: Boolean) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (message.providerName != null) {
            ProviderBadge(message.providerName, message.model)
        }

        message.switches.forEach { note -> SwitchNotice(note) }

        if (message.reasoning.isNotBlank()) {
            CollapsibleBlock(
                title = "Reasoning",
                icon = Icons.Rounded.Bolt,
                body = message.reasoning,
                container = MaterialTheme.colorScheme.surfaceContainerLow,
            )
        }

        if (message.content.isNotBlank()) {
            val body = if (showCursor) message.content + "▍" else message.content
            MarkdownText(body, Modifier.fillMaxWidth())
        } else if (showCursor && message.error == null) {
            Text("▍", style = MaterialTheme.typography.bodyLarge)
        }

        message.toolCalls.takeIf { it.isNotEmpty() }?.let { calls ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                calls.forEach { call -> ToolChip(call.name) }
            }
        }

        message.error?.let { error ->
            Surface(
                color = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer,
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    Modifier.padding(14.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    Icon(Icons.Rounded.ErrorOutline, contentDescription = null, Modifier.size(20.dp))
                    Text(error, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

@Composable
private fun ProviderBadge(providerName: String, model: String?) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(
            Modifier
                .size(8.dp)
                .clip(RoundedCornerShape(50))
                .background(MaterialTheme.colorScheme.tertiary)
        )
        Text(
            text = buildString {
                append(providerName)
                model?.takeIf { it.isNotBlank() }?.let { append(" · $it") }
            },
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SwitchNotice(note: String) {
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        shape = RoundedCornerShape(14.dp),
    ) {
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(Icons.Rounded.SwapHoriz, contentDescription = null, Modifier.size(16.dp))
            Text(note, style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
private fun ToolChip(name: String) {
    Surface(
        color = MaterialTheme.colorScheme.tertiaryContainer,
        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
        shape = RoundedCornerShape(12.dp),
    ) {
        Row(
            Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(iconForTool(name), contentDescription = null, Modifier.size(15.dp))
            Text(name, style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
private fun ToolMessage(message: ChatMessage) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        message.toolResults.forEach { result -> ToolResultCard(result) }
    }
}

@Composable
private fun ToolResultCard(result: ToolResult) {
    val running = result.content == "…running"
    CollapsibleBlock(
        title = buildString {
            append(result.name)
            when {
                running -> append(" · running")
                result.isError -> append(" · failed")
                result.durationMs > 0 -> append(" · ${result.durationMs} ms")
            }
        },
        icon = iconForTool(result.name),
        body = result.content,
        container = if (result.isError) MaterialTheme.colorScheme.errorContainer
        else MaterialTheme.colorScheme.surfaceContainerHigh,
        monospace = true,
    )
}

@Composable
private fun CollapsibleBlock(
    title: String,
    icon: ImageVector,
    body: String,
    container: androidx.compose.ui.graphics.Color,
    monospace: Boolean = false,
) {
    var expanded by remember { mutableStateOf(false) }
    Surface(
        color = container,
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(),
    ) {
        Column {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .padding(horizontal = 14.dp, vertical = 11.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(icon, contentDescription = null, Modifier.size(18.dp))
                Text(title, style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
                Icon(
                    if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                    contentDescription = if (expanded) "Collapse" else "Expand",
                    modifier = Modifier.size(18.dp),
                )
            }
            AnimatedVisibility(expanded) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(start = 14.dp, end = 14.dp, bottom = 12.dp)
                ) {
                    if (monospace) {
                        Text(
                            text = body,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                            lineHeight = 17.sp,
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                        )
                    } else {
                        Text(body, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
}

private fun iconForTool(name: String): ImageVector = when (name) {
    "web_search" -> Icons.Rounded.Search
    "web_fetch" -> Icons.Rounded.Language
    "exec_js" -> Icons.Rounded.Code
    "shell" -> Icons.Rounded.Terminal
    "read_file" -> Icons.Rounded.Description
    "write_file" -> Icons.Rounded.NoteAdd
    "edit_file" -> Icons.Rounded.EditNote
    "delete_file" -> Icons.Rounded.DeleteOutline
    "list_files" -> Icons.Rounded.FolderOpen
    else -> Icons.Rounded.Bolt
}
