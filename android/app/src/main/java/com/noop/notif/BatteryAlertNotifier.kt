package com.noop.notif

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.noop.R
import com.noop.ui.appLaunchIntent

/**
 * Posts battery-state alerts — low battery (<15%) and charge-complete (100%) — as real system
 * notifications. Mirrors [IllnessAlertNotifier]'s pattern: called from WhoopConnectionService
 * on every live-state update; crossing-edge guards prevent repeat fires.
 */
object BatteryAlertNotifier {
    private const val CHANNEL_ID = "noop_battery_alert"
    private const val NOTIF_ID_LOW = 4203
    private const val NOTIF_ID_FULL = 4204

    private const val LOW_BATTERY_THRESHOLD = 15

    /**
     * Evaluate the current vs previous battery state and fire at most one alert per crossing:
     * - LOW: `batteryPct` first drops below [LOW_BATTERY_THRESHOLD] in a session.
     * - FULL: `batteryPct` reaches 100 (charging flag not required — WHOOP firmware clears it the
     *   moment the cell tops out, so `charging == true` would miss the crossing).
     *
     * [prevPct]/[currPct] are the strap battery percentages rounded to Int (null = unknown).
     * [charging] reflects the charging state (not used for the 100% gate; kept for context).
     */
    @SuppressLint("MissingPermission")
    fun onBatteryUpdate(
        context: Context,
        prevPct: Int?,
        currPct: Int?,
        charging: Boolean?,
    ) {
        if (currPct == null) return
        runCatching {
            if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return
            ensureChannel(context)
            val openApp = PendingIntent.getActivity(
                context, 3,
                appLaunchIntent(context),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            // Low battery: crossed below threshold (was above or unknown, now below).
            val wasAbove = prevPct == null || prevPct >= LOW_BATTERY_THRESHOLD
            if (wasAbove && currPct < LOW_BATTERY_THRESHOLD) {
                val n = NotificationCompat.Builder(context, CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_stat_heart)
                    .setContentTitle("Low Battery")
                    .setContentText("Recharge your whoop before going to bed today")
                    .setContentIntent(openApp)
                    .setAutoCancel(true)
                    .setCategory(NotificationCompat.CATEGORY_STATUS)
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .build()
                NotificationManagerCompat.from(context).notify(NOTIF_ID_LOW, n)
            }
            // Charge complete: first reading at 100% (regardless of charging flag — firmware clears
            // it at full charge, so the strap typically shows charging=false when it tops out).
            val wasBelow100 = prevPct == null || prevPct < 100
            if (currPct == 100 && wasBelow100) {
                val n = NotificationCompat.Builder(context, CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_stat_heart)
                    .setContentTitle("Strap fully charged")
                    .setContentText("Your WHOOP is at 100%")
                    .setContentIntent(openApp)
                    .setAutoCancel(true)
                    .setCategory(NotificationCompat.CATEGORY_STATUS)
                    .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                    .build()
                NotificationManagerCompat.from(context).notify(NOTIF_ID_FULL, n)
            }
        }
    }

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        runCatching {
            val mgr = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (mgr.getNotificationChannel(CHANNEL_ID) != null) return
            mgr.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID, "Battery alerts",
                    NotificationManager.IMPORTANCE_HIGH,
                ).apply {
                    description = "Alerts when the strap battery is low or fully charged."
                },
            )
        }
    }
}
