package com.zerostudio.cloudreve.feature.preview

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.AudioDeviceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.CommandButton
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaNotification
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.google.common.collect.ImmutableList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import org.koin.android.ext.android.inject

private const val MUSIC_MEDIA_NOTIFICATION_ID = 5199
private const val MUSIC_LIVE_UPDATE_NOTIFICATION_ID = 5200
private const val MUSIC_LIVE_UPDATE_CHANNEL_ID = "music_live_update"
private const val LIVE_UPDATE_FRAME_MILLIS = 650L

internal object CloudreveAudioOutputRouter {
    private var player: ExoPlayer? = null
    private var preferredDevice: AudioDeviceInfo? = null

    fun attach(player: ExoPlayer) {
        this.player = player
        player.setPreferredAudioDevice(preferredDevice)
    }

    fun detach(player: ExoPlayer) {
        if (this.player === player) {
            this.player = null
        }
    }

    fun setPreferredAudioDevice(device: AudioDeviceInfo?) {
        preferredDevice = device
        player?.setPreferredAudioDevice(device)
    }
}

class CloudrevePlaybackService : MediaSessionService() {
    private val authenticatedClient: OkHttpClient by inject()
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var mediaSession: MediaSession? = null
    private var player: ExoPlayer? = null
    private var musicLiveUpdateController: MusicLiveUpdateNotificationController? = null
    private var liveUpdateTickerJob: Job? = null
    private var cachedArtworkData: ByteArray? = null
    private var cachedArtwork: Bitmap? = null
    private val liveUpdatePlayerListener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            if (events.contains(Player.EVENT_IS_PLAYING_CHANGED) ||
                events.contains(Player.EVENT_PLAYBACK_STATE_CHANGED) ||
                events.contains(Player.EVENT_MEDIA_METADATA_CHANGED) ||
                events.contains(Player.EVENT_MEDIA_ITEM_TRANSITION) ||
                events.contains(Player.EVENT_POSITION_DISCONTINUITY)
            ) {
                syncMusicLiveUpdate(player)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        initializePlayerAndSession()
    }

