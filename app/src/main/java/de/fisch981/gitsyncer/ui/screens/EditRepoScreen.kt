package de.fisch981.gitsyncer.ui.screens

import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import de.fisch981.gitsyncer.data.GitHubApiClient
import de.fisch981.gitsyncer.data.GitHubRepo
import de.fisch981.gitsyncer.data.RepositoryStore
import de.fisch981.gitsyncer.git.GitManager
import de.fisch981.gitsyncer.model.*
import de.fisch981.gitsyncer.sync.SyncScheduler
import de.fisch981.gitsyncer.ui.theme.GitGreen
import de.fisch981.gitsyncer.ui.theme.LocalAppStrings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
    val api = remember { GitHubApiClient() }
    val strings = LocalAppStrings.current

    val sshKeys by repoStore.sshKeys.collectAsState()
    val globalToken by repoStore.githubToken.collectAsState()
    val globalUser by repoStore.githubUser.collectAsState()
    val defaultBaseDir by repoStore.defaultBaseDir.collectAsState()

    val existingRepo = remember(repoId) {
        if (repoId != null) repoStore.getRepository(repoId) else null
    }

    var name by remember { mutableStateOf(existingRepo?.name ?: "") }
    var remoteUrl by remember { mutableStateOf(existingRepo?.remoteUrl ?: "") }
    var localPath by remember {
        mutableStateOf(
            existingRepo?.localPath ?: if (name.isNotBlank()) "$defaultBaseDir/$name" else defaultBaseDir
        )
    }
    var branch by remember { mutableStateOf(existingRepo?.branch ?: "main") }
    var authType by remember { mutableStateOf(existingRepo?.authType ?: AuthType.HTTPS_TOKEN) }
    var tokenOrPassword by remember {
        mutableStateOf(
            existingRepo?.tokenOrPassword ?: globalToken
        )
    }
    var username by remember {
        mutableStateOf(
            existingRepo?.username ?: globalUser
        )
    }
    var selectedSshKeyId by remember { mutableStateOf(existingRepo?.sshKeyId ?: sshKeys.firstOrNull()?.id) }
    var isEnabled by remember { mutableStateOf(existingRepo?.isEnabled ?: true) }
    var autoSyncEnabled by remember { mutableStateOf(existingRepo?.autoSyncEnabled ?: true) }
    var shallowClone by remember { mutableStateOf(existingRepo?.shallowClone ?: true) }
    var releaseApksOnly by remember { mutableStateOf(existingRepo?.releaseApksOnly ?: false) }
    var syncIntervalMinutes by remember { mutableStateOf(existingRepo?.syncIntervalMinutes ?: 60) }
    var syncDirection by remember { mutableStateOf(existingRepo?.syncDirection ?: SyncDirection.TWO_WAY) }
    var autoCommitMessage by remember { mutableStateOf(existingRepo?.autoCommitMessage ?: "Sync %timestamp%") }
    var authorName by remember {
        mutableStateOf(
            existingRepo?.authorName ?: if (globalUser.isNotBlank()) globalUser else "GitSyncer"
        )
    }
    var authorEmail by remember { mutableStateOf(existingRepo?.authorEmail ?: "gitsyncer@fisch981.de") }

    var isPasswordVisible by remember { mutableStateOf(false) }
    var isCloning by remember { mutableStateOf(false) }
    var statusText by remember { mutableStateOf("") }

    // GitHub Repo Selector Dialog State
    var showGithubPicker by remember { mutableStateOf(false) }
    var githubRepos by remember { mutableStateOf<List<GitHubRepo>>(emptyList()) }
    var isFetchingRepos by remember { mutableStateOf(false) }
    var repoFilter by remember { mutableStateOf("") }

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
            val cleanPath = path.trim().trimEnd('/', '\\')
            val cleanBase = defaultBaseDir.trim().trimEnd('/', '\\')
            localPath = if (!releaseApksOnly && cleanPath.equals(cleanBase, ignoreCase = true) && name.isNotBlank()) {
                "$cleanBase/${name.trim()}"
            } else {
                cleanPath
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (existingRepo == null) strings.addRepoTitle else strings.editRepoTitle) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = strings.back)
                    }
                },
                actions = {
                    if (existingRepo != null) {
                        IconButton(
                            onClick = {
                                SyncScheduler.cancelRepo(context, existingRepo.id)
                                repoStore.deleteRepository(existingRepo.id)
                                Toast.makeText(context, strings.repoDeleted, Toast.LENGTH_SHORT).show()
                                onNavigateBack()
                            }
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = strings.delete, tint = MaterialTheme.colorScheme.error)
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
            // GitHub Quick Import Card (for new repositories)
            if (existingRepo == null) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.CloudDownload,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                strings.githubImportTitle,
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }

                        Text(
                            if (globalToken.isNotBlank()) {
                                String.format(strings.githubConnectedDesc, globalUser.ifBlank { "User" })
                            } else {
                                strings.githubNotConnectedDesc
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )

                        Button(
                            onClick = {
                                val tokenToUse = if (tokenOrPassword.isNotBlank()) tokenOrPassword else globalToken
                                if (tokenToUse.isBlank()) {
                                    Toast.makeText(context, strings.githubTokenRequiredHint, Toast.LENGTH_LONG).show()
                                    return@Button
                                }
                                showGithubPicker = true
                                isFetchingRepos = true
                                coroutineScope.launch {
                                    val result = api.listUserRepos(tokenToUse)
                                    isFetchingRepos = false
                                    result.onSuccess { list ->
                                        githubRepos = list
                                    }.onFailure { err ->
                                        Toast.makeText(context, "${strings.error}: ${err.localizedMessage}", Toast.LENGTH_LONG).show()
                                    }
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.surface,
                                contentColor = MaterialTheme.colorScheme.primary
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.List, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(strings.githubLoadReposButton)
                        }
                    }
                }
            }

            // General Info Card
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(strings.generalInfoTitle, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

                    OutlinedTextField(
                        value = name,
                        onValueChange = {
                            name = it
                            if (existingRepo == null && (localPath == defaultBaseDir || localPath.startsWith(defaultBaseDir))) {
                                localPath = if (it.isNotBlank()) "$defaultBaseDir/$it" else defaultBaseDir
                            }
                        },
                        label = { Text(strings.repoNameLabel) },
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
                        label = { Text(strings.repoRemoteUrlLabel) },
                        placeholder = { Text("https://github.com/user/notes.git") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = localPath,
                        onValueChange = { localPath = it },
                        label = { Text(strings.repoLocalPathLabel) },
                        trailingIcon = {
                            IconButton(onClick = { folderPickerLauncher.launch(null) }) {
                                Icon(Icons.Default.FolderOpen, contentDescription = strings.chooseFolder)
                            }
                        },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    val cleanCurrentPath = localPath.trim().trimEnd('/', '\\')
                    val cleanBaseDir = defaultBaseDir.trim().trimEnd('/', '\\')
                    if (!releaseApksOnly && cleanCurrentPath.equals(cleanBaseDir, ignoreCase = true) && cleanCurrentPath.isNotBlank()) {
                        Surface(
                            shape = MaterialTheme.shapes.small,
                            color = MaterialTheme.colorScheme.errorContainer
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    strings.baseDirWarning,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    modifier = Modifier.weight(1f)
                                )
                                if (name.isNotBlank()) {
                                    TextButton(onClick = { localPath = "$cleanBaseDir/${name.trim()}" }) {
                                        Text(String.format(strings.appendSubfolder, name.trim()))
                                    }
                                }
                            }
                        }
                    }

                    if (!releaseApksOnly) {
                        OutlinedTextField(
                            value = branch,
                            onValueChange = { branch = it },
                            label = { Text(strings.repoBranchLabel) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            // Authentication Card
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(strings.authCardTitle, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = authType == AuthType.HTTPS_TOKEN,
                            onClick = { authType = AuthType.HTTPS_TOKEN },
                            label = { Text(strings.authHttpsToken) }
                        )
                        FilterChip(
                            selected = authType == AuthType.SSH_KEY,
                            onClick = { authType = AuthType.SSH_KEY },
                            label = { Text(strings.authSshKey) }
                        )
                        FilterChip(
                            selected = authType == AuthType.HTTPS_CREDENTIALS,
                            onClick = { authType = AuthType.HTTPS_CREDENTIALS },
                            label = { Text(strings.authCredentials) }
                        )
                    }

                    when (authType) {
                        AuthType.HTTPS_TOKEN -> {
                            OutlinedTextField(
                                value = tokenOrPassword,
                                onValueChange = { tokenOrPassword = it },
                                label = { Text(strings.patLabel) },
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
                                                    Toast.makeText(context, strings.pastedFromClipboard, Toast.LENGTH_SHORT).show()
                                                }
                                            }
                                        }) {
                                            Icon(Icons.Default.ContentPaste, contentDescription = strings.paste)
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

                            if (globalToken.isNotBlank() && tokenOrPassword != globalToken) {
                                TextButton(
                                    onClick = {
                                        tokenOrPassword = globalToken
                                        username = globalUser
                                        Toast.makeText(context, strings.centralTokenApplied, Toast.LENGTH_SHORT).show()
                                    }
                                ) {
                                    Icon(Icons.Default.Sync, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(String.format(strings.useCentralToken, globalUser.ifBlank { "GitHub" }))
                                }
                            }

                            // 1-Click Browser Button
                            OutlinedButton(
                                onClick = {
                                    val url = "https://github.com/settings/tokens/new?description=GitSyncer&scopes=repo"
                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                                    context.startActivity(intent)
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.OpenInBrowser, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(strings.createTokenBrowser)
                            }
                        }
                        AuthType.HTTPS_CREDENTIALS -> {
                            OutlinedTextField(
                                value = username,
                                onValueChange = { username = it },
                                label = { Text(strings.usernameLabel) },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            OutlinedTextField(
                                value = tokenOrPassword,
                                onValueChange = { tokenOrPassword = it },
                                label = { Text(strings.passwordLabel) },
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
                            if (releaseApksOnly) {
                                Surface(
                                    shape = MaterialTheme.shapes.small,
                                    color = MaterialTheme.colorScheme.secondaryContainer
                                ) {
                                    Text(
                                        text = strings.sshApkNotice,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                                        modifier = Modifier.padding(10.dp)
                                    )
                                }
                            }
                            if (sshKeys.isEmpty()) {
                                Text(
                                    strings.noSshKeysHint,
                                    color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.bodySmall
                                )
                                Button(
                                    onClick = onNavigateToSshKeys,
                                    colors = ButtonDefaults.outlinedButtonColors()
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = null)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(strings.createSshKeyNow)
                                }
                            } else {
                                Text(strings.selectSshKey, style = MaterialTheme.typography.bodyMedium)
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
                    Text(strings.syncOptionsTitle, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(strings.repoActive, fontWeight = FontWeight.SemiBold)
                            Text(strings.repoActiveDesc, style = MaterialTheme.typography.bodySmall)
                        }
                        Switch(
                            checked = isEnabled,
                            onCheckedChange = { isEnabled = it }
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(strings.releaseApksOnlyLabel, fontWeight = FontWeight.SemiBold)
                            Text(
                                strings.releaseApksOnlyDesc,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = releaseApksOnly,
                            onCheckedChange = { releaseApksOnly = it }
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(strings.autoSyncLabel, fontWeight = FontWeight.SemiBold)
                            Text(strings.autoSyncDesc, style = MaterialTheme.typography.bodySmall)
                        }
                        Switch(
                            checked = autoSyncEnabled,
                            onCheckedChange = { autoSyncEnabled = it }
                        )
                    }

                    if (!releaseApksOnly) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(strings.shallowCloneLabel, fontWeight = FontWeight.SemiBold)
                                Text(
                                    strings.shallowCloneDesc,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = shallowClone,
                                onCheckedChange = { shallowClone = it }
                            )
                        }
                    }

                    if (autoSyncEnabled) {
                        Text(strings.syncIntervalLabel, style = MaterialTheme.typography.labelMedium)
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

                    if (!releaseApksOnly) {
                        Text(strings.syncDirectionLabel, style = MaterialTheme.typography.labelMedium)
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(
                                selected = syncDirection == SyncDirection.TWO_WAY,
                                onClick = { syncDirection = SyncDirection.TWO_WAY },
                                label = { Text(strings.syncTwoWay) }
                            )
                            FilterChip(
                                selected = syncDirection == SyncDirection.PUSH_ONLY,
                                onClick = { syncDirection = SyncDirection.PUSH_ONLY },
                                label = { Text(strings.syncPushOnly) }
                            )
                            FilterChip(
                                selected = syncDirection == SyncDirection.PULL_ONLY,
                                onClick = { syncDirection = SyncDirection.PULL_ONLY },
                                label = { Text(strings.syncPullOnly) }
                            )
                        }

                        OutlinedTextField(
                            value = autoCommitMessage,
                            onValueChange = { autoCommitMessage = it },
                            label = { Text(strings.commitMessageTemplateLabel) },
                            placeholder = { Text("Sync %timestamp%") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = authorName,
                                onValueChange = { authorName = it },
                                label = { Text(strings.gitAuthorLabel) },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = authorEmail,
                                onValueChange = { authorEmail = it },
                                label = { Text(strings.gitEmailLabel) },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                        }
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
                        Text(statusText.ifBlank { strings.syncing }, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }

            fun buildUpdatedConfig(preserveSyncState: Boolean): GitRepositoryConfig {
                val cleanBase = defaultBaseDir.trim().trimEnd('/', '\\')
                var resolvedPath = localPath.trim().trimEnd('/', '\\')
                if (!releaseApksOnly && resolvedPath.equals(cleanBase, ignoreCase = true) && name.isNotBlank()) {
                    resolvedPath = "$cleanBase/${name.trim()}"
                    localPath = resolvedPath
                }
                return GitRepositoryConfig(
                    id = existingRepo?.id ?: java.util.UUID.randomUUID().toString(),
                    name = name.trim(),
                    remoteUrl = remoteUrl.trim(),
                    localPath = resolvedPath,
                    branch = branch.trim().ifBlank { "main" },
                    authType = authType,
                    tokenOrPassword = tokenOrPassword.trim(),
                    username = username.trim(),
                    sshKeyId = selectedSshKeyId,
                    autoSyncEnabled = autoSyncEnabled,
                    isEnabled = isEnabled,
                    shallowClone = shallowClone,
                    releaseApksOnly = releaseApksOnly,
                    lastReleaseTag = existingRepo?.lastReleaseTag,
                    lastReleaseAssetMeta = existingRepo?.lastReleaseAssetMeta,
                    syncIntervalMinutes = syncIntervalMinutes,
                    syncDirection = syncDirection,
                    autoCommitMessage = autoCommitMessage.trim(),
                    authorName = authorName.trim(),
                    authorEmail = authorEmail.trim(),
                    lastSyncStatus = if (preserveSyncState) (existingRepo?.lastSyncStatus ?: "IDLE") else "IDLE",
                    lastSyncTimestamp = if (preserveSyncState) (existingRepo?.lastSyncTimestamp ?: 0L) else 0L,
                    lastErrorMessage = if (preserveSyncState) existingRepo?.lastErrorMessage else null
                )
            }

            // Save Buttons
            Button(
                onClick = {
                    if (name.isBlank() || remoteUrl.isBlank() || localPath.isBlank()) {
                        Toast.makeText(context, strings.fillRequiredFields, Toast.LENGTH_SHORT).show()
                        return@Button
                    }

                    val updatedConfig = buildUpdatedConfig(preserveSyncState = false)
                    repoStore.saveRepository(updatedConfig)
                    SyncScheduler.scheduleRepo(context, updatedConfig)

                    isCloning = true
                    statusText = if (updatedConfig.releaseApksOnly) {
                        strings.downloadingApks
                    } else {
                        strings.cloningRepository
                    }
                    coroutineScope.launch {
                        val result = withContext(Dispatchers.IO) {
                            if (updatedConfig.releaseApksOnly) {
                                gitManager.sync(updatedConfig) { msg, _ -> statusText = msg }
                            } else if (!gitManager.isGitRepo(updatedConfig.localPath)) {
                                gitManager.cloneRepository(updatedConfig) { msg, _ -> statusText = msg }
                            } else {
                                gitManager.sync(updatedConfig) { msg, _ -> statusText = msg }
                            }
                        }
                        isCloning = false
                        if (result.success) {
                            repoStore.updateRepoSyncStatus(updatedConfig.id, "SUCCESS")
                            val successMsg = if (updatedConfig.releaseApksOnly) result.message else strings.repoSetupSuccess
                            Toast.makeText(context, successMsg, Toast.LENGTH_SHORT).show()
                            onNavigateBack()
                        } else {
                            repoStore.updateRepoSyncStatus(updatedConfig.id, "FAILED", result.message)
                            Toast.makeText(context, "${strings.warning}: ${result.message}", Toast.LENGTH_LONG).show()
                            onNavigateBack()
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isCloning
            ) {
                Icon(Icons.Default.Save, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(strings.saveAndSync)
            }

            OutlinedButton(
                onClick = {
                    if (name.isBlank() || remoteUrl.isBlank() || localPath.isBlank()) {
                        Toast.makeText(context, strings.fillRequiredFields, Toast.LENGTH_SHORT).show()
                        return@OutlinedButton
                    }

                    val updatedConfig = buildUpdatedConfig(preserveSyncState = true)
                    repoStore.saveRepository(updatedConfig)
                    SyncScheduler.scheduleRepo(context, updatedConfig)
                    Toast.makeText(context, strings.saved, Toast.LENGTH_SHORT).show()
                    onNavigateBack()
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(strings.saveOnly)
            }
        }
    }

    // GitHub Repo Selector Dialog
    if (showGithubPicker) {
        Dialog(
            onDismissRequest = { showGithubPicker = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth(0.95f)
                    .fillMaxHeight(0.85f),
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 6.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(strings.githubSelectRepoTitle, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        IconButton(onClick = { showGithubPicker = false }) {
                            Icon(Icons.Default.Close, contentDescription = strings.close)
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = repoFilter,
                        onValueChange = { repoFilter = it },
                        label = { Text(strings.filterReposPlaceholder) },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    if (isFetchingRepos) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                CircularProgressIndicator()
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(strings.loadingGithubRepos)
                            }
                        }
                    } else {
                        val filteredList = githubRepos.filter {
                            it.name.contains(repoFilter, ignoreCase = true) ||
                            (it.description?.contains(repoFilter, ignoreCase = true) == true)
                        }

                        if (filteredList.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(strings.noMatchingReposFound)
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(filteredList) { repo ->
                                    Card(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                name = repo.name
                                                remoteUrl = repo.clone_url
                                                branch = repo.default_branch
                                                localPath = "$defaultBaseDir/${repo.name}"
                                                authType = AuthType.HTTPS_TOKEN
                                                if (tokenOrPassword.isBlank()) {
                                                    tokenOrPassword = globalToken
                                                }
                                                if (username.isBlank()) {
                                                    username = globalUser
                                                }
                                                showGithubPicker = false
                                                Toast.makeText(context, String.format(strings.repoAppliedToast, repo.name), Toast.LENGTH_SHORT).show()
                                            }
                                    ) {
                                        Column(modifier = Modifier.padding(12.dp)) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Icon(
                                                        if (repo.private) Icons.Default.Lock else Icons.Default.Public,
                                                        contentDescription = null,
                                                        modifier = Modifier.size(16.dp),
                                                        tint = if (repo.private) MaterialTheme.colorScheme.error else GitGreen
                                                    )
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    Text(repo.name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                                                }

                                                Surface(
                                                    shape = MaterialTheme.shapes.extraSmall,
                                                    color = MaterialTheme.colorScheme.surfaceVariant
                                                ) {
                                                    Text(
                                                        repo.default_branch,
                                                        fontSize = 11.sp,
                                                        fontFamily = FontFamily.Monospace,
                                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                                    )
                                                }
                                            }

                                            if (!repo.description.isNullOrBlank()) {
                                                Spacer(modifier = Modifier.height(4.dp))
                                                Text(
                                                    repo.description,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    maxLines = 2
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
