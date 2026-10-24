package de.fisch981.gitsyncer

import android.app.Application
import de.fisch981.gitsyncer.data.RepositoryStore
import de.fisch981.gitsyncer.sync.SyncScheduler
import org.eclipse.jgit.storage.file.WindowCacheConfig

class GitSyncerApp : Application() {

    override fun onCreate() {
        super.onCreate()

        // JGit Optimierung für Android (verhindert OOM und Map failed bei großen Repos wie FolderGallery)
        try {
            val windowCacheConfig = WindowCacheConfig().apply {
                isPackedGitMMAP = false
                packedGitWindowSize = 8 * 1024
                packedGitLimit = 32 * 1024 * 1024L
                deltaBaseCacheLimit = 8 * 1024 * 1024
                streamFileThreshold = 4 * 1024 * 1024 // Streaming ab 4 MB für große Binärdateien/APKs
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
