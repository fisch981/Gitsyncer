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

    var isSyncingAll by remember { mutableStateOf(false) }

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
                            if (repositories.isEmpty()) return@IconButton
                            isSyncingAll = true
                            coroutineScope.launch {
                                for (repo in repositories) {
                                    repoStore.updateRepoSyncStatus(repo.id, "RUNNING")
                                    val res = withContext(Dispatchers.IO) {
                                        gitManager.sync(repo)
                                    }
                                    if (res.success) {
                                        repoStore.updateRepoSyncStatus(repo.id, "SUCCESS")
                                    } else {
                                        val status = if (res.conflicts.isNotEmpty()) "CONFLICT" else "FAILED"
                                        repoStore.updateRepoSyncStatus(repo.id, status, res.message)
                                    }
                                }
                                isSyncingAll = false
                                Toast.makeText(context, "Alle Repositories synchronisiert!", Toast.LENGTH_SHORT).show()
                            }
                        },
                        enabled = !isSyncingAll && repositories.isNotEmpty()
                    ) {
                        if (isSyncingAll) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Default.Sync, contentDescription = "Alle synchronisieren")
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
                                "Synchronisiere beliebig viele Ordner (z.B. Obsidian Vaults, Markdown-Notizen oder Code) nahtlos mit GitHub, GitLab oder Gitea.\n\nAlle Profi-Funktionen & unbegrenzte Repositories sind 100% kostenlos freigeschaltet!",
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
                    items(repositories, key = { it.id }) { repo ->
                        RepositoryCard(
                            repo = repo,
                            onOpen = { onOpenRepo(repo.id) },
                            onEdit = { onEditRepo(repo.id) },
                            onSync = {
                                coroutineScope.launch {
                                    repoStore.updateRepoSyncStatus(repo.id, "RUNNING")
                                    val res = withContext(Dispatchers.IO) {
                                        gitManager.sync(repo)
                                    }
                                    if (res.success) {
                                        repoStore.updateRepoSyncStatus(repo.id, "SUCCESS")
                                        Toast.makeText(context, "${repo.name}: Synchronisiert!", Toast.LENGTH_SHORT).show()
                                    } else {
                                        val status = if (res.conflicts.isNotEmpty()) "CONFLICT" else "FAILED"
                                        repoStore.updateRepoSyncStatus(repo.id, status, res.message)
                                        Toast.makeText(context, "${repo.name}: ${res.message}", Toast.LENGTH_LONG).show()
                                    }
                                }
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
    onSync: () -> Unit
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

                Surface(
                    shape = MaterialTheme.shapes.extraSmall,
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Text(
                        text = repo.branch,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Status Indicator & Last Sync
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                val (statusColor, statusText, statusIcon) = when (repo.lastSyncStatus) {
                    "SUCCESS" -> Triple(GitGreen, "Synchronisiert", Icons.Default.CheckCircle)
                    "RUNNING" -> Triple(MaterialTheme.colorScheme.primary, "Synchronisiert...", Icons.Default.Sync)
                    "CONFLICT" -> Triple(GitRed, "Merge-Konflikt", Icons.Default.Warning)
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

            if (!repo.lastErrorMessage.isNullOrBlank() && repo.lastSyncStatus != "SUCCESS") {
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
                    Button(
                        onClick = onSync,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Icon(Icons.Default.Sync, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Sync Jetzt")
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

                IconButton(onClick = onEdit) {
                    Icon(Icons.Default.Tune, contentDescription = "Konfigurieren")
                }
            }
        }
    }
}
