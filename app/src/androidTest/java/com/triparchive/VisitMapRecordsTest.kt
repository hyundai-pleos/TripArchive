package com.triparchive

import androidx.activity.ComponentActivity
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.triparchive.core.designsystem.TripArchiveTheme
import com.triparchive.presentation.map.MapViewport
import com.triparchive.presentation.map.VisitMapContent
import com.triparchive.presentation.map.VisitMapScreen
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class VisitMapRecordsTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun recordsCanBeOpenedWhileMapLoadsOrFails() {
        val content = mutableStateOf<VisitMapContent>(VisitMapContent.Loading)
        var clicks = 0
        compose.setContent {
            TripArchiveTheme {
                VisitMapScreen(
                    content = content.value,
                    viewport = { MapViewport() },
                    onAction = {},
                    onBack = {},
                    onRecordsClick = { clicks++ },
                )
            }
        }
        compose.onNodeWithText("기록 보기").assertIsDisplayed().performClick()
        compose.runOnIdle { content.value = VisitMapContent.Error(secondsUntilRetry = 5) }
        compose.onNodeWithText("기록 보기").assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(2, clicks) }
    }
}
