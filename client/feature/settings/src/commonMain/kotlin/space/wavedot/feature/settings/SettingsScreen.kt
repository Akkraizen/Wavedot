package space.wavedot.feature.settings

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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import space.wavedot.ui.designsystem.SectionHeader

@Composable
fun SettingsScreen(state: SettingsUiState, onIntent: (SettingsIntent) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
        state.message?.let {
            Card(Modifier.fillMaxWidth().padding(16.dp)) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(it, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    TextButton(onClick = { onIntent(SettingsIntent.DismissMessage) }) { Text("OK") }
                }
            }
        }
        SectionHeader("Sources")
        state.plugins.forEach { PluginCard(it, onIntent) }

        state.musicFolders?.let { folders ->
            SectionHeader("Music folders")
            folders.forEach { folder ->
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(folder, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    TextButton(onClick = { onIntent(SettingsIntent.RemoveFolder(folder)) }) { Text("Remove") }
                }
            }
            var path by remember { mutableStateOf("") }
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(path, { path = it }, label = { Text("Folder path") }, singleLine = true, modifier = Modifier.weight(1f))
                TextButton(onClick = { onIntent(SettingsIntent.AddFolder(path)); path = "" }, enabled = !state.busy) { Text("Add") }
            }
        }

        SectionHeader("Sync")
        Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (state.hubConnected) {
                Text("Connected to Wavedot Hub", style = MaterialTheme.typography.bodyMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { onIntent(SettingsIntent.SyncNow) }, enabled = !state.busy) { Text("Sync now") }
                    OutlinedButton(onClick = { onIntent(SettingsIntent.DisconnectHub) }) { Text("Disconnect") }
                }
            } else {
                var url by remember { mutableStateOf("") }
                OutlinedTextField(url, { url = it }, label = { Text("Hub URL") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { onIntent(SettingsIntent.ConnectHub(url)) }, enabled = url.isNotBlank() && !state.busy) {
                        Text("Connect")
                    }
                    OutlinedButton(onClick = { onIntent(SettingsIntent.SyncNow) }, enabled = !state.busy) {
                        Text("Import likes")
                    }
                }
            }
        }

        SectionHeader("About")
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Wavedot ${state.appVersion}", modifier = Modifier.weight(1f))
            OutlinedButton(onClick = { onIntent(SettingsIntent.CheckUpdates) }, enabled = !state.busy) { Text("Check for updates") }
        }
    }
}

@Composable
private fun PluginCard(plugin: PluginInfo, onIntent: (SettingsIntent) -> Unit) {
    Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(plugin.name, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                Text(
                    plugin.status,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (plugin.active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (plugin.description.isNotBlank()) {
                Text(plugin.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            plugin.fields.forEach { field ->
                Spacer(Modifier.height(8.dp))
                var value by remember(plugin.id, field.key) { mutableStateOf("") }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = value,
                        onValueChange = { value = it },
                        label = { Text(field.title) },
                        singleLine = true,
                        visualTransformation = if (field.secret) PasswordVisualTransformation() else VisualTransformation.None,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = { onIntent(SettingsIntent.SavePluginValue(plugin.id, field.key, value)); value = "" }) {
                        Text("Save")
                    }
                }
            }
        }
    }
}
