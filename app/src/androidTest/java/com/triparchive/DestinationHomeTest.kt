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
import com.triparchive.data.profile.Profile
import com.triparchive.data.profile.ProfileRelationship
import com.triparchive.domain.navigation.CategoryLookupStatus
import com.triparchive.domain.navigation.Destination
import com.triparchive.domain.navigation.DestinationCategory
import com.triparchive.domain.navigation.DestinationState
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
                DestinationHome(
                    Profile(1, "준", ProfileRelationship.PARENT),
                    {},
                    { finished++ },
                    state,
                    renderCharacter = false,
                )
            }
        }
        compose.onNodeWithText("준님과 여행하고 있어요.").assertIsDisplayed()
        compose.onNodeWithText("목적지를 설정하면 기록이 시작됩니다").assertIsDisplayed()
        captureIfRequested("home-no-destination")
        compose.onNodeWithText("여행 마치기").performClick()
        compose.onNodeWithText("취소").performClick()
        compose.runOnIdle { state.value = DestinationTestFixtures.active("안목해변") }
        compose.onNodeWithText("안목해변").assertIsDisplayed()
        compose.onNodeWithText("목적지를 설정하면 기록이 시작됩니다").assertDoesNotExist()
        captureIfRequested("home-destination")
        compose.onNodeWithText("여행 마치기").performClick()
        compose.onNodeWithText("취소").performClick()
        compose.runOnIdle {
            assertEquals(0, finished)
            state.value =
                state.value.copy(
                    pendingArrival = state.value.current ?: DestinationTestFixtures.place("안목해변"),
                    current = null,
                    routing = false,
                )
        }
        compose.onNodeWithText("안목해변에 도착했어요!").assertIsDisplayed()
        compose.onNodeWithText("도착 확인하고 계속하기").performClick()
        compose.onNodeWithText("안목해변에 도착했어요!").assertDoesNotExist()
        compose.runOnIdle { state.value = DestinationTestFixtures.active("시장", id = 2) }
        compose.onNodeWithText("시장").assertIsDisplayed()
        compose.runOnIdle { state.value = state.value.copy(current = null, routing = false) }
        compose.onNodeWithText("목적지를 설정하면 기록이 시작됩니다").assertIsDisplayed()
        compose.onNodeWithText("여행 마치기").performClick()
        compose.onNodeWithText("마치기").performClick()
        compose.runOnIdle { assertEquals(1, finished) }
    }

    @Test
    fun categoryLookupShowsLoadingSuccessAndFailure() {
        val state =
            MutableStateFlow(DestinationTestFixtures.active("서울역", categoryStatus = CategoryLookupStatus.LOADING))
        compose.setContent {
            TripArchiveTheme {
                DestinationHome(Profile(1, "준", ProfileRelationship.PARENT), {}, {}, state, renderCharacter = false)
            }
        }
        compose.onNodeWithText("[임시] 카테고리 조회 중").assertIsDisplayed()
        compose.runOnIdle {
            state.value =
                state.value.copy(
                    current =
                        state.value.current!!.copy(
                            category = DestinationCategory.FOOD,
                            categoryStatus = CategoryLookupStatus.SUCCESS,
                        ),
                )
        }
        compose.onNodeWithText("[임시] 카테고리 조회 성공").assertIsDisplayed()
        compose.onNodeWithText("분류: 음식점").assertIsDisplayed()
        compose.runOnIdle {
            state.value =
                state.value.copy(
                    current =
                        state.value.current!!.copy(
                            categoryStatus = CategoryLookupStatus.FAILURE,
                            categoryFailure = "카카오 API 응답 오류 (401)",
                        ),
                )
        }
        compose.onNodeWithText("[임시] 카테고리 조회 실패 (무작위 배정)").assertIsDisplayed()
        compose.onNodeWithText("카카오 API 응답 오류 (401)").assertIsDisplayed()
    }

    @Test
    fun automaticArrivalUsesLatestCategoryWithoutRestartingCountdown() {
        val state = MutableStateFlow(DestinationTestFixtures.arrived("시장", category = DestinationCategory.MART))
        val recorded = mutableListOf<DestinationCategory?>()
        compose.setContent {
            TripArchiveTheme {
                DestinationHome(
                    Profile(1, "준", ProfileRelationship.PARENT),
                    {},
                    {},
                    state,
                    renderCharacter = false,
                    onArrival = { recorded.add(it.category) },
                )
            }
        }
        compose.onNodeWithText("5").assertIsDisplayed()
        compose.mainClock.advanceTimeBy(2000)
        compose.onNodeWithText("3").assertIsDisplayed()
        compose.runOnIdle {
            state.value =
                state.value.copy(
                    pendingArrival =
                        state.value.pendingArrival!!.copy(
                            category = DestinationCategory.FOOD,
                            categoryStatus = CategoryLookupStatus.SUCCESS,
                        ),
                )
        }
        compose.mainClock.advanceTimeBy(3100)
        compose.runOnIdle { assertEquals(listOf(DestinationCategory.FOOD), recorded) }
    }

    @Test
    fun uiDoesNotAssignCategoryWhileLookupIsPending() {
        val state =
            MutableStateFlow(DestinationTestFixtures.arrived("시장", categoryStatus = CategoryLookupStatus.LOADING))
        val recorded = mutableListOf<DestinationCategory?>()
        compose.setContent {
            TripArchiveTheme {
                DestinationHome(
                    Profile(1, "준", ProfileRelationship.PARENT),
                    {},
                    {},
                    state,
                    renderCharacter = false,
                    onArrival = { recorded.add(it.category) },
                )
            }
        }
        compose.onNodeWithText("도착 확인하고 계속하기").performClick()
        compose.runOnIdle { assertEquals(listOf<DestinationCategory?>(null), recorded) }
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
                    MutableStateFlow(DestinationTestFixtures.active("시장", id = 2)),
                    renderCharacter = false,
                )
            }
        }
        compose.onNodeWithText("도착 재현").assertDoesNotExist()
    }

    @Test
    fun demoArrivalPreservesSdkStateAndSupportsResetAndFinish() {
        assumeTrue(BuildConfig.ARRIVAL_DEMO)
        val state = MutableStateFlow(DestinationState())
        val source = FakeDestinationSource(state)
        var finished = 0
        compose.setContent {
            TripArchiveTheme {
                DestinationHome(
                    Profile(1, "준", ProfileRelationship.PARENT),
                    {},
                    { finished++ },
                    state,
                    renderCharacter = false,
                    simulateArrival = source::createDemoArrival,
                )
            }
        }
        compose.onNodeWithText("도착 재현").assertIsNotEnabled()
        compose.runOnIdle { state.value = DestinationTestFixtures.active("시장", id = 2) }
        compose.onNodeWithText("도착 재현").performClick()
        compose.onNodeWithText("시장에 도착했어요!").assertIsDisplayed()
        // A real SDK refresh/change must not interrupt the simulated arrival screen.
        compose.runOnIdle { state.value = DestinationTestFixtures.active("해변", id = 3) }
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
            assertEquals(DestinationTestFixtures.active("해변", id = 3), state.value)
        }
        compose.onNode(isDialog()).assertDoesNotExist()
        compose.runOnIdle {
            state.value =
                state.value.copy(
                    pendingArrival = state.value.current ?: DestinationTestFixtures.place("해변"),
                    current = null,
                    routing = false,
                )
        }
        compose.onNode(isDialog()).assertDoesNotExist()
        compose.runOnIdle { assertEquals(1, finished) }
    }

    @Test
    fun arrivalDialogCountsDownAndPausesDuringFinishConfirmation() {
        val state = MutableStateFlow(DestinationTestFixtures.active("안목해변"))
        compose.setContent {
            TripArchiveTheme {
                DestinationHome(Profile(1, "준", ProfileRelationship.PARENT), {}, {}, state, renderCharacter = false)
            }
        }
        compose.runOnIdle {
            state.value =
                state.value.copy(
                    pendingArrival = state.value.current ?: DestinationTestFixtures.place("안목해변"),
                    current = null,
                    routing = false,
                )
        }
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
        val state = MutableStateFlow(DestinationTestFixtures.active("안목해변"))
        compose.setContent {
            TripArchiveTheme {
                DestinationHome(Profile(1, "준", ProfileRelationship.PARENT), {
                }, {}, state, renderCharacter = false, drawBackground = false)
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
        compose.runOnIdle {
            state.value =
                state.value.copy(
                    pendingArrival = state.value.current ?: DestinationTestFixtures.place("안목해변"),
                    current = null,
                    routing = false,
                )
        }
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
                DestinationHome(
                    Profile(1, "준", ProfileRelationship.PARENT),
                    {},
                    { finished++ },
                    state,
                    renderCharacter = false,
                    drawBackground = false,
                )
            }
        }
        compose.runOnIdle {
            state.value =
                state.value.copy(
                    pendingArrival = state.value.current ?: DestinationTestFixtures.place("안목해변"),
                    current = null,
                    routing = false,
                )
        }
        compose.onNodeWithText("여기서 여행 마치기").performClick()
        compose.onNodeWithText("마치기").performClick()
        compose.onNode(isDialog()).assertDoesNotExist()
        compose.runOnIdle {
            assertEquals(1, finished)
            state.value =
                DestinationState(
                    current = DestinationTestFixtures.place("병원", 2),
                    pendingArrival = DestinationTestFixtures.place("시장", 1),
                    routing = true,
                )
        }
        compose.mainClock.advanceTimeBy(6000)
        compose.onNode(isDialog()).assertDoesNotExist()
        val pixels = compose.onNodeWithTag("home-surface").captureToImage().toPixelMap()
        assertEquals(1f, pixels[pixels.width / 2, pixels.height / 10].red, 0.02f)
        compose.runOnIdle { assertEquals(1, finished) }
    }

    @Test
    fun nextDestinationAndLateLookupCannotChangePendingArrivalIdentity() {
        val market = DestinationTestFixtures.place("시장", 1, status = CategoryLookupStatus.LOADING)
        val hospital =
            DestinationTestFixtures.place(
                "병원",
                2,
                DestinationCategory.HOSPITAL,
                CategoryLookupStatus.SUCCESS,
            )
        val state = MutableStateFlow(DestinationState(pendingArrival = market))
        val recorded = mutableListOf<Destination>()
        compose.setContent {
            TripArchiveTheme {
                DestinationHome(
                    Profile(1, "준", ProfileRelationship.PARENT),
                    {},
                    {},
                    state,
                    renderCharacter = false,
                    onArrival = { recorded.add(it) },
                )
            }
        }
        compose.onNodeWithText("5").assertIsDisplayed()
        compose.mainClock.advanceTimeBy(2000)
        compose.runOnIdle {
            state.value =
                state.value.copy(
                    current = hospital,
                    routing = true,
                    pendingArrival =
                        market.copy(
                            category = DestinationCategory.MART,
                            categoryStatus = CategoryLookupStatus.SUCCESS,
                        ),
                )
        }
        compose.mainClock.advanceTimeBy(3100)
        compose.runOnIdle {
            assertEquals(1, recorded.size)
            assertEquals(market.id, recorded.single().id)
            assertEquals("시장", recorded.single().name)
            assertEquals(DestinationCategory.MART, recorded.single().category)
        }
        compose.runOnIdle {
            state.value =
                state.value.copy(
                    current = hospital.copy(category = DestinationCategory.FOOD),
                )
        }
        compose.mainClock.advanceTimeBy(6000)
        compose.runOnIdle { assertEquals(1, recorded.size) }
    }

    @Test
    fun revisitingSameNameWithNewIdShowsANewArrivalCountdown() {
        val first = DestinationTestFixtures.place("시장", 1, DestinationCategory.MART)
        val second = first.copy(id = 2)
        val state = MutableStateFlow(DestinationState(pendingArrival = first))
        val recorded = mutableListOf<Long>()
        compose.setContent {
            TripArchiveTheme {
                DestinationHome(
                    Profile(1, "준", ProfileRelationship.PARENT),
                    {},
                    {},
                    state,
                    renderCharacter = false,
                    onArrival = { recorded.add(it.id) },
                )
            }
        }
        compose.onNodeWithText("도착 확인하고 계속하기").performClick()
        compose.runOnIdle { state.value = DestinationState(pendingArrival = second) }
        compose.onNodeWithText("5").assertIsDisplayed()
        compose.mainClock.advanceTimeBy(5100)
        compose.runOnIdle { assertEquals(listOf(1L, 2L), recorded) }
    }

    @Test
    fun arrivalCategoryDoesNotFallBackToTheNextActiveDestination() {
        val hospital = DestinationTestFixtures.place("강릉아산병원", 2, DestinationCategory.HOSPITAL)
        val state =
            MutableStateFlow(
                DestinationState(
                    current = hospital,
                    pendingArrival = DestinationTestFixtures.place("성심당 DCC점", 1),
                    routing = true,
                ),
            )
        var renderedCategory: DestinationCategory? = DestinationCategory.MART
        compose.setContent {
            TripArchiveTheme {
                DestinationHome(
                    Profile(1, "준", ProfileRelationship.PARENT),
                    {},
                    {},
                    state,
                    renderCharacter = false,
                    onDestinationCategoryChange = { renderedCategory = it },
                )
            }
        }
        compose.waitForIdle()
        compose.runOnIdle { assertNull(renderedCategory) }
        compose.onNodeWithText("병원").assertDoesNotExist()
        compose.runOnIdle { state.value = state.value.copy(pendingArrival = null) }
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
