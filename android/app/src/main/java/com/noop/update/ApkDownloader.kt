package com.noop.update

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.FileProvider
import com.noop.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Downloads the release APK directly from noop.fans and triggers the system package-installer —
 * no browser, no Play Store, no manual save.
 *
 * Flow:
 * 1. Caller passes the [apkUrl] obtained from [UpdateCheck.Result.Available.apkUrl].
 * 2. The APK is streamed into `cacheDir/downloads/Noop-<version>.apk` with a progress
 *    notification that counts down the megabytes.
 * 3. On completion the notification becomes a tap-to-install action; the install Intent is also
 *    launched immediately so the system installer appears without a tap (some launchers require
 *    the tap — the notification covers that case).
 * 4. Any error (offline, 4xx, disk full) resolves to [Result.Failed] with a human-readable
 *    message; the progress notification is cleared.
 *
 * Permissions required (declared in AndroidManifest):
 *   - INTERNET (already present)
 *   - REQUEST_INSTALL_PACKAGES (added by this feature)
 * The APK is shared via [FileProvider] (`${applicationId}.fileprovider`, `downloads/` path).
 */
object ApkDownloader {

    sealed interface Result {
        data class Success(val apkFile: File) : Result
        data class Failed(val message: String) : Result
    }

    private const val CHANNEL_ID = "noop_apk_download"
    private const val NOTIF_PROGRESS = 8100
    private const val NOTIF_DONE = 8101

    suspend fun download(
        context: Context,
        version: String,
        apkUrl: String,
    ): Result = withContext(Dispatchers.IO) {
        val ctx = context.applicationContext
        ensureChannel(ctx)

        val dir = File(ctx.cacheDir, "downloads").also { it.mkdirs() }
        val dest = File(dir, "Noop-$version.apk")

        // Show indeterminate progress immediately.
        postProgress(ctx, version, -1, -1)

        runCatching {
            val conn = (URL(apkUrl).openConnection() as HttpURLConnection).apply {
                connectTimeout = 15_000
                readTimeout = 60_000
            }
            try {
                if (conn.responseCode != 200) {
                    clearProgress(ctx)
                    return@runCatching Result.Failed("Server returned ${conn.responseCode}.")
                }
                val total = conn.contentLengthLong   // -1 when unknown
                var downloaded = 0L
                val buf = ByteArray(64 * 1024)
                conn.inputStream.use { input ->
                    dest.outputStream().use { output ->
                        while (true) {
                            val n = input.read(buf)
                            if (n < 0) break
                            output.write(buf, 0, n)
                            downloaded += n
                            postProgress(ctx, version, downloaded, total)
                        }
                    }
                }
            } finally {
                conn.disconnect()
            }

            clearProgress(ctx)
            postInstallNotification(ctx, version, dest)
            launchInstall(ctx, dest)
            Result.Success(dest)
        }.getOrElse { e ->
            dest.delete()
            clearProgress(ctx)
            Result.Failed(e.message ?: "Download failed.")
        }
    }

    private fun postProgress(ctx: Context, version: String, downloaded: Long, total: Long) {
        val progress = if (total > 0) ((downloaded * 100) / total).toInt() else 0
        val indeterminate = total <= 0
        val mb = if (downloaded > 0) " (${downloaded / (1024 * 1024)} MB)" else ""
        val notification = NotificationCompat.Builder(ctx, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Downloading NOOP $version")
            .setContentText(if (indeterminate) "Starting…" else "$progress%$mb")
            .setProgress(100, progress, indeterminate)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()
        runCatching { NotificationManagerCompat.from(ctx).notify(NOTIF_PROGRESS, notification) }
    }

    private fun clearProgress(ctx: Context) {
        NotificationManagerCompat.from(ctx).cancel(NOTIF_PROGRESS)
    }

    private fun postInstallNotification(ctx: Context, version: String, apk: File) {
        val uri = apkUri(ctx, apk)
        val installIntent = PendingInstallIntent(ctx, uri)
        val notification = NotificationCompat.Builder(ctx, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("NOOP $version downloaded")
            .setContentText("Tap to install.")
            .setContentIntent(installIntent)
            .setAutoCancel(true)
            .build()
        runCatching { NotificationManagerCompat.from(ctx).notify(NOTIF_DONE, notification) }
    }

    private fun launchInstall(ctx: Context, apk: File) {
        val uri = apkUri(ctx, apk)
        ctx.startActivity(
            Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK
            },
        )
    }

    private fun apkUri(ctx: Context, apk: File): Uri =
        FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", apk)

    private fun PendingInstallIntent(ctx: Context, uri: Uri): android.app.PendingIntent {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK
        }
        return android.app.PendingIntent.getActivity(
            ctx, 0, intent,
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun ensureChannel(ctx: Context) {
        val mgr = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (mgr.getNotificationChannel(CHANNEL_ID) != null) return
        mgr.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "App download", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Progress while downloading a NOOP update."
            },
        )
    }
}