    private fun initializePlayerAndSession() {
        if (mediaSession != null) return
        configureMediaNotificationProvider()
        val dataSourceFactory = OkHttpDataSource.Factory(authenticatedClient)
        val exoPlayer = ExoPlayer.Builder(this)
            .setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory))
            .build()
            .apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                        .setUsage(C.USAGE_MEDIA)
                        .build(),
                    true,
                )
                setHandleAudioBecomingNoisy(true)
            }
        val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
            ?.apply {
                addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
            ?: Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
                setPackage(packageName)
                addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
        val sessionActivity = PendingIntent.getActivity(
            this,
            0,
            launchIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        musicLiveUpdateController = MusicLiveUpdateNotificationController(
            context = this,
            sessionActivity = sessionActivity,
            smallIconRes = R.drawable.ic_material_music_note_24,
        )
        player = exoPlayer
        CloudreveAudioOutputRouter.attach(exoPlayer)
        exoPlayer.addListener(liveUpdatePlayerListener)
        mediaSession = MediaSession.Builder(this, exoPlayer)
            .setSessionActivity(sessionActivity)
            .build()
    }

    @OptIn(UnstableApi::class)
    private fun configureMediaNotificationProvider() {
        val provider = CloudreveLiveUpdateMediaNotificationProvider(this)
        provider.setSmallIcon(resolveMediaSessionIconResource())
        setMediaNotificationProvider(provider)
    }

    private fun resolveMediaSessionIconResource(): Int {
        mediaSessionIconResourceNames.forEach { name ->
            val resourceId = resources.getIdentifier(name, "drawable", packageName)
            if (resourceId != 0) return resourceId
        }
        applicationInfo.icon.takeIf { it != 0 }?.let { return it }
        return androidx.media3.session.R.drawable.media3_notification_small_icon
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    override fun onDestroy() {
        stopMusicLiveUpdateTicker(cancelNotification = true)
        player?.removeListener(liveUpdatePlayerListener)
        musicLiveUpdateController?.cancel()
        musicLiveUpdateController = null
        serviceScope.cancel()
        mediaSession?.release()
        mediaSession = null
        player?.let { exoPlayer ->
            CloudreveAudioOutputRouter.detach(exoPlayer)
            exoPlayer.release()
        }
        player = null
        super.onDestroy()
    }

    private fun syncMusicLiveUpdate(player: Player) {
        val controller = musicLiveUpdateController ?: return
        if (!player.isPlaying) {
            stopMusicLiveUpdateTicker(cancelNotification = true)
            return
        }
        if (liveUpdateTickerJob?.isActive != true) {
            liveUpdateTickerJob = serviceScope.launch {
                while (isActive) {
                    runCatching { updateMusicLiveUpdateFrame(player) }
                    delay(LIVE_UPDATE_FRAME_MILLIS)
                }
            }
        }
        serviceScope.launch {
            runCatching { updateMusicLiveUpdateFrame(player) }
        }
    }

    private fun stopMusicLiveUpdateTicker(cancelNotification: Boolean) {
        liveUpdateTickerJob?.cancel()
        liveUpdateTickerJob = null
        if (cancelNotification) {
            musicLiveUpdateController?.cancel()
        }
    }

    private suspend fun updateMusicLiveUpdateFrame(player: Player) {
        val controller = musicLiveUpdateController ?: return
        if (!player.isPlaying) {
            stopMusicLiveUpdateTicker(cancelNotification = true)
            return
        }
        val metadata = player.mediaMetadata
        val title = metadata.title
            ?.toString()
            ?.takeIf { it.isNotBlank() }
            ?: metadata.displayTitle
                ?.toString()
                ?.takeIf { it.isNotBlank() }
            ?: return
        val artist = metadata.artist
            ?.toString()
            ?.takeIf { it.isNotBlank() }
            ?: metadata.subtitle
                ?.toString()
                ?.takeIf { it.isNotBlank() }
        val lyricLines = MusicLiveUpdateLyricsRegistry
            .get(player.currentMediaItem?.mediaId)
            .ifEmpty { metadata.extras.readMusicLiveUpdateLyrics() }
        val rawLyric = lyricLines
            .currentLiveUpdateLyric(player.currentPosition)
        val artwork = resolveArtwork(metadata.artworkData)
        controller.show(title = title, artist = artist, lyric = rawLyric, artwork = artwork)
    }

    private suspend fun resolveArtwork(artworkData: ByteArray?): Bitmap? {
        if (artworkData == null) {
            cachedArtworkData = null
            cachedArtwork = null
            return null
        }
        if (artworkData === cachedArtworkData) return cachedArtwork
        cachedArtworkData = artworkData
        cachedArtwork = withContext(Dispatchers.Default) {
            runCatching {
                BitmapFactory.decodeByteArray(artworkData, 0, artworkData.size)
            }.getOrNull()
        }
        return cachedArtwork
    }

    private companion object {
        val mediaSessionIconResourceNames = listOf(
            "cloudreve_launcher_icon",
            "ic_launcher_foreground",
        )
    }
}

private class MusicLiveUpdateNotificationController(
    private val context: Context,
    private val sessionActivity: PendingIntent,
    private val smallIconRes: Int,
) {
    private val appContext = context.applicationContext
    private val notificationManager = NotificationManagerCompat.from(appContext)

    fun show(title: String, artist: String?, lyric: String?, artwork: Bitmap?) {
        if (!canPostNotifications()) return
        ensureChannel()
        val capsuleText = lyric?.takeIf { it.isNotBlank() } ?: title
        val songSummary = buildSongSummary(title = title, artist = artist)
        val body = if (lyric.isNullOrBlank()) {
            artist ?: appContext.getString(R.string.music_live_update_playing)
        } else {
            songSummary
        }
        val notification = NotificationCompat.Builder(appContext, MUSIC_LIVE_UPDATE_CHANNEL_ID)
            .setSmallIcon(smallIconRes)
            .setContentTitle(capsuleText)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(capsuleText))
            .setSubText(if (lyric.isNullOrBlank()) artist else title)
            .setTicker(capsuleText)
            .setLargeIcon(artwork)
            .setContentIntent(sessionActivity)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setOnlyAlertOnce(true)
            .setLocalOnly(false)
            .setOngoing(true)
            .setRequestPromotedOngoing(true)
            .setShortCriticalText(capsuleText)
            .build()
        notify(notification)
    }

    private fun buildSongSummary(title: String, artist: String?): String =
        listOfNotNull(title.takeIf { it.isNotBlank() }, artist?.takeIf { it.isNotBlank() })
            .joinToString(separator = " · ")

    fun cancel() {
        notificationManager.cancel(MUSIC_LIVE_UPDATE_NOTIFICATION_ID)
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = appContext.getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(MUSIC_LIVE_UPDATE_CHANNEL_ID) != null) return

        val channel = NotificationChannel(
            MUSIC_LIVE_UPDATE_CHANNEL_ID,
            appContext.getString(R.string.music_live_update_channel),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = appContext.getString(R.string.music_live_update_channel_description)
            setShowBadge(false)
        }
        manager.createNotificationChannel(channel)
    }

    @SuppressLint("MissingPermission")
    private fun notify(notification: android.app.Notification) {
        notificationManager.notify(MUSIC_LIVE_UPDATE_NOTIFICATION_ID, notification)
    }

    private fun canPostNotifications(): Boolean {
        val runtimePermissionGranted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(appContext, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        return runtimePermissionGranted && notificationManager.areNotificationsEnabled()
    }

}

@OptIn(UnstableApi::class)
private class CloudreveLiveUpdateMediaNotificationProvider(
    context: Context,
) : DefaultMediaNotificationProvider(
    context,
    DefaultMediaNotificationProvider.NotificationIdProvider { MUSIC_MEDIA_NOTIFICATION_ID },
    MUSIC_LIVE_UPDATE_CHANNEL_ID,
    R.string.music_live_update_channel,
) {
    override fun addNotificationActions(
        mediaSession: MediaSession,
        mediaButtons: ImmutableList<CommandButton>,
        builder: NotificationCompat.Builder,
        actionFactory: MediaNotification.ActionFactory,
    ): IntArray {
        val compactViewActions = super.addNotificationActions(
            mediaSession,
            mediaButtons,
            builder,
            actionFactory,
        )
        val title = mediaSession.player.mediaMetadata.title
            ?.toString()
            ?.takeIf { it.isNotBlank() }
        builder
            .setCategory(NotificationCompat.CATEGORY_TRANSPORT)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOnlyAlertOnce(true)
            .setOngoing(mediaSession.player.isPlaying)
        return compactViewActions
    }

}
