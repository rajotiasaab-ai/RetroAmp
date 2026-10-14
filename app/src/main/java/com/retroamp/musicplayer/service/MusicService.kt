package com.retroamp.musicplayer.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.os.Binder
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.core.app.NotificationCompat
import com.retroamp.musicplayer.MainActivity
import com.retroamp.musicplayer.R
import com.retroamp.musicplayer.model.Track
import java.io.File
import java.io.FileInputStream
import java.util.Random

class MusicService : Service(),
    MediaPlayer.OnPreparedListener,
    MediaPlayer.OnCompletionListener,
    MediaPlayer.OnErrorListener,
    MediaPlayer.OnSeekCompleteListener,
    AudioManager.OnAudioFocusChangeListener {

    companion object {
        const val CHANNEL_ID = "retroamp_playback_v3"
        const val NOTIFICATION_ID = 101

        const val ACTION_PLAY_PAUSE = "com.retroamp.musicplayer.ACTION_PLAY_PAUSE"
        const val ACTION_NEXT = "com.retroamp.musicplayer.ACTION_NEXT"
        const val ACTION_PREV = "com.retroamp.musicplayer.ACTION_PREV"
        const val ACTION_STOP = "com.retroamp.musicplayer.ACTION_STOP"
    }

    interface PlaybackCallback {
        fun onTrackChanged(track: Track, index: Int)
        fun onPlaybackStateChanged(isPlaying: Boolean)
        fun onPlaybackProgress(currentMillis: Int, totalMillis: Int)
    }

    private val binder = MusicBinder()
    private var mediaPlayer: MediaPlayer? = null
    private val playlist = mutableListOf<Track>()
    private var currentTrackIndex = -1
    private var isShuffle = false
    private var isRepeat = false

    @Volatile
    private var isPreparing = false

    @Volatile
    private var isLoaded = false

    @Volatile
    private var isSeeking = false

    private var callback: PlaybackCallback? = null

    private var audioManager: AudioManager? = null
    private var audioFocusRequest: AudioFocusRequest? = null
    private var hasAudioFocus = false
    private var resumeOnFocusGain = false

    private var equalizer: Equalizer? = null
    private var bassBoost: BassBoost? = null
    private var lastAudioSessionId = -1

    private val mainHandler = Handler(Looper.getMainLooper())
    private var debouncedLoadRunnable: Runnable? = null

    private var becomingNoisyReceiver: BroadcastReceiver? = null
    private var isNoisyReceiverRegistered = false

    private var wakeLock: PowerManager.WakeLock? = null
    private var mediaSession: MediaSessionCompat? = null

    inner class MusicBinder : Binder() {
        val service: MusicService
            get() = this@MusicService
    }

    override fun onCreate() {
        super.onCreate()
        audioManager = getSystemService(Context.AUDIO_SERVICE) as? AudioManager

        val pm = getSystemService(Context.POWER_SERVICE) as? PowerManager
        wakeLock = pm?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "RetroAmp:AudioPlaybackLock")?.apply {
            setReferenceCounted(false)
        }

        initMediaSession()
        createNotificationChannel()
        initPlayer()
        registerBecomingNoisyReceiver()
    }

    private fun initMediaSession() {
        try {
            mediaSession = MediaSessionCompat(this, "RetroAmp").apply {
                setFlags(
                    MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS or
                    MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS
                )
                setCallback(object : MediaSessionCompat.Callback() {
                    override fun onPlay() {
                        togglePlayPause()
                    }
                    override fun onPause() {
                        togglePlayPause()
                    }
                    override fun onSkipToNext() {
                        nextTrack()
                    }
                    override fun onSkipToPrevious() {
                        prevTrack()
                    }
                    override fun onSeekTo(pos: Long) {
                        seekTo(pos.toInt())
                    }
                    override fun onStop() {
                        if (isPlaying()) {
                            togglePlayPause()
                        }
                    }
                })
                isActive = true
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun createCassetteBitmap(): Bitmap {
        val size = 128
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        // Chassis body
        paint.color = Color.parseColor("#12141F")
        canvas.drawRoundRect(8f, 16f, 120f, 112f, 12f, 12f, paint)

        // Outer neon green border
        paint.color = Color.parseColor("#00FF66")
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 3f
        canvas.drawRoundRect(8f, 16f, 120f, 112f, 12f, 12f, paint)

        // Center tape window
        paint.style = Paint.Style.FILL
        paint.color = Color.parseColor("#06080E")
        canvas.drawRoundRect(28f, 38f, 100f, 88f, 8f, 8f, paint)

        // Dual reels
        paint.color = Color.parseColor("#E0E3EB")
        canvas.drawCircle(46f, 63f, 14f, paint)
        canvas.drawCircle(82f, 63f, 14f, paint)

        paint.color = Color.parseColor("#06080E")
        canvas.drawCircle(46f, 63f, 6f, paint)
        canvas.drawCircle(82f, 63f, 6f, paint)

        // Drive spokes
        paint.color = Color.parseColor("#00FF66")
        paint.strokeWidth = 2.5f
        paint.style = Paint.Style.STROKE
        canvas.drawLine(46f, 53f, 46f, 73f, paint)
        canvas.drawLine(82f, 53f, 82f, 73f, paint)

        return bmp
    }

    private fun acquireWakeLock() {
        try {
            if (wakeLock?.isHeld != true) {
                wakeLock?.acquire(24 * 3600 * 1000L)
            }
        } catch (ignored: Exception) {}
    }

    private fun releaseWakeLock() {
        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
            }
        } catch (ignored: Exception) {}
    }

    private fun initPlayer() {
        safelyReleasePlayer()
        try {
            mediaPlayer = MediaPlayer().apply {
                setWakeMode(applicationContext, PowerManager.PARTIAL_WAKE_LOCK)
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .build()
                )
                setOnPreparedListener(this@MusicService)
                setOnCompletionListener(this@MusicService)
                setOnErrorListener(this@MusicService)
                setOnSeekCompleteListener(this@MusicService)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        isPreparing = false
        isLoaded = false
        isSeeking = false
    }

    private fun safelyReleasePlayer() {
        releaseWakeLock()
        try {
            mediaPlayer?.setOnPreparedListener(null)
            mediaPlayer?.setOnCompletionListener(null)
            mediaPlayer?.setOnErrorListener(null)
            mediaPlayer?.setOnSeekCompleteListener(null)
            if (mediaPlayer?.isPlaying == true) {
                mediaPlayer?.stop()
            }
            mediaPlayer?.release()
        } catch (ignored: Exception) {}
        mediaPlayer = null
    }

    private fun safelyResetPlayer(): Boolean {
        return try {
            if (mediaPlayer == null) {
                initPlayer()
                true
            } else {
                if (mediaPlayer?.isPlaying == true) {
                    mediaPlayer?.stop()
                }
                mediaPlayer?.reset()
                true
            }
        } catch (e: Exception) {
            initPlayer()
            true
        }
    }

    private fun initAudioFx() {
        try {
            val mp = mediaPlayer ?: return
            val sessionId = mp.audioSessionId
            if (sessionId <= 0) return

            if (equalizer != null && sessionId == lastAudioSessionId) {
                equalizer?.enabled = true
                bassBoost?.enabled = true
                return
            }

            try { equalizer?.release() } catch (ignored: Exception) {}
            try { bassBoost?.release() } catch (ignored: Exception) {}
            equalizer = null
            bassBoost = null

            equalizer = Equalizer(0, sessionId).apply { enabled = true }
            bassBoost = BassBoost(0, sessionId).apply { enabled = true }
            lastAudioSessionId = sessionId
        } catch (e: Exception) {
            equalizer = null
            bassBoost = null
        }
    }

    fun setPlaylist(list: List<Track>, startIndex: Int, autoPlay: Boolean = false) {
        playlist.clear()
        playlist.addAll(list)

        if (startIndex in playlist.indices) {
            currentTrackIndex = startIndex
            val track = playlist[startIndex]
            callback?.onTrackChanged(track, startIndex)
            if (autoPlay) {
                playTrackAt(startIndex)
            }
        }
    }

    fun playTrackAt(index: Int) {
        if (index !in playlist.indices) return
        currentTrackIndex = index
        val track = playlist[index]

        // 1. Immediately update UI metadata for zero-latency user feedback!
        callback?.onTrackChanged(track, currentTrackIndex)
        updateNotification(true)

        // 2. Debounce player load: if rapid clicks occur, cancel pending load and delay by 120ms
        debouncedLoadRunnable?.let { mainHandler.removeCallbacks(it) }
        debouncedLoadRunnable = Runnable {
            loadAndPlayTrackInternal(currentTrackIndex)
        }
        mainHandler.postDelayed(debouncedLoadRunnable!!, 120)
    }

    @Synchronized
    private fun loadAndPlayTrackInternal(index: Int) {
        if (index !in playlist.indices) return
        val track = playlist[index]

        if (!requestAudioFocus()) {
            return
        }

        try {
            safelyResetPlayer()
            isPreparing = true
            isLoaded = false
            isSeeking = false

            var sourceSet = false

            // Try 1: Content URI for MediaStore audio
            if (track.uri != null && track.uri.scheme == "content") {
                try {
                    mediaPlayer?.setDataSource(applicationContext, track.uri)
                    sourceSet = true
                } catch (ignored: Exception) {}
            }

            // Try 2: Direct File descriptor via FileInputStream (prevents file locking issues)
            if (!sourceSet && !track.path.isNullOrBlank()) {
                val file = File(track.path)
                if (file.exists() && file.canRead()) {
                    try {
                        FileInputStream(file).use { fis ->
                            mediaPlayer?.setDataSource(fis.fd)
                            sourceSet = true
                        }
                    } catch (ignored: Exception) {
                        try {
                            mediaPlayer?.setDataSource(track.path)
                            sourceSet = true
                        } catch (e2: Exception) {}
                    }
                }
            }

            // Try 3: General URI fallback
            if (!sourceSet && track.uri != null) {
                try {
                    mediaPlayer?.setDataSource(applicationContext, track.uri)
                    sourceSet = true
                } catch (ignored: Exception) {}
            }

            if (sourceSet) {
                mediaPlayer?.prepareAsync()
            } else {
                isPreparing = false
                isLoaded = false
                callback?.onPlaybackStateChanged(false)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            isPreparing = false
            isLoaded = false
            callback?.onPlaybackStateChanged(false)
        }
    }

    fun togglePlayPause() {
        val mp = mediaPlayer
        if (mp == null) {
            initPlayer()
            playTrackAt(currentTrackIndex.coerceAtLeast(0))
            return
        }

        try {
            if (mp.isPlaying) {
                mp.pause()
                releaseWakeLock()
                callback?.onPlaybackStateChanged(false)
                updateNotification(false)
            } else {
                if (isPreparing) return
                if (!isLoaded) {
                    playTrackAt(currentTrackIndex.coerceAtLeast(0))
                } else {
                    if (requestAudioFocus()) {
                        acquireWakeLock()
                        mp.start()
                        callback?.onPlaybackStateChanged(true)
                        updateNotification(true)
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            playTrackAt(currentTrackIndex.coerceAtLeast(0))
        }
    }

    fun nextTrack() {
        if (playlist.isEmpty()) return
        if (isShuffle) {
            var next: Int
            do {
                next = Random().nextInt(playlist.size)
            } while (playlist.size > 1 && next == currentTrackIndex)
            currentTrackIndex = next
        } else {
            currentTrackIndex = (currentTrackIndex + 1) % playlist.size
        }
        playTrackAt(currentTrackIndex)
    }

    fun prevTrack() {
        if (playlist.isEmpty()) return
        if (getCurrentPosition() > 3000) {
            seekTo(0)
            return
        }
        currentTrackIndex--
        if (currentTrackIndex < 0) {
            currentTrackIndex = playlist.size - 1
        }
        playTrackAt(currentTrackIndex)
    }

    fun seekTo(positionMillis: Int) {
        try {
            if (mediaPlayer != null && isLoaded && !isPreparing) {
                isSeeking = true
                mediaPlayer?.seekTo(positionMillis)
            }
        } catch (ignored: Exception) {
            isSeeking = false
        }
    }

    override fun onSeekComplete(mp: MediaPlayer?) {
        isSeeking = false
    }

    fun isPlaying(): Boolean {
        return try {
            mediaPlayer?.isPlaying == true && !isPreparing
        } catch (ignored: Exception) {
            false
        }
    }

    fun getCurrentPosition(): Int {
        return try {
            if (mediaPlayer != null && isLoaded && !isPreparing) {
                mediaPlayer?.currentPosition ?: 0
            } else 0
        } catch (ignored: Exception) {
            0
        }
    }

    fun getDuration(): Int {
        try {
            if (mediaPlayer != null && isLoaded && !isPreparing) {
                val d = mediaPlayer?.duration ?: 0
                if (d > 0) {
                    val cur = currentTrack
                    if (cur != null && cur.duration <= 0) {
                        cur.duration = d.toLong()
                    }
                    return d
                }
            }
        } catch (ignored: Exception) {}
        return (currentTrack?.duration ?: 0L).toInt()
    }

    val currentTrack: Track?
        get() = if (currentTrackIndex in playlist.indices) playlist[currentTrackIndex] else null

    fun getPlaylist(): List<Track> = playlist

    fun getCurrentIndex(): Int = currentTrackIndex

    fun setCallback(cb: PlaybackCallback) {
        this.callback = cb
    }

    fun setShuffleMode(enabled: Boolean) {
        this.isShuffle = enabled
    }

    fun isShuffleMode(): Boolean = isShuffle

    fun setRepeatMode(enabled: Boolean) {
        this.isRepeat = enabled
    }

    fun isRepeatMode(): Boolean = isRepeat

    fun getEqualizerEffect(): Equalizer? = equalizer

    fun getBassBoostEffect(): BassBoost? = bassBoost

    override fun onPrepared(mp: MediaPlayer) {
        isPreparing = false
        isLoaded = true
        isSeeking = false
        try {
            if (hasAudioFocus || requestAudioFocus()) {
                acquireWakeLock()
                mp.start()
                initAudioFx()
                val cur = currentTrack
                if (cur != null) {
                    callback?.onTrackChanged(cur, currentTrackIndex)
                    callback?.onPlaybackStateChanged(true)
                }
                updateNotification(true)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            releaseWakeLock()
            callback?.onPlaybackStateChanged(false)
        }
    }

    override fun onCompletion(mp: MediaPlayer) {
        if (isRepeat) {
            playTrackAt(currentTrackIndex)
        } else {
            nextTrack()
        }
    }

    override fun onError(mp: MediaPlayer, what: Int, extra: Int): Boolean {
        releaseWakeLock()
        isPreparing = false
        isLoaded = false
        isSeeking = false
        try {
            mp.reset()
        } catch (ignored: Exception) {}
        callback?.onPlaybackStateChanged(false)
        updateNotification(false)
        // Return true to handle error without calling onCompletion loop
        return true
    }

    // Audio Focus Handling
    private fun requestAudioFocus(): Boolean {
        val am = audioManager ?: return true
        if (hasAudioFocus) return true

        val result = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .build()
                )
                .setOnAudioFocusChangeListener(this)
                .build()
            audioFocusRequest = req
            am.requestAudioFocus(req)
        } else {
            @Suppress("DEPRECATION")
            am.requestAudioFocus(this, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN)
        }

        hasAudioFocus = (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED)
        return hasAudioFocus
    }

    private fun abandonAudioFocus() {
        val am = audioManager ?: return
        if (!hasAudioFocus) return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioFocusRequest?.let { am.abandonAudioFocusRequest(it) }
        } else {
            @Suppress("DEPRECATION")
            am.abandonAudioFocus(this)
        }
        hasAudioFocus = false
    }

    override fun onAudioFocusChange(focusChange: Int) {
        when (focusChange) {
            AudioManager.AUDIOFOCUS_LOSS -> {
                hasAudioFocus = false
                resumeOnFocusGain = false
                if (isPlaying()) {
                    togglePlayPause()
                }
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                hasAudioFocus = false
                if (isPlaying()) {
                    resumeOnFocusGain = true
                    togglePlayPause()
                }
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                try {
                    mediaPlayer?.setVolume(0.5f, 0.5f)
                } catch (ignored: Exception) {}
            }
            AudioManager.AUDIOFOCUS_GAIN -> {
                hasAudioFocus = true
                try {
                    mediaPlayer?.setVolume(1.0f, 1.0f)
                } catch (ignored: Exception) {}
                if (resumeOnFocusGain) {
                    resumeOnFocusGain = false
                    togglePlayPause()
                }
            }
        }
    }

    private fun registerBecomingNoisyReceiver() {
        if (!isNoisyReceiverRegistered) {
            becomingNoisyReceiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context?, intent: Intent?) {
                    if (intent?.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) {
                        if (isPlaying()) {
                            togglePlayPause()
                        }
                    }
                }
            }
            val filter = IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY)
            registerReceiver(becomingNoisyReceiver, filter)
            isNoisyReceiverRegistered = true
        }
    }

    private fun unregisterBecomingNoisyReceiver() {
        if (isNoisyReceiverRegistered && becomingNoisyReceiver != null) {
            try {
                unregisterReceiver(becomingNoisyReceiver)
            } catch (ignored: Exception) {}
            isNoisyReceiverRegistered = false
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "RetroAmp Playback",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "RetroAmp music playback controls and metadata"
                setShowBadge(false)
                setSound(null, null)
                enableVibration(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    private fun updateNotification(isPlaying: Boolean) {
        try {
            val cur = currentTrack
            val title = cur?.cleanTitle ?: "RetroAmp Music Player"
            val artist = cur?.cleanArtist ?: "Offline Playback"
            val album = if (!cur?.album.isNullOrBlank()) cur!!.album else "RetroAmp Hi-Fi"

            // 1. Update MediaSession State & Metadata for System Media Player & Lockscreen
            val state = if (isPlaying) PlaybackStateCompat.STATE_PLAYING else PlaybackStateCompat.STATE_PAUSED
            val playbackState = PlaybackStateCompat.Builder()
                .setActions(
                    PlaybackStateCompat.ACTION_PLAY or
                    PlaybackStateCompat.ACTION_PAUSE or
                    PlaybackStateCompat.ACTION_PLAY_PAUSE or
                    PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
                    PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS or
                    PlaybackStateCompat.ACTION_SEEK_TO or
                    PlaybackStateCompat.ACTION_STOP
                )
                .setState(state, getCurrentPosition().toLong(), 1.0f)
                .build()
            mediaSession?.setPlaybackState(playbackState)

            val meta = MediaMetadataCompat.Builder()
                .putString(MediaMetadataCompat.METADATA_KEY_TITLE, title)
                .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, artist)
                .putString(MediaMetadataCompat.METADATA_KEY_ALBUM, album)
                .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, getDuration().toLong())
                .putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, createCassetteBitmap())
                .build()
            mediaSession?.setMetadata(meta)

            // 2. Interactive Notification PendingIntents
            val openAppIntent = Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val contentIntent = PendingIntent.getActivity(
                this, 0, openAppIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val prevIntent = Intent(this, MusicService::class.java).apply { action = ACTION_PREV }
            val prevPending = PendingIntent.getService(
                this, 1, prevIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val playPauseIntent = Intent(this, MusicService::class.java).apply { action = ACTION_PLAY_PAUSE }
            val playPausePending = PendingIntent.getService(
                this, 2, playPauseIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val nextIntent = Intent(this, MusicService::class.java).apply { action = ACTION_NEXT }
            val nextPending = PendingIntent.getService(
                this, 3, nextIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val playPauseIcon = if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play
            val playPauseTitle = if (isPlaying) "Pause" else "Play"

            val mediaStyle = androidx.media.app.NotificationCompat.MediaStyle()
                .setMediaSession(mediaSession?.sessionToken)
                .setShowActionsInCompactView(0, 1, 2)

            val notification = NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_stat_music)
                .setLargeIcon(createCassetteBitmap())
                .setContentTitle(title)
                .setContentText("$artist • $album")
                .setSubText(cur?.format ?: "Hi-Fi")
                .setContentIntent(contentIntent)
                .setOngoing(isPlaying)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .addAction(android.R.drawable.ic_media_previous, "Previous", prevPending)
                .addAction(playPauseIcon, playPauseTitle, playPausePending)
                .addAction(android.R.drawable.ic_media_next, "Next", nextPending)
                .setStyle(mediaStyle)
                .build()

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PLAY_PAUSE -> togglePlayPause()
            ACTION_NEXT -> nextTrack()
            ACTION_PREV -> prevTrack()
            ACTION_STOP -> {
                if (isPlaying()) {
                    togglePlayPause()
                }
            }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onDestroy() {
        super.onDestroy()
        mediaSession?.isActive = false
        mediaSession?.release()
        mediaSession = null
        releaseWakeLock()
        unregisterBecomingNoisyReceiver()
        abandonAudioFocus()
        debouncedLoadRunnable?.let { mainHandler.removeCallbacks(it) }
        try { equalizer?.release() } catch (ignored: Exception) {}
        try { bassBoost?.release() } catch (ignored: Exception) {}
        safelyReleasePlayer()
        equalizer = null
        bassBoost = null
    }
}
