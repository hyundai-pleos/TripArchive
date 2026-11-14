package com.triparchive.feature.map

import androidx.compose.ui.graphics.Color
import com.triparchive.data.profile.ProfileMapColor
import com.triparchive.domain.map.VisitIntensity
import com.triparchive.presentation.map.VisitMapPalette
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VisitMapPaletteTest {
    @Test
    fun eachSwatchReachesTheSelectedColorAtTheFifthVisit() {
        ProfileMapColor.entries.forEach { color ->
            assertEquals(Color(color.argb), VisitMapPalette.fill(VisitIntensity.FIFTH, Color(color.argb)))
        }
    }

    @Test
    fun lowerVisitLevelsBlendTheChosenColorWithWhite() {
        val first = VisitMapPalette.fill(VisitIntensity.FIRST, Color.Blue)
        val third = VisitMapPalette.fill(VisitIntensity.THIRD, Color.Blue)
        assertTrue(first.red > third.red)
        assertTrue(third.red > 0f)
        assertEquals(1f, first.blue, 0.001f)
        assertEquals(1f, first.alpha, 0.001f)
    }
}
