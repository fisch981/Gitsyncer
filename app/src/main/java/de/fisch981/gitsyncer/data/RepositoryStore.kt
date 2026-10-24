package de.fisch981.gitsyncer.data

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import de.fisch981.gitsyncer.model.GitRepositoryConfig
import de.fisch981.gitsyncer.model.SshKeyEntry
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class RepositoryStore(private val context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("gitsyncer_prefs", Context.MODE_PRIVATE)
    private val gson = Gson()

    private val _repositories = MutableStateFlow<List<GitRepositoryConfig>>(emptyList())
    val repositories: StateFlow<List<GitRepositoryConfig>> = _repositories.asStateFlow()

    private val _sshKeys = MutableStateFlow<List<SshKeyEntry>>(emptyList())
    val sshKeys: StateFlow<List<SshKeyEntry>> = _sshKeys.asStateFlow()

    init {
        loadRepositories()
        loadSshKeys()
    }

    @Synchronized
    private fun loadRepositories() {
        val json = prefs.getString(KEY_REPOSITORIES, null)
        val list: List<GitRepositoryConfig> = if (!json.isNullOrBlank()) {
            try {
                val type = object : TypeToken<List<GitRepositoryConfig>>() {}.type
                gson.fromJson(json, type) ?: emptyList()
            } catch (e: Exception) {
                emptyList()
            }
        } else {
            emptyList()
        }
        _repositories.value = list
    }

    @Synchronized
    private fun loadSshKeys() {
        val json = prefs.getString(KEY_SSH_KEYS, null)
        val list: List<SshKeyEntry> = if (!json.isNullOrBlank()) {
            try {
                val type = object : TypeToken<List<SshKeyEntry>>() {}.type
                gson.fromJson(json, type) ?: emptyList()
            } catch (e: Exception) {
                emptyList()
            }
        } else {
            emptyList()
        }
        _sshKeys.value = list
    }

    @Synchronized
    fun saveRepository(repo: GitRepositoryConfig) {
        val current = _repositories.value.toMutableList()
        val index = current.indexOfFirst { it.id == repo.id }
        if (index >= 0) {
            current[index] = repo
        } else {
            current.add(repo)
        }
        _repositories.value = current
        prefs.edit().putString(KEY_REPOSITORIES, gson.toJson(current)).apply()
    }

    @Synchronized
    fun updateRepoSyncStatus(
        repoId: String,
        status: String,
        errorMessage: String? = null,
        timestamp: Long = System.currentTimeMillis()
    ) {
        val current = _repositories.value.toMutableList()
        val index = current.indexOfFirst { it.id == repoId }
        if (index >= 0) {
            val updated = current[index].copy(
                lastSyncStatus = status,
                lastSyncTimestamp = timestamp,
                lastErrorMessage = errorMessage
            )
            current[index] = updated
            _repositories.value = current
            prefs.edit().putString(KEY_REPOSITORIES, gson.toJson(current)).apply()
        }
    }

    @Synchronized
    fun deleteRepository(repoId: String) {
        val current = _repositories.value.toMutableList()
        current.removeAll { it.id == repoId }
        _repositories.value = current
        prefs.edit().putString(KEY_REPOSITORIES, gson.toJson(current)).apply()
    }

    fun getRepository(id: String): GitRepositoryConfig? {
        return _repositories.value.firstOrNull { it.id == id }
    }

    @Synchronized
    fun saveSshKey(key: SshKeyEntry) {
        val current = _sshKeys.value.toMutableList()
        val index = current.indexOfFirst { it.id == key.id }
        if (index >= 0) {
            current[index] = key
        } else {
            current.add(key)
        }
        _sshKeys.value = current
        prefs.edit().putString(KEY_SSH_KEYS, gson.toJson(current)).apply()
    }

    @Synchronized
    fun deleteSshKey(keyId: String) {
        val current = _sshKeys.value.toMutableList()
        current.removeAll { it.id == keyId }
        _sshKeys.value = current
        prefs.edit().putString(KEY_SSH_KEYS, gson.toJson(current)).apply()
    }

    fun getSshKey(id: String?): SshKeyEntry? {
        if (id == null) return null
        return _sshKeys.value.firstOrNull { it.id == id }
    }

    companion object {
        private const val KEY_REPOSITORIES = "repositories"
        private const val KEY_SSH_KEYS = "ssh_keys"

        @Volatile
        private var INSTANCE: RepositoryStore? = null

        fun getInstance(context: Context): RepositoryStore {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: RepositoryStore(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
}
