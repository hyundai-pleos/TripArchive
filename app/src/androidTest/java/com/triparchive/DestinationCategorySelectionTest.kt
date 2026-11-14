package com.triparchive

import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.triparchive.core.designsystem.TripArchiveTheme
import com.triparchive.data.profile.Profile
import com.triparchive.data.profile.ProfileRelationship
import com.triparchive.domain.navigation.CategoryLookupStatus
import com.triparchive.domain.navigation.DestinationCategory
import com.triparchive.domain.navigation.DestinationState
import com.triparchive.presentation.LocalDrivingRestricted
import com.triparchive.presentation.home.DestinationHome
import com.triparchive.presentation.home.label
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

class DestinationCategorySelectionTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private fun active(name: String): DestinationState =
        DestinationTestFixtures.active(
            name,
            id = name.hashCode().toLong(),
            categoryStatus = CategoryLookupStatus.SUCCESS,
        )

    private fun DestinationState.toArrival(): DestinationState = DestinationState(pendingArrival = current)

    @Test
    fun eachCategoryCanBeSelectedAndFollowsTheDestinationToArrival() {
        val source = MutableStateFlow(DestinationState())
        var displayedCategory: DestinationCategory? = null
        compose.setContent {
            TripArchiveTheme {
                DestinationHome(
                    profile = Profile(1, "준", ProfileRelationship.PARENT),
                    onProfileClick = {},
                    onFinish = {},
                    source = source,
                    renderCharacter = false,
                    onDestinationCategoryChange = { displayedCategory = it },
                )
            }
        }

        DestinationCategory.values().forEachIndexed { index, category ->
            val name = "목적지$index"
            compose.runOnIdle { source.value = active(name) }
            compose.onNodeWithText("어떤 목적지인가요?").assertIsDisplayed()
            compose.onNodeWithText("확인").assertIsNotEnabled()
            compose.onNodeWithText(category.label).performClick()
            compose.onNodeWithText("확인").performClick()
            compose.onNodeWithText("어떤 목적지인가요?").assertDoesNotExist()
            compose.runOnIdle { assertEquals(category, displayedCategory) }

            // A subsequent provider classification must not override the manual choice.
            val otherCategory = DestinationCategory.values().first { it != category }
            compose.runOnIdle {
                source.value = source.value.copy(current = source.value.current?.copy(category = otherCategory))
            }
            compose.runOnIdle { assertEquals(category, displayedCategory) }
            compose.onNodeWithText("어떤 목적지인가요?").assertDoesNotExist()

            compose.runOnIdle { source.value = source.value.toArrival() }
            compose.onNodeWithText("${name}에 도착했어요!").assertIsDisplayed()
            compose.runOnIdle { assertEquals(category, displayedCategory) }
            compose.onNodeWithText("도착 확인하고 계속하기").performClick()
        }
    }

    @Test
    fun otherAndTimeoutDoNotAskAgainUntilDestinationChanges() {
        val source = MutableStateFlow(active("첫 목적지"))
        var displayedCategory: DestinationCategory? = DestinationCategory.MART
        compose.setContent {
            TripArchiveTheme {
                DestinationHome(
                    profile = Profile(1, "준", ProfileRelationship.PARENT),
                    onProfileClick = {},
                    onFinish = {},
                    source = source,
                    renderCharacter = false,
                    onDestinationCategoryChange = { displayedCategory = it },
                )
            }
        }

        compose.onNodeWithText("기타").performClick()
        compose.onNodeWithText("확인").performClick()
        compose.onNodeWithText("어떤 목적지인가요?").assertDoesNotExist()
        compose.runOnIdle { assertNull(displayedCategory) }
        compose.mainClock.advanceTimeBy(6000)
        compose.onNodeWithText("어떤 목적지인가요?").assertDoesNotExist()

        compose.runOnIdle { source.value = active("다른 목적지") }
        compose.onNodeWithText("어떤 목적지인가요?").assertIsDisplayed()
        compose.mainClock.advanceTimeBy(5100)
        compose.onNodeWithText("어떤 목적지인가요?").assertDoesNotExist()
        compose.runOnIdle { assertNull(displayedCategory) }
        compose.mainClock.advanceTimeBy(6000)
        compose.onNodeWithText("어떤 목적지인가요?").assertDoesNotExist()
    }

    @Test
    fun lookupDefersSelectionUntilAnUnclassifiedResult() {
        val source =
            MutableStateFlow(
                active("목적지").let {
                    it.copy(current = it.current?.copy(categoryStatus = CategoryLookupStatus.LOADING))
                },
            )
        compose.setContent {
            TripArchiveTheme {
                DestinationHome(
                    profile = Profile(1, "준", ProfileRelationship.PARENT),
                    onProfileClick = {},
                    onFinish = {},
                    source = source,
                    renderCharacter = false,
                )
            }
        }
        compose.mainClock.advanceTimeBy(6000)
        compose.onNodeWithText("어떤 목적지인가요?").assertDoesNotExist()
        compose.runOnIdle {
            source.value =
                source.value.copy(current = source.value.current?.copy(categoryStatus = CategoryLookupStatus.FAILURE))
        }
        compose.onNodeWithText("어떤 목적지인가요?").assertIsDisplayed()
        compose.onNodeWithText("5").assertIsDisplayed()
    }

    @Test
    fun restrictionDefersAndPausesTheCountdown() {
        val source = MutableStateFlow(active("목적지"))
        var restricted by mutableStateOf(true)
        compose.setContent {
            CompositionLocalProvider(LocalDrivingRestricted provides restricted) {
                TripArchiveTheme {
                    DestinationHome(
                        profile = Profile(1, "준", ProfileRelationship.PARENT),
                        onProfileClick = {},
                        onFinish = {},
                        source = source,
                        renderCharacter = false,
                    )
                }
            }
        }

        compose.mainClock.advanceTimeBy(6000)
        compose.onNodeWithText("어떤 목적지인가요?").assertDoesNotExist()
        compose.runOnIdle { restricted = false }
        compose.onNodeWithText("어떤 목적지인가요?").assertIsDisplayed()
        compose.onNodeWithText("5").assertIsDisplayed()
        compose.mainClock.advanceTimeBy(2000)
        compose.onNodeWithText("3").assertIsDisplayed()
        compose.runOnIdle { restricted = true }
        compose.onNodeWithText("어떤 목적지인가요?").assertDoesNotExist()
        compose.mainClock.advanceTimeBy(6000)
        compose.runOnIdle { restricted = false }
        compose.onNodeWithText("3").assertIsDisplayed()
        compose.mainClock.advanceTimeBy(3100)
        compose.onNodeWithText("어떤 목적지인가요?").assertDoesNotExist()
    }

    @Test
    fun timerConfirmsSelection() {
        val source = MutableStateFlow(active("첫 목적지"))
        var displayedCategory: DestinationCategory? = null
        compose.setContent {
            TripArchiveTheme {
                DestinationHome(
                    profile = Profile(1, "준", ProfileRelationship.PARENT),
                    onProfileClick = {},
                    onFinish = {},
                    source = source,
                    renderCharacter = false,
                    onDestinationCategoryChange = { displayedCategory = it },
                )
            }
        }
        compose.onNodeWithText("학교").performClick()
        compose.mainClock.advanceTimeBy(5100)
        compose.onNodeWithText("어떤 목적지인가요?").assertDoesNotExist()
        compose.runOnIdle { assertEquals(DestinationCategory.SCHOOL, displayedCategory) }
    }

    @Test
    fun providerClassificationArrivalAndCancellationClosePendingDialog() {
        val source = MutableStateFlow(active("첫 목적지"))
        var displayedCategory: DestinationCategory? = null
        compose.setContent {
            TripArchiveTheme {
                DestinationHome(
                    profile = Profile(1, "준", ProfileRelationship.PARENT),
                    onProfileClick = {},
                    onFinish = {},
                    source = source,
                    renderCharacter = false,
                    onDestinationCategoryChange = { displayedCategory = it },
                )
            }
        }
        compose.onNodeWithText("어떤 목적지인가요?").assertIsDisplayed()
        compose.runOnIdle {
            source.value =
                source.value.copy(current = source.value.current?.copy(category = DestinationCategory.HOSPITAL))
        }
        compose.onNodeWithText("어떤 목적지인가요?").assertDoesNotExist()
        compose.runOnIdle { assertEquals(DestinationCategory.HOSPITAL, displayedCategory) }

        compose.runOnIdle { source.value = active("두 번째 목적지") }
        compose.onNodeWithText("어떤 목적지인가요?").assertIsDisplayed()
        compose.runOnIdle { source.value = source.value.toArrival() }
        compose.onNodeWithText("어떤 목적지인가요?").assertDoesNotExist()
        compose.onNodeWithText("두 번째 목적지에 도착했어요!").assertIsDisplayed()
        compose.onNodeWithText("도착 확인하고 계속하기").performClick()

        compose.runOnIdle { source.value = active("세 번째 목적지") }
        compose.onNodeWithText("어떤 목적지인가요?").assertIsDisplayed()
        compose.runOnIdle { source.value = DestinationState() }
        compose.onNodeWithText("어떤 목적지인가요?").assertDoesNotExist()
    }

    @Test
    fun homeNavigationAndBackgroundPauseTheCountdown() {
        val source = MutableStateFlow(active("목적지"))
        var homeActive by mutableStateOf(true)
        val owner =
            object : LifecycleOwner {
                val registry = LifecycleRegistry(this)
                override val lifecycle: Lifecycle get() = registry
            }
        compose.runOnUiThread { owner.registry.currentState = Lifecycle.State.RESUMED }
        compose.setContent {
            CompositionLocalProvider(LocalLifecycleOwner provides owner) {
                TripArchiveTheme {
                    DestinationHome(
                        profile = Profile(1, "준", ProfileRelationship.PARENT),
                        onProfileClick = {},
                        onFinish = {},
                        source = source,
                        renderCharacter = false,
                        homeActive = homeActive,
                    )
                }
            }
        }

        compose.onNodeWithText("어떤 목적지인가요?").assertIsDisplayed()
        compose.mainClock.advanceTimeBy(2000)
        compose.onNodeWithText("3").assertIsDisplayed()
        compose.runOnIdle { homeActive = false }
        compose.onNodeWithText("어떤 목적지인가요?").assertDoesNotExist()
        compose.mainClock.advanceTimeBy(6000)
        compose.runOnIdle { homeActive = true }
        compose.onNodeWithText("3").assertIsDisplayed()
        compose.runOnUiThread { owner.registry.currentState = Lifecycle.State.CREATED }
        compose.onNodeWithText("어떤 목적지인가요?").assertDoesNotExist()
        compose.mainClock.advanceTimeBy(6000)
        compose.runOnUiThread { owner.registry.currentState = Lifecycle.State.RESUMED }
        compose.onNodeWithText("3").assertIsDisplayed()
        compose.mainClock.advanceTimeBy(3100)
        compose.onNodeWithText("어떤 목적지인가요?").assertDoesNotExist()
    }
}
