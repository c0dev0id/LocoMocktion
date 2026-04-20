package de.codevoid.locomocktion.updater

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class ReleaseInfo(
    val tagName: String,
    val name: String,
    val body: String,
    val apkDownloadUrl: String?,
    val htmlUrl: String,
)

private const val LATEST_RELEASE_URL =
    "https://api.github.com/repos/c0dev0id/locomocktion/releases/latest"

suspend fun fetchLatestRelease(): ReleaseInfo = withContext(Dispatchers.IO) {
    val connection = (URL(LATEST_RELEASE_URL).openConnection() as HttpURLConnection).apply {
        requestMethod = "GET"
        connectTimeout = 15_000
        readTimeout = 15_000
        setRequestProperty("Accept", "application/vnd.github+json")
        setRequestProperty("User-Agent", "LocoMocktion-App")
    }
    try {
        val code = connection.responseCode
        if (code !in 200..299) {
            throw IllegalStateException("GitHub API returned HTTP $code")
        }
        val payload = connection.inputStream.bufferedReader().use { it.readText() }
        val json = JSONObject(payload)
        val assets = json.optJSONArray("assets")
        var apkUrl: String? = null
        if (assets != null) {
            for (i in 0 until assets.length()) {
                val asset = assets.getJSONObject(i)
                val name = asset.optString("name")
                if (name.endsWith(".apk", ignoreCase = true)) {
                    apkUrl = asset.optString("browser_download_url").takeIf { it.isNotEmpty() }
                    break
                }
            }
        }
        ReleaseInfo(
            tagName = json.optString("tag_name"),
            name = json.optString("name").ifEmpty { json.optString("tag_name") },
            body = json.optString("body"),
            apkDownloadUrl = apkUrl,
            htmlUrl = json.optString("html_url"),
        )
    } finally {
        connection.disconnect()
    }
}

fun isNewerVersion(latestTag: String, currentTag: String): Boolean = latestTag != currentTag
