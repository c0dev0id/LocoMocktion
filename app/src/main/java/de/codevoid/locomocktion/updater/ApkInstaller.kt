package de.codevoid.locomocktion.updater

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

suspend fun downloadApk(
    context: Context,
    url: String,
    onProgress: (Float) -> Unit,
): File = withContext(Dispatchers.IO) {
    val target = File(context.cacheDir, "update.apk")
    if (target.exists()) target.delete()

    val connection = (URL(url).openConnection() as HttpURLConnection).apply {
        requestMethod = "GET"
        connectTimeout = 15_000
        readTimeout = 30_000
        instanceFollowRedirects = true
        setRequestProperty("User-Agent", "LocoMocktion-App")
    }
    try {
        val code = connection.responseCode
        if (code !in 200..299) {
            throw IllegalStateException("Download failed: HTTP $code")
        }
        val total = connection.contentLengthLong
        connection.inputStream.use { input ->
            target.outputStream().use { output ->
                val buffer = ByteArray(16 * 1024)
                var read: Int
                var downloaded = 0L
                var lastReported = -1
                while (input.read(buffer).also { read = it } != -1) {
                    output.write(buffer, 0, read)
                    downloaded += read
                    if (total > 0) {
                        val progress = downloaded.toFloat() / total.toFloat()
                        val bucket = (progress * 100).toInt()
                        if (bucket != lastReported) {
                            lastReported = bucket
                            onProgress(progress.coerceIn(0f, 1f))
                        }
                    }
                }
                output.flush()
            }
        }
        onProgress(1f)
    } finally {
        connection.disconnect()
    }
    target
}

fun launchInstaller(context: Context, file: File) {
    val authority = "${context.packageName}.fileprovider"
    val uri = FileProvider.getUriForFile(context, authority, file)
    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, "application/vnd.android.package-archive")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    context.startActivity(intent)
}
