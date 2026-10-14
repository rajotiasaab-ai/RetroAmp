package com.retroamp.musicplayer.model

import android.net.Uri
import java.io.Serializable

data class Track(
    val id: Long,
    val title: String,
    val artist: String,
    val album: String,
    var duration: Long,
    val path: String?,
    val uri: Uri?
) : Serializable {

    val cleanTitle: String
        get() = if (title.isNotBlank()) title else "Unknown Title"

    val cleanArtist: String
        get() = if (artist.isNotBlank()) artist else "Unknown Artist"

    val cleanAlbum: String
        get() = if (album.isNotBlank()) album else "Unknown Album"

    val format: String
        get() {
            if (path != null) {
                val dot = path.lastIndexOf('.')
                if (dot in 0 until path.length - 1) {
                    return path.substring(dot + 1).uppercase()
                }
            }
            return "AUDIO"
        }

    val formattedDuration: String
        get() {
            if (duration <= 0) return "--:--"
            val seconds = duration / 1000
            val mins = seconds / 60
            val secs = seconds % 60
            return String.format("%02d:%02d", mins, secs)
        }
}

