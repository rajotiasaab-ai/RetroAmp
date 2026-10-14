package com.retroamp.musicplayer

import android.Manifest
import android.app.AlertDialog
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.retroamp.musicplayer.databinding.ActivityMainBinding
import com.retroamp.musicplayer.model.Skin
import com.retroamp.musicplayer.model.Track
import com.retroamp.musicplayer.service.MusicService
import com.retroamp.musicplayer.ui.TrackAdapter
import com.retroamp.musicplayer.utils.MusicScanner

class MainActivity : AppCompatActivity(), MusicService.PlaybackCallback {

    companion object {
        private const val PERMISSION_REQ_CODE = 200
        private const val PREF_NAME = "retroamp_prefs"
        private const val KEY_SELECTED_SKIN = "selected_skin_id"
    }

    private lateinit var binding: ActivityMainBinding
    private var musicService: MusicService? = null
    private var isBound = false

    private lateinit var audioManager: AudioManager
    private lateinit var prefs: SharedPreferences

    private val allTracks = mutableListOf<Track>()
    private val displayedTracks = mutableListOf<Track>()
    private lateinit var trackAdapter: TrackAdapter
    private var currentSkin: Skin = Skin.getClassicWinamp()

    private var activeFilterFormat = "ALL"
    private var currentSearchQuery = ""
    private var isUserSeeking = false

    private val progressHandler = Handler(Looper.getMainLooper())
    private lateinit var progressRunnable: Runnable
    private var isActivityVisible = false

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as MusicService.MusicBinder
            musicService = binder.service
            musicService?.setCallback(this@MainActivity)
            isBound = true

            if (allTracks.isNotEmpty()) {
                val curIdx = musicService?.getCurrentIndex() ?: 0
                val activeIdx = if (curIdx in allTracks.indices) curIdx else 0
                musicService?.setPlaylist(allTracks, activeIdx, autoPlay = false)
                updateUIWithTrack(allTracks[activeIdx], activeIdx)
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            isBound = false
            musicService = null
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        prefs = getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

        loadSavedSkin()
        setupBackNavigation()
        setupRecyclerView()
        setupPlayerControls()
        setupLibraryControls()
        setupVolumeControls()
        applySkin(currentSkin)
        checkAndRequestPermissions()

        val intent = Intent(this, MusicService::class.java)
        startService(intent)
        bindService(intent, connection, Context.BIND_AUTO_CREATE)
    }

    private fun loadSavedSkin() {
        val savedId = prefs.getString(KEY_SELECTED_SKIN, Skin.getClassicWinamp().id)
        currentSkin = Skin.getAllSkins().find { it.id == savedId } ?: Skin.getClassicWinamp()
    }

