package com.triparchive.presentation.map

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import com.triparchive.domain.map.VisitIntensity

internal object VisitMapPalette {
    val unvisited = Color.White
    val boundary = Color(0xFF8B95A1)
    val districtBoundary = Color(0xFFC1C9D3)
    val emphasizedBorder = Color(0xFF26364A)
    val emphasizedBorderHalo = Color.White
    val text = Color(0xFF191F28)
    val secondaryText = Color(0xFF6B7684)
    val accent = Color(0xFFFF66AF)

    fun fill(intensity: VisitIntensity): Color {
        val fraction = (intensity.ordinal + 1) / 5f
        return accent.copy(alpha = fraction).compositeOver(unvisited)
    }
}
