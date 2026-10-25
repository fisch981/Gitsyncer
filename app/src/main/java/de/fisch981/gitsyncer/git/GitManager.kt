package de.fisch981.gitsyncer.git

import android.content.Context
import com.jcraft.jsch.JSch
import com.jcraft.jsch.Session
import de.fisch981.gitsyncer.data.RepositoryStore
import de.fisch981.gitsyncer.model.*
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.api.ListBranchCommand
import org.eclipse.jgit.api.MergeResult
import org.eclipse.jgit.api.ResetCommand
import org.eclipse.jgit.api.TransportConfigCallback
import org.eclipse.jgit.api.errors.GitAPIException
import org.eclipse.jgit.diff.DiffFormatter
import org.eclipse.jgit.lib.*
import org.eclipse.jgit.storage.file.FileRepositoryBuilder
import org.eclipse.jgit.storage.file.WindowCacheConfig
import org.eclipse.jgit.transport.*
import org.eclipse.jgit.util.FS
import java.io.ByteArrayOutputStream
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ConcurrentHashMap

class GitManager(private val context: Context) {

    private val repoStore = RepositoryStore.getInstance(context)

    companion object {
        private val repoLocks = ConcurrentHashMap<String, Any>()

        fun cleanupStaleLocks(localPath: String) {
            try {
                val gitDir = File(localPath, ".git")
                if (!gitDir.exists()) return
                File(gitDir, "index.lock").takeIf { it.exists() }?.delete()
                File(gitDir, "HEAD.lock").takeIf { it.exists() }?.delete()
                File(gitDir, "config.lock").takeIf { it.exists() }?.delete()
                val refsDir = File(gitDir, "refs")
                if (refsDir.exists()) {
                    refsDir.walkTopDown().filter { it.name.endsWith(".lock") }.forEach { it.delete() }
                }
            } catch (e: Throwable) {
                e.printStackTrace()
            }
        }

        fun resetJGitInflaterCache() {
            try {
                val windowCacheConfig = WindowCacheConfig().apply {
                    isPackedGitMMAP = false
                    isPackedGitUseStrongRefs = true
                    isPackedIndexGitUseStrongRefs = true
                    packedGitWindowSize = 8192
                    packedGitLimit = 64 * 1024 * 1024L
                    deltaBaseCacheLimit = 16 * 1024 * 1024
                    streamFileThreshold = 5 * 1024 * 1024
                }
                windowCacheConfig.install()
            } catch (e: Throwable) {
                e.printStackTrace()
            }
        }
    }

    fun isGitRepo(localPath: String): Boolean {
        val dir = File(localPath)
        val gitDir = File(dir, ".git")
        if (!gitDir.exists() || !gitDir.isDirectory) return false
        val headFile = File(gitDir, "HEAD")
        val configFile = File(gitDir, "config")
        return headFile.exists() && headFile.length() > 0 && configFile.exists()
    }

    private fun getCredentialsProvider(config: GitRepositoryConfig): CredentialsProvider? {
        return when (config.authType) {
            AuthType.HTTPS_TOKEN -> {
                val token = config.tokenOrPassword.trim()
                if (token.isNotEmpty()) {
                    val user = if (config.username.isNotBlank()) config.username.trim() else "token"
                    UsernamePasswordCredentialsProvider(user, token)
                } else null
            }
            AuthType.HTTPS_CREDENTIALS -> {
                UsernamePasswordCredentialsProvider(config.username.trim(), config.tokenOrPassword)
            }
            AuthType.SSH_KEY -> null
        }
    }

