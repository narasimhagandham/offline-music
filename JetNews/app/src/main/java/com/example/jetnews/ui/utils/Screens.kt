package com.example.jetnews.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubScreen(title: String, onBack: () -> Unit, actions: @Composable () -> Unit = {}, content: @Composable androidx.compose.foundation.layout.BoxScope.() -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                },
                actions = { actions() }
            )
        }
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) { content() }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary
    )
}

@Composable
private fun SwitchRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    ListItem(
        modifier = Modifier.clickable { onChange(!checked) },
        headlineContent = { Text(title) },
        supportingContent = { Text(subtitle) },
        trailingContent = { Switch(checked = checked, onCheckedChange = onChange) }
    )
}

@Composable
fun SettingsScreen(
    settings: AppSettings,
    folders: List<Pair<String, Int>>,
    onChange: (AppSettings) -> Unit,
    onBack: () -> Unit
) {
    SubScreen(title = "Settings", onBack = onBack) {
        Column(Modifier.verticalScroll(rememberScrollState())) {
            SectionTitle("Appearance")
            var themeMenu by remember { mutableStateOf(false) }
            Box {
                ListItem(
                    modifier = Modifier.clickable { themeMenu = true },
                    headlineContent = { Text("Theme") },
                    supportingContent = { Text(settings.theme.label) }
                )
                DropdownMenu(expanded = themeMenu, onDismissRequest = { themeMenu = false }) {
                    ThemeMode.entries.forEach { mode ->
                        DropdownMenuItem(
                            text = { Text(mode.label) },
                            onClick = {
                                themeMenu = false
                                onChange(settings.copy(theme = mode))
                            }
                        )
                    }
                }
            }
            SwitchRow(
                "Dynamic color",
                "Use wallpaper colors (Android 12+)",
                settings.dynamicColor
            ) { onChange(settings.copy(dynamicColor = it)) }
            SwitchRow(
                "Reduce animations",
                "Skip transitions between tabs",
                settings.reduceAnimations
            ) { onChange(settings.copy(reduceAnimations = it)) }
            SwitchRow(
                "Low power mode",
                "Update the UI less often while playing",
                settings.lowPower
            ) { onChange(settings.copy(lowPower = it)) }

            HorizontalDivider()
            SectionTitle("Library")
            Text(
                "Default sort order",
                Modifier.padding(horizontal = 20.dp),
                style = MaterialTheme.typography.bodyLarge
            )
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)
            ) {
                SortOrder.entries.forEach { order ->
                    FilterChip(
                        selected = settings.defaultSort == order,
                        onClick = { onChange(settings.copy(defaultSort = order)) },
                        label = { Text(order.label, maxLines = 1) }
                    )
                }
            }
            Text(
                "Ignore tracks shorter than ${settings.minDurationSec} s",
                Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                style = MaterialTheme.typography.bodyLarge
            )
            Slider(
                value = settings.minDurationSec.toFloat(),
                onValueChange = { onChange(settings.copy(minDurationSec = it.toInt())) },
                valueRange = 0f..120f,
                modifier = Modifier.padding(horizontal = 20.dp)
            )

            HorizontalDivider()
            SectionTitle("Playback")
            SwitchRow(
                "Resume on launch",
                "Restore the last queue when the app starts",
                settings.resumeOnLaunch
            ) { onChange(settings.copy(resumeOnLaunch = it)) }

            HorizontalDivider()
            SectionTitle("Folders")
            if (folders.isEmpty()) {
                Text("No folders found", Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
            }
            folders.forEach { (folder, count) ->
                val visible = folder !in settings.hiddenFolders
                ListItem(
                    modifier = Modifier.clickable {
                        val hidden = if (visible) settings.hiddenFolders + folder else settings.hiddenFolders - folder
                        onChange(settings.copy(hiddenFolders = hidden))
                    },
                    headlineContent = { Text(folder, maxLines = 1) },
                    supportingContent = { Text("$count songs") },
                    trailingContent = {
                        Checkbox(checked = visible, onCheckedChange = {
                            val hidden = if (it) settings.hiddenFolders - folder else settings.hiddenFolders + folder
                            onChange(settings.copy(hiddenFolders = hidden))
                        })
                    }
                )
            }
        }
    }
}

@Composable
fun QueueScreen(
    items: List<QueueItem>,
    currentIndex: Int,
    onPlayAt: (Int) -> Unit,
    onRemove: (Int) -> Unit,
    onMove: (Int, Int) -> Unit,
    onClear: () -> Unit,
    onBack: () -> Unit
) {
    SubScreen(
        title = "Queue (${items.size})",
        onBack = onBack,
        actions = {
            if (items.isNotEmpty()) {
                IconButton(onClick = onClear) { Icon(Icons.Default.Delete, "Clear queue") }
            }
        }
    ) {
        if (items.isEmpty()) {
            Text("The queue is empty", Modifier.align(Alignment.Center), color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            LazyColumn(Modifier.fillMaxSize()) {
                itemsIndexed(items, key = { i, item -> "${item.mediaId}#$i" }) { i, item ->
                    val current = i == currentIndex
                    ListItem(
                        modifier = Modifier.clickable { onPlayAt(i) },
                        colors = if (current) {
                            ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                        } else {
                            ListItemDefaults.colors()
                        },
                        leadingContent = { ArtThumb(item.art, 48) },
                        headlineContent = { Text(item.title, maxLines = 1, fontWeight = if (current) FontWeight.Bold else null) },
                        supportingContent = { Text(item.artist, maxLines = 1) },
                        trailingContent = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(onClick = { onMove(i, i - 1) }, enabled = i > 0) {
                                    Icon(Icons.Default.KeyboardArrowUp, "Move up")
                                }
                                IconButton(onClick = { onMove(i, i + 1) }, enabled = i < items.size - 1) {
                                    Icon(Icons.Default.KeyboardArrowDown, "Move down")
                                }
                                IconButton(onClick = { onRemove(i) }) { Icon(Icons.Default.Remove, "Remove") }
                            }
                        }
                    )
                }
            }
        }
    }
}