    private fun setupBackNavigation() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (binding.libraryContainer.visibility == View.VISIBLE) {
                    closeLibraryView()
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        })
    }

    private fun setupRecyclerView() {
        binding.rvTracks.layoutManager = LinearLayoutManager(this)
        trackAdapter = TrackAdapter(displayedTracks) { position ->
            if (position in displayedTracks.indices) {
                val selectedTrack = displayedTracks[position]
                val fullIndex = allTracks.indexOfFirst { it.id == selectedTrack.id }
                val playIdx = if (fullIndex >= 0) fullIndex else 0

                musicService?.setPlaylist(allTracks, playIdx, autoPlay = true)
                trackAdapter.setCurrentTrackIndex(position, playing = true)
                closeLibraryView()
            }
        }
        binding.rvTracks.adapter = trackAdapter
    }

    private fun setupPlayerControls() {
        // Transport Controls
        binding.btnPlay.setOnClickListener {
            if (isBound) musicService?.togglePlayPause()
        }

        binding.btnPrev.setOnClickListener {
            if (isBound) musicService?.prevTrack()
        }

        binding.btnNext.setOnClickListener {
            if (isBound) musicService?.nextTrack()
        }

        binding.btnStop.setOnClickListener {
            if (isBound) {
                musicService?.seekTo(0)
                if (musicService?.isPlaying() == true) {
                    musicService?.togglePlayPause()
                }
                binding.tvLcdTime.text = "00:00"
                binding.tvSeekElapsed.text = "00:00"
                binding.seekBar.progress = 0
                binding.tvStatusIndicator.text = " [STOPPED]"
                binding.btnPlay.text = "▶"
                binding.visualizerView.setPlaying(false)
                binding.cassetteDeckView.setPlaying(false)
                binding.cassetteDeckView.setProgress(0f)
            }
        }

        binding.btnShuffle.setOnClickListener {
            if (isBound) {
                val s = !(musicService?.isShuffleMode() ?: false)
                musicService?.setShuffleMode(s)
                binding.btnShuffle.setTextColor(if (s) currentSkin.accentColor else currentSkin.textSecondary)
                Toast.makeText(this, if (s) "Shuffle: ON" else "Shuffle: OFF", Toast.LENGTH_SHORT).show()
            }
        }

        binding.btnRepeat.setOnClickListener {
            if (isBound) {
                val r = !(musicService?.isRepeatMode() ?: false)
                musicService?.setRepeatMode(r)
                binding.btnRepeat.setTextColor(if (r) currentSkin.accentColor else currentSkin.textSecondary)
                Toast.makeText(this, if (r) "Repeat: ON" else "Repeat: OFF", Toast.LENGTH_SHORT).show()
            }
        }

        // Quick Jump Buttons
        binding.btnRewind10.setOnClickListener {
            if (isBound) {
                val cur = musicService?.getCurrentPosition() ?: 0
                musicService?.seekTo((cur - 10000).coerceAtLeast(0))
            }
        }

        binding.btnForward10.setOnClickListener {
            if (isBound) {
                val cur = musicService?.getCurrentPosition() ?: 0
                val dur = musicService?.getDuration() ?: 0
                musicService?.seekTo((cur + 10000).coerceAtMost(dur))
            }
        }

        // Top Deck Actions
        binding.btnSkinPicker.setOnClickListener { showSkinPickerDialog() }
        binding.btnEqualizer.setOnClickListener { showEqualizerDialog() }
        binding.btnOpenLibraryTop.setOnClickListener { openLibraryView() }
        binding.btnOpenLibraryBottom.setOnClickListener { openLibraryView() }

        // Rock-Solid Seekbar: ONLY seek on onStopTrackingTouch to prevent audio stutter!
        binding.seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onStartTrackingTouch(seekBar: SeekBar?) {
                isUserSeeking = true
            }

            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    val duration = musicService?.getDuration() ?: 0
                    if (duration > 0) {
                        val newPos = ((progress.toLong() * duration) / 1000).toInt()
                        binding.tvSeekElapsed.text = formatTime(newPos.toLong())
                        binding.tvLcdTime.text = formatTime(newPos.toLong())
                        binding.cassetteDeckView.setProgress(progress / 1000f)
                    }
                }
            }

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                if (isBound) {
                    val duration = musicService?.getDuration() ?: 0
                    if (duration > 0) {
                        val finalPos = ((binding.seekBar.progress.toLong() * duration) / 1000).toInt()
                        musicService?.seekTo(finalPos)
                    }
                }
                isUserSeeking = false
            }
        })
    }

    private fun setupLibraryControls() {
        binding.btnBackToPlayer.setOnClickListener { closeLibraryView() }
        binding.btnScanStorage.setOnClickListener { loadMusicFromStorage() }

        binding.btnClearSearch.setOnClickListener {
            binding.etSearch.setText("")
        }

        binding.etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                currentSearchQuery = s?.toString().orEmpty().trim()
                binding.btnClearSearch.visibility = if (currentSearchQuery.isNotEmpty()) View.VISIBLE else View.GONE
                applyFilters()
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        // Format Filter Chips
        val filterButtons = listOf(
            binding.chipAll to "ALL",
            binding.chipMp3 to "MP3",
            binding.chipFlac to "FLAC",
            binding.chipWav to "WAV",
            binding.chipM4a to "M4A",
            binding.chipOgg to "OGG"
        )

        for ((chipBtn, format) in filterButtons) {
            chipBtn.setOnClickListener {
                activeFilterFormat = format
                updateFilterChipsUI()
                applyFilters()
            }
        }
    }

    private fun updateFilterChipsUI() {
        val filterButtons = listOf(
            binding.chipAll to "ALL",
            binding.chipMp3 to "MP3",
            binding.chipFlac to "FLAC",
            binding.chipWav to "WAV",
            binding.chipM4a to "M4A",
            binding.chipOgg to "OGG"
        )

        for ((btn, format) in filterButtons) {
            if (activeFilterFormat == format) {
                btn.backgroundTintList = ColorStateList.valueOf(currentSkin.accentColor)
                btn.setTextColor(Color.BLACK)
            } else {
                btn.backgroundTintList = ColorStateList.valueOf(currentSkin.buttonBg)
                btn.setTextColor(currentSkin.textSecondary)
            }
        }
    }

    private fun applyFilters() {
        displayedTracks.clear()
        val query = currentSearchQuery.lowercase()

        val filtered = allTracks.filter { track ->
            val matchesFormat = when (activeFilterFormat) {
                "ALL" -> true
                "M4A" -> track.format == "M4A" || track.format == "AAC"
                "OGG" -> track.format == "OGG" || track.format == "OPUS"
                else -> track.format.equals(activeFilterFormat, ignoreCase = true)
            }

            val matchesSearch = query.isBlank() ||
                    track.title.lowercase().contains(query) ||
                    track.artist.lowercase().contains(query) ||
                    track.album.lowercase().contains(query)

            matchesFormat && matchesSearch
        }

        displayedTracks.addAll(filtered)
        trackAdapter.updateTracks(displayedTracks)

        val curIdx = musicService?.getCurrentIndex() ?: -1
        if (curIdx in allTracks.indices) {
            val currentTrack = allTracks[curIdx]
            val dispIdx = displayedTracks.indexOfFirst { it.id == currentTrack.id }
            trackAdapter.setCurrentTrackIndex(dispIdx, musicService?.isPlaying() == true)
        }

        binding.tvLibrarySummary.text = "Showing ${displayedTracks.size} of ${allTracks.size} tracks • Tap to play"
        binding.btnOpenLibraryBottom.text = "📂 OPEN MUSIC LIBRARY (${allTracks.size} TRACKS) ➔"
    }

    private fun openLibraryView() {
        applyFilters()
        binding.libraryContainer.visibility = View.VISIBLE
        binding.playerContainer.visibility = View.GONE
    }

    private fun closeLibraryView() {
        binding.libraryContainer.visibility = View.GONE
        binding.playerContainer.visibility = View.VISIBLE
    }

    private fun setupVolumeControls() {
        val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val curVol = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        binding.sbVolume.max = maxVol
        binding.sbVolume.progress = curVol
        val pct = if (maxVol > 0) ((curVol.toFloat() / maxVol) * 100).toInt() else 0
        binding.tvVolValue.text = "$pct%"

        binding.sbVolume.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, progress, 0)
                    val p = if (maxVol > 0) ((progress.toFloat() / maxVol) * 100).toInt() else 0
                    binding.tvVolValue.text = "$p%"
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
    }

    override fun onStart() {
        super.onStart()
        isActivityVisible = true
        startProgressLoop()
        val isPlaying = musicService?.isPlaying() == true
        binding.visualizerView.setPlaying(isPlaying)
        binding.cassetteDeckView.setPlaying(isPlaying)
    }

    override fun onStop() {
        super.onStop()
        isActivityVisible = false
        stopProgressLoop()
        binding.visualizerView.setPlaying(false)
        binding.cassetteDeckView.setPlaying(false)
    }

    private fun stopProgressLoop() {
        progressHandler.removeCallbacksAndMessages(null)
    }

    private fun startProgressLoop() {
        stopProgressLoop()
        progressRunnable = Runnable {
            if (!isActivityVisible) return@Runnable
            try {
                if (isBound && musicService?.isPlaying() == true) {
                    val pos = musicService?.getCurrentPosition() ?: 0
                    val dur = musicService?.getDuration() ?: 0

                    if (dur > 0) {
                        val progress = ((pos.toLong() * 1000) / dur).toInt()
                        if (!isUserSeeking) {
                            binding.seekBar.progress = progress
                            binding.tvLcdTime.text = formatTime(pos.toLong())
                            binding.tvSeekElapsed.text = formatTime(pos.toLong())
                            binding.tvLcdTotalTime.text = "/ " + formatTime(dur.toLong())
                            binding.tvSeekTotal.text = formatTime(dur.toLong())
                            binding.cassetteDeckView.setProgress(progress / 1000f)
                        }
                    }

                    binding.visualizerView.updateVisualizer()
                    val l = (Math.random() * 7 + 3).toInt()
                    val r = (Math.random() * 7 + 3).toInt()
                    binding.tvVuLeft.text = "L [ " + makeVuString(l) + " -3dB ]"
                    binding.tvVuRight.text = "R [ " + makeVuString(r) + " -3dB ]"
                }

                // Keep physical volume slider in sync
                val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                val curVol = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                if (binding.sbVolume.progress != curVol) {
                    binding.sbVolume.progress = curVol
                    val p = if (maxVol > 0) ((curVol.toFloat() / maxVol) * 100).toInt() else 0
                    binding.tvVolValue.text = "$p%"
                }
            } catch (ignored: Exception) {}

            if (isActivityVisible) {
                progressHandler.postDelayed(progressRunnable, 100)
            }
        }
        progressHandler.post(progressRunnable)
    }

    private fun makeVuString(level: Int): String {
        val sb = StringBuilder()
        for (i in 0 until 10) {
            sb.append(if (i < level) "■" else "□")
        }
        return sb.toString()
    }

    private fun formatTime(millis: Long): String {
        val sec = millis / 1000
        val m = sec / 60
        val s = sec % 60
        return String.format("%02d:%02d", m, s)
    }

    private fun checkAndRequestPermissions() {
        val permissions = mutableListOf<String>()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.READ_MEDIA_AUDIO)
            }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.POST_NOTIFICATIONS)
            }
        } else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
        }

        if (permissions.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, permissions.toTypedArray(), PERMISSION_REQ_CODE)
        } else {
            loadMusicFromStorage()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        loadMusicFromStorage()
    }

    private fun loadMusicFromStorage() {
        Thread {
            val scanned = MusicScanner.scanDeviceTracks(this)
            runOnUiThread {
                allTracks.clear()
                allTracks.addAll(scanned)
                applyFilters()

                if (allTracks.isNotEmpty() && isBound) {
                    val cur = musicService?.getCurrentIndex() ?: 0
                    val startIdx = if (cur in allTracks.indices) cur else 0
                    musicService?.setPlaylist(allTracks, startIdx, autoPlay = false)
                    updateUIWithTrack(allTracks[startIdx], startIdx)
                }

                binding.btnOpenLibraryBottom.text = "📂 OPEN MUSIC LIBRARY (${allTracks.size} TRACKS) ➔"
                Toast.makeText(this, "Found ${allTracks.size} audio tracks", Toast.LENGTH_SHORT).show()
            }
        }.start()
    }

    private fun updateUIWithTrack(track: Track?, index: Int) {
        if (track == null) return
        binding.tvTrackNumber.text = String.format("TRK %02d/%02d", index + 1, allTracks.size)
        binding.tvMarqueeTitle.text = track.cleanTitle
        binding.tvMarqueeTitle.isSelected = true
        binding.tvArtistAlbum.text = track.cleanArtist + if (track.album.isNotBlank()) " • ${track.album}" else ""
        binding.tvAudioSpecs.text = "${track.format} • STEREO • HIGH-FIDELITY"

        if (track.duration > 0) {
            binding.tvLcdTotalTime.text = "/ " + track.formattedDuration
            binding.tvSeekTotal.text = track.formattedDuration
        } else {
            binding.tvLcdTotalTime.text = "/ --:--"
            binding.tvSeekTotal.text = "--:--"
        }
    }

    override fun onTrackChanged(track: Track, index: Int) {
        runOnUiThread {
            updateUIWithTrack(track, index)
            val isPlaying = musicService?.isPlaying() == true
            val dispIdx = displayedTracks.indexOfFirst { it.id == track.id }
            trackAdapter.setCurrentTrackIndex(dispIdx, isPlaying)
            binding.btnPlay.text = if (isPlaying) "⏸" else "▶"
            binding.tvStatusIndicator.text = if (isPlaying) " [PLAYING]" else " [READY]"
            binding.tvCurrentPlayingIndicator.text = if (isPlaying) "▶ PLAYING: ${track.cleanTitle}" else "⏹ READY"
            binding.visualizerView.setPlaying(isPlaying && isActivityVisible)
            binding.cassetteDeckView.setPlaying(isPlaying && isActivityVisible)
        }
    }

    override fun onPlaybackStateChanged(isPlaying: Boolean) {
        runOnUiThread {
            binding.btnPlay.text = if (isPlaying) "⏸" else "▶"
            binding.tvStatusIndicator.text = if (isPlaying) " [PLAYING]" else " [PAUSED]"
            val cur = musicService?.currentTrack
            binding.tvCurrentPlayingIndicator.text = if (isPlaying) "▶ PLAYING: ${cur?.cleanTitle ?: ""}" else "⏸ PAUSED"
            binding.visualizerView.setPlaying(isPlaying && isActivityVisible)
            binding.cassetteDeckView.setPlaying(isPlaying && isActivityVisible)

            val curIdx = musicService?.getCurrentIndex() ?: -1
            if (curIdx in allTracks.indices) {
                val dispIdx = displayedTracks.indexOfFirst { it.id == allTracks[curIdx].id }
                trackAdapter.setCurrentTrackIndex(dispIdx, isPlaying)
            }
        }
    }

    override fun onPlaybackProgress(currentMillis: Int, totalMillis: Int) {}

    private fun showSkinPickerDialog() {
        val skins = Skin.getAllSkins()
        val skinTitles = skins.map { "${it.name} [${it.era}]" }.toTypedArray()

        AlertDialog.Builder(this)
            .setTitle("🎨 Choose Rich Color Theme")
            .setItems(skinTitles) { _, which ->
                val chosen = skins[which]
                applySkin(chosen)
                prefs.edit().putString(KEY_SELECTED_SKIN, chosen.id).apply()
            }
            .show()
    }

    private fun applySkin(skin: Skin) {
        this.currentSkin = skin

        // Backgrounds
        binding.rootLayout.setBackgroundColor(skin.bgColor)
        binding.playerContainer.setBackgroundColor(skin.bgColor)
        binding.libraryContainer.setBackgroundColor(skin.bgColor)

        // Cards and Sections
        binding.lcdScreen.background = GradientDrawable().apply {
            cornerRadius = 12f
            setColor(skin.lcdBgColor)
            setStroke(2, skin.accentColor)
        }

        binding.tapeDeckLayout.background = GradientDrawable().apply {
            cornerRadius = 12f
            setColor(skin.surfaceColor)
            setStroke(2, skin.surfaceHighlight)
        }

        // Title and Badges
        binding.tvBrandTitle.text = "⚡ RETROAMP [${skin.era}] - ${skin.name.uppercase()}"
        binding.tvBrandTitle.setTextColor(skin.accentColor)

        binding.tvTrackNumber.setTextColor(skin.lcdTextColor)
        binding.tvStatusIndicator.setTextColor(skin.lcdTextColor)
        binding.tvAudioSpecs.setTextColor(skin.lcdTextColor)
        binding.tvMarqueeTitle.setTextColor(skin.lcdTextColor)
        binding.tvArtistAlbum.setTextColor(skin.textSecondary)
        binding.tvLcdTime.setTextColor(skin.lcdTextColor)
        binding.tvLcdTotalTime.setTextColor(skin.lcdTextColor)

        binding.tvSeekElapsed.setTextColor(skin.lcdTextColor)
        binding.tvSeekTotal.setTextColor(skin.lcdTextColor)

        binding.tvTapeDeckBadge.setTextColor(skin.accentColor)
        binding.tvEngineBadge.setTextColor(skin.textSecondary)
        binding.tvVuLeft.setTextColor(skin.accentColor)
        binding.tvVuRight.setTextColor(skin.accentColor)

        binding.tvVolLabel.setTextColor(skin.accentColor)
        binding.tvVolValue.setTextColor(skin.accentColor)

        binding.tvLibraryTitle.setTextColor(skin.accentColor)
        binding.tvLibrarySummary.setTextColor(skin.textSecondary)
        binding.tvCurrentPlayingIndicator.setTextColor(skin.accentColor)

        // Developer Credit Styling (Dynamic to active Skin)
        binding.tvDeveloperCredit.setTextColor(skin.accentColor)
        binding.tvDeveloperCredit.background = GradientDrawable().apply {
            cornerRadius = 6f
            setColor(skin.lcdBgColor)
            setStroke(1, skin.accentColor)
        }
        binding.tvFooterCredit.setTextColor(skin.accentColor)
        binding.tvLibraryDevCredit.setTextColor(skin.accentColor)

        // Custom Views
        binding.visualizerView.setPalette(skin.spectrumColors)
        binding.cassetteDeckView.setThemeColors(
            accent = skin.accentColor,
            secondary = skin.accentSecondary,
            chassis = skin.surfaceColor,
            windowBg = skin.lcdBgColor
        )

        // Seekbar & Volume Tints
        val accentList = ColorStateList.valueOf(skin.accentColor)
        binding.seekBar.progressTintList = accentList
        binding.seekBar.thumbTintList = accentList
        binding.sbVolume.progressTintList = accentList
        binding.sbVolume.thumbTintList = accentList

        // Buttons
        val btnBgList = ColorStateList.valueOf(skin.buttonBg)
        setButtonTheme(binding.btnSkinPicker, btnBgList, skin.accentColor)
        setButtonTheme(binding.btnEqualizer, btnBgList, skin.accentColor)
        setButtonTheme(binding.btnOpenLibraryTop, btnBgList, skin.accentColor)
        setButtonTheme(binding.btnOpenLibraryBottom, btnBgList, skin.accentColor)

        setButtonTheme(binding.btnPrev, btnBgList, skin.accentColor)
        setButtonTheme(binding.btnPlay, btnBgList, skin.accentColor)
        setButtonTheme(binding.btnStop, btnBgList, skin.accentColor)
        setButtonTheme(binding.btnNext, btnBgList, skin.accentColor)

        val isShuf = musicService?.isShuffleMode() == true
        setButtonTheme(binding.btnShuffle, btnBgList, if (isShuf) skin.accentColor else skin.textSecondary)

        val isRep = musicService?.isRepeatMode() == true
        setButtonTheme(binding.btnRepeat, btnBgList, if (isRep) skin.accentColor else skin.textSecondary)

        setButtonTheme(binding.btnRewind10, btnBgList, skin.accentColor)
        setButtonTheme(binding.btnForward10, btnBgList, skin.accentColor)

        setButtonTheme(binding.btnBackToPlayer, btnBgList, skin.accentColor)
        setButtonTheme(binding.btnScanStorage, btnBgList, skin.accentColor)

        updateFilterChipsUI()
        trackAdapter.setSkin(skin)
    }

    private fun setButtonTheme(btn: Button?, bgList: ColorStateList, textColor: Int) {
        btn?.let {
            it.backgroundTintList = bgList
            it.setTextColor(textColor)
        }
    }

    private fun showEqualizerDialog() {
        val eq = musicService?.getEqualizerEffect()
        val bb = musicService?.getBassBoostEffect()

        if (!isBound || eq == null) {
            Toast.makeText(this, "Equalizer initializing or not supported on this audio device", Toast.LENGTH_SHORT).show()
            return
        }

        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_equalizer, null)
        val dialog = AlertDialog.Builder(this).setView(dialogView).create()

        val bandsContainer = dialogView.findViewById<LinearLayout>(R.id.eqBandsContainer)
        val sbBassBoost = dialogView.findViewById<SeekBar>(R.id.sbBassBoost)
        val btnClose = dialogView.findViewById<Button>(R.id.btnEqClose)

        val numBands = eq.numberOfBands
        val minLevel = eq.bandLevelRange[0]
        val maxLevel = eq.bandLevelRange[1]

        for (i in 0 until numBands) {
            val bandIndex = i.toShort()
            val centerFreq = eq.getCenterFreq(bandIndex) / 1000

            val tvBand = TextView(this).apply {
                text = "$centerFreq Hz"
                setTextColor(currentSkin.textPrimary)
                textSize = 11f
            }
            bandsContainer.addView(tvBand)

            val sb = SeekBar(this).apply {
                max = (maxLevel - minLevel).toInt()
                progress = (eq.getBandLevel(bandIndex) - minLevel).toInt()
                progressTintList = ColorStateList.valueOf(currentSkin.accentColor)
                thumbTintList = ColorStateList.valueOf(currentSkin.accentColor)
                setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                        if (fromUser) {
                            eq.setBandLevel(bandIndex, (progress + minLevel).toShort())
                        }
                    }
                    override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                    override fun onStopTrackingTouch(seekBar: SeekBar?) {}
                })
            }
            bandsContainer.addView(sb)
        }

        if (bb != null) {
            sbBassBoost.progress = bb.roundedStrength.toInt()
            sbBassBoost.progressTintList = ColorStateList.valueOf(currentSkin.accentColor)
            sbBassBoost.thumbTintList = ColorStateList.valueOf(currentSkin.accentColor)

            sbBassBoost.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    if (fromUser) {
                        bb.setStrength(progress.toShort())
                    }
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            })
        }

        btnClose.backgroundTintList = ColorStateList.valueOf(currentSkin.buttonBg)
        btnClose.setTextColor(currentSkin.accentColor)
        btnClose.setOnClickListener { dialog.dismiss() }
        dialog.show()
    }

    override fun onDestroy() {
        super.onDestroy()
        stopProgressLoop()
        if (isBound) {
            unbindService(connection)
            isBound = false
        }
    }
}
