package de.fisch981.gitsyncer.data

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.BufferedReader
import java.io.File
import java.io.FileOutputStream
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

data class GitHubReleaseAsset(
    val id: Long,
    val name: String,
    val size: Long,
    val url: String,
    val browser_download_url: String? = null,
    val updated_at: String? = null,
    val content_type: String? = null
)

data class GitHubRelease(
    val id: Long,
    val tag_name: String,
    val name: String? = null,
    val body: String? = null,
    val draft: Boolean = false,
    val prerelease: Boolean = false,
    val published_at: String? = null,
    val created_at: String? = null,
    val assets: List<GitHubReleaseAsset> = emptyList()
)

class GitHubApiClient {
    private val gson = Gson()

    companion object {
        fun parseOwnerAndRepo(remoteUrl: String): Pair<String, String>? {
            val trimmed = remoteUrl.trim().trimEnd('/')
            if (trimmed.isBlank()) return null

            val pathPart = when {
                trimmed.startsWith("git@github.com:", ignoreCase = true) ->
                    trimmed.substringAfter("git@github.com:")
                trimmed.contains("github.com/", ignoreCase = true) ->
                    trimmed.substringAfter("github.com/", "")
                trimmed.contains("github.com:", ignoreCase = true) ->
                    trimmed.substringAfter("github.com:", "")
                else -> return null
            }

            val clean = pathPart
                .trim('/')
                .removeSuffix(".git")
                .trimEnd('/')
            val segments = clean.split("/").filter { it.isNotBlank() }
            if (segments.size < 2) return null
            return segments[0] to segments[1]
        }
    }

    private fun openApiGetConnection(urlStr: String, token: String, timeoutMs: Int = 15000): HttpURLConnection {
        return (URL(urlStr).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            if (token.isNotBlank()) {
                setRequestProperty("Authorization", "Bearer ${token.trim()}")
            }
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("User-Agent", "GitSyncer-Android")
            connectTimeout = timeoutMs
            readTimeout = timeoutMs
        }
    }

    private fun readStreamText(conn: HttpURLConnection, isError: Boolean): String {
        val stream = (if (isError) conn.errorStream else conn.inputStream) ?: return ""
        return BufferedReader(InputStreamReader(stream)).use { it.readText() }
    }

    private fun formatApiError(code: Int, errBody: String): String {
        return when (code) {
            401, 403 -> "Zugriff verweigert (HTTP $code): Bitte GitHub-Token mit 'repo'-Berechtigung prüfen."
            404 -> "Repository oder Release nicht gefunden (HTTP 404). Bei privaten Repos Token prüfen."
            else -> "GitHub API Fehler ($code): ${errBody.take(200)}"
        }
    }

