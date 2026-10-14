package com.retroamp.musicplayer.utils

import android.content.ContentUris
import android.content.Context
import android.database.Cursor
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.retroamp.musicplayer.model.Track
import java.io.File
import java.io.FileOutputStream

object MusicScanner {

    private val SUPPORTED_EXTENSIONS = arrayOf(
        ".mp3", ".wav", ".flac", ".m4a", ".aac", ".ogg", ".opus", ".wma"
    )

    fun scanDeviceTracks(context: Context): List<Track> {
        val trackList = mutableListOf<Track>()
        val seenPaths = mutableSetOf<String>()

        // 1. Scan from MediaStore with audio criteria
        scanFromMediaStore(context, trackList, seenPaths)

        // 2. Direct filesystem scan for folders (e.g. /sdcard/Music, /sdcard/Download)
        scanCommonDirectories(trackList, seenPaths)

        // 3. If storage is empty (e.g. fresh emulator or phone without local audio yet),
        // extract built-in demo tracks so the user can immediately play music and see visualizers!
        if (trackList.isEmpty()) {
            extractDemoTracks(context, trackList, seenPaths)
        }

        return trackList
    }

    private fun scanFromMediaStore(
        context: Context,
        trackList: MutableList<Track>,
        seenPaths: MutableSet<String>
    ) {
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        }

        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.DATA
        )

        val selection = "(" +
                MediaStore.Audio.Media.MIME_TYPE + " LIKE 'audio/%' OR " +
                MediaStore.Audio.Media.IS_MUSIC + " != 0 OR " +
                MediaStore.Audio.Media.DATA + " LIKE '%.mp3' OR " +
                MediaStore.Audio.Media.DATA + " LIKE '%.wav' OR " +
                MediaStore.Audio.Media.DATA + " LIKE '%.flac' OR " +
                MediaStore.Audio.Media.DATA + " LIKE '%.m4a' OR " +
                MediaStore.Audio.Media.DATA + " LIKE '%.aac' OR " +
                MediaStore.Audio.Media.DATA + " LIKE '%.ogg' OR " +
                MediaStore.Audio.Media.DATA + " LIKE '%.opus')"

        val sortOrder = MediaStore.Audio.Media.TITLE + " ASC"

        try {
            context.contentResolver.query(
                collection,
                projection,
                selection,
                null,
                sortOrder
            )?.use { cursor ->
                val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val titleColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                val artistColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                val albumColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
                val durationColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                val dataColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idColumn)
                    val title = cursor.getString(titleColumn) ?: "Unknown Title"
                    val artist = cursor.getString(artistColumn) ?: "Unknown Artist"
                    val album = cursor.getString(albumColumn) ?: "Unknown Album"
                    val duration = cursor.getLong(durationColumn)
                    val path = cursor.getString(dataColumn)

                    if (path != null && !seenPaths.contains(path)) {
                        seenPaths.add(path)
                        val contentUri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id)
                        trackList.add(Track(id, title, artist, album, duration, path, contentUri))
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun scanCommonDirectories(
        trackList: MutableList<Track>,
        seenPaths: MutableSet<String>
    ) {
        try {
            val externalDir = Environment.getExternalStorageDirectory()
            if (externalDir != null && externalDir.exists()) {
                scanDirectoryRecursive(File(externalDir, "Music"), trackList, seenPaths, 0)
                scanDirectoryRecursive(File(externalDir, "Download"), trackList, seenPaths, 0)
                scanDirectoryRecursive(File(externalDir, "Podcasts"), trackList, seenPaths, 0)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun scanDirectoryRecursive(
        dir: File?,
        trackList: MutableList<Track>,
        seenPaths: MutableSet<String>,
        depth: Int
    ) {
        if (dir == null || !dir.exists() || !dir.isDirectory || depth > 4) return
        val files = dir.listFiles() ?: return

        for (file in files) {
            if (file.isDirectory) {
                if (!file.name.startsWith(".")) {
                    scanDirectoryRecursive(file, trackList, seenPaths, depth + 1)
                }
            } else if (file.isFile) {
                val path = file.absolutePath
                if (isAudioFile(path) && !seenPaths.contains(path)) {
                    seenPaths.add(path)
                    createTrackFromFile(file)?.let { trackList.add(it) }
                }
            }
        }
    }

    private fun isAudioFile(path: String): Boolean {
        val lower = path.lowercase()
        return SUPPORTED_EXTENSIONS.any { lower.endsWith(it) }
    }

    private fun createTrackFromFile(file: File): Track? {
        val path = file.absolutePath
        var title = file.nameWithoutExtension
        var artist = "Unknown Artist"
        var album = "Unknown Album"
        var duration = 0L

        var mmr: MediaMetadataRetriever? = null
        try {
            mmr = MediaMetadataRetriever()
            mmr.setDataSource(path)
            mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)?.let { if (it.isNotBlank()) title = it }
            mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)?.let { if (it.isNotBlank()) artist = it }
            mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)?.let { if (it.isNotBlank()) album = it }
            mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()?.let { duration = it }
        } catch (ignored: Exception) {
        } finally {
            try { mmr?.release() } catch (ignored: Exception) {}
        }

        val uri = Uri.fromFile(file)
        return Track(file.hashCode().toLong(), title, artist, album, duration, path, uri)
    }

    private fun extractDemoTracks(
        context: Context,
        trackList: MutableList<Track>,
        seenPaths: MutableSet<String>
    ) {
        val demoFiles = arrayOf("test_synthwave.mp3", "test_lofi.mp3")
        val demoDir = File(context.filesDir, "demo_tracks").apply { mkdirs() }

        for (demoName in demoFiles) {
            try {
                val destFile = File(demoDir, demoName)
                if (!destFile.exists()) {
                    context.assets.open(demoName).use { input ->
                        FileOutputStream(destFile).use { output ->
                            input.copyTo(output)
                        }
                    }
                }
                if (destFile.exists() && !seenPaths.contains(destFile.absolutePath)) {
                    seenPaths.add(destFile.absolutePath)
                    createTrackFromFile(destFile)?.let { trackList.add(it) }
                }
            } catch (e: Exception) {
                // If demo asset is missing, ignore
            }
        }
    }
}

