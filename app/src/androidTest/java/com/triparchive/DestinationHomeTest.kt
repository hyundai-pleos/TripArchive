package com.triparchive

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.triparchive.core.designsystem.TripArchiveTheme
import com.triparchive.data.navigation.DestinationState
import com.triparchive.data.profile.Profile
import com.triparchive.data.profile.ProfileRelationship
import com.triparchive.presentation.home.DestinationHome
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test

class DestinationHomeTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun destinationArrivalAndFinish() {
        val state = MutableStateFlow(DestinationState())
        var finished = 0
        compose.setContent {
            TripArchiveTheme {
                DestinationHome(Profile(1, "준", ProfileRelationship.PARENT), {}, { finished++ }, state)
            }
        }
        compose.onNodeWithText("여행 마치기").assertDoesNotExist()
        compose.runOnIdle { state.value = DestinationState("안목해변", routing = true) }
        compose.onNodeWithText("안목해변").assertIsDisplayed()
        compose.onNodeWithText("여행 마치기").performClick()
        compose.onNodeWithText("취소").performClick()
        compose.runOnIdle {
            assertEquals(0, finished)
            state.value = state.value.arrive("안목해변")
        }
        compose.onNodeWithText("안목해변에\n도착했어요!").assertIsDisplayed()
        compose.onNodeWithText("도착 확인하고 계속하기").performClick()
        compose.onNodeWithText("안목해변에\n도착했어요!").assertDoesNotExist()
        compose.runOnIdle { state.value = DestinationState("시장", routing = true) }
        compose.onNodeWithText("여행 마치기").performClick()
        compose.onNodeWithText("마치기").performClick()
        compose.runOnIdle { assertEquals(1, finished) }
    }

    @Test
    fun normalBuildDoesNotExposeDemoControls() {
        assumeTrue(!BuildConfig.ARRIVAL_DEMO)
        compose.setContent {
            TripArchiveTheme {
                DestinationHome(
                    Profile(1, "준", ProfileRelationship.PARENT),
                    {},
                    {},
                    MutableStateFlow(DestinationState("시장", routing = true)),
                )
            }
        }
        compose.onNodeWithText("도착 재현").assertDoesNotExist()
    }

    @Test
    fun demoArrivalPreservesSdkStateAndSupportsResetAndFinish() {
        assumeTrue(BuildConfig.ARRIVAL_DEMO)
        val state = MutableStateFlow(DestinationState())
        var finished = 0
        compose.setContent {
            TripArchiveTheme {
                DestinationHome(Profile(1, "준", ProfileRelationship.PARENT), {}, { finished++ }, state)
            }
        }
        compose.onNodeWithText("도착 재현").assertIsNotEnabled()
        compose.runOnIdle { state.value = DestinationState("시장", routing = true) }
        compose.onNodeWithText("도착 재현").performClick()
        compose.onNodeWithText("시장에\n도착했어요!").assertIsDisplayed()
        // A real SDK refresh/change must not interrupt the simulated arrival screen.
        compose.runOnIdle { state.value = DestinationState("해변", routing = true) }
        compose.onNodeWithText("시장에\n도착했어요!").assertIsDisplayed()
        compose.onNodeWithText("재현 초기화").performClick()
        compose.onNodeWithText("해변").assertIsDisplayed()
        compose.onNodeWithText("도착 재현").performClick()
        compose.onNodeWithText("해변에\n도착했어요!").assertIsDisplayed()
        compose.onNodeWithText("도착 확인하고 계속하기").performClick()
        compose.onNodeWithText("해변").assertIsDisplayed()
        compose.onNodeWithText("도착 재현").performClick()
        compose.onNodeWithText("여기서 여행 마치기").performClick()
        compose.onNodeWithText("취소").performClick()
        compose.onNodeWithText("해변에\n도착했어요!").assertIsDisplayed()
        compose.onNodeWithText("여기서 여행 마치기").performClick()
        compose.onNodeWithText("마치기").performClick()
        compose.runOnIdle {
            assertEquals(1, finished)
            assertEquals(DestinationState("해변", routing = true), state.value)
        }
    }
}
