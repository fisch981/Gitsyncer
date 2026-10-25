package de.fisch981.gitsyncer

import android.app.Application
import de.fisch981.gitsyncer.data.RepositoryStore
import de.fisch981.gitsyncer.sync.SyncScheduler
import org.eclipse.jgit.storage.file.WindowCacheConfig

class GitSyncerApp : Application() {

    override fun onCreate() {
        super.onCreate()

        // JGit Optimierung für Android:
        // isPackedGitUseStrongRefs = true verhindert GC-Finalisierung von Inflaters während des Entpackens ("Inflater has been closed")
        try {
            val windowCacheConfig = WindowCacheConfig().apply {
                isPackedGitMMAP = false
                isPackedGitUseStrongRefs = true // Verhindert "Inflater has been closed"
                isPackedIndexGitUseStrongRefs = true
                packedGitWindowSize = 8192
                packedGitLimit = 64 * 1024 * 1024L // 64 MB
                deltaBaseCacheLimit = 16 * 1024 * 1024 // 16 MB
                streamFileThreshold = 5 * 1024 * 1024 // 5 MB
            }
            windowCacheConfig.install()
        } catch (e: Throwable) {
            e.printStackTrace()
        }

        val repoStore = RepositoryStore.getInstance(this)
        // Ensure background sync is scheduled for all auto-sync enabled repositories
        for (repo in repoStore.repositories.value) {
            if (repo.autoSyncEnabled) {
                SyncScheduler.scheduleRepo(this, repo)
            }
        }
    }
}
