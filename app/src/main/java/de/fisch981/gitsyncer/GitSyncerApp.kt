package de.fisch981.gitsyncer

import android.app.Application
import de.fisch981.gitsyncer.data.RepositoryStore
import de.fisch981.gitsyncer.sync.SyncScheduler

class GitSyncerApp : Application() {

    override fun onCreate() {
        super.onCreate()
        val repoStore = RepositoryStore.getInstance(this)
        // Ensure background sync is scheduled for all auto-sync enabled repositories
        for (repo in repoStore.repositories.value) {
            if (repo.autoSyncEnabled) {
                SyncScheduler.scheduleRepo(this, repo)
            }
        }
    }
}
