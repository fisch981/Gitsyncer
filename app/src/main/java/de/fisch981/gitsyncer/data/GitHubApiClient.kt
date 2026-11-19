package de.fisch981.gitsyncer.data

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

data class GitHubUser(
    val login: String,
    val name: String?,
    val avatar_url: String?
)

data class GitHubRepo(
    val id: Long,
    val name: String,
    val full_name: String,
    val private: Boolean,
    val clone_url: String,
    val ssh_url: String,
    val default_branch: String = "main",
    val description: String?,
    val updated_at: String?
)

class GitHubApiClient {
    private val gson = Gson()

    suspend fun getUser(token: String): Result<GitHubUser> = withContext(Dispatchers.IO) {
        try {
            val url = URL("https://api.github.com/user")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                setRequestProperty("Authorization", "Bearer ${token.trim()}")
                setRequestProperty("Accept", "application/vnd.github.v3+json")
                setRequestProperty("User-Agent", "GitSyncer-Android")
                connectTimeout = 10000
                readTimeout = 10000
            }

            if (conn.responseCode in 200..299) {
                val reader = BufferedReader(InputStreamReader(conn.inputStream))
                val response = reader.readText()
                reader.close()
                val user = gson.fromJson(response, GitHubUser::class.java)
                Result.success(user)
            } else {
                val errReader = conn.errorStream?.let { BufferedReader(InputStreamReader(it)) }
                val errMsg = errReader?.readText() ?: "HTTP ${conn.responseCode}"
                Result.failure(Exception("GitHub API Fehler (${conn.responseCode}): $errMsg"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun listUserRepos(token: String): Result<List<GitHubRepo>> = withContext(Dispatchers.IO) {
        try {
            val url = URL("https://api.github.com/user/repos?per_page=100&sort=updated&affiliation=owner,collaborator")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                setRequestProperty("Authorization", "Bearer ${token.trim()}")
                setRequestProperty("Accept", "application/vnd.github.v3+json")
                setRequestProperty("User-Agent", "GitSyncer-Android")
                connectTimeout = 15000
                readTimeout = 15000
            }

            if (conn.responseCode in 200..299) {
                val reader = BufferedReader(InputStreamReader(conn.inputStream))
                val response = reader.readText()
                reader.close()
                val type = object : TypeToken<List<GitHubRepo>>() {}.type
                val repos: List<GitHubRepo> = gson.fromJson(response, type) ?: emptyList()
                Result.success(repos)
            } else {
                val errReader = conn.errorStream?.let { BufferedReader(InputStreamReader(it)) }
                val errMsg = errReader?.readText() ?: "HTTP ${conn.responseCode}"
                Result.failure(Exception("GitHub API Fehler (${conn.responseCode}): $errMsg"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
