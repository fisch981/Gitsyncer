package de.fisch981.gitsyncer

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import de.fisch981.gitsyncer.ui.screens.*
import de.fisch981.gitsyncer.ui.theme.GitSyncerTheme

class MainActivity : ComponentActivity() {

    private val requestNotificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { _ ->
            // Notification permission result handled
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

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
            GitSyncerTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    GitSyncerNavigation()
                }
            }
        }
    }
}

@Composable
fun GitSyncerNavigation() {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = "dashboard"
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
