package de.fisch981.gitsyncer.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import de.fisch981.gitsyncer.ui.theme.GitGreen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()

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
            // Unlocked Edition Badge
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Verified,
                        contentDescription = null,
                        tint = GitGreen,
                        modifier = Modifier.size(36.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            "Vollversion Aktiviert",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            "Alle Funktionen (unbegrenzte Repositories, freie Sync-Intervalle, SSH-Keys, Diff-Viewer) sind komplett kostenlos & ohne Bezahlschranke freigeschaltet.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
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

                    Divider()

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
                    Text("Version: 1.0.0 (Build 1)", style = MaterialTheme.typography.bodySmall)
                    Text("Paketname: de.fisch981.gitsyncer", style = MaterialTheme.typography.bodySmall)
                    Text("Engine: Eclipse JGit 6.8 & JSch SSH Provider", style = MaterialTheme.typography.bodySmall)
                    Text("Entwickelt für: fisch981", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}
