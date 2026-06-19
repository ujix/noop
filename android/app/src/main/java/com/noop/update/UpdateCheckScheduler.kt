package com.noop.update

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.noop.BuildConfig
import com.noop.R
import com.noop.ui.MainActivity
import com.noop.ui.UpdateItem
import com.noop.ui.UpdateKind
import com.noop.ui.UpdateStore
import java.util.concurrent.TimeUnit

/**
 * Periodic background update check — every 12 hours.
 *
 * On each run it calls [UpdateCheck.check]. When a newer version is available it:
 * 1. Posts an [UpdateKind.UPDATE_AVAILABLE] item to the [UpdateStore] inbox (bell badge).
 * 2. Fires an OS notification on the "noop_update" channel so the user sees it even with the
 *    app closed.
 *
 * Idempotent: the inbox post is guarded by [UpdateStore.hasUpdateItem] so repeated checks for the
 * same version don't stack duplicates. The OS notification is also deduplicated by a fixed
 * notification id. The worker is enqueued as UNIQUE_KEEP so app restarts don't reset the 12 h
 * clock.
 *
 * Privacy: the check reads only the public release endpoint (version tag + release notes). Nothing
 * about the device, user, or install is sent — the same guarantee as the user-initiated check.
 */
object UpdateCheckScheduler {

    private const val WORK_NAME = "noop_update_check_periodic"
    private const val INTERVAL_HOURS = 12L

    /** Enqueue the 12 h periodic check. KEEP policy: the first call anchors the period; subsequent
     *  app launches leave the existing schedule untouched so the check doesn't always fire at
     *  startup. Safe to call every launch. */
    fun schedule(context: Context) {
        val request = PeriodicWorkRequestBuilder<UpdateCheckWorker>(INTERVAL_HOURS, TimeUnit.HOURS)
            .build()
        WorkManager.getInstance(context.applicationContext)
            .enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    /** Cancel the periodic check (e.g. when a future preference to disable it is added). */
    fun cancel(context: Context) {
        WorkManager.getInstance(context.applicationContext).cancelUniqueWork(WORK_NAME)
    }
}

class UpdateCheckWorker(
    private val ctx: Context,
    params: WorkerParameters,
) : CoroutineWorker(ctx, params) {

    override suspend fun doWork(): Result {
        val result = UpdateCheck.check(BuildConfig.VERSION_NAME)
        if (result is UpdateCheck.Result.Available) {
            postToInbox(result)
            postNotification(result)
        }
        return Result.success()
    }

    private fun postToInbox(avail: UpdateCheck.Result.Available) {
        val store = UpdateStore.from(ctx)
        // Only post once per version — don't stack entries if the check fires multiple times before
        // the user updates.
        if (store.items.any { it.kind == UpdateKind.UPDATE_AVAILABLE && it.title.contains(avail.version) }) return
        store.post(
            UpdateItem(
                kind = UpdateKind.UPDATE_AVAILABLE,
                title = "NOOP ${avail.version} is available",
                message = avail.notes.ifBlank { "A new version is ready to download." },
                deepLink = "settings",
            ),
        )
    }

    private fun postNotification(avail: UpdateCheck.Result.Available) {
        ensureChannel()
        val tapIntent = PendingIntent.getActivity(
            ctx,
            0,
            Intent(ctx, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra("deepLink", "settings")
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(ctx, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("NOOP ${avail.version} is available")
            .setContentText(avail.notes.lines().firstOrNull { it.isNotBlank() } ?: "Tap to see what's new.")
            .setStyle(NotificationCompat.BigTextStyle().bigText(avail.notes.take(400)))
            .setContentIntent(tapIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        runCatching {
            NotificationManagerCompat.from(ctx).notify(NOTIFICATION_ID, notification)
        }
    }

    private fun ensureChannel() {
        val mgr = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (mgr.getNotificationChannel(CHANNEL_ID) != null) return
        mgr.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "App updates", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Notifies when a new NOOP version is available."
            },
        )
    }

    companion object {
        const val CHANNEL_ID = "noop_update"
        private const val NOTIFICATION_ID = 8001
    }
}