    private fun getTransportConfigCallback(config: GitRepositoryConfig): TransportConfigCallback {
        return TransportConfigCallback { transport ->
            if (transport is SshTransport && config.authType == AuthType.SSH_KEY) {
                val sshKey = repoStore.getSshKey(config.sshKeyId)
                val sessionFactory = object : org.eclipse.jgit.transport.ssh.jsch.JschConfigSessionFactory() {
                    override fun configure(hc: org.eclipse.jgit.transport.ssh.jsch.OpenSshConfig.Host?, session: Session?) {
                        session?.setConfig("StrictHostKeyChecking", "no")
                    }

                    override fun createDefaultJSch(fs: FS?): JSch {
                        val jsch = super.createDefaultJSch(fs)
                        if (sshKey != null && sshKey.privateKey.isNotBlank()) {
                            try {
                                val privKeyBytes = sshKey.privateKey.toByteArray(Charsets.UTF_8)
                                val pubKeyBytes = sshKey.publicKey.toByteArray(Charsets.UTF_8)
                                jsch.addIdentity("gitsyncer_key", privKeyBytes, pubKeyBytes, null)
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }
                        return jsch
                    }
                }
                transport.sshSessionFactory = sessionFactory
            }
        }
    }

    fun cloneRepository(
        config: GitRepositoryConfig,
        progressListener: ((String, Int) -> Unit)? = null
    ): SyncResult {
        val lock = repoLocks.computeIfAbsent(config.id) { Any() }
        synchronized(lock) {
            val targetDir = File(config.localPath)
            if (!targetDir.exists()) {
                targetDir.mkdirs()
            }

            cleanupStaleLocks(config.localPath)

            val monitor = object : ProgressMonitor {
                private var totalTasks = 0
                private var currentWork = 0
                private var totalWork = 0

                override fun start(totalTasks: Int) {
                    this.totalTasks = totalTasks
                }

                override fun beginTask(title: String?, totalWork: Int) {
                    this.totalWork = totalWork
                    this.currentWork = 0
                    progressListener?.invoke(title ?: "Cloning...", 0)
                }

                override fun update(completed: Int) {
                    currentWork += completed
                    val pct = if (totalWork > 0) (currentWork * 100) / totalWork else 0
                    progressListener?.invoke("Downloading: $pct%", pct)
                }

                override fun endTask() {}
                override fun isCancelled(): Boolean = false
                override fun showDuration(enabled: Boolean) {}
            }

            return try {
                val targetBranch = config.branch.ifBlank { "main" }
                val cloneCommand = Git.cloneRepository()
                    .setURI(config.remoteUrl)
                    .setDirectory(targetDir)
                    .setBranch(targetBranch)
                    .setTimeout(300)
                    .setProgressMonitor(monitor)

                if (config.shallowClone) {
                    cloneCommand.setDepth(1)
                }

                getCredentialsProvider(config)?.let { cloneCommand.setCredentialsProvider(it) }
                cloneCommand.setTransportConfigCallback(getTransportConfigCallback(config))

                val git = cloneCommand.call()
                git.close()
                SyncResult(success = true, message = "Clone completed successfully")
            } catch (e: OutOfMemoryError) {
                SyncResult(success = false, message = "Speichermangel (OOM): Repository enthält große Dateien/Verlauf. Bitte Shallow-Clone aktivieren.")
            } catch (e: Throwable) {
                SyncResult(success = false, message = "Clone failed: ${e.localizedMessage ?: e.javaClass.simpleName}")
            }
        }
    }

    fun recloneRepository(
        config: GitRepositoryConfig,
        progressListener: ((String, Int) -> Unit)? = null
    ): SyncResult {
        val lock = repoLocks.computeIfAbsent(config.id) { Any() }
        synchronized(lock) {
            val targetDir = File(config.localPath)
            val gitDir = File(targetDir, ".git")
            if (gitDir.exists()) {
                gitDir.deleteRecursively()
            }
            return cloneRepository(config, progressListener)
        }
    }

    fun sync(
        config: GitRepositoryConfig,
        progressListener: ((String, Int) -> Unit)? = null
    ): SyncResult {
        val lock = repoLocks.computeIfAbsent(config.id) { Any() }
        synchronized(lock) {
            cleanupStaleLocks(config.localPath)
            var result = performSync(config, progressListener)

            // Auto-recovery if "Inflater has been closed" occurs
            if (!result.success && result.message.contains("Inflater has been closed", ignoreCase = true)) {
                progressListener?.invoke("Zlib Inflater-Bereinigung & Selbstreparatur...", 30)
                resetJGitInflaterCache()
                cleanupStaleLocks(config.localPath)
                result = performSync(config, progressListener)
                if (result.success) {
                    return SyncResult(
                        success = true,
                        message = "Erfolgreich synchronisiert (nach automatischer Cache-Bereinigung)",
                        commitsPulled = result.commitsPulled,
                        commitsPushed = result.commitsPushed
                    )
                }
            }

            return result
        }
    }

    private fun performSync(
        config: GitRepositoryConfig,
        progressListener: ((String, Int) -> Unit)? = null
    ): SyncResult {
        val targetDir = File(config.localPath)
        if (!isGitRepo(config.localPath)) {
            // Need initial clone
            progressListener?.invoke("Initial clone starting...", 10)
            val cloneRes = cloneRepository(config, progressListener)
            if (!cloneRes.success) return cloneRes
        }

        var repository: Repository? = null
        var git: Git? = null

        return try {
            val repoBuilder = FileRepositoryBuilder()
            repository = repoBuilder.setGitDir(File(targetDir, ".git"))
                .readEnvironment()
                .findGitDir()
                .build()

            git = Git(repository)
            val creds = getCredentialsProvider(config)
            val transportCallback = getTransportConfigCallback(config)
            val targetBranch = config.branch.ifBlank { "main" }

            // Clean up any interrupted merge or rebase state
            val state = repository.repositoryState
            if (state == RepositoryState.MERGING || state == RepositoryState.MERGING_RESOLVED || state.isRebasing) {
                try {
                    git.reset().setMode(ResetCommand.ResetType.HARD).call()
                } catch (e: Throwable) {
                    e.printStackTrace()
                }
            }

            // Ensure local branch matches targetBranch
            try {
                val currentBranch = repository.branch
                if (currentBranch != targetBranch) {
                    val branchRef = repository.findRef("refs/heads/$targetBranch")
                    if (branchRef != null) {
                        git.checkout().setName(targetBranch).call()
                    } else if (repository.findRef("refs/remotes/origin/$targetBranch") != null) {
                        git.checkout()
                            .setCreateBranch(true)
                            .setName(targetBranch)
                            .setUpstreamMode(org.eclipse.jgit.api.CreateBranchCommand.SetupUpstreamMode.TRACK)
                            .setStartPoint("origin/$targetBranch")
                            .call()
                    } else if (currentBranch == "master" && targetBranch != "master") {
                        git.branchRename().setOldName("master").setNewName(targetBranch).call()
                    }
                }
            } catch (e: Throwable) {
                e.printStackTrace()
            }

            // Ensure tracking configuration is properly set in git config
            try {
                val gitConfig = repository.config
                val currentRemote = gitConfig.getString("branch", targetBranch, "remote")
                if (currentRemote.isNullOrBlank()) {
                    gitConfig.setString("branch", targetBranch, "remote", "origin")
                    gitConfig.setString("branch", targetBranch, "merge", "refs/heads/$targetBranch")
                    gitConfig.save()
                }
            } catch (e: Throwable) {
                e.printStackTrace()
            }

            progressListener?.invoke("Checking local status...", 20)
            val status = git.status().call()

            var commitsPushed = 0
            var commitsPulled = 0

            // 1. If not PULL_ONLY, stage local changes and commit
            if (config.syncDirection != SyncDirection.PULL_ONLY) {
                val hasChanges = status.hasUncommittedChanges() || status.untracked.isNotEmpty()
                if (hasChanges) {
                    progressListener?.invoke("Staging local changes...", 35)
                    git.add().addFilepattern(".").call()
                    // Stage deletions
                    if (status.missing.isNotEmpty() || status.removed.isNotEmpty()) {
                        val rm = git.rm()
                        status.missing.forEach { rm.addFilepattern(it) }
                        status.removed.forEach { rm.addFilepattern(it) }
                        rm.call()
                    }

                    val dateStr = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
                    val msg = config.autoCommitMessage.replace("%timestamp%", dateStr)
                    val author = PersonIdent(config.authorName, config.authorEmail)

                    progressListener?.invoke("Committing local changes...", 50)
                    git.commit()
                        .setMessage(msg)
                        .setAuthor(author)
                        .setCommitter(author)
                        .call()
                }
            }

            // 2. Fetch and Pull from remote
            if (config.syncDirection != SyncDirection.PUSH_ONLY) {
                progressListener?.invoke("Pulling from remote...", 65)
                val pullCmd = git.pull()
                    .setRemote("origin")
                    .setRemoteBranchName(targetBranch)
                    .setTimeout(300)
                creds?.let { pullCmd.setCredentialsProvider(it) }
                pullCmd.setTransportConfigCallback(transportCallback)

                val pullResult = pullCmd.call()
                if (!pullResult.isSuccessful) {
                    val mergeResult = pullResult.mergeResult
                    if (mergeResult != null && mergeResult.mergeStatus == MergeResult.MergeStatus.CONFLICTING) {
                        val conflicts = mergeResult.conflicts?.keys?.toList() ?: emptyList()
                        return SyncResult(
                            success = false,
                            message = "Merge conflicts encountered in ${conflicts.size} file(s)",
                            conflicts = conflicts
                        )
                    }
                }
                commitsPulled = 1
            }

            // 3. Push to remote
            if (config.syncDirection != SyncDirection.PULL_ONLY) {
                progressListener?.invoke("Pushing commits to remote...", 85)
                val pushCmd = git.push()
                    .setRemote("origin")
                    .setRefSpecs(RefSpec("refs/heads/$targetBranch:refs/heads/$targetBranch"))
                    .setTimeout(300)
                creds?.let { pushCmd.setCredentialsProvider(it) }
                pushCmd.setTransportConfigCallback(transportCallback)
                val pushResults = pushCmd.call()
                for (pushRes in pushResults) {
                    for (update in pushRes.remoteUpdates) {
                        if (update.status == RemoteRefUpdate.Status.OK || update.status == RemoteRefUpdate.Status.UP_TO_DATE) {
                            commitsPushed++
                        } else if (update.status == RemoteRefUpdate.Status.REJECTED_NONFASTFORWARD) {
                            return SyncResult(
                                success = false,
                                message = "Push rejected: Remote has newer changes. Please pull first."
                            )
                        }
                    }
                }
            }

            progressListener?.invoke("Sync complete!", 100)
            SyncResult(
                success = true,
                message = "Synchronized successfully",
                commitsPulled = commitsPulled,
                commitsPushed = commitsPushed
            )
        } catch (e: org.eclipse.jgit.api.errors.RefNotAdvertisedException) {
            // Self-repair: If remote rejected due to wrong ref (like master), perform clean reclone
            progressListener?.invoke("Bereinige lokale Branch-Referenz und klone neu...", 10)
            val recloneRes = recloneRepository(config, progressListener)
            if (recloneRes.success) {
                SyncResult(success = true, message = "Automatische Reparatur & Synchronisation erfolgreich")
            } else {
                SyncResult(success = false, message = "Branch-Fehler: ${e.localizedMessage}")
            }
        } catch (e: OutOfMemoryError) {
            SyncResult(success = false, message = "Speichermangel (OOM): Repository enthält zu große Dateien.")
        } catch (e: Throwable) {
            val msg = e.localizedMessage ?: e.javaClass.simpleName
            SyncResult(success = false, message = "Sync error: $msg")
        } finally {
            try {
                git?.close()
                repository?.close()
            } catch (e: Throwable) {
                e.printStackTrace()
            }
        }
    }

    fun getStatus(config: GitRepositoryConfig): List<FileChange> {
        val lock = repoLocks.computeIfAbsent(config.id) { Any() }
        synchronized(lock) {
            val targetDir = File(config.localPath)
            if (!isGitRepo(config.localPath)) return emptyList()
            cleanupStaleLocks(config.localPath)

            var repository: Repository? = null
            var git: Git? = null

            return try {
                repository = FileRepositoryBuilder()
                    .setGitDir(File(targetDir, ".git"))
                    .build()
                git = Git(repository)
                val status = git.status().call()
                val changes = mutableListOf<FileChange>()

                status.conflicting.forEach { changes.add(FileChange(it, ChangeType.CONFLICT)) }
                status.modified.forEach { changes.add(FileChange(it, ChangeType.MODIFIED)) }
                status.added.forEach { changes.add(FileChange(it, ChangeType.ADDED)) }
                status.untracked.forEach { changes.add(FileChange(it, ChangeType.UNTRACKED)) }
                status.removed.forEach { changes.add(FileChange(it, ChangeType.DELETED)) }
                status.missing.forEach { changes.add(FileChange(it, ChangeType.DELETED)) }

                changes
            } catch (e: Throwable) {
                emptyList()
            } finally {
                try {
                    git?.close()
                    repository?.close()
                } catch (e: Throwable) {
                    e.printStackTrace()
                }
            }
        }
    }

    fun getLog(config: GitRepositoryConfig, maxCount: Int = 30): List<CommitInfo> {
        val lock = repoLocks.computeIfAbsent(config.id) { Any() }
        synchronized(lock) {
            val targetDir = File(config.localPath)
            if (!isGitRepo(config.localPath)) return emptyList()
            cleanupStaleLocks(config.localPath)

            var repository: Repository? = null
            var git: Git? = null

            return try {
                repository = FileRepositoryBuilder()
                    .setGitDir(File(targetDir, ".git"))
                    .build()
                git = Git(repository)
                val commits = git.log().setMaxCount(maxCount).call()
                val list = mutableListOf<CommitInfo>()
                for (commit in commits) {
                    list.add(
                        CommitInfo(
                            id = commit.name,
                            shortId = commit.name.take(7),
                            author = commit.authorIdent.name ?: "Unknown",
                            email = commit.authorIdent.emailAddress ?: "",
                            message = commit.shortMessage ?: "",
                            timestamp = commit.commitTime * 1000L
                        )
                    )
                }
                list
            } catch (e: Throwable) {
                emptyList()
            } finally {
                try {
                    git?.close()
                    repository?.close()
                } catch (e: Throwable) {
                    e.printStackTrace()
                }
            }
        }
    }

    fun getDiff(config: GitRepositoryConfig, filePath: String): String {
        val lock = repoLocks.computeIfAbsent(config.id) { Any() }
        synchronized(lock) {
            val targetDir = File(config.localPath)
            if (!isGitRepo(config.localPath)) return "Not a git repository"
            cleanupStaleLocks(config.localPath)

            var repository: Repository? = null
            var git: Git? = null

            return try {
                repository = FileRepositoryBuilder()
                    .setGitDir(File(targetDir, ".git"))
                    .build()
                git = Git(repository)
                val out = ByteArrayOutputStream()
                val formatter = DiffFormatter(out)
                formatter.setRepository(repository)

                val diffs = git.diff()
                    .setPathFilter(org.eclipse.jgit.treewalk.filter.PathFilter.create(filePath))
                    .call()

                for (entry in diffs) {
                    formatter.format(entry)
                }
                formatter.flush()
                out.toString("UTF-8").ifBlank { "No changes in $filePath" }
            } catch (e: Throwable) {
                "Error loading diff: ${e.localizedMessage}"
            } finally {
                try {
                    git?.close()
                    repository?.close()
                } catch (e: Throwable) {
                    e.printStackTrace()
                }
            }
        }
    }

    fun resolveConflict(config: GitRepositoryConfig, filePath: String, useLocal: Boolean): Boolean {
        val lock = repoLocks.computeIfAbsent(config.id) { Any() }
        synchronized(lock) {
            val targetDir = File(config.localPath)
            if (!isGitRepo(config.localPath)) return false
            cleanupStaleLocks(config.localPath)

            var repository: Repository? = null
            var git: Git? = null

            return try {
                repository = FileRepositoryBuilder()
                    .setGitDir(File(targetDir, ".git"))
                    .build()
                git = Git(repository)

                // Checkout ours or theirs
                git.checkout()
                    .setStage(if (useLocal) org.eclipse.jgit.api.CheckoutCommand.Stage.OURS else org.eclipse.jgit.api.CheckoutCommand.Stage.THEIRS)
                    .addPath(filePath)
                    .call()

                // Stage resolution
                git.add().addFilepattern(filePath).call()
                true
            } catch (e: Throwable) {
                e.printStackTrace()
                false
            } finally {
                try {
                    git?.close()
                    repository?.close()
                } catch (e: Throwable) {
                    e.printStackTrace()
                }
            }
        }
    }

    fun discardChanges(config: GitRepositoryConfig, filePath: String): Boolean {
        val lock = repoLocks.computeIfAbsent(config.id) { Any() }
        synchronized(lock) {
            val targetDir = File(config.localPath)
            if (!isGitRepo(config.localPath)) return false
            cleanupStaleLocks(config.localPath)

            var repository: Repository? = null
            var git: Git? = null

            return try {
                repository = FileRepositoryBuilder()
                    .setGitDir(File(targetDir, ".git"))
                    .build()
                git = Git(repository)

                val file = File(targetDir, filePath)
                if (file.exists()) {
                    git.checkout().addPath(filePath).call()
                }
                true
            } catch (e: Throwable) {
                false
            } finally {
                try {
                    git?.close()
                    repository?.close()
                } catch (e: Throwable) {
                    e.printStackTrace()
                }
            }
        }
    }
}
