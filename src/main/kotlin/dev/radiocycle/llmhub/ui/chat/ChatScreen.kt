package dev.radiocycle.llmhub.ui.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.radiocycle.llmhub.data.model.Provider
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    viewModel: ChatViewModel,
    onOpenProviders: () -> Unit,
) {
    val state by viewModel.state.collectAsState()
    val providers by viewModel.providers.collectAsState()
    var showModelPicker by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                state.title,
                                maxLines = 1,
                                style = MaterialTheme.typography.titleMedium,
                            )
                            ActiveRouteLabel(state, providers)
                        }
                    },
                    actions = {
                        IconButton(onClick = { showModelPicker = true }) {
                            Icon(Icons.Rounded.Tune, contentDescription = "Choose model")
                        }
                        IconButton(onClick = viewModel::retryLast, enabled = !state.isStreaming) {
                            Icon(Icons.Rounded.Refresh, contentDescription = "Retry last turn")
                        }
                        IconButton(onClick = viewModel::newChat) {
                            Icon(Icons.Rounded.Add, contentDescription = "New chat")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    ),
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))
            }
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
            contentAlignment = Alignment.TopCenter,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxHeight()
                    .widthIn(max = 880.dp)
                    .fillMaxWidth(),
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                ) {
                    key(state.conversationId) {
                        if (state.messages.isEmpty()) {
                            EmptyState(
                                hasProviders = providers.any { it.enabled },
                                onOpenProviders = onOpenProviders,
                            )
                        } else {
                            MessageList(state)
                        }
                    }
                }

                Composer(
                    isStreaming = state.isStreaming,
                    onSend = viewModel::send,
                    onStop = viewModel::stop,
                )
            }

            state.notice?.let { notice ->
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                    shape = RoundedCornerShape(18.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.4f)),
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 90.dp, start = 16.dp, end = 16.dp),
                ) {
                    Row(
                        Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(notice, style = MaterialTheme.typography.bodyMedium)
                        Spacer(Modifier.size(12.dp))
                        TextButton(onClick = viewModel::dismissNotice) { Text("Dismiss") }
                    }
                }
            }
        }
    }

    if (showModelPicker) {
        ModelPickerDialog(
            providers = providers,
            selectedProviderId = state.pinnedProviderId,
            selectedModel = state.pinnedModel,
            onPick = { providerId, model ->
                viewModel.pin(providerId, model)
                showModelPicker = false
            },
            onDismiss = { showModelPicker = false },
            onManageProviders = {
                showModelPicker = false
                onOpenProviders()
            },
        )
    }
}

@Composable
private fun ActiveRouteLabel(state: ChatUiState, providers: List<Provider>) {
    val provider = providers.firstOrNull { it.id == state.pinnedProviderId }
    val label = when {
        provider != null -> "${provider.name} · ${state.pinnedModel ?: provider.defaultModel}"
        providers.count { it.enabled } > 0 -> "Auto-rotation · ${providers.count { it.enabled }} providers"
        else -> "No providers configured"
    }
    Text(
        text = label,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
    )
}

