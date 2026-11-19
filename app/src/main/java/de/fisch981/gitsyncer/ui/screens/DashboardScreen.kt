package de.fisch981.gitsyncer.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.fisch981.gitsyncer.data.RepositoryStore
import de.fisch981.gitsyncer.git.GitManager
import de.fisch981.gitsyncer.model.GitRepositoryConfig
import de.fisch981.gitsyncer.sync.SyncScheduler
import de.fisch981.gitsyncer.ui.theme.GitGreen
import de.fisch981.gitsyncer.ui.theme.GitOrange
import de.fisch981.gitsyncer.ui.theme.GitRed
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    onAddRepo: () -> Unit,
    onOpenRepo: (repoId: String) -> Unit,
    onEditRepo: (repoId: String) -> Unit,
    onNavigateToSshKeys: () -> Unit,
    onNavigateToSettings: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val repoStore = remember { RepositoryStore.getInstance(context) }
    val gitManager = remember { GitManager(context) }
    val repositories by repoStore.repositories.collectAsState()
    val sortedRepositories = remember(repositories) {
        repositories.sortedWith(
            compareByDescending<GitRepositoryConfig> { it.isEnabled }
                .thenBy { it.name.lowercase() }
        )
    }

    var isSyncingAll by remember { mutableStateOf(false) }
    var syncAllJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    val activeSyncJobs = remember { mutableStateMapOf<String, kotlinx.coroutines.Job>() }

    // Check storage permission
    val hasStoragePermission = remember {
        derivedStateOf {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                Environment.isExternalStorageManager()
            } else {
                true
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.SyncAlt,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("GitSyncer", fontWeight = FontWeight.Bold)
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            if (isSyncingAll) {
                                syncAllJob?.cancel()
                                syncAllJob = null
                                isSyncingAll = false
                                repositories.forEach {
                                    GitManager.cancelSync(it.id, it.localPath)
                                    if (it.lastSyncStatus == "RUNNING") {
                                        repoStore.updateRepoSyncStatus(it.id, "CANCELLED", "Sammel-Sync abgebrochen")
                                    }
                                }
                                Toast.makeText(context, "Sammel-Sync abgebrochen", Toast.LENGTH_SHORT).show()
                                return@IconButton
                            }

                            val activeRepos = repositories.filter { it.isEnabled }.sortedBy { it.name.lowercase() }
                            if (activeRepos.isEmpty()) {
                                Toast.makeText(context, "Keine aktiven Repositories vorhanden.", Toast.LENGTH_SHORT).show()
                                return@IconButton
                            }
                            isSyncingAll = true
                            syncAllJob = coroutineScope.launch {
                                for (repo in activeRepos) {
                                    if (!isSyncingAll) break
                                    GitManager.resetCancelFlag(repo.id)
                                    repoStore.updateRepoSyncStatus(repo.id, "RUNNING")
                                    val res = withContext(Dispatchers.IO) {
                                        gitManager.sync(repo)
                                    }
                                    if (!isSyncingAll || GitManager.isSyncCancelled(repo.id)) {
                                        repoStore.updateRepoSyncStatus(repo.id, "CANCELLED", "Sammel-Sync abgebrochen")
                                        break
                                    }
                                    if (res.success) {
                                        repoStore.updateRepoSyncStatus(repo.id, "SUCCESS")
                                    } else {
                                        val status = if (res.conflicts.isNotEmpty()) "CONFLICT" else if (res.message.contains("abgebrochen", ignoreCase = true)) "CANCELLED" else "FAILED"
                                        repoStore.updateRepoSyncStatus(repo.id, status, res.message)
                                    }
                                }
                                isSyncingAll = false
                                syncAllJob = null
                                Toast.makeText(context, "${activeRepos.size} Repositories synchronisiert!", Toast.LENGTH_SHORT).show()
                            }
                        },
                        enabled = repositories.any { it.isEnabled }
                    ) {
                        if (isSyncingAll) {
                            Icon(Icons.Default.Stop, contentDescription = "Sammel-Sync abbrechen", tint = MaterialTheme.colorScheme.error)
                        } else {
                            Icon(Icons.Default.Sync, contentDescription = "Aktive Repositories synchronisieren")
                        }
                    }
                    IconButton(onClick = onNavigateToSshKeys) {
                        Icon(Icons.Default.VpnKey, contentDescription = "SSH Schlüssel")
                    }
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Einstellungen")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onAddRepo,
                containerColor = MaterialTheme.colorScheme.primary
            ) {
                Icon(Icons.Default.Add, contentDescription = "Repository hinzufügen")
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Permission Notice Banner if needed
            AnimatedVisibility(visible = !hasStoragePermission.value) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.FolderSpecial, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "Speicherzugriff erforderlich",
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "Damit GitSyncer deine lokalen Obsidian-Vaults und Notizordner synchronisieren kann, wird die Berechtigung für Gerätedateien benötigt.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            onClick = {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                                    val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                                        data = Uri.parse("package:${context.packageName}")
                                    }
                                    context.startActivity(intent)
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                        ) {
                            Text("Berechtigung erteilen")
                        }
                    }
                }
            }

            if (repositories.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                Icons.Default.CloudSync,
                                contentDescription = null,
                                modifier = Modifier.size(64.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                "Willkommen bei GitSyncer",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                "Synchronisiere deine Ordner (z.B. Obsidian Vaults, Markdown-Notizen oder Code) zuverlässig mit GitHub, GitLab oder Gitea.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(20.dp))
                            Button(onClick = onAddRepo) {
                                Icon(Icons.Default.Add, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Erstes Repository verknüpfen")
                            }
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(sortedRepositories, key = { it.id }) { repo ->
                        RepositoryCard(
                            repo = repo,
                            onOpen = { onOpenRepo(repo.id) },
                            onEdit = { onEditRepo(repo.id) },
                            onToggleEnabled = {
                                val newState = repoStore.toggleRepositoryEnabled(repo.id)
                                val msg = if (newState) "${repo.name} aktiviert" else "${repo.name} pausiert"
                                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                            },
                            onSync = {
                                val job = coroutineScope.launch {
                                    GitManager.resetCancelFlag(repo.id)
                                    repoStore.updateRepoSyncStatus(repo.id, "RUNNING")
                                    val res = withContext(Dispatchers.IO) {
                                        gitManager.sync(repo)
                                    }
                                    activeSyncJobs.remove(repo.id)
                                    val current = repoStore.getRepository(repo.id)
                                    if (current?.lastSyncStatus == "CANCELLED" || GitManager.isSyncCancelled(repo.id)) {
                                        return@launch
                                    }
                                    if (res.success) {
                                        repoStore.updateRepoSyncStatus(repo.id, "SUCCESS")
                                        Toast.makeText(context, "${repo.name}: Synchronisiert!", Toast.LENGTH_SHORT).show()
                                    } else {
                                        val status = if (res.conflicts.isNotEmpty()) "CONFLICT" else if (res.message.contains("abgebrochen", ignoreCase = true)) "CANCELLED" else "FAILED"
                                        repoStore.updateRepoSyncStatus(repo.id, status, res.message)
                                        Toast.makeText(context, "${repo.name}: ${res.message}", Toast.LENGTH_LONG).show()
                                    }
                                }
                                activeSyncJobs[repo.id] = job
                            },
                            onCancel = {
                                GitManager.cancelSync(repo.id, repo.localPath)
                                activeSyncJobs[repo.id]?.cancel()
                                activeSyncJobs.remove(repo.id)
                                repoStore.updateRepoSyncStatus(repo.id, "CANCELLED", "Synchronisation durch Benutzer abgebrochen")
                                Toast.makeText(context, "${repo.name}: Synchronisation abgebrochen", Toast.LENGTH_SHORT).show()
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun RepositoryCard(
    repo: GitRepositoryConfig,
    onOpen: () -> Unit,
    onEdit: () -> Unit,
    onToggleEnabled: () -> Unit,
    onSync: () -> Unit,
    onCancel: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onOpen() },
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = repo.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = repo.localPath,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (!repo.isEnabled) {
                        Surface(
                            shape = MaterialTheme.shapes.extraSmall,
                            color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.8f)
                        ) {
                            Text(
                                text = "Pausiert",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                    Surface(
                        shape = MaterialTheme.shapes.extraSmall,
                        color = if (repo.isEnabled) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Text(
                            text = repo.branch,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (repo.isEnabled) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Status Indicator & Last Sync
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                val (statusColor, statusText, statusIcon) = if (!repo.isEnabled) {
                    Triple(MaterialTheme.colorScheme.outline, "Pausiert (Sync inaktiv)", Icons.Default.PauseCircleFilled)
                } else when (repo.lastSyncStatus) {
                    "SUCCESS" -> Triple(GitGreen, "Synchronisiert", Icons.Default.CheckCircle)
                    "RUNNING" -> Triple(MaterialTheme.colorScheme.primary, "Synchronisiert...", Icons.Default.Sync)
                    "CONFLICT" -> Triple(GitRed, "Merge-Konflikt", Icons.Default.Warning)
                    "CANCELLED" -> Triple(MaterialTheme.colorScheme.error, "Abgebrochen", Icons.Default.Cancel)
                    "FAILED" -> Triple(GitRed, "Fehler", Icons.Default.Error)
                    else -> Triple(MaterialTheme.colorScheme.onSurfaceVariant, "Bereit", Icons.Default.CloudQueue)
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        statusIcon,
                        contentDescription = null,
                        tint = statusColor,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = statusText,
                        color = statusColor,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 12.sp
                    )
                }

                val lastSyncTime = if (repo.lastSyncTimestamp > 0) {
                    SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(repo.lastSyncTimestamp))
                } else "Noch nie"

                Text(
                    text = "Zuletzt: $lastSyncTime",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp
                )
            }

            if (!repo.lastErrorMessage.isNullOrBlank() && repo.lastSyncStatus != "SUCCESS" && repo.isEnabled) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = repo.lastErrorMessage,
                    color = GitRed,
                    style = MaterialTheme.typography.bodySmall,
                    fontSize = 11.sp,
                    maxLines = 2
                )
            }

            Spacer(modifier = Modifier.height(12.dp))
            Divider()
            Spacer(modifier = Modifier.height(8.dp))

            // Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (repo.lastSyncStatus == "RUNNING") {
                        OutlinedButton(
                            onClick = onCancel,
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.error)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Abbrechen")
                        }
                    } else {
                        Button(
                            onClick = onSync,
                            enabled = repo.isEnabled,
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Icon(Icons.Default.Sync, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Sync Jetzt")
                        }
                    }

                    OutlinedButton(
                        onClick = onOpen,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Icon(Icons.Default.Folder, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Dateien & Diff")
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onToggleEnabled) {
                        Icon(
                            if (repo.isEnabled) Icons.Default.PauseCircleOutline else Icons.Default.PlayCircleOutline,
                            contentDescription = if (repo.isEnabled) "Repository pausieren" else "Repository aktivieren",
                            tint = if (repo.isEnabled) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary
                        )
                    }
                    IconButton(onClick = onEdit) {
                        Icon(Icons.Default.Tune, contentDescription = "Konfigurieren")
                    }
                }
            }
        }
    }
}
