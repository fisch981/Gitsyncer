package de.fisch981.gitsyncer.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
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
import de.fisch981.gitsyncer.model.SshKeyEntry
import de.fisch981.gitsyncer.ssh.SshKeyManager
import de.fisch981.gitsyncer.ui.theme.LocalAppStrings

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SshKeysScreen(
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val repoStore = remember { RepositoryStore.getInstance(context) }
    val sshKeys by repoStore.sshKeys.collectAsState()
    val strings = LocalAppStrings.current

    var showGenerateDialog by remember { mutableStateOf(false) }
    var showImportDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(strings.sshKeysTitle) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = strings.back)
                    }
                },
                actions = {
                    IconButton(onClick = { showImportDialog = true }) {
                        Icon(Icons.Default.FileDownload, contentDescription = strings.importSshKey)
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showGenerateDialog = true },
                containerColor = MaterialTheme.colorScheme.primary
            ) {
                Icon(Icons.Default.Add, contentDescription = strings.generateSshKey)
            }
        }
    ) { padding ->
        if (sshKeys.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.VpnKey,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        strings.noSshKeys,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        strings.noSshKeysDesc,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                    Spacer(modifier = Modifier.height(20.dp))
                    Button(onClick = { showGenerateDialog = true }) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(strings.generateSshKey)
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(sshKeys, key = { it.id }) { key ->
                    SshKeyCard(
                        key = key,
                        onCopyPublic = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            val clip = ClipData.newPlainText("SSH Public Key", key.publicKey)
                            clipboard.setPrimaryClip(clip)
                            Toast.makeText(context, strings.publicKeyCopied, Toast.LENGTH_SHORT).show()
                        },
                        onDelete = {
                            repoStore.deleteSshKey(key.id)
                            Toast.makeText(context, strings.keyDeleted, Toast.LENGTH_SHORT).show()
                        }
                    )
                }
            }
        }
    }

    if (showGenerateDialog) {
        GenerateKeyDialog(
            onDismiss = { showGenerateDialog = false },
            onGenerate = { name, type ->
                val newKey = if (type == "Ed25519") {
                    SshKeyManager.generateEd25519Key(name)
                } else {
                    SshKeyManager.generateRsaKey(name, 2048)
                }
                repoStore.saveSshKey(newKey)
                showGenerateDialog = false
                Toast.makeText(context, strings.keyCreatedSuccess, Toast.LENGTH_SHORT).show()
            }
        )
    }

    if (showImportDialog) {
        ImportKeyDialog(
            onDismiss = { showImportDialog = false },
            onImport = { name, privKey, pubKey ->
                val entry = SshKeyEntry(
                    name = name,
                    publicKey = pubKey,
                    privateKey = privKey,
                    keyType = "Imported",
                    createdTimestamp = System.currentTimeMillis()
                )
                repoStore.saveSshKey(entry)
                showImportDialog = false
                Toast.makeText(context, strings.keyImportedSuccess, Toast.LENGTH_SHORT).show()
            }
        )
    }
}

@Composable
fun SshKeyCard(
    key: SshKeyEntry,
    onCopyPublic: () -> Unit,
    onDelete: () -> Unit
) {
    val strings = LocalAppStrings.current

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Key,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(key.name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                        Text(
                            key.keyType,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Default.DeleteOutline,
                        contentDescription = strings.delete,
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                strings.publicKeyTitle,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(4.dp))

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onCopyPublic() },
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            ) {
                Text(
                    text = key.publicKey,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    maxLines = 2,
                    modifier = Modifier.padding(8.dp)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Button(
                onClick = onCopyPublic,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors()
            ) {
                Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(strings.copyPublicKeyHint)
            }
        }
    }
}

@Composable
fun GenerateKeyDialog(
    onDismiss: () -> Unit,
    onGenerate: (name: String, type: String) -> Unit
) {
    val strings = LocalAppStrings.current
    var name by remember { mutableStateOf("Android Device") }
    var selectedType by remember { mutableStateOf("RSA") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(strings.generateDialogTitle) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(strings.keyNameLabel) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Text(strings.keyTypeLabel, style = MaterialTheme.typography.labelMedium)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = selectedType == "RSA",
                        onClick = { selectedType = "RSA" },
                        label = { Text("RSA (2048-bit)") }
                    )
                    FilterChip(
                        selected = selectedType == "Ed25519",
                        onClick = { selectedType = "Ed25519" },
                        label = { Text("Ed25519") }
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isNotBlank()) onGenerate(name.trim(), selectedType)
                }
            ) {
                Text(strings.generateAction)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(strings.cancel) }
        }
    )
}

@Composable
fun ImportKeyDialog(
    onDismiss: () -> Unit,
    onImport: (name: String, privKey: String, pubKey: String) -> Unit
) {
    val strings = LocalAppStrings.current
    var name by remember { mutableStateOf("") }
    var privKey by remember { mutableStateOf("") }
    var pubKey by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(strings.importDialogTitle) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(strings.keyNameLabel) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = privKey,
                    onValueChange = { privKey = it },
                    label = { Text(strings.privateKeyLabel) },
                    modifier = Modifier.fillMaxWidth().height(100.dp),
                    placeholder = { Text("-----BEGIN RSA PRIVATE KEY-----...") }
                )
                OutlinedTextField(
                    value = pubKey,
                    onValueChange = { pubKey = it },
                    label = { Text(strings.publicKeyLabel) },
                    modifier = Modifier.fillMaxWidth().height(80.dp),
                    placeholder = { Text("ssh-rsa AAAA...") }
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isNotBlank() && privKey.isNotBlank()) {
                        onImport(name.trim(), privKey.trim(), pubKey.trim())
                    }
                }
            ) {
                Text(strings.importAction)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(strings.cancel) }
        }
    )
}
