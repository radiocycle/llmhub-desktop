package dev.radiocycle.llmhub.ui.nav

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Chat
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Hub
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.radiocycle.llmhub.AppContainer
import dev.radiocycle.llmhub.ui.chat.ChatScreen
import dev.radiocycle.llmhub.ui.chat.ChatViewModel
import dev.radiocycle.llmhub.ui.providers.ProviderEditScreen
import dev.radiocycle.llmhub.ui.providers.ProvidersScreen
import dev.radiocycle.llmhub.ui.providers.ProvidersViewModel
import dev.radiocycle.llmhub.ui.settings.SettingsScreen

enum class DesktopTab(val label: String, val icon: ImageVector) {
    CHAT("Chat", Icons.AutoMirrored.Rounded.Chat),
    PROVIDERS("Providers", Icons.Rounded.Hub),
    SETTINGS("Settings", Icons.Rounded.Settings),
}

@Composable
fun DesktopAppShell(container: AppContainer) {
    val chatViewModel = remember(container) { ChatViewModel(container) }
    val providersViewModel = remember(container) { ProvidersViewModel(container) }

    var tab by remember { mutableStateOf(DesktopTab.CHAT) }
    var editingProvider by remember { mutableStateOf(false) }
    var showClearAllConfirm by remember { mutableStateOf(false) }

    val conversations by chatViewModel.conversations.collectAsState()
    val chatState by chatViewModel.state.collectAsState()

    if (showClearAllConfirm) {
        AlertDialog(
            onDismissRequest = { showClearAllConfirm = false },
            title = { Text("Clear all conversations?") },
            text = { Text("This will permanently delete all chat history.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        chatViewModel.deleteAllConversations()
                        showClearAllConfirm = false
                    },
                ) {
                    Text("Clear all", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearAllConfirm = false }) {
                    Text("Cancel")
                }
            },
        )
    }

    Row(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        // Left Sidebar: Width 280dp
        Surface(
            modifier = Modifier.width(280.dp).fillMaxHeight(),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            tonalElevation = 1.dp,
        ) {
            Column(Modifier.fillMaxSize().padding(12.dp)) {
                // App Brand Header
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Surface(
                        color = MaterialTheme.colorScheme.primary,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.size(28.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Rounded.Hub,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                    Text(
                        "LLMHub",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.weight(1f))
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        shape = RoundedCornerShape(8.dp),
                    ) {
                        Text(
                            "Linux",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        )
                    }
                }

                Spacer(Modifier.height(8.dp))

                // Navigation Tabs
                DesktopTab.entries.forEach { entry ->
                    NavigationDrawerItem(
                        label = { Text(entry.label) },
                        icon = { Icon(entry.icon, contentDescription = entry.label) },
                        selected = tab == entry && !editingProvider,
                        onClick = {
                            if (editingProvider) {
                                providersViewModel.discardDraft()
                                editingProvider = false
                            }
                            tab = entry
                        },
                        shape = RoundedCornerShape(16.dp),
                        colors = NavigationDrawerItemDefaults.colors(
                            selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                            selectedTextColor = MaterialTheme.colorScheme.onSecondaryContainer,
                            selectedIconColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        ),
                        modifier = Modifier.padding(vertical = 2.dp),
                    )
                }

                HorizontalDivider(Modifier.padding(vertical = 10.dp))

                // If in Chat tab: show Conversation list with New Chat button
                if (tab == DesktopTab.CHAT) {
                    NavigationDrawerItem(
                        label = { Text("New chat") },
                        icon = { Icon(Icons.Rounded.Add, contentDescription = null) },
                        selected = false,
                        onClick = { chatViewModel.newChat() },
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.padding(vertical = 2.dp),
                    )

                    Spacer(Modifier.height(8.dp))

                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            "Recent chats",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (conversations.isNotEmpty()) {
                            IconButton(
                                onClick = { showClearAllConfirm = true },
                                modifier = Modifier.size(24.dp),
                            ) {
                                Icon(
                                    Icons.Rounded.DeleteSweep,
                                    contentDescription = "Clear all chats",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(16.dp),
                                )
                            }
                        }
                    }

                    LazyColumn(Modifier.weight(1f)) {
                        items(conversations, key = { it.id }) { conversation ->
                            val isSelected = conversation.id == chatState.conversationId
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(
                                        if (isSelected) MaterialTheme.colorScheme.surfaceContainerHighest
                                        else MaterialTheme.colorScheme.surfaceContainerLow
                                    )
                                    .clickable { chatViewModel.open(conversation.id) }
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = conversation.title,
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f),
                                )
                                IconButton(
                                    onClick = { chatViewModel.deleteConversation(conversation.id) },
                                    modifier = Modifier.size(24.dp),
                                ) {
                                    Icon(
                                        Icons.Rounded.Delete,
                                        contentDescription = "Delete chat",
                                        modifier = Modifier.size(16.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                } else {
                    Spacer(Modifier.weight(1f))
                }
            }
        }

        // Vertical divider between sidebar and main pane
        Box(
            Modifier
                .width(1.dp)
                .fillMaxHeight()
                .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        )

        // Main Content Area
        Box(Modifier.weight(1f).fillMaxHeight()) {
            AnimatedContent(
                targetState = if (editingProvider) null else tab,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "desktop_screen",
            ) { target ->
                when (target) {
                    null -> ProviderEditScreen(
                        viewModel = providersViewModel,
                        onClose = { editingProvider = false },
                    )

                    DesktopTab.CHAT -> ChatScreen(
                        viewModel = chatViewModel,
                        onOpenProviders = { tab = DesktopTab.PROVIDERS },
                    )

                    DesktopTab.PROVIDERS -> ProvidersScreen(
                        viewModel = providersViewModel,
                        onEdit = { editingProvider = true },
                    )

                    DesktopTab.SETTINGS -> SettingsScreen(
                        repository = container.settings,
                        workspace = container.workspace,
                        providersViewModel = providersViewModel,
                    )
                }
            }
        }
    }
}
