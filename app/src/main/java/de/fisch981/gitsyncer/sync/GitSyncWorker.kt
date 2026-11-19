package de.fisch981.gitsyncer.sync

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import de.fisch981.gitsyncer.data.RepositoryStore
import de.fisch981.gitsyncer.git.GitManager
import de.fisch981.gitsyncer.model.GitRepositoryConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class GitSyncWorker(
    private val context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    private val repoStore = RepositoryStore.getInstance(context)
    private val gitManager = GitManager(context)
    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    init {
        createNotificationChannel()
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
            showNotification(repo.name, "Synchronizing...")

            val result = gitManager.sync(repo) { message, _ ->
                showNotification(repo.name, message)
            }

            if (GitManager.isSyncCancelled(repo.id) || result.message.contains("abgebrochen", ignoreCase = true)) {
                repoStore.updateRepoSyncStatus(repo.id, "CANCELLED", "Synchronisation abgebrochen")
                continue
            }

            if (result.success) {
                repoStore.updateRepoSyncStatus(repo.id, "SUCCESS")
                showNotification(repo.name, result.message)
            } else {
                anyFailed = true
                val status = if (result.conflicts.isNotEmpty()) "CONFLICT" else "FAILED"
                repoStore.updateRepoSyncStatus(repo.id, status, result.message)
                showNotification(repo.name, "Sync problem: ${result.message}", isError = true)
            }
        }

        if (anyFailed) Result.retry() else Result.success()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "GitSync Background Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows progress and alerts for Git repository synchronization"
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun showNotification(title: String, text: String, isError: Boolean = false) {
        val icon = if (isError) android.R.drawable.stat_notify_error else android.R.drawable.stat_notify_sync
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(icon)
            .setContentTitle("GitSync: $title")
            .setContentText(text)
            .setPriority(if (isError) NotificationCompat.PRIORITY_DEFAULT else NotificationCompat.PRIORITY_LOW)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    companion object {
        const val CHANNEL_ID = "gitsyncer_sync_channel"
        const val NOTIFICATION_ID = 9811
    }
}
