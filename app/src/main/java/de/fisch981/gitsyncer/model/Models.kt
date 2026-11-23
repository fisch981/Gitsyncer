package de.fisch981.gitsyncer.model

import java.util.UUID

enum class AuthType {
    HTTPS_TOKEN,
    HTTPS_CREDENTIALS,
    SSH_KEY
}

enum class SyncDirection {
    TWO_WAY,
    PUSH_ONLY,
    PULL_ONLY
}

data class GitRepositoryConfig(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val remoteUrl: String,
    val localPath: String,
    val branch: String = "main",
    val authType: AuthType = AuthType.HTTPS_TOKEN,
    val tokenOrPassword: String = "",
    val username: String = "",
    val sshKeyId: String? = null,
    val autoSyncEnabled: Boolean = true,
    val isEnabled: Boolean = true,
    val syncIntervalMinutes: Int = 60,
    val syncDirection: SyncDirection = SyncDirection.TWO_WAY,
    val autoCommitMessage: String = "Sync %timestamp%",
    val authorName: String = "GitSyncer",
    val authorEmail: String = "gitsyncer@fisch981.de",
    val shallowClone: Boolean = true,
    val releaseApksOnly: Boolean = false,
    val lastReleaseTag: String? = null,
    val lastReleaseAssetMeta: String? = null,
    val lastSyncStatus: String = "IDLE", // IDLE, SUCCESS, FAILED, RUNNING, CONFLICT
    val lastSyncTimestamp: Long = 0L,
    val lastErrorMessage: String? = null
)

data class SshKeyEntry(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val publicKey: String,
    val privateKey: String,
    val keyType: String = "RSA",
    val createdTimestamp: Long = System.currentTimeMillis()
)

data class CommitInfo(
    val id: String,
    val shortId: String,
    val author: String,
    val email: String,
    val message: String,
    val timestamp: Long
)

enum class ChangeType {
    MODIFIED,
    ADDED,
    DELETED,
    UNTRACKED,
    CONFLICT
}

data class FileChange(
    val path: String,
    val changeType: ChangeType
)

data class SyncResult(
    val success: Boolean,
    val message: String,
    val commitsPulled: Int = 0,
    val commitsPushed: Int = 0,
    val conflicts: List<String> = emptyList(),
    val downloadedApkPath: String? = null,
    val releaseTag: String? = null
)
