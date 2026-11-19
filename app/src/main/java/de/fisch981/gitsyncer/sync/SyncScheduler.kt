package de.fisch981.gitsyncer.sync

import android.content.Context
import androidx.work.*
import de.fisch981.gitsyncer.model.GitRepositoryConfig
import java.util.concurrent.TimeUnit

object SyncScheduler {

    private const val TAG_PREFIX = "sync_repo_"

    fun scheduleRepo(context: Context, repo: GitRepositoryConfig) {
        val workManager = WorkManager.getInstance(context)
        val tag = "$TAG_PREFIX${repo.id}"

        if (!repo.isEnabled || !repo.autoSyncEnabled || repo.syncIntervalMinutes <= 0) {
            workManager.cancelAllWorkByTag(tag)
            return
        }

        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val intervalMinutes = repo.syncIntervalMinutes.coerceAtLeast(15).toLong()

        val data = workDataOf("repo_id" to repo.id)

        val syncRequest = PeriodicWorkRequestBuilder<GitSyncWorker>(
            intervalMinutes, TimeUnit.MINUTES,
            5, TimeUnit.MINUTES
        )
            .setConstraints(constraints)
            .setInputData(data)
            .addTag(tag)
            .build()

        workManager.enqueueUniquePeriodicWork(
            tag,
            ExistingPeriodicWorkPolicy.UPDATE,
            syncRequest
        )
    }

    fun triggerOneShotSync(context: Context, repoId: String? = null) {
        val workManager = WorkManager.getInstance(context)
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val data = if (repoId != null) {
            workDataOf("repo_id" to repoId, "manual_trigger" to true)
        } else {
            workDataOf("manual_trigger" to true)
        }

        val oneShot = OneTimeWorkRequestBuilder<GitSyncWorker>()
            .setConstraints(constraints)
            .setInputData(data)
            .build()

        workManager.enqueue(oneShot)
    }

    fun cancelRepo(context: Context, repoId: String) {
        WorkManager.getInstance(context).cancelAllWorkByTag("$TAG_PREFIX$repoId")
    }
}
