package de.fisch981.gitsyncer.sync

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.FileProvider
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import de.fisch981.gitsyncer.MainActivity
import de.fisch981.gitsyncer.data.RepositoryStore
import de.fisch981.gitsyncer.git.GitManager
import de.fisch981.gitsyncer.model.GitRepositoryConfig
import de.fisch981.gitsyncer.model.SyncResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class GitSyncWorker(
    private val context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    private val repoStore = RepositoryStore.getInstance(context)
    private val gitManager = GitManager(context)
    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    init {
        createNotificationChannels()
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val targetRepoId = inputData.getString("repo_id")
        val isManual = inputData.getBoolean("manual_trigger", targetRepoId == null)
        val reposToSync = if (targetRepoId != null) {
            val r = repoStore.getRepository(targetRepoId)
            if (r != null && r.isEnabled) listOf(r) else emptyList()
        } else {
            // Bei manuellem Start (z.B. Schnellzugriffsleiste/One-Shot): ALLE aktiven (isEnabled) Repos syncen
            if (isManual) {
                repoStore.repositories.value.filter { it.isEnabled }.sortedBy { it.name.lowercase() }
            } else {
                repoStore.repositories.value.filter { it.isEnabled && it.autoSyncEnabled }.sortedBy { it.name.lowercase() }
            }
        }

        if (reposToSync.isEmpty()) {
            return@withContext Result.success()
        }

        var anyFailed = false

        for (repo in reposToSync) {
            if (GitManager.isSyncCancelled(repo.id)) continue
            repoStore.updateRepoSyncStatus(repo.id, "RUNNING")
            showProgressNotification(repo.name, "Synchronisiere...")

            val result = gitManager.sync(repo) { message, _ ->
                showProgressNotification(repo.name, message)
            }

            if (GitManager.isSyncCancelled(repo.id) || result.message.contains("abgebrochen", ignoreCase = true)) {
                repoStore.updateRepoSyncStatus(repo.id, "CANCELLED", "Synchronisation abgebrochen")
                continue
            }

            if (result.success) {
                repoStore.updateRepoSyncStatus(repo.id, "SUCCESS")
                showCompletionNotification(repo, result)
            } else {
                anyFailed = true
                val status = if (result.conflicts.isNotEmpty()) "CONFLICT" else "FAILED"
                repoStore.updateRepoSyncStatus(repo.id, status, result.message)
                showCompletionNotification(repo, result)
            }
        }

        // Laufende Fortschritts-Benachrichtigung nach Abschluss entfernen
        notificationManager.cancel(NOTIFICATION_ID_PROGRESS)

        if (anyFailed) Result.retry() else Result.success()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Kanal für Fortschrittsanzeige während des Syncs
            val progressChannel = NotificationChannel(
                CHANNEL_ID_PROGRESS,
                "GitSync Hintergrund-Fortschritt",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Zeigt laufende Synchronisationsvorgänge an"
            }
            notificationManager.createNotificationChannel(progressChannel)

            // Kanal für abgeschlossene Aktionen und Fehlermeldungen
            val completedChannel = NotificationChannel(
                CHANNEL_ID_COMPLETED,
                "GitSync Benachrichtigungen",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Benachrichtigungen über fertige Downloads, Sync-Ergebnisse und Fehler"
            }
            notificationManager.createNotificationChannel(completedChannel)
        }
    }

    private fun showProgressNotification(repoName: String, message: String) {
        val notification = NotificationCompat.Builder(context, CHANNEL_ID_PROGRESS)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentTitle("GitSync: $repoName")
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setAutoCancel(false)
            .build()

        notificationManager.notify(NOTIFICATION_ID_PROGRESS, notification)
    }

    private fun showCompletionNotification(repo: GitRepositoryConfig, result: SyncResult) {
        val notifId = repoNotificationId(repo.id)
        val isError = !result.success
        val apkFile = if (repo.releaseApksOnly) findLatestApk(repo, result.downloadedApkPath) else null

        val pendingIntent = if (isError) {
            // 3. Fehler: Klick öffnet die Einstellungen des Repos in GitSyncer
            val intent = Intent(context, MainActivity::class.java).apply {
                action = "EDIT_REPO"
                putExtra("route", "edit_repo/${repo.id}")
                putExtra("repo_id", repo.id)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
            PendingIntent.getActivity(
                context,
                notifId + 1,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        } else if (repo.releaseApksOnly) {
            // 1. Erfolgreicher APK-Download: Klick startet direkt die Installation
            if (apkFile != null && apkFile.exists()) {
                val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", apkFile)
                val installIntent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, "application/vnd.android.package-archive")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                PendingIntent.getActivity(
                    context,
                    notifId + 2,
                    installIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            } else {
                createFolderPendingIntent(repo, notifId + 2)
            }
        } else {
            // 2. Erfolgreicher Komplett-Sync: Klick öffnet den Zielordner
            createFolderPendingIntent(repo, notifId + 3)
        }

        val icon = when {
            isError -> android.R.drawable.stat_notify_error
            repo.releaseApksOnly -> android.R.drawable.stat_sys_download_done
            else -> android.R.drawable.stat_notify_sync
        }

        val apkVersionName = try {
            apkFile?.let { context.packageManager.getPackageArchiveInfo(it.absolutePath, 0)?.versionName }
        } catch (_: Exception) { null }

        val versionTag = result.releaseTag
            ?: repo.lastReleaseTag
            ?: apkVersionName?.let { if (it.startsWith("v", ignoreCase = true)) it else "v$it" }

        val title = when {
            isError -> "${repo.name}: Synchronisation fehlgeschlagen"
            repo.releaseApksOnly -> {
                if (!versionTag.isNullOrBlank()) {
                    "${repo.name} ($versionTag): APK heruntergeladen"
                } else {
                    "${repo.name}: APK heruntergeladen"
                }
            }
            else -> "${repo.name}: Synchronisiert"
        }

        val text = when {
            isError -> "${result.message} • Tippen für Einstellungen"
            repo.releaseApksOnly -> {
                val apkName = apkFile?.name
                val base = if (result.message.isNotBlank()) {
                    result.message
                } else {
                    if (!versionTag.isNullOrBlank()) "Release $versionTag heruntergeladen" else "APK heruntergeladen"
                }
                if (apkName != null && !base.contains(apkName)) {
                    "$base ($apkName) • Tippen zum Installieren"
                } else {
                    "$base • Tippen zum Installieren"
                }
            }
            else -> "${result.message} • Tippen zum Öffnen des Ordners"
        }

        val builder = NotificationCompat.Builder(context, CHANNEL_ID_COMPLETED)
            .setSmallIcon(icon)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(if (isError) NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)

        if (!isError && repo.releaseApksOnly && pendingIntent != null) {
            val installLabel = if (!versionTag.isNullOrBlank()) "Installieren ($versionTag)" else "Installieren"
            builder.addAction(android.R.drawable.stat_sys_download_done, installLabel, pendingIntent)
        } else if (!isError && !repo.releaseApksOnly && pendingIntent != null) {
            builder.addAction(android.R.drawable.ic_menu_agenda, "Ordner öffnen", pendingIntent)
        } else if (isError && pendingIntent != null) {
            builder.addAction(android.R.drawable.ic_menu_preferences, "Einstellungen", pendingIntent)
        }

        notificationManager.notify(notifId, builder.build())
    }

    private fun createFolderPendingIntent(repo: GitRepositoryConfig, requestCode: Int): PendingIntent {
        val folder = File(repo.localPath)
        if (!folder.exists()) {
            folder.mkdirs()
        }

        val primaryPrefix = "/storage/emulated/0/"
        val isPrimary = folder.absolutePath.startsWith(primaryPrefix)

        var targetIntent: Intent? = null

        if (isPrimary) {
            val relPath = Uri.encode("primary:" + folder.absolutePath.removePrefix(primaryPrefix))
            val documentsUri = Uri.parse("content://com.android.externalstorage.documents/document/$relPath")
            val viewIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(documentsUri, "vnd.android.document/directory")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
                putExtra("org.openintents.extra.ABSOLUTE_PATH", folder.absolutePath)
            }
            if (viewIntent.resolveActivity(context.packageManager) != null) {
                targetIntent = viewIntent
            }
        }

        if (targetIntent == null) {
            targetIntent = Intent(context, MainActivity::class.java).apply {
                action = "OPEN_FOLDER"
                putExtra("route", "open_folder")
                putExtra("folder_path", folder.absolutePath)
                putExtra("repo_id", repo.id)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
        }

        return PendingIntent.getActivity(
            context,
            requestCode,
            targetIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun findLatestApk(repo: GitRepositoryConfig, downloadedPath: String?): File? {
        if (!downloadedPath.isNullOrBlank()) {
            val f = File(downloadedPath)
            if (f.exists() && f.isFile) return f
        }
        val dir = File(repo.localPath)
        if (!dir.exists() || !dir.isDirectory) return null
        return dir.listFiles()
            ?.filter { it.isFile && it.extension.equals("apk", ignoreCase = true) }
            ?.maxByOrNull { it.lastModified() }
    }

    private fun repoNotificationId(repoId: String): Int {
        return (repoId.hashCode() and 0x3FFFFFFF) + 10000
    }

    companion object {
        const val CHANNEL_ID_PROGRESS = "gitsyncer_progress_channel"
        const val CHANNEL_ID_COMPLETED = "gitsyncer_sync_channel"
        const val NOTIFICATION_ID_PROGRESS = 9811
    }
}
