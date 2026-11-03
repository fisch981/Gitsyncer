package de.fisch981.gitsyncer

import android.app.Application
import de.fisch981.gitsyncer.data.RepositoryStore
import de.fisch981.gitsyncer.sync.SyncScheduler
import org.eclipse.jgit.storage.file.WindowCacheConfig

class GitSyncerApp : Application() {

    override fun onCreate() {
        super.onCreate()

        // Deaktiviere HTTP Keep-Alive auf Android: Verhindert "Software caused connection abort: recv failed"
        // durch veraltete Verbindungs-Pools im mobilen Netz (5G/LTE Carrier NATs)
        System.setProperty("http.keepAlive", "false")

        // JGit Optimierung für Android:
        // isPackedGitUseStrongRefs = true verhindert GC-Finalisierung von Inflaters während des Entpackens ("Inflater has been closed")
        try {
            val windowCacheConfig = WindowCacheConfig().apply {
                isPackedGitMMAP = false
                isPackedGitUseStrongRefs = true // Verhindert "Inflater has been closed"
                isPackedIndexGitUseStrongRefs = true
                packedGitWindowSize = 8192
                packedGitLimit = 128 * 1024 * 1024L // 128 MB
                deltaBaseCacheLimit = 32 * 1024 * 1024 // 32 MB
                streamFileThreshold = 100 * 1024 * 1024 // 100 MB: verhindert tmpFile.renameTo() Fehler auf Android FUSE
            }
            windowCacheConfig.install()
        } catch (e: Throwable) {
            e.printStackTrace()
        }

        val repoStore = RepositoryStore.getInstance(this)
        // Ensure background sync is scheduled for all active auto-sync enabled repositories
        for (repo in repoStore.repositories.value) {
            if (repo.isEnabled && repo.autoSyncEnabled) {
                SyncScheduler.scheduleRepo(this, repo)
            }
        }
    }
}
