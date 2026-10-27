package de.fisch981.gitsyncer.ui.screens

import android.widget.Toast
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.fisch981.gitsyncer.data.RepositoryStore
import de.fisch981.gitsyncer.git.GitManager
import de.fisch981.gitsyncer.model.*
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
fun RepoDetailScreen(
    repoId: String,
    onNavigateBack: () -> Unit,
    onEditRepo: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val repoStore = remember { RepositoryStore.getInstance(context) }
    val gitManager = remember { GitManager(context) }

    val repoList by repoStore.repositories.collectAsState()
    val repo = repoList.firstOrNull { it.id == repoId }

    if (repo == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Repository nicht gefunden")
        }
        return
    }

    var selectedTabIndex by remember { mutableIntStateOf(0) }
    var changes by remember { mutableStateOf<List<FileChange>>(emptyList()) }
    var commits by remember { mutableStateOf<List<CommitInfo>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var commitMessage by remember { mutableStateOf("") }

    // Diff Dialog state
    var selectedDiffFile by remember { mutableStateOf<FileChange?>(null) }
    var diffContent by remember { mutableStateOf("") }
    var isDiffLoading by remember { mutableStateOf(false) }

    fun refreshData() {
        coroutineScope.launch {
            isLoading = true
            withContext(Dispatchers.IO) {
                changes = gitManager.getStatus(repo)
                commits = gitManager.getLog(repo, 30)
            }
            isLoading = false
        }
    }

    LaunchedEffect(repoId) {
        refreshData()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(repo.name)
                        if (!repo.isEnabled) {
                            Spacer(modifier = Modifier.width(8.dp))
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
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Zurück")
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            val newState = repoStore.toggleRepositoryEnabled(repo.id)
                            val msg = if (newState) "${repo.name} aktiviert" else "${repo.name} pausiert"
                            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                        }
                    ) {
                        Icon(
                            if (repo.isEnabled) Icons.Default.PauseCircleOutline else Icons.Default.PlayCircleOutline,
                            contentDescription = if (repo.isEnabled) "Pausieren" else "Aktivieren",
                            tint = if (repo.isEnabled) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary
                        )
                    }
                    IconButton(
                        onClick = {
                            coroutineScope.launch {
                                isLoading = true
                                repoStore.updateRepoSyncStatus(repo.id, "RUNNING")
                                val result = withContext(Dispatchers.IO) {
                                    gitManager.sync(repo)
                                }
                                isLoading = false
                                if (result.success) {
                                    repoStore.updateRepoSyncStatus(repo.id, "SUCCESS")
                                    Toast.makeText(context, "Erfolgreich synchronisiert!", Toast.LENGTH_SHORT).show()
                                } else {
                                    val status = if (result.conflicts.isNotEmpty()) "CONFLICT" else "FAILED"
                                    repoStore.updateRepoSyncStatus(repo.id, status, result.message)
                                    Toast.makeText(context, "Fehler: ${result.message}", Toast.LENGTH_LONG).show()
                                }
                                refreshData()
                            }
                        }
                    ) {
                        Icon(Icons.Default.Sync, contentDescription = "Synchronisieren")
                    }
                    IconButton(onClick = onEditRepo) {
                        Icon(Icons.Default.Settings, contentDescription = "Einstellungen")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            TabRow(selectedTabIndex = selectedTabIndex) {
                Tab(
                    selected = selectedTabIndex == 0,
                    onClick = { selectedTabIndex = 0 },
                    text = { Text("Änderungen (${changes.size})") }
                )
                Tab(
                    selected = selectedTabIndex == 1,
                    onClick = { selectedTabIndex = 1 },
                    text = { Text("Historie (${commits.size})") }
                )
                Tab(
                    selected = selectedTabIndex == 2,
                    onClick = { selectedTabIndex = 2 },
                    text = { Text("Übersicht") }
                )
            }

            if (isLoading) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            when (selectedTabIndex) {
                0 -> {
                    // Status & Changes Tab
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp)
                    ) {
                        // Quick Commit Box
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        ) {
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedTextField(
                                    value = commitMessage,
                                    onValueChange = { commitMessage = it },
                                    label = { Text("Commit Nachricht") },
                                    placeholder = { Text("z.B. Notizen aktualisiert") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Button(
                                        onClick = {
                                            if (commitMessage.isBlank()) {
                                                Toast.makeText(context, "Bitte Nachricht eingeben", Toast.LENGTH_SHORT).show()
                                                return@Button
                                            }
                                            coroutineScope.launch {
                                                isLoading = true
                                                val res = withContext(Dispatchers.IO) {
                                                    val tempRepo = repo.copy(autoCommitMessage = commitMessage)
                                                    gitManager.sync(tempRepo)
                                                }
                                                isLoading = false
                                                if (res.success) {
                                                    commitMessage = ""
                                                    Toast.makeText(context, "Commit & Push erfolgreich!", Toast.LENGTH_SHORT).show()
                                                    refreshData()
                                                } else {
                                                    Toast.makeText(context, "Fehler: ${res.message}", Toast.LENGTH_LONG).show()
                                                }
                                            }
                                        },
                                        modifier = Modifier.weight(1f),
                                        enabled = !isLoading && changes.isNotEmpty()
                                    ) {
                                        Icon(Icons.Default.Upload, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Commit & Push")
                                    }

                                    OutlinedButton(
                                        onClick = { refreshData() },
                                        enabled = !isLoading
                                    ) {
                                        Icon(Icons.Default.Refresh, contentDescription = "Aktualisieren")
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        if (changes.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(
                                        Icons.Default.CheckCircle,
                                        contentDescription = null,
                                        tint = GitGreen,
                                        modifier = Modifier.size(48.dp)
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text("Arbeitsverzeichnis ist sauber", fontWeight = FontWeight.SemiBold)
                                    Text("Keine ungespeicherten lokalen Änderungen", style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                items(changes) { change ->
                                    val (badgeColor, badgeText) = when (change.changeType) {
                                        ChangeType.MODIFIED -> GitOrange to "MOD"
                                        ChangeType.ADDED -> GitGreen to "NEU"
                                        ChangeType.DELETED -> GitRed to "DEL"
                                        ChangeType.UNTRACKED -> MaterialTheme.colorScheme.primary to "UNT"
                                        ChangeType.CONFLICT -> GitRed to "KONFLIKT"
                                    }

                                    Card(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                selectedDiffFile = change
                                                isDiffLoading = true
                                                coroutineScope.launch {
                                                    diffContent = withContext(Dispatchers.IO) {
                                                        gitManager.getDiff(repo, change.path)
                                                    }
                                                    isDiffLoading = false
                                                }
                                            }
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .padding(12.dp)
                                                .fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Row(
                                                modifier = Modifier.weight(1f),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Surface(
                                                    color = badgeColor.copy(alpha = 0.2f),
                                                    shape = MaterialTheme.shapes.small
                                                ) {
                                                    Text(
                                                        text = badgeText,
                                                        color = badgeColor,
                                                        fontWeight = FontWeight.Bold,
                                                        fontSize = 10.sp,
                                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                    )
                                                }
                                                Spacer(modifier = Modifier.width(10.dp))
                                                Text(
                                                    text = change.path,
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    fontFamily = FontFamily.Monospace,
                                                    maxLines = 1
                                                )
                                            }

                                            Icon(
                                                Icons.Default.ChevronRight,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                1 -> {
                    // Commit History Tab
                    if (commits.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("Keine Commit-Historie vorhanden")
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(commits) { commit ->
                                val dateStr = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())
                                    .format(Date(commit.timestamp))

                                Card(modifier = Modifier.fillMaxWidth()) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(
                                                commit.shortId,
                                                fontFamily = FontFamily.Monospace,
                                                color = MaterialTheme.colorScheme.primary,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 12.sp
                                            )
                                            Text(
                                                dateStr,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            commit.message,
                                            fontWeight = FontWeight.SemiBold,
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            commit.author,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                2 -> {
                    // Overview Tab
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("Konfigurations-Details", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                                Divider()
                                DetailRow("Status:", if (repo.isEnabled) "Aktiv" else "Pausiert (von Sync ausgenommen)")
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("Repository aktiv:", fontWeight = FontWeight.Medium)
                                    Switch(
                                        checked = repo.isEnabled,
                                        onCheckedChange = {
                                            repoStore.toggleRepositoryEnabled(repo.id)
                                        }
                                    )
                                }
                                Divider()
                                DetailRow("Remote URL:", repo.remoteUrl)
                                DetailRow("Branch:", repo.branch)
                                DetailRow("Lokaler Pfad:", repo.localPath)
                                DetailRow("Auth-Methode:", repo.authType.name)
                                DetailRow("Sync-Intervall:", "${repo.syncIntervalMinutes} Minuten")
                                DetailRow("Auto-Sync aktiv:", if (repo.autoSyncEnabled) "Ja" else "Nein")
                                DetailRow("Richtung:", repo.syncDirection.name)
                                DetailRow("Commit Author:", "${repo.authorName} <${repo.authorEmail}>")
                            }
                        }

                        Button(
                            onClick = onEditRepo,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Edit, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Repository Konfiguration bearbeiten")
                        }

                        OutlinedButton(
                            onClick = {
                                coroutineScope.launch {
                                    isLoading = true
                                    val res = withContext(Dispatchers.IO) {
                                        gitManager.recloneRepository(repo)
                                    }
                                    isLoading = false
                                    if (res.success) {
                                        repoStore.updateRepoSyncStatus(repo.id, "SUCCESS")
                                        Toast.makeText(context, "Erfolgreich sauber neu geklont!", Toast.LENGTH_SHORT).show()
                                    } else {
                                        repoStore.updateRepoSyncStatus(repo.id, "FAILED", res.message)
                                        Toast.makeText(context, "Fehler: ${res.message}", Toast.LENGTH_LONG).show()
                                    }
                                    refreshData()
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Repository sauber neu klonen (Reset)")
                        }
                    }
                }
            }
        }
    }

    // Diff Dialog
    if (selectedDiffFile != null) {
        val file = selectedDiffFile!!
        DiffViewerDialog(
            filePath = file.path,
            diffText = if (isDiffLoading) "Diff wird geladen..." else diffContent,
            isConflict = file.changeType == ChangeType.CONFLICT,
            onDismiss = { selectedDiffFile = null },
            onResolveConflict = { useLocal ->
                coroutineScope.launch {
                    val resolved = withContext(Dispatchers.IO) {
                        gitManager.resolveConflict(repo, file.path, useLocal)
                    }
                    if (resolved) {
                        Toast.makeText(context, "Konflikt behoben!", Toast.LENGTH_SHORT).show()
                        selectedDiffFile = null
                        refreshData()
                    }
                }
            }
        )
    }
}

@Composable
fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        Text(value, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodySmall)
    }
}
