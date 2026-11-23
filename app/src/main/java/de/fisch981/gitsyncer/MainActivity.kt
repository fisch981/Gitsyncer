package de.fisch981.gitsyncer

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.DocumentsContract
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import de.fisch981.gitsyncer.data.RepositoryStore
import de.fisch981.gitsyncer.ui.screens.*
import de.fisch981.gitsyncer.ui.theme.GitSyncerTheme
import de.fisch981.gitsyncer.ui.theme.LocalAppStrings
import de.fisch981.gitsyncer.ui.theme.getAppStrings
import java.io.File

class MainActivity : ComponentActivity() {

    private val requestNotificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { _ ->
            // Notification permission result handled
        }

    private val pendingRoute = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleNotificationIntent(intent)

        // Request notification permission on Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                requestNotificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        setContent {
            val repoStore = remember { RepositoryStore.getInstance(applicationContext) }
            val appLanguage by repoStore.appLanguage.collectAsState()
            val strings = remember(appLanguage) { getAppStrings(appLanguage) }

            CompositionLocalProvider(LocalAppStrings provides strings) {
                GitSyncerTheme {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        GitSyncerNavigation(pendingRoute = pendingRoute)
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleNotificationIntent(intent)
    }

    private fun handleNotificationIntent(intent: Intent?) {
        if (intent == null) return
        val route = intent.getStringExtra("route")
        if (route == "open_folder") {
            val folderPath = intent.getStringExtra("folder_path")
            val repoId = intent.getStringExtra("repo_id")
            if (!folderPath.isNullOrBlank()) {
                openFolderInFileManager(this, folderPath, repoId)
            }
        } else if (!route.isNullOrBlank()) {
            pendingRoute.value = route
        }
    }

    companion object {
        fun openFolderInFileManager(context: Context, folderPath: String, repoId: String? = null) {
            val folder = File(folderPath)
            if (!folder.exists()) {
                folder.mkdirs()
            }

            val primaryPrefix = "/storage/emulated/0/"
            val isPrimary = folder.absolutePath.startsWith(primaryPrefix)
            val relPath = if (isPrimary) folder.absolutePath.removePrefix(primaryPrefix) else ""

            val intentsToTry = mutableListOf<Intent>()

            if (isPrimary && relPath.isNotEmpty()) {
                val encoded = Uri.encode("primary:$relPath")
                // 1. DocumentsUI directory
                intentsToTry.add(Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(
                        Uri.parse("content://com.android.externalstorage.documents/document/$encoded"),
                        "vnd.android.document/directory"
                    )
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    putExtra("org.openintents.extra.ABSOLUTE_PATH", folder.absolutePath)
                })
                // 2. DocumentsUI root
                intentsToTry.add(Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(
                        Uri.parse("content://com.android.externalstorage.documents/document/$encoded"),
                        "vnd.android.document/root"
                    )
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
                })
                // 3. System SAF OPEN_DOCUMENT_TREE
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    val treeUri = Uri.parse("content://com.android.externalstorage.documents/document/" + Uri.encode("primary:$relPath"))
                    intentsToTry.add(Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
                        putExtra(DocumentsContract.EXTRA_INITIAL_URI, treeUri)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    })
                }
            }

            // 4. FileProvider directory URI
            try {
                val fpUri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", folder)
                intentsToTry.add(Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(fpUri, "vnd.android.document/directory")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
                })
            } catch (_: Exception) {}

            // 5. Generic resource/folder
            intentsToTry.add(Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(Uri.fromFile(folder), "resource/folder")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                putExtra("org.openintents.extra.ABSOLUTE_PATH", folder.absolutePath)
            })

            for (intent in intentsToTry) {
                try {
                    context.startActivity(intent)
                    return
                } catch (_: Exception) {}
            }

            Toast.makeText(context, "Ordner: ${folder.absolutePath}", Toast.LENGTH_LONG).show()
        }
    }
}

@Composable
fun GitSyncerNavigation(
    startDestination: String = "dashboard",
    pendingRoute: MutableState<String?>? = null
) {
    val navController = rememberNavController()

    val targetRoute by (pendingRoute ?: remember { mutableStateOf(null) })
    LaunchedEffect(targetRoute) {
        targetRoute?.let { route ->
            navController.navigate(route)
            pendingRoute?.value = null
        }
    }

    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {
        composable("dashboard") {
            DashboardScreen(
                onAddRepo = { navController.navigate("add_repo") },
                onOpenRepo = { repoId -> navController.navigate("repo_detail/$repoId") },
                onEditRepo = { repoId -> navController.navigate("edit_repo/$repoId") },
                onNavigateToSshKeys = { navController.navigate("ssh_keys") },
                onNavigateToSettings = { navController.navigate("settings") }
            )
        }

        composable("add_repo") {
            EditRepoScreen(
                repoId = null,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToSshKeys = { navController.navigate("ssh_keys") }
            )
        }

        composable(
            route = "edit_repo/{repoId}",
            arguments = listOf(navArgument("repoId") { type = NavType.StringType })
        ) { backStackEntry ->
            val repoId = backStackEntry.arguments?.getString("repoId")
            EditRepoScreen(
                repoId = repoId,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToSshKeys = { navController.navigate("ssh_keys") }
            )
        }

        composable(
            route = "repo_detail/{repoId}",
            arguments = listOf(navArgument("repoId") { type = NavType.StringType })
        ) { backStackEntry ->
            val repoId = backStackEntry.arguments?.getString("repoId") ?: ""
            RepoDetailScreen(
                repoId = repoId,
                onNavigateBack = { navController.popBackStack() },
                onEditRepo = { navController.navigate("edit_repo/$repoId") }
            )
        }

        composable("ssh_keys") {
            SshKeysScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable("settings") {
            SettingsScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}
