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
import androidx.compose.ui.unit.sp
import de.fisch981.gitsyncer.data.GitHubApiClient
import de.fisch981.gitsyncer.data.RepositoryStore
import de.fisch981.gitsyncer.ui.theme.GitGreen
import de.fisch981.gitsyncer.ui.theme.GitRed
import de.fisch981.gitsyncer.ui.theme.LocalAppStrings
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
    val strings = LocalAppStrings.current

    val savedToken by repoStore.githubToken.collectAsState()
    val savedUser by repoStore.githubUser.collectAsState()
    val savedBaseDir by repoStore.defaultBaseDir.collectAsState()
    val currentLang by repoStore.appLanguage.collectAsState()

    var tokenInput by remember(savedToken) { mutableStateOf(savedToken) }
    var isPasswordVisible by remember { mutableStateOf(false) }
    var isTestingToken by remember { mutableStateOf(false) }
    var githubStatusMessage by remember { mutableStateOf<String?>(null) }
    var isGithubSuccess by remember { mutableStateOf(false) }

    var baseDirInput by remember(savedBaseDir) { mutableStateOf(savedBaseDir) }

    val folderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
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
            Toast.makeText(context, strings.save, Toast.LENGTH_SHORT).show()
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
                title = { Text(strings.settingsTitle) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = strings.back)
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
            // Language Selection Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Language, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(strings.languageSectionTitle, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    }

                    Text(
                        strings.languageSectionDesc,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    val languageOptions = listOf(
                        "system" to strings.languageSystem,
                        "de" to strings.languageGerman,
                        "en" to strings.languageEnglish
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        languageOptions.forEach { (key, label) ->
                            val isSelected = currentLang == key
                            FilterChip(
                                selected = isSelected,
                                onClick = { repoStore.setAppLanguage(key) },
                                label = { Text(label, fontSize = 12.sp) }
                            )
                        }
                    }
                }
            }

            // Central GitHub Credentials Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.AccountCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(strings.githubAccountSectionTitle, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    }

                    Text(
                        strings.githubAccountSectionDesc,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    OutlinedTextField(
                        value = tokenInput,
                        onValueChange = { tokenInput = it },
                        label = { Text(strings.githubTokenLabel) },
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
                                        }
                                    }
                                }) {
                                    Icon(Icons.Default.ContentPaste, contentDescription = strings.copy)
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

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                if (tokenInput.isBlank()) {
                                    Toast.makeText(context, strings.githubTokenHint, Toast.LENGTH_SHORT).show()
                                    return@Button
                                }
                                isTestingToken = true
                                githubStatusMessage = null
                                coroutineScope.launch {
                                    val result = api.getUser(tokenInput.trim())
                                    isTestingToken = false
                                    result.onSuccess { user ->
                                        isGithubSuccess = true
                                        githubStatusMessage = "${strings.tokenValid} @${user.login} (${user.name ?: ""})"
                                        repoStore.setGithubCredentials(tokenInput.trim(), user.login)
                                    }.onFailure { err ->
                                        isGithubSuccess = false
                                        githubStatusMessage = err.localizedMessage ?: strings.error
                                    }
                                }
                            },
                            enabled = !isTestingToken,
                            modifier = Modifier.weight(1f)
                        ) {
                            if (isTestingToken) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                            }
                            Text(strings.testAndSave)
                        }

                        if (savedToken.isNotBlank()) {
                            OutlinedButton(
                                onClick = {
                                    repoStore.setGithubCredentials("", "")
                                    tokenInput = ""
                                    githubStatusMessage = null
                                    Toast.makeText(context, strings.removeToken, Toast.LENGTH_SHORT).show()
                                }
                            ) {
                                Icon(Icons.Default.DeleteOutline, contentDescription = strings.delete, tint = MaterialTheme.colorScheme.error)
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
                        Text(strings.createTokenInBrowser)
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
                                    text = githubStatusMessage ?: "${strings.tokenValid} @$savedUser",
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
                        Text(strings.defaultBaseDirTitle, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    }

                    Text(
                        strings.defaultBaseDirDesc,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    OutlinedTextField(
                        value = baseDirInput,
                        onValueChange = {
                            baseDirInput = it
                            repoStore.setDefaultBaseDir(it)
                        },
                        label = { Text(strings.defaultBaseDirTitle) },
                        trailingIcon = {
                            IconButton(onClick = { folderPickerLauncher.launch(null) }) {
                                Icon(Icons.Default.FolderOpen, contentDescription = strings.chooseFolder)
                            }
                        },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Button(
                        onClick = {
                            repoStore.setDefaultBaseDir(baseDirInput)
                            Toast.makeText(context, strings.save, Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(strings.save)
                    }
                }
            }

            // System Permissions Card
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(strings.systemIntegrationsTitle, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)

                    // Storage
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(strings.storageAccessAllFiles, fontWeight = FontWeight.SemiBold)
                            Text(
                                if (hasStoragePermission) strings.storageAccessGranted else strings.storageAccessNotGranted,
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
                                Text(strings.allow)
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
                            Text(strings.batteryOptimizationTitle, fontWeight = FontWeight.SemiBold)
                            Text(
                                if (isIgnoringBatteryOptimizations) strings.batteryOptimizationIgnored else strings.batteryOptimizationNotIgnored,
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
                                Text(strings.allow)
                            }
                        }
                    }
                }
            }

            // Quick Settings Tile guide
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(strings.quickSettingsTileTitle, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    Text(
                        strings.quickSettingsTileGuide,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // About Card
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(strings.aboutTitle, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    Text("${strings.versionLabel} ${de.fisch981.gitsyncer.BuildConfig.VERSION_NAME} (Build ${de.fisch981.gitsyncer.BuildConfig.VERSION_CODE})", style = MaterialTheme.typography.bodySmall)
                    Text("${strings.packageNameLabel} de.fisch981.gitsyncer", style = MaterialTheme.typography.bodySmall)
                    Text(strings.gitEngineLabel, style = MaterialTheme.typography.bodySmall)
                    Text(strings.developerLabel, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}
