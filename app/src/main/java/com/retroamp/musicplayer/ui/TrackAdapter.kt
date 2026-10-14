package com.retroamp.musicplayer.ui

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.retroamp.musicplayer.R
import com.retroamp.musicplayer.model.Skin
import com.retroamp.musicplayer.model.Track

class TrackAdapter(
    private var tracks: List<Track>,
    private val onTrackClick: (Int) -> Unit
) : RecyclerView.Adapter<TrackAdapter.ViewHolder>() {

    private var currentTrackIndex = -1
    private var isPlaying = false
    private var skin: Skin = Skin.getClassicWinamp()

    fun updateTracks(newTracks: List<Track>) {
        this.tracks = newTracks
        notifyDataSetChanged()
    }

    fun setCurrentTrackIndex(index: Int, playing: Boolean = false) {
        this.currentTrackIndex = index
        this.isPlaying = playing
        notifyDataSetChanged()
    }

    fun setSkin(newSkin: Skin) {
        this.skin = newSkin
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_track, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val track = tracks[position]
        val isSelected = position == currentTrackIndex

        if (isSelected) {
            holder.tvIndex.text = if (isPlaying) "▶" else "●"
            holder.tvIndex.setTextColor(skin.accentColor)
            holder.tvTitle.setTextColor(skin.accentColor)
            holder.tvFormat.setTextColor(skin.accentColor)
            holder.tvDuration.setTextColor(skin.accentColor)
            holder.itemView.setBackgroundColor(skin.buttonBg)
        } else {
            holder.tvIndex.text = String.format("%02d.", position + 1)
            holder.tvIndex.setTextColor(skin.textSecondary)
            holder.tvTitle.setTextColor(skin.textPrimary)
            holder.tvFormat.setTextColor(skin.accentSecondary)
            holder.tvDuration.setTextColor(skin.textSecondary)
            holder.itemView.setBackgroundColor(Color.TRANSPARENT)
        }

        holder.tvTitle.text = track.cleanTitle
        holder.tvArtist.text = track.cleanArtist + if (track.album.isNotBlank()) " • ${track.album}" else ""
        holder.tvArtist.setTextColor(skin.textSecondary)
        holder.tvFormat.text = track.format
        holder.tvDuration.text = track.formattedDuration

        // Format badge background
        val pillBg = GradientDrawable().apply {
            cornerRadius = 12f
            setColor(if (isSelected) skin.surfaceHighlight else skin.surfaceColor)
            setStroke(2, if (isSelected) skin.accentColor else Color.argb(60, 255, 255, 255))
        }
        holder.tvFormat.background = pillBg

        holder.itemView.setOnClickListener {
            onTrackClick(position)
        }
    }

    override fun getItemCount(): Int = tracks.size

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvIndex: TextView = itemView.findViewById(R.id.tvTrackIndex)
        val tvTitle: TextView = itemView.findViewById(R.id.tvTrackTitle)
        val tvArtist: TextView = itemView.findViewById(R.id.tvTrackArtist)
        val tvFormat: TextView = itemView.findViewById(R.id.tvTrackFormat)
        val tvDuration: TextView = itemView.findViewById(R.id.tvTrackDuration)
    }
}
