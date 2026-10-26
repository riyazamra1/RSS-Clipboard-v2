package com.riyaz.rssclipboard

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.riyaz.rssclipboard.data.ClipEntity
import com.riyaz.rssclipboard.ui.ClipboardViewModel
import com.riyaz.rssclipboard.ui.FilterTab

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val sharedText = when (intent?.action) {
            Intent.ACTION_SEND -> intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString()
            Intent.ACTION_PROCESS_TEXT -> intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)?.toString()
            else -> null
        }
        setContent { MaterialTheme { ClipboardScreen(sharedText) } }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ClipboardScreen(sharedText: String?, vm: ClipboardViewModel = viewModel()) {
    val context = LocalContext.current
    val clips by vm.clips.collectAsState()
    val query by vm.searchQuery.collectAsState()
    val selected by vm.selectedFilter.collectAsState()
    var showClearConfirm by remember { mutableStateOf(false) }
    var editingClip by remember { mutableStateOf<ClipEntity?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("RSS Clipboard", fontWeight = FontWeight.Bold)
                        Text("Your personal clipboard vault", style = MaterialTheme.typography.labelSmall)
                    }
                },
                actions = {
                    IconButton(onClick = { showClearConfirm = true }) {
                        Icon(Icons.Default.Delete, contentDescription = "Clear unpinned clips")
                    }
                }
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp)) {
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = query,
                onValueChange = vm::onSearchQueryChange,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Search clipboard") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                singleLine = true,
                shape = RoundedCornerShape(16.dp)
            )
            Spacer(Modifier.height(10.dp))
            androidx.compose.foundation.lazy.LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(FilterTab.entries.toList()) { filter ->
                    FilterChip(
                        selected = selected == filter,
                        onClick = { vm.onFilterChange(filter) },
                        label = { Text(filter.label) }
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("${clips.size} clips", style = MaterialTheme.typography.labelLarge)
                Text("Unpinned clips expire after 24 hours", style = MaterialTheme.typography.labelSmall)
            }
            Spacer(Modifier.height(8.dp))
            if (clips.isEmpty()) {
                Surface(
                    modifier = Modifier.fillMaxWidth().padding(top = 28.dp),
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Your clipboard is empty", style = MaterialTheme.typography.titleMedium)
                        Text("Copy text on your device and enable RSS Clipboard in Android Accessibility settings to capture it automatically.")
                        Button(onClick = {
                            context.startActivity(Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS))
                        }) { Text("Enable clipboard capture") }
                    }
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(clips, key = { it.id }) { clip ->
                        ClipCard(
                            clip = clip,
                            onCopy = { copyClip(context, clip.content) },
                            onPin = { vm.togglePin(clip) },
                            onDelete = { vm.deleteClip(clip) },
                            onEdit = { editingClip = clip }
                        )
                    }
                }
            }
        }
    }

    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            title = { Text("Clear unpinned clips?") },
            text = { Text("This permanently deletes all unpinned clips. Pinned clips will remain.") },
            confirmButton = {
                TextButton(onClick = { vm.clearUnpinned(); showClearConfirm = false }) { Text("Clear") }
            },
            dismissButton = { TextButton(onClick = { showClearConfirm = false }) { Text("Cancel") } }
        )
    }

    editingClip?.let { clip ->
        var content by remember(clip.id) { mutableStateOf(clip.content) }
        var name by remember(clip.id) { mutableStateOf(clip.customName.orEmpty()) }
        var sensitive by remember(clip.id) { mutableStateOf(clip.isSensitive) }
        AlertDialog(
            onDismissRequest = { editingClip = null },
            title = { Text("Edit clip") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(content, { content = it }, label = { Text("Content") }, minLines = 3)
                    OutlinedTextField(name, { name = it }, label = { Text("Saved name (optional)") }, singleLine = true)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(sensitive, { sensitive = !sensitive }, label = { Text("Sensitive") })
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.updateClipContent(clip, content, sensitive, name)
                    editingClip = null
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { editingClip = null }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun ClipCard(
    clip: ClipEntity,
    onCopy: () -> Unit,
    onPin: () -> Unit,
    onDelete: () -> Unit,
    onEdit: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(clip.customName ?: clip.category.displayName, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                Text(if (clip.isPinned) "PINNED" else java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.SHORT, java.text.DateFormat.SHORT).format(java.util.Date(clip.createdAt)), style = MaterialTheme.typography.labelSmall)
            }
            Text(
                text = if (clip.isSensitive) "••••••••••••" else clip.content,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 5
            )
            Divider(color = MaterialTheme.colorScheme.outlineVariant)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                IconButton(onClick = onCopy) { Icon(Icons.Default.ContentCopy, contentDescription = "Copy clip") }
                IconButton(onClick = onPin) { Icon(Icons.Default.PushPin, contentDescription = if (clip.isPinned) "Unpin clip" else "Pin clip") }
                TextButton(onClick = onEdit) { Text("Edit") }
                IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, contentDescription = "Delete clip") }
            }
        }
    }
}

private fun copyClip(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("RSS Clipboard", text))
}
