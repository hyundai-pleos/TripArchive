package com.triparchive

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import com.triparchive.domain.map.VisitIntensity
import com.triparchive.presentation.map.BoundaryCanvas
import com.triparchive.presentation.map.MapRenderData
import com.triparchive.presentation.map.MapViewport
import com.triparchive.presentation.map.RenderedBoundary
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class VisitMapColorTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun changingColorRepaintsVisitedAreaWithoutChangingVisits() {
        var color by mutableStateOf(Color.Red)
        val path = Path().apply { addRect(Rect(0f, 0f, 100f, 100f)) }
        val paths = MapRenderData(provinces = listOf(RenderedBoundary("11", path)), districts = emptyList())
        val visits = mapOf("11" to VisitIntensity.FIFTH)
        compose.setContent {
            BoundaryCanvas(
                paths = paths,
                visitIntensities = visits,
                viewport = { MapViewport() },
                onAction = {},
                visitColor = color,
                modifier = Modifier.size(150.dp).testTag("visit-map-color-test"),
            )
        }

        assertEquals(Color.Red, compose.onNodeWithTag("visit-map-color-test").captureToImage().toPixelMap()[50, 50])
        compose.runOnIdle { color = Color.Blue }
        assertEquals(Color.Blue, compose.onNodeWithTag("visit-map-color-test").captureToImage().toPixelMap()[50, 50])
    }
}
