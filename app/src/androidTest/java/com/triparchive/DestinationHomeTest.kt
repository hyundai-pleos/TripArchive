package com.triparchive

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import com.triparchive.core.designsystem.TripArchiveTheme
import com.triparchive.data.navigation.DestinationCategory
import com.triparchive.data.navigation.DestinationState
import com.triparchive.data.profile.Profile
import com.triparchive.data.profile.ProfileRelationship
import com.triparchive.presentation.home.DestinationHome
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import java.io.File

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
        compose.onNodeWithText("준님과 여행하고 있어요.").assertIsDisplayed()
        compose.onNodeWithText("목적지를 설정하면 기록이 시작됩니다").assertIsDisplayed()
        captureIfRequested("home-no-destination")
        compose.onNodeWithText("여행 마치기").performClick()
        compose.onNodeWithText("취소").performClick()
        compose.runOnIdle {
            state.value = DestinationState("안목해변", routing = true, category = DestinationCategory.TOURISM)
        }
        compose.onNodeWithText("안목해변").assertIsDisplayed()
        compose.onNodeWithText("목적지를 설정하면 기록이 시작됩니다").assertDoesNotExist()
        captureIfRequested("home-destination")
        compose.onNodeWithText("여행 마치기").performClick()
        compose.onNodeWithText("취소").performClick()
        compose.runOnIdle {
            assertEquals(0, finished)
            state.value = state.value.arrive("안목해변")
        }
        compose.onNodeWithText("안목해변에 도착했어요!").assertIsDisplayed()
        compose.onNodeWithText("도착 확인하고 계속하기").performClick()
        compose.onNodeWithText("안목해변에 도착했어요!").assertDoesNotExist()
        compose.runOnIdle { state.value = DestinationState("시장", routing = true, category = DestinationCategory.MART) }
        compose.onNodeWithText("시장").assertIsDisplayed()
        compose.runOnIdle { state.value = state.value.routeChanged(false) }
        compose.onNodeWithText("목적지를 설정하면 기록이 시작됩니다").assertIsDisplayed()
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
                    MutableStateFlow(DestinationState("시장", routing = true, category = DestinationCategory.MART)),
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
        compose.runOnIdle { state.value = DestinationState("시장", routing = true, category = DestinationCategory.MART) }
        compose.onNodeWithText("도착 재현").performClick()
        compose.onNodeWithText("시장에 도착했어요!").assertIsDisplayed()
        // A real SDK refresh/change must not interrupt the simulated arrival screen.
        compose.runOnIdle {
            state.value = DestinationState("해변", routing = true, category = DestinationCategory.TOURISM)
        }
        compose.onNodeWithText("시장에 도착했어요!").assertIsDisplayed()
        compose.onNodeWithText("도착 확인하고 계속하기").performClick()
        compose.onNodeWithText("해변").assertIsDisplayed()
        compose.onNodeWithText("도착 재현").performClick()
        compose.onNodeWithText("해변에 도착했어요!").assertIsDisplayed()
        compose.onNodeWithText("도착 확인하고 계속하기").performClick()
        compose.onNodeWithText("해변").assertIsDisplayed()
        compose.onNodeWithText("도착 재현").performClick()
        compose.onNodeWithText("여기서 여행 마치기").performClick()
        compose.onNodeWithText("취소").performClick()
        compose.onNodeWithText("해변에 도착했어요!").assertIsDisplayed()
        compose.onNodeWithText("여기서 여행 마치기").performClick()
        compose.onNodeWithText("마치기").performClick()
        compose.runOnIdle {
            assertEquals(1, finished)
            assertEquals(DestinationState("해변", routing = true, category = DestinationCategory.TOURISM), state.value)
        }
        compose.onNode(isDialog()).assertDoesNotExist()
        compose.runOnIdle { state.value = state.value.arrive("해변") }
        compose.onNode(isDialog()).assertDoesNotExist()
        compose.runOnIdle { assertEquals(1, finished) }
    }

    @Test
    fun arrivalDialogCountsDownAndPausesDuringFinishConfirmation() {
        val state = MutableStateFlow(DestinationState("안목해변", routing = true, category = DestinationCategory.TOURISM))
        compose.setContent {
            TripArchiveTheme {
                DestinationHome(Profile(1, "준", ProfileRelationship.PARENT), {}, {}, state)
            }
        }
        compose.runOnIdle { state.value = state.value.arrive("안목해변") }
        compose.onNodeWithText("안목해변에 도착했어요!").assertIsDisplayed()
        compose.onNodeWithText("5").assertIsDisplayed()
        captureIfRequested("arrival-dialog", dialog = true)
        compose.mainClock.advanceTimeBy(2000)
        compose.onNodeWithText("3").assertIsDisplayed()
        compose.onNodeWithText("여기서 여행 마치기").performClick()
        compose.mainClock.advanceTimeBy(6000)
        compose.onNodeWithText("정말 여행을 마칠까요?").assertIsDisplayed()
        compose.onNodeWithText("취소").performClick()
        compose.onNodeWithText("안목해변에 도착했어요!").assertIsDisplayed()
        compose.onNodeWithText("3").assertIsDisplayed()
        compose.mainClock.advanceTimeBy(3100)
        compose.onNodeWithText("안목해변에 도착했어요!").assertDoesNotExist()
        compose.onNodeWithText("준님과 여행하고 있어요.").assertIsDisplayed()
        compose.mainClock.advanceTimeBy(6000)
        compose.onNodeWithText("안목해변에 도착했어요!").assertDoesNotExist()
    }

    @Test
    fun homeIsDimmedWhileArrivalOrFinishDialogIsVisible() {
        val state = MutableStateFlow(DestinationState("안목해변", routing = true, category = DestinationCategory.TOURISM))
        compose.setContent {
            TripArchiveTheme {
                DestinationHome(Profile(1, "준", ProfileRelationship.PARENT), {}, {}, state)
            }
        }

        fun assertBackground(expected: Float) {
            val pixels = compose.onNodeWithTag("home-surface").captureToImage().toPixelMap()
            val color = pixels[pixels.width / 2, pixels.height / 10]
            assertEquals(expected, color.red, 0.02f)
            assertEquals(expected, color.green, 0.02f)
            assertEquals(expected, color.blue, 0.02f)
        }
        assertBackground(1f)
        compose.runOnIdle { state.value = state.value.arrive("안목해변") }
        compose.onNodeWithText("안목해변에 도착했어요!").assertIsDisplayed()
        assertBackground(0.8f)
        compose.onNodeWithText("여기서 여행 마치기").performClick()
        assertBackground(0.8f)
        compose.onNodeWithText("취소").performClick()
        assertBackground(0.8f)
        compose.onNodeWithText("도착 확인하고 계속하기").performClick()
        assertBackground(1f)
    }

    @Test
    fun finishingFromArrivalClearsDialogsAndIgnoresLateSdkEvents() {
        val state = MutableStateFlow(DestinationState())
        var finished = 0
        compose.setContent {
            TripArchiveTheme {
                // Keep the outgoing screen composed, as it is during a navigation transition.
                DestinationHome(Profile(1, "준", ProfileRelationship.PARENT), {}, { finished++ }, state)
            }
        }
        compose.runOnIdle { state.value = state.value.arrive("안목해변") }
        compose.onNodeWithText("여기서 여행 마치기").performClick()
        compose.onNodeWithText("마치기").performClick()
        compose.onNode(isDialog()).assertDoesNotExist()
        compose.runOnIdle {
            assertEquals(1, finished)
            state.value = DestinationState("시장", arrived = "시장", routing = true)
        }
        compose.mainClock.advanceTimeBy(6000)
        compose.onNode(isDialog()).assertDoesNotExist()
        val pixels = compose.onNodeWithTag("home-surface").captureToImage().toPixelMap()
        assertEquals(1f, pixels[pixels.width / 2, pixels.height / 10].red, 0.02f)
        compose.runOnIdle { assertEquals(1, finished) }
    }

    @Test
    fun arrivalCategoryDoesNotFallBackToTheNextActiveDestination() {
        val state =
            MutableStateFlow(
                DestinationState(
                    name = "강릉아산병원",
                    arrived = "성심당 DCC점",
                    routing = true,
                    category = DestinationCategory.HOSPITAL,
                    arrivedCategory = null,
                ),
            )
        var renderedCategory: DestinationCategory? = DestinationCategory.MART
        compose.setContent {
            TripArchiveTheme {
                DestinationHome(
                    profile = Profile(1, "준", ProfileRelationship.PARENT),
                    onProfileClick = {},
                    onFinish = {},
                    source = state,
                    renderCharacter = false,
                    onDestinationCategoryChange = { renderedCategory = it },
                )
            }
        }
        compose.waitForIdle()
        compose.runOnIdle { assertNull(renderedCategory) }
        compose.onNodeWithText("병원").assertDoesNotExist()

        compose.runOnIdle {
            state.value = DestinationState("강릉아산병원", routing = true, category = DestinationCategory.HOSPITAL)
        }
        compose.runOnIdle { assertEquals(DestinationCategory.HOSPITAL, renderedCategory) }
        compose.onNodeWithText("병원").assertIsDisplayed()
    }

    private fun captureIfRequested(
        name: String,
        dialog: Boolean = false,
    ) {
        if (InstrumentationRegistry.getArguments().getString("captureScreenshots") != "true") return
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val bitmap = (if (dialog) compose.onNode(isDialog()) else compose.onRoot()).captureToImage().asAndroidBitmap()
        File(context.cacheDir, "$name.png").outputStream().use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }
}
