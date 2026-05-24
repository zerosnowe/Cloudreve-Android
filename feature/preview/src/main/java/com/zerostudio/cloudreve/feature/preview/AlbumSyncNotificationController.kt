package com.zerostudio.cloudreve.feature.preview

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.IconCompat

class AlbumSyncNotificationController(
    context: Context,
) {
    private val appContext = context.applicationContext
    private val notificationManager = NotificationManagerCompat.from(appContext)

    fun showSyncing(scannedFolders: Int, photoCount: Int) {
        if (!canPostNotifications()) return
        ensureChannel()

        val text = appContext.getString(
            R.string.album_sync_notification_progress,
            scannedFolders,
            photoCount,
        )
        notify(
            buildBaseNotification(
                title = appContext.getString(R.string.album_sync_notification_title),
                text = text,
                ongoing = true,
            )
                .setProgress(0, 0, true)
                .setStyle(
                    NotificationCompat.ProgressStyle()
                        .setProgressIndeterminate(true)
                        .setStyledByProgress(false)
                        .setProgressTrackerIcon(IconCompat.createWithResource(appContext, SMALL_ICON_RES)),
                )
                .setRequestPromotedOngoing(true)
                .setShortCriticalText(scannedFolders.coerceAtLeast(0).toString())
                .build(),
        )
    }

    fun showCompleted(photoCount: Int) {
        if (!canPostNotifications()) return
        ensureChannel()

        val text = appContext.resources.getQuantityString(
            R.plurals.album_sync_notification_completed,
            photoCount,
            photoCount,
        )
        notify(
            buildBaseNotification(
                title = appContext.getString(R.string.album_sync_completed),
                text = text,
                ongoing = false,
            )
                .setProgress(100, 100, false)
                .setStyle(
                    NotificationCompat.ProgressStyle()
                        .setProgress(100)
                        .setProgressIndeterminate(false)
                        .setStyledByProgress(false)
                        .setProgressTrackerIcon(IconCompat.createWithResource(appContext, SMALL_ICON_RES)),
                )
                .setAutoCancel(true)
                .setTimeoutAfter(COMPLETE_TIMEOUT_MILLIS)
                .build(),
        )
    }

    fun showFailed(message: String?) {
        if (!canPostNotifications()) return
        ensureChannel()

        notify(
            buildBaseNotification(
                title = appContext.getString(R.string.album_sync_notification_failed),
                text = message?.takeIf { it.isNotBlank() }
                    ?: appContext.getString(R.string.album_sync_notification_failed_message),
                ongoing = false,
            )
                .setAutoCancel(true)
                .setTimeoutAfter(COMPLETE_TIMEOUT_MILLIS)
                .build(),
        )
    }

    private fun buildBaseNotification(
        title: String,
        text: String,
        ongoing: Boolean,
    ): NotificationCompat.Builder =
        NotificationCompat.Builder(appContext, CHANNEL_ID)
            .setSmallIcon(SMALL_ICON_RES)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(openAppPendingIntent())
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOnlyAlertOnce(true)
            .setOngoing(ongoing)
            .setLocalOnly(false)

    private fun openAppPendingIntent(): PendingIntent? {
        val intent = appContext.packageManager
            .getLaunchIntentForPackage(appContext.packageName)
            ?.addFlags(android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP or android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP)
            ?: return null
        return PendingIntent.getActivity(
            appContext,
            OPEN_APP_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = appContext.getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return

        val channel = NotificationChannel(
            CHANNEL_ID,
            appContext.getString(R.string.album_sync_notification_channel),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = appContext.getString(R.string.album_sync_notification_channel_description)
            setShowBadge(false)
        }
        manager.createNotificationChannel(channel)
    }

    @SuppressLint("MissingPermission")
    private fun notify(notification: android.app.Notification) {
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    private fun canPostNotifications(): Boolean {
        val runtimePermissionGranted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(appContext, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        return runtimePermissionGranted && notificationManager.areNotificationsEnabled()
    }

    private companion object {
        const val CHANNEL_ID = "album_sync_live_update"
        const val NOTIFICATION_ID = 4100
        const val OPEN_APP_REQUEST_CODE = 4101
        const val COMPLETE_TIMEOUT_MILLIS = 5_000L
        const val SMALL_ICON_RES = android.R.drawable.stat_notify_sync
    }
}