@Composable
private fun MessageList(state: ChatUiState) {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var autoScroll by remember { mutableStateOf(true) }
    val isDragged by listState.interactionSource.collectIsDraggedAsState()

    val canScrollForward by remember {
        derivedStateOf { listState.canScrollForward }
    }

    LaunchedEffect(listState) {
        snapshotFlow { isDragged to listState.canScrollForward }
            .collect { (dragged, canForward) ->
                if (dragged && canForward) {
                    autoScroll = false
                }
            }
    }

    LaunchedEffect(listState) {
        snapshotFlow { listState.isScrollInProgress to listState.canScrollForward }
            .collect { (inProgress, canForward) ->
                if (!inProgress && !canForward) {
                    autoScroll = true
                }
            }
    }

    var lastMessageCount by remember { mutableStateOf(state.messages.size) }
    LaunchedEffect(state.messages.size) {
        if (state.messages.size > lastMessageCount) {
            autoScroll = true
            val target = state.messages.size + if (state.isStreaming) 1 else 0
            listState.animateScrollToItem(target)
        }
        lastMessageCount = state.messages.size
    }

    LaunchedEffect(Unit) {
        if (state.messages.isNotEmpty()) {
            val target = state.messages.size + if (state.isStreaming) 1 else 0
            listState.scrollToItem(target)
        }
    }

    LaunchedEffect(state.messages.lastOrNull()?.content?.length, state.isStreaming) {
        if (autoScroll && !isDragged && !listState.isScrollInProgress && state.messages.isNotEmpty()) {
            val target = state.messages.size + if (state.isStreaming) 1 else 0
            listState.scrollToItem(target)
        }
    }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            items(state.messages, key = { it.id }) { message ->
                MessageItem(
                    message = message,
                    isLast = message.id == state.messages.lastOrNull()?.id,
                    isStreaming = state.isStreaming,
                )
            }
            if (state.isStreaming) {
                item(key = "streaming_indicator") {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 4.dp)) {
                        CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.size(10.dp))
                        Text(
                            "Working…",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            item(key = "bottom_spacer") { Spacer(Modifier.height(16.dp)) }
        }

        AnimatedVisibility(
            visible = canScrollForward,
            enter = fadeIn() + scaleIn(),
            exit = fadeOut() + scaleOut(),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 16.dp, bottom = 12.dp),
        ) {
            SmallFloatingActionButton(
                onClick = {
                    autoScroll = true
                    scope.launch {
                        val target = state.messages.size + if (state.isStreaming) 1 else 0
                        listState.animateScrollToItem(target)
                    }
                },
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                contentColor = MaterialTheme.colorScheme.primary,
                shape = RoundedCornerShape(16.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Rounded.ArrowDownward,
                        contentDescription = "Scroll to bottom",
                        modifier = Modifier.size(20.dp),
                    )
                    if (state.isStreaming) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .size(6.dp)
                                .align(Alignment.TopEnd),
                        ) {}
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyState(hasProviders: Boolean, onOpenProviders: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Surface(
            color = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            shape = CircleShape,
        ) {
            Icon(
                Icons.Rounded.AutoAwesome,
                contentDescription = null,
                modifier = Modifier.padding(24.dp).size(40.dp),
            )
        }
        Spacer(Modifier.height(20.dp))
        Text(
            if (hasProviders) "Ask anything" else "Set up a provider first",
            style = MaterialTheme.typography.headlineSmall,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = if (hasProviders) {
                "Requests rotate across your enabled providers. If one fails mid-answer, the next one " +
                    "picks the reply up where it stopped."
            } else {
                "Add an API key for OpenAI, Anthropic, Gemini or any OpenAI-compatible endpoint."
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (!hasProviders) {
            Spacer(Modifier.height(20.dp))
            TextButton(onClick = onOpenProviders) { Text("Open providers") }
        }
    }
}

@Composable
private fun Composer(isStreaming: Boolean, onSend: (String) -> Unit, onStop: () -> Unit) {
    var text by remember { mutableStateOf("") }

    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(24.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
        tonalElevation = 3.dp,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, bottom = 14.dp, top = 6.dp),
    ) {
        Row(
            Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            TextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 44.dp, max = 180.dp)
                    .onPreviewKeyEvent {
                        if (it.key == Key.Enter && !it.isShiftPressed) {
                            if (!isStreaming && text.isNotBlank()) {
                                onSend(text)
                                text = ""
                                true
                            } else false
                        } else false
                    },
                placeholder = {
                    Text(
                        "Message (Enter to send, Shift+Enter for new line)",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    )
                },
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    disabledContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    disabledIndicatorColor = Color.Transparent,
                ),
                maxLines = 6,
            )

            FilledIconButton(
                onClick = {
                    if (isStreaming) {
                        onStop()
                    } else if (text.isNotBlank()) {
                        onSend(text)
                        text = ""
                    }
                },
                modifier = Modifier.size(42.dp),
                shape = CircleShape,
                colors = if (isStreaming) {
                    IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer,
                    )
                } else {
                    IconButtonDefaults.filledIconButtonColors(
                        containerColor = if (text.isNotBlank()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh,
                        contentColor = if (text.isNotBlank()) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
            ) {
                Icon(
                    if (isStreaming) Icons.Rounded.Stop else Icons.Rounded.ArrowUpward,
                    contentDescription = if (isStreaming) "Stop" else "Send",
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}
