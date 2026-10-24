package de.fisch981.gitsyncer.ui.screens

import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import de.fisch981.gitsyncer.data.RepositoryStore
import de.fisch981.gitsyncer.git.GitManager
import de.fisch981.gitsyncer.model.*
import de.fisch981.gitsyncer.sync.SyncScheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditRepoScreen(
    repoId: String?,
    onNavigateBack: () -> Unit,
    onNavigateToSshKeys: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val repoStore = remember { RepositoryStore.getInstance(context) }
    val gitManager = remember { GitManager(context) }
    val sshKeys by repoStore.sshKeys.collectAsState()

    val existingRepo = remember(repoId) {
        if (repoId != null) repoStore.getRepository(repoId) else null
    }

    var name by remember { mutableStateOf(existingRepo?.name ?: "") }
    var remoteUrl by remember { mutableStateOf(existingRepo?.remoteUrl ?: "") }
    var localPath by remember {
        mutableStateOf(
            existingRepo?.localPath ?: "${Environment.getExternalStorageDirectory().absolutePath}/Documents/GitSyncVault"
        )
    }
    var branch by remember { mutableStateOf(existingRepo?.branch ?: "main") }
    var authType by remember { mutableStateOf(existingRepo?.authType ?: AuthType.HTTPS_TOKEN) }
    var tokenOrPassword by remember { mutableStateOf(existingRepo?.tokenOrPassword ?: "") }
    var username by remember { mutableStateOf(existingRepo?.username ?: "") }
    var selectedSshKeyId by remember { mutableStateOf(existingRepo?.sshKeyId ?: sshKeys.firstOrNull()?.id) }
    var autoSyncEnabled by remember { mutableStateOf(existingRepo?.autoSyncEnabled ?: true) }
    var syncIntervalMinutes by remember { mutableStateOf(existingRepo?.syncIntervalMinutes ?: 60) }
    var syncDirection by remember { mutableStateOf(existingRepo?.syncDirection ?: SyncDirection.TWO_WAY) }
    var autoCommitMessage by remember { mutableStateOf(existingRepo?.autoCommitMessage ?: "Sync %timestamp%") }
    var authorName by remember { mutableStateOf(existingRepo?.authorName ?: "GitSyncer") }
    var authorEmail by remember { mutableStateOf(existingRepo?.authorEmail ?: "gitsyncer@fisch981.de") }

    var isPasswordVisible by remember { mutableStateOf(false) }
    var isCloning by remember { mutableStateOf(false) }
    var statusText by remember { mutableStateOf("") }

    // Folder picker launcher
    val folderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        if (uri != null) {
            val path = uri.path?.let { p ->
                if (p.contains("primary:")) {
                    "/storage/emulated/0/" + p.substringAfter("primary:")
                } else {
                    p
                }
            } ?: uri.toString()
            localPath = path
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (existingRepo == null) "Neues Repository" else "Repository bearbeiten") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Zurück")
                    }
                },
                actions = {
                    if (existingRepo != null) {
                        IconButton(
                            onClick = {
                                SyncScheduler.cancelRepo(context, existingRepo.id)
                                repoStore.deleteRepository(existingRepo.id)
                                Toast.makeText(context, "Repository gelöscht", Toast.LENGTH_SHORT).show()
                                onNavigateBack()
                            }
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = "Löschen", tint = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            )
        }
    ) { padding ->
        val scrollState = rememberScrollState()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(scrollState)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // General Info Card
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Repository Basisdaten", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Name (z. B. Obsidian Vault)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = remoteUrl,
                        onValueChange = {
                            remoteUrl = it
                            if (it.startsWith("git@") && authType != AuthType.SSH_KEY) {
                                authType = AuthType.SSH_KEY
                            }
                        },
                        label = { Text("Remote Git URL (HTTPS oder SSH)") },
                        placeholder = { Text("https://github.com/user/notes.git") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = localPath,
                        onValueChange = { localPath = it },
                        label = { Text("Lokaler Ordnerpfad") },
                        trailingIcon = {
                            IconButton(onClick = { folderPickerLauncher.launch(null) }) {
                                Icon(Icons.Default.FolderOpen, contentDescription = "Ordner wählen")
                            }
                        },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = branch,
                        onValueChange = { branch = it },
                        label = { Text("Branch") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            // Authentication Card
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Authentifizierung", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = authType == AuthType.HTTPS_TOKEN,
                            onClick = { authType = AuthType.HTTPS_TOKEN },
                            label = { Text("HTTPS Token") }
                        )
                        FilterChip(
                            selected = authType == AuthType.SSH_KEY,
                            onClick = { authType = AuthType.SSH_KEY },
                            label = { Text("SSH Key") }
                        )
                        FilterChip(
                            selected = authType == AuthType.HTTPS_CREDENTIALS,
                            onClick = { authType = AuthType.HTTPS_CREDENTIALS },
                            label = { Text("User/Pass") }
                        )
                    }

                    when (authType) {
                        AuthType.HTTPS_TOKEN -> {
                            OutlinedTextField(
                                value = tokenOrPassword,
                                onValueChange = { tokenOrPassword = it },
                                label = { Text("Personal Access Token (PAT)") },
                                placeholder = { Text("ghp_... oder glpat-...") },
                                visualTransformation = if (isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                                trailingIcon = {
                                    Row {
                                        IconButton(onClick = {
                                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                            val clip = clipboard.primaryClip
                                            if (clip != null && clip.itemCount > 0) {
                                                val pasted = clip.getItemAt(0).text?.toString() ?: ""
                                                if (pasted.isNotBlank()) {
                                                    tokenOrPassword = pasted.trim()
                                                    Toast.makeText(context, "Aus Zwischenablage eingefügt", Toast.LENGTH_SHORT).show()
                                                }
                                            }
                                        }) {
                                            Icon(Icons.Default.ContentPaste, contentDescription = "Einfügen")
                                        }
                                        IconButton(onClick = { isPasswordVisible = !isPasswordVisible }) {
                                            Icon(
                                                if (isPasswordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                                contentDescription = null
                                            )
                                        }
                                    }
                                },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )

                            Spacer(modifier = Modifier.height(4.dp))

                            // One-click button to open GitHub Token generator in Browser
                            Button(
                                onClick = {
                                    val url = "https://github.com/settings/tokens/new?description=GitSyncer&scopes=repo"
                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                                    context.startActivity(intent)
                                },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.filledTonalButtonColors()
                            ) {
                                Icon(Icons.Default.OpenInBrowser, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("GitHub Token im Browser erstellen (1-Klick)")
                            }

                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                            ) {
                                Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text("💡 Anleitung für das GitHub Token:", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
                                    Text(
                                        "1. Tippe auf den Button oben – dein Browser öffnet direkt die GitHub-Tokenseite.\n" +
                                        "2. Der Haken bei 'repo' (Repository-Zugriff) ist bereits vorausgewählt.\n" +
                                        "3. Scrolle ganz nach unten und klicke auf den grünen Button 'Generate token'.\n" +
                                        "4. Kopiere das angezeigte Token (beginnt mit 'ghp_') und füge es hier ein.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                        AuthType.HTTPS_CREDENTIALS -> {
                            OutlinedTextField(
                                value = username,
                                onValueChange = { username = it },
                                label = { Text("Benutzername") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            OutlinedTextField(
                                value = tokenOrPassword,
                                onValueChange = { tokenOrPassword = it },
                                label = { Text("Passwort / Token") },
                                visualTransformation = if (isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                                trailingIcon = {
                                    IconButton(onClick = { isPasswordVisible = !isPasswordVisible }) {
                                        Icon(
                                            if (isPasswordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                            contentDescription = null
                                        )
                                    }
                                },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        AuthType.SSH_KEY -> {
                            if (sshKeys.isEmpty()) {
                                Text(
                                    "Noch keine SSH-Schlüssel hinterlegt.",
                                    color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.bodySmall
                                )
                                Button(
                                    onClick = onNavigateToSshKeys,
                                    colors = ButtonDefaults.outlinedButtonColors()
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = null)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("SSH Schlüssel jetzt erstellen")
                                }
                            } else {
                                Text("Wähle einen SSH Schlüssel:", style = MaterialTheme.typography.bodyMedium)
                                sshKeys.forEach { key ->
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        RadioButton(
                                            selected = selectedSshKeyId == key.id,
                                            onClick = { selectedSshKeyId = key.id }
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Column {
                                            Text(key.name, fontWeight = FontWeight.SemiBold)
                                            Text(key.keyType, style = MaterialTheme.typography.bodySmall)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Sync Settings Card
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Synchronisations-Optionen (100% Kostenlos)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Automatische Hintergrund-Sync", fontWeight = FontWeight.SemiBold)
                            Text("Synchronisiert im Hintergrund per WorkManager", style = MaterialTheme.typography.bodySmall)
                        }
                        Switch(
                            checked = autoSyncEnabled,
                            onCheckedChange = { autoSyncEnabled = it }
                        )
                    }

                    if (autoSyncEnabled) {
                        Text("Sync-Intervall:", style = MaterialTheme.typography.labelMedium)
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            val intervals = listOf(15 to "15m", 30 to "30m", 60 to "1h", 360 to "6h", 1440 to "24h")
                            intervals.forEach { (mins, label) ->
                                FilterChip(
                                    selected = syncIntervalMinutes == mins,
                                    onClick = { syncIntervalMinutes = mins },
                                    label = { Text(label) }
                                )
                            }
                        }
                    }

                    Text("Richtung:", style = MaterialTheme.typography.labelMedium)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = syncDirection == SyncDirection.TWO_WAY,
                            onClick = { syncDirection = SyncDirection.TWO_WAY },
                            label = { Text("Zwei-Wege (Bidirektional)") }
                        )
                        FilterChip(
                            selected = syncDirection == SyncDirection.PUSH_ONLY,
                            onClick = { syncDirection = SyncDirection.PUSH_ONLY },
                            label = { Text("Nur Push") }
                        )
                        FilterChip(
                            selected = syncDirection == SyncDirection.PULL_ONLY,
                            onClick = { syncDirection = SyncDirection.PULL_ONLY },
                            label = { Text("Nur Pull") }
                        )
                    }

                    OutlinedTextField(
                        value = autoCommitMessage,
                        onValueChange = { autoCommitMessage = it },
                        label = { Text("Commit-Nachricht Template") },
                        placeholder = { Text("Sync %timestamp%") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = authorName,
                            onValueChange = { authorName = it },
                            label = { Text("Git Author Name") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = authorEmail,
                            onValueChange = { authorEmail = it },
                            label = { Text("Git Email") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            if (isCloning) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(32.dp))
                        Text(statusText.ifBlank { "Synchronisiere..." }, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }

            // Save Buttons
            Button(
                onClick = {
                    if (name.isBlank() || remoteUrl.isBlank() || localPath.isBlank()) {
                        Toast.makeText(context, "Bitte alle Pflichtfelder ausfüllen!", Toast.LENGTH_SHORT).show()
                        return@Button
                    }

                    val updatedConfig = GitRepositoryConfig(
                        id = existingRepo?.id ?: java.util.UUID.randomUUID().toString(),
                        name = name.trim(),
                        remoteUrl = remoteUrl.trim(),
                        localPath = localPath.trim(),
                        branch = branch.trim().ifBlank { "main" },
                        authType = authType,
                        tokenOrPassword = tokenOrPassword.trim(),
                        username = username.trim(),
                        sshKeyId = selectedSshKeyId,
                        autoSyncEnabled = autoSyncEnabled,
                        syncIntervalMinutes = syncIntervalMinutes,
                        syncDirection = syncDirection,
                        autoCommitMessage = autoCommitMessage.trim(),
                        authorName = authorName.trim(),
                        authorEmail = authorEmail.trim()
                    )

                    repoStore.saveRepository(updatedConfig)
                    SyncScheduler.scheduleRepo(context, updatedConfig)

                    isCloning = true
                    statusText = "Klone / Initialisiere Repository..."
                    coroutineScope.launch {
                        val result = withContext(Dispatchers.IO) {
                            if (!gitManager.isGitRepo(updatedConfig.localPath)) {
                                gitManager.cloneRepository(updatedConfig) { msg, _ -> statusText = msg }
                            } else {
                                gitManager.sync(updatedConfig) { msg, _ -> statusText = msg }
                            }
                        }
                        isCloning = false
                        if (result.success) {
                            repoStore.updateRepoSyncStatus(updatedConfig.id, "SUCCESS")
                            Toast.makeText(context, "Repository erfolgreich eingerichtet!", Toast.LENGTH_SHORT).show()
                            onNavigateBack()
                        } else {
                            repoStore.updateRepoSyncStatus(updatedConfig.id, "FAILED", result.message)
                            Toast.makeText(context, "Warnung: ${result.message}", Toast.LENGTH_LONG).show()
                            onNavigateBack()
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isCloning
            ) {
                Icon(Icons.Default.Save, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Speichern & Synchronisieren")
            }

            OutlinedButton(
                onClick = {
                    if (name.isBlank() || remoteUrl.isBlank() || localPath.isBlank()) {
                        Toast.makeText(context, "Bitte alle Pflichtfelder ausfüllen!", Toast.LENGTH_SHORT).show()
                        return@OutlinedButton
                    }

                    val updatedConfig = GitRepositoryConfig(
                        id = existingRepo?.id ?: java.util.UUID.randomUUID().toString(),
                        name = name.trim(),
                        remoteUrl = remoteUrl.trim(),
                        localPath = localPath.trim(),
                        branch = branch.trim().ifBlank { "main" },
                        authType = authType,
                        tokenOrPassword = tokenOrPassword.trim(),
                        username = username.trim(),
                        sshKeyId = selectedSshKeyId,
                        autoSyncEnabled = autoSyncEnabled,
                        syncIntervalMinutes = syncIntervalMinutes,
                        syncDirection = syncDirection,
                        autoCommitMessage = autoCommitMessage.trim(),
                        authorName = authorName.trim(),
                        authorEmail = authorEmail.trim()
                    )

                    repoStore.saveRepository(updatedConfig)
                    SyncScheduler.scheduleRepo(context, updatedConfig)
                    Toast.makeText(context, "Gespeichert", Toast.LENGTH_SHORT).show()
                    onNavigateBack()
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Nur Speichern (ohne Sofort-Sync)")
            }
        }
    }
}
