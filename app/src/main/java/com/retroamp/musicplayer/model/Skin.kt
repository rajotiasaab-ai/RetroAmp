package com.retroamp.musicplayer.model

import android.graphics.Color

data class Skin(
    val id: String,
    val name: String,
    val era: String,
    val bgColor: Int,
    val surfaceColor: Int,
    val surfaceHighlight: Int,
    val lcdBgColor: Int,
    val lcdTextColor: Int,
    val accentColor: Int,
    val accentSecondary: Int,
    val textPrimary: Int,
    val textSecondary: Int,
    val buttonBg: Int,
    val buttonBorder: Int,
    val spectrumColors: IntArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as Skin
        return id == other.id
    }

    override fun hashCode(): Int = id.hashCode()

    companion object {
        fun getCyberpunkNeon() = Skin(
            id = "CYBERPUNK_NEON",
            name = "Cyberpunk Neon",
            era = "2088",
            bgColor = Color.parseColor("#090A10"),
            surfaceColor = Color.parseColor("#121422"),
            surfaceHighlight = Color.parseColor("#212338"),
            lcdBgColor = Color.parseColor("#080514"),
            lcdTextColor = Color.parseColor("#00F0FF"),
            accentColor = Color.parseColor("#FF007F"),
            accentSecondary = Color.parseColor("#00F0FF"),
            textPrimary = Color.parseColor("#FFFFFF"),
            textSecondary = Color.parseColor("#A5A9C5"),
            buttonBg = Color.parseColor("#1B1A32"),
            buttonBorder = Color.parseColor("#FF007F"),
            spectrumColors = intArrayOf(
                Color.parseColor("#FF007F"),
                Color.parseColor("#D900FF"),
                Color.parseColor("#7928CA"),
                Color.parseColor("#00B4D8"),
                Color.parseColor("#00F0FF"),
                Color.parseColor("#FFE600")
            )
        )

        fun getClassicWinamp() = Skin(
            id = "WINAMP_CLASSIC",
            name = "Emerald RetroAmp",
            era = "1997",
            bgColor = Color.parseColor("#111317"),
            surfaceColor = Color.parseColor("#1E2028"),
            surfaceHighlight = Color.parseColor("#2E313D"),
            lcdBgColor = Color.parseColor("#021405"),
            lcdTextColor = Color.parseColor("#00FF66"),
            accentColor = Color.parseColor("#00FF66"),
            accentSecondary = Color.parseColor("#FFD700"),
            textPrimary = Color.parseColor("#00FF66"),
            textSecondary = Color.parseColor("#8E95A5"),
            buttonBg = Color.parseColor("#2A2D3A"),
            buttonBorder = Color.parseColor("#00FF66"),
            spectrumColors = intArrayOf(
                Color.parseColor("#00FF66"),
                Color.parseColor("#44FF00"),
                Color.parseColor("#AAFF00"),
                Color.parseColor("#FFEA00"),
                Color.parseColor("#FF7700"),
                Color.parseColor("#FF2200")
            )
        )

        fun getSynthwave() = Skin(
            id = "SYNTHWAVE",
            name = "Synthwave Sunset",
            era = "1984",
            bgColor = Color.parseColor("#0F031E"),
            surfaceColor = Color.parseColor("#1A082E"),
            surfaceHighlight = Color.parseColor("#2E1052"),
            lcdBgColor = Color.parseColor("#130024"),
            lcdTextColor = Color.parseColor("#FF2A85"),
            accentColor = Color.parseColor("#FF7700"),
            accentSecondary = Color.parseColor("#00FFF0"),
            textPrimary = Color.parseColor("#FF2A85"),
            textSecondary = Color.parseColor("#B388FF"),
            buttonBg = Color.parseColor("#2D124D"),
            buttonBorder = Color.parseColor("#FF7700"),
            spectrumColors = intArrayOf(
                Color.parseColor("#9B5DE5"),
                Color.parseColor("#F15BB5"),
                Color.parseColor("#FF2A85"),
                Color.parseColor("#FEE440"),
                Color.parseColor("#FF7700"),
                Color.parseColor("#00FFF0")
            )
        )

        fun getRetroHiFi() = Skin(
            id = "RETRO_HIFI",
            name = "Vintage Golden Hi-Fi",
            era = "1975",
            bgColor = Color.parseColor("#140E0A"),
            surfaceColor = Color.parseColor("#221812"),
            surfaceHighlight = Color.parseColor("#38271E"),
            lcdBgColor = Color.parseColor("#1C1005"),
            lcdTextColor = Color.parseColor("#FFB703"),
            accentColor = Color.parseColor("#FB8500"),
            accentSecondary = Color.parseColor("#FFE8D6"),
            textPrimary = Color.parseColor("#FFD166"),
            textSecondary = Color.parseColor("#C9ADA7"),
            buttonBg = Color.parseColor("#35251C"),
            buttonBorder = Color.parseColor("#FB8500"),
            spectrumColors = intArrayOf(
                Color.parseColor("#8A5A36"),
                Color.parseColor("#A66B38"),
                Color.parseColor("#C67D3B"),
                Color.parseColor("#E09F3E"),
                Color.parseColor("#FFB703"),
                Color.parseColor("#FFD166")
            )
        )

        fun getSolarizedCrimson() = Skin(
            id = "SOLARIZED_CRIMSON",
            name = "Royal Crimson & Cobalt",
            era = "2010",
            bgColor = Color.parseColor("#080D1A"),
            surfaceColor = Color.parseColor("#111A2E"),
            surfaceHighlight = Color.parseColor("#1C2A4A"),
            lcdBgColor = Color.parseColor("#0A1224"),
            lcdTextColor = Color.parseColor("#00D2FF"),
            accentColor = Color.parseColor("#FF1744"),
            accentSecondary = Color.parseColor("#00D2FF"),
            textPrimary = Color.parseColor("#F8F9FA"),
            textSecondary = Color.parseColor("#8D99AE"),
            buttonBg = Color.parseColor("#182642"),
            buttonBorder = Color.parseColor("#3A86FF"),
            spectrumColors = intArrayOf(
                Color.parseColor("#00D2FF"),
                Color.parseColor("#3A86FF"),
                Color.parseColor("#7209B7"),
                Color.parseColor("#F72585"),
                Color.parseColor("#FF1744")
            )
        )

        fun getMidnightOled() = Skin(
            id = "MIDNIGHT_OLED",
            name = "Midnight OLED",
            era = "2026",
            bgColor = Color.parseColor("#000000"),
            surfaceColor = Color.parseColor("#0E0E12"),
            surfaceHighlight = Color.parseColor("#1A1A22"),
            lcdBgColor = Color.parseColor("#050508"),
            lcdTextColor = Color.parseColor("#818CF8"),
            accentColor = Color.parseColor("#6366F1"),
            accentSecondary = Color.parseColor("#A5B4FC"),
            textPrimary = Color.parseColor("#FFFFFF"),
            textSecondary = Color.parseColor("#94A3B8"),
            buttonBg = Color.parseColor("#191924"),
            buttonBorder = Color.parseColor("#6366F1"),
            spectrumColors = intArrayOf(
                Color.parseColor("#4338CA"),
                Color.parseColor("#6366F1"),
                Color.parseColor("#818CF8"),
                Color.parseColor("#A5B4FC"),
                Color.parseColor("#C7D2FE")
            )
        )

        fun getAllSkins() = listOf(
            getCyberpunkNeon(),
            getClassicWinamp(),
            getSynthwave(),
            getRetroHiFi(),
            getSolarizedCrimson(),
            getMidnightOled()
        )
    }
}