    suspend fun getUser(token: String): Result<GitHubUser> = withContext(Dispatchers.IO) {
        try {
            val conn = openApiGetConnection("https://api.github.com/user", token, 10000)
            try {
                if (conn.responseCode in 200..299) {
                    val response = readStreamText(conn, false)
                    val user = gson.fromJson(response, GitHubUser::class.java)
                    Result.success(user)
                } else {
                    val errMsg = readStreamText(conn, true).ifBlank { "HTTP ${conn.responseCode}" }
                    Result.failure(Exception("GitHub API Fehler (${conn.responseCode}): $errMsg"))
                }
            } finally {
                conn.disconnect()
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun listUserRepos(token: String): Result<List<GitHubRepo>> = withContext(Dispatchers.IO) {
        try {
            val conn = openApiGetConnection(
                "https://api.github.com/user/repos?per_page=100&sort=updated&affiliation=owner,collaborator",
                token,
                15000
            )
            try {
                if (conn.responseCode in 200..299) {
                    val response = readStreamText(conn, false)
                    val type = object : TypeToken<List<GitHubRepo>>() {}.type
                    val repos: List<GitHubRepo> = gson.fromJson(response, type) ?: emptyList()
                    Result.success(repos)
                } else {
                    val errMsg = readStreamText(conn, true).ifBlank { "HTTP ${conn.responseCode}" }
                    Result.failure(Exception("GitHub API Fehler (${conn.responseCode}): $errMsg"))
                }
            } finally {
                conn.disconnect()
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun listReleasesSync(
        owner: String,
        repo: String,
        token: String,
        limit: Int = 15
    ): Result<List<GitHubRelease>> {
        return try {
            val conn = openApiGetConnection(
                "https://api.github.com/repos/$owner/$repo/releases?per_page=$limit",
                token,
                15000
            )
            try {
                val code = conn.responseCode
                if (code in 200..299) {
                    val response = readStreamText(conn, false)
                    val type = object : TypeToken<List<GitHubRelease>>() {}.type
                    val all: List<GitHubRelease> = gson.fromJson(response, type) ?: emptyList()
                    Result.success(all.filter { !it.draft })
                } else {
                    val errBody = readStreamText(conn, true)
                    Result.failure(Exception(formatApiError(code, errBody)))
                }
            } finally {
                conn.disconnect()
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun listReleases(
        owner: String,
        repo: String,
        token: String,
        limit: Int = 15
    ): Result<List<GitHubRelease>> = withContext(Dispatchers.IO) {
        listReleasesSync(owner, repo, token, limit)
    }

    fun getLatestReleaseSync(
        owner: String,
        repo: String,
        token: String
    ): Result<GitHubRelease> {
        return try {
            val conn = openApiGetConnection(
                "https://api.github.com/repos/$owner/$repo/releases/latest",
                token,
                15000
            )
            val code: Int
            val body: String
            val errBody: String
            try {
                code = conn.responseCode
                if (code in 200..299) {
                    body = readStreamText(conn, false)
                    val release = gson.fromJson(body, GitHubRelease::class.java)
                    return Result.success(release)
                }
                errBody = readStreamText(conn, true)
            } finally {
                conn.disconnect()
            }

            if (code == 404) {
                val fallback = listReleasesSync(owner, repo, token, 10)
                return fallback.fold(
                    onSuccess = { list ->
                        if (list.isEmpty()) {
                            Result.failure(Exception("Kein veröffentlichtes GitHub Release für $owner/$repo gefunden."))
                        } else {
                            val withApk = list.firstOrNull { rel ->
                                rel.assets.any { it.name.endsWith(".apk", ignoreCase = true) }
                            }
                            Result.success(withApk ?: list.first())
                        }
                    },
                    onFailure = { Result.failure(it) }
                )
            }
            Result.failure(Exception(formatApiError(code, errBody)))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getLatestRelease(
        owner: String,
        repo: String,
        token: String
    ): Result<GitHubRelease> = withContext(Dispatchers.IO) {
        getLatestReleaseSync(owner, repo, token)
    }

    fun downloadReleaseAssetSync(
        asset: GitHubReleaseAsset,
        token: String,
        destFile: File,
        isCancelled: () -> Boolean = { false },
        progressListener: ((String, Int) -> Unit)? = null
    ): Result<File> {
        var apiConn: HttpURLConnection? = null
        var downloadConn: HttpURLConnection? = null
        val parentDir = destFile.parentFile ?: destFile.absoluteFile.parentFile
        val tmpFile = File(parentDir, "._${destFile.name}.tmp")

        return try {
            if (isCancelled()) {
                return Result.failure(Exception("Download durch Benutzer abgebrochen"))
            }

            apiConn = (URL(asset.url).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                instanceFollowRedirects = false
                if (token.isNotBlank()) {
                    setRequestProperty("Authorization", "Bearer ${token.trim()}")
                }
                setRequestProperty("Accept", "application/octet-stream")
                setRequestProperty("User-Agent", "GitSyncer-Android")
                connectTimeout = 20000
                readTimeout = 60000
            }

            val firstCode = apiConn.responseCode
            val streamConn: HttpURLConnection = if (firstCode in listOf(301, 302, 303, 307, 308)) {
                val location = apiConn.getHeaderField("Location")
                apiConn.disconnect()
                apiConn = null
                if (location.isNullOrBlank()) {
                    return Result.failure(Exception("Weiterleitung ohne Ziel-URL (HTTP $firstCode)"))
                }
                // Pre-signed S3/Azure URL must NOT receive the GitHub Authorization header
                downloadConn = (URL(location).openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    instanceFollowRedirects = true
                    setRequestProperty("User-Agent", "GitSyncer-Android")
                    connectTimeout = 20000
                    readTimeout = 60000
                }
                downloadConn
            } else {
                apiConn
            }

            val finalCode = streamConn.responseCode
            if (finalCode !in 200..299) {
                val errBody = readStreamText(streamConn, true)
                return Result.failure(
                    Exception(
                        if (finalCode == 401 || finalCode == 403 || finalCode == 404) {
                            formatApiError(finalCode, errBody)
                        } else {
                            "Download fehlgeschlagen (HTTP $finalCode): ${errBody.take(150)}"
                        }
                    )
                )
            }

            parentDir?.mkdirs()
            if (tmpFile.exists()) tmpFile.delete()

            val expectedTotal = if (asset.size > 0) asset.size else streamConn.contentLengthLong
            var downloadedBytes = 0L
            var lastReportedPct = -1
            var lastReportTime = 0L

            BufferedInputStream(streamConn.inputStream, 64 * 1024).use { input ->
                BufferedOutputStream(FileOutputStream(tmpFile), 64 * 1024).use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        if (isCancelled()) {
                            return Result.failure(Exception("Download durch Benutzer abgebrochen"))
                        }
                        val read = input.read(buffer)
                        if (read == -1) break
                        output.write(buffer, 0, read)
                        downloadedBytes += read

                        if (progressListener != null && expectedTotal > 0) {
                            val pct = ((downloadedBytes * 100L) / expectedTotal).toInt().coerceIn(0, 100)
                            val now = System.currentTimeMillis()
                            if (pct != lastReportedPct && (pct - lastReportedPct >= 2 || now - lastReportTime >= 250 || pct == 100)) {
                                lastReportedPct = pct
                                lastReportTime = now
                                progressListener.invoke("Lade ${asset.name}: $pct%", pct)
                            }
                        }
                    }
                    output.flush()
                }
            }

            if (isCancelled()) {
                tmpFile.delete()
                return Result.failure(Exception("Download durch Benutzer abgebrochen"))
            }

            if (downloadedBytes <= 0L || (asset.size > 0L && downloadedBytes != asset.size)) {
                tmpFile.delete()
                return Result.failure(
                    Exception("Unvollständiger Download von ${asset.name} ($downloadedBytes von ${asset.size} Bytes)")
                )
            }

            destFile.parentFile?.mkdirs()
            if (destFile.exists()) {
                destFile.delete()
            }
            if (!tmpFile.renameTo(destFile)) {
                tmpFile.copyTo(destFile, overwrite = true)
                tmpFile.delete()
            }

            Result.success(destFile)
        } catch (e: Exception) {
            if (tmpFile.exists()) tmpFile.delete()
            Result.failure(e)
        } finally {
            if (tmpFile.exists()) tmpFile.delete()
            try { apiConn?.disconnect() } catch (_: Throwable) {}
            try { downloadConn?.disconnect() } catch (_: Throwable) {}
        }
    }

    suspend fun downloadReleaseAsset(
        asset: GitHubReleaseAsset,
        token: String,
        destFile: File,
        isCancelled: () -> Boolean = { false },
        progressListener: ((String, Int) -> Unit)? = null
    ): Result<File> = withContext(Dispatchers.IO) {
        downloadReleaseAssetSync(asset, token, destFile, isCancelled, progressListener)
    }
}
