package de.fisch981.gitsyncer.ui.screens

import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
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
import de.fisch981.gitsyncer.data.GitHubApiClient
import de.fisch981.gitsyncer.data.RepositoryStore
import de.fisch981.gitsyncer.ui.theme.GitGreen
import de.fisch981.gitsyncer.ui.theme.GitRed
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val scrollState = rememberScrollState()
    val repoStore = remember { RepositoryStore.getInstance(context) }
    val api = remember { GitHubApiClient() }

    val savedToken by repoStore.githubToken.collectAsState()
    val savedUser by repoStore.githubUser.collectAsState()
    val savedBaseDir by repoStore.defaultBaseDir.collectAsState()

    var tokenInput by remember(savedToken) { mutableStateOf(savedToken) }
    var baseDirInput by remember(savedBaseDir) { mutableStateOf(savedBaseDir) }
    var isPasswordVisible by remember { mutableStateOf(false) }
    var isCheckingGithub by remember { mutableStateOf(false) }
    var githubStatusMessage by remember { mutableStateOf<String?>(null) }
    var isGithubSuccess by remember { mutableStateOf(false) }

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
            baseDirInput = path
            repoStore.setDefaultBaseDir(path)
            Toast.makeText(context, "Basisverzeichnis gespeichert", Toast.LENGTH_SHORT).show()
        }
    }

    val hasStoragePermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        Environment.isExternalStorageManager()
    } else true

    val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
    val isIgnoringBatteryOptimizations = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        powerManager?.isIgnoringBatteryOptimizations(context.packageName) ?: false
    } else true

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Einstellungen") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Zurück")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(scrollState)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Central GitHub Credentials Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.AccountCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Zentraler GitHub Account", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    }

                    Text(
                        "Hinterlege dein GitHub-Token einmalig hier. Es wird automatisch für alle neuen Repositories verwendet und liest deine Repositories direkt aus GitHub aus.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    OutlinedTextField(
                        value = tokenInput,
                        onValueChange = { tokenInput = it },
                        label = { Text("GitHub Personal Access Token (PAT)") },
                        placeholder = { Text("ghp_...") },
                        visualTransformation = if (isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            Row {
                                IconButton(onClick = {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    val clip = clipboard.primaryClip
                                    if (clip != null && clip.itemCount > 0) {
                                        val pasted = clip.getItemAt(0).text?.toString() ?: ""
                                        if (pasted.isNotBlank()) {
                                            tokenInput = pasted.trim()
                                            Toast.makeText(context, "Eingefügt", Toast.LENGTH_SHORT).show()
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

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                                if (tokenInput.isBlank()) {
                                    Toast.makeText(context, "Bitte Token eingeben", Toast.LENGTH_SHORT).show()
                                    return@Button
                                }
                                isCheckingGithub = true
                                githubStatusMessage = null
                                coroutineScope.launch {
                                    val result = api.getUser(tokenInput.trim())
                                    isCheckingGithub = false
                                    result.onSuccess { user ->
                                        repoStore.setGithubCredentials(tokenInput.trim(), user.login)
                                        isGithubSuccess = true
                                        githubStatusMessage = "Erfolgreich verbunden als @${user.login}!"
                                        Toast.makeText(context, "GitHub Account gespeichert!", Toast.LENGTH_SHORT).show()
                                    }.onFailure { err ->
                                        isGithubSuccess = false
                                        githubStatusMessage = "Verbindungsfehler: ${err.localizedMessage}"
                                    }
                                }
                            },
                            modifier = Modifier.weight(1f),
                            enabled = !isCheckingGithub
                        ) {
                            if (isCheckingGithub) {
                                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                            } else {
                                Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Verbindung testen & Speichern")
                            }
                        }

                        if (savedToken.isNotBlank()) {
                            OutlinedButton(
                                onClick = {
                                    tokenInput = ""
                                    repoStore.setGithubCredentials("", "")
                                    githubStatusMessage = null
                                    Toast.makeText(context, "Zugangsdaten entfernt", Toast.LENGTH_SHORT).show()
                                }
                            ) {
                                Icon(Icons.Default.DeleteOutline, contentDescription = "Löschen", tint = MaterialTheme.colorScheme.error)
                            }
                        }
                    }

                    // Direct Browser Button
                    OutlinedButton(
                        onClick = {
                            val url = "https://github.com/settings/tokens/new?description=GitSyncer&scopes=repo"
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                            context.startActivity(intent)
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.OpenInBrowser, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Neues Token bei GitHub im Browser erstellen")
                    }

                    AnimatedVisibility(visible = githubStatusMessage != null || savedUser.isNotBlank()) {
                        Surface(
                            shape = MaterialTheme.shapes.small,
                            color = if (isGithubSuccess || savedUser.isNotBlank()) GitGreen.copy(alpha = 0.15f) else GitRed.copy(alpha = 0.15f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    if (isGithubSuccess || savedUser.isNotBlank()) Icons.Default.CheckCircle else Icons.Default.Error,
                                    contentDescription = null,
                                    tint = if (isGithubSuccess || savedUser.isNotBlank()) GitGreen else GitRed,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = githubStatusMessage ?: "Verbunden als @$savedUser",
                                    color = if (isGithubSuccess || savedUser.isNotBlank()) GitGreen else GitRed,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }
            }

            // Global Base Directory Card
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Folder, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Standard-Basisverzeichnis", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    }

                    Text(
                        "In diesem Basisverzeichnis werden neue Repositories automatisch als eigener Unterordner angelegt (z. B. <Basisverzeichnis>/<RepoName>).",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    OutlinedTextField(
                        value = baseDirInput,
                        onValueChange = {
                            baseDirInput = it
                            repoStore.setDefaultBaseDir(it)
                        },
                        label = { Text("Basis-Ordnerpfad") },
                        trailingIcon = {
                            IconButton(onClick = { folderPickerLauncher.launch(null) }) {
                                Icon(Icons.Default.FolderOpen, contentDescription = "Ordner wählen")
                            }
                        },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Button(
                        onClick = {
                            repoStore.setDefaultBaseDir(baseDirInput)
                            Toast.makeText(context, "Basisverzeichnis gespeichert!", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Pfad speichern")
                    }
                }
            }

            // System Permissions Card
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Berechtigungen & Hintergrundbetrieb", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)

                    // Storage
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Vollständiger Speicherzugriff", fontWeight = FontWeight.SemiBold)
                            Text(
                                if (hasStoragePermission) "Erteilt (Zugriff auf alle Vault-Ordner aktiv)" else "Nicht erteilt",
                                color = if (hasStoragePermission) GitGreen else MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }

                        if (!hasStoragePermission) {
                            Button(
                                onClick = {
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                                        val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                                            data = Uri.parse("package:${context.packageName}")
                                        }
                                        context.startActivity(intent)
                                    }
                                }
                            ) {
                                Text("Aktivieren")
                            }
                        }
                    }

                    HorizontalDivider()

                    // Battery optimization
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Akku-Optimierung ignorieren", fontWeight = FontWeight.SemiBold)
                            Text(
                                if (isIgnoringBatteryOptimizations) "Ausgenommen (Zuverlässiger Hintergrundsync)" else "Nicht ausgenommen (Android kann Sync verzögern)",
                                color = if (isIgnoringBatteryOptimizations) GitGreen else MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }

                        if (!isIgnoringBatteryOptimizations) {
                            Button(
                                onClick = {
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                                        val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                                            data = Uri.parse("package:${context.packageName}")
                                        }
                                        context.startActivity(intent)
                                    }
                                }
                            ) {
                                Text("Erlauben")
                            }
                        }
                    }
                }
            }

            // Quick Settings Tile guide
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Schnell-Einstellungen Kachel (Quick Settings)", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Du kannst GitSyncer zu deiner Android-Benachrichtigungsleiste hinzufügen:\n" +
                                "1. Ziehe die Statusleiste zweimal nach unten.\n" +
                                "2. Tippe auf das Stift-Symbol (Bearbeiten).\n" +
                                "3. Ziehe die Kachel 'Git Sync' in deine aktiven Kacheln.\n\n" +
                                "So kannst du jederzeit mit einem einzigen Fingertipp alle Repositories synchronisieren!",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // About Card
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Über GitSyncer", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    Text("Version: ${de.fisch981.gitsyncer.BuildConfig.VERSION_NAME} (Build ${de.fisch981.gitsyncer.BuildConfig.VERSION_CODE})", style = MaterialTheme.typography.bodySmall)
                    Text("Paketname: de.fisch981.gitsyncer", style = MaterialTheme.typography.bodySmall)
                    Text("Engine: Eclipse JGit 6.8 & JSch SSH Provider", style = MaterialTheme.typography.bodySmall)
                    Text("Entwickelt für: fisch981", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}
