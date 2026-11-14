package com.triparchive.presentation.home

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.triparchive.BuildConfig
import com.triparchive.DestinationTestFixtures
import com.triparchive.FakeDestinationSource
import com.triparchive.core.designsystem.TripArchiveTheme
import com.triparchive.data.local.TripArchiveDatabase
import com.triparchive.data.profile.Profile
import com.triparchive.data.profile.ProfileRelationship
import com.triparchive.data.record.RoomRecordRepository
import com.triparchive.domain.navigation.DestinationState
import com.triparchive.domain.navigation.ResolveDestinationCategory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class TripRecordingDemoTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private lateinit var database: TripArchiveDatabase
    private lateinit var repository: RoomRecordRepository
    private var profileId: Long = 0
    private var otherProfileId: Long = 0

    @Before
    fun setUp() {
        assumeTrue(BuildConfig.ARRIVAL_DEMO)
        database =
            Room
                .inMemoryDatabaseBuilder(
                    InstrumentationRegistry.getInstrumentation().targetContext,
                    TripArchiveDatabase::class.java,
                ).build()
        repository = RoomRecordRepository(database.recordDao())
        runBlocking {
            profileId = database.profileDao().insert(Profile(nickname = "준", relationship = ProfileRelationship.PARENT))
            otherProfileId =
                database.profileDao().insert(Profile(nickname = "다른 사람", relationship = ProfileRelationship.CHILD))
        }
    }

    @After
    fun tearDown() {
        if (::database.isInitialized) database.close()
    }

    @Test
    fun twoDemoArrivalsAndFinishSaveOrderedRecordForSelectedProfile() {
        val source = MutableStateFlow(DestinationTestFixtures.active("시장"))
        val session = TripSessionViewModel(repository, FakeDestinationSource(source), ResolveDestinationCategory())
        var finished = false
        compose.setContent {
            TripArchiveTheme {
                DestinationHome(
                    profile = Profile(profileId, "준", ProfileRelationship.PARENT),
                    onProfileClick = {},
                    onFinish = { finished = true },
                    source = source,
                    renderCharacter = false,
                    onArrival = session::recordArrival,
                    simulateArrival = session::createDemoArrival,
                    saveTrip = { pending -> session.save(profileId, pending) },
                )
            }
        }
        compose.onNodeWithText("도착 재현").performClick()
        compose.onNodeWithText("시장에 도착했어요!").assertIsDisplayed()
        compose.onNodeWithText("도착 확인하고 계속하기").performClick()
        compose.runOnIdle { source.value = DestinationTestFixtures.active("해변", id = 2) }
        compose.onNodeWithText("도착 재현").performClick()
        compose.onNodeWithText("해변에 도착했어요!").assertIsDisplayed()
        compose.onNodeWithText("도착 확인하고 계속하기").performClick()
        compose.onNodeWithText("여행 마치기").performClick()
        compose.onNodeWithText("마치기").performClick()
        compose.waitUntil(10_000) { finished }
        runBlocking {
            val record = repository.observeRecords(profileId).first().single()
            assertEquals(
                listOf("시장", "해변"),
                repository
                    .observeRecord(profileId, record.id)
                    .first()!!
                    .stops
                    .map { it.placeName },
            )
            assertEquals(emptyList<Any>(), repository.observeRecords(otherProfileId).first())
        }
    }

    @Test
    fun finishFromArrivalIncludesPendingStopAndCancelDoesNotSave() {
        val source = MutableStateFlow(DestinationTestFixtures.active("해변", id = 2))
        val session = TripSessionViewModel(repository, FakeDestinationSource(source), ResolveDestinationCategory())
        var finished = false
        compose.setContent {
            TripArchiveTheme {
                DestinationHome(
                    profile = Profile(profileId, "준", ProfileRelationship.PARENT),
                    onProfileClick = {},
                    onFinish = { finished = true },
                    source = source,
                    renderCharacter = false,
                    onArrival = session::recordArrival,
                    simulateArrival = session::createDemoArrival,
                    saveTrip = { pending -> session.save(profileId, pending) },
                )
            }
        }
        compose.onNodeWithText("도착 재현").performClick()
        compose.onNodeWithText("여기서 여행 마치기").performClick()
        compose.onNodeWithText("취소").performClick()
        runBlocking { assertEquals(0, repository.observeRecords(profileId).first().size) }
        compose.onNodeWithText("여기서 여행 마치기").performClick()
        compose.onNodeWithText("마치기").performClick()
        compose.waitUntil(10_000) { finished }
        runBlocking {
            val record = repository.observeRecords(profileId).first().single()
            assertEquals(
                listOf("해변"),
                repository
                    .observeRecord(profileId, record.id)
                    .first()!!
                    .stops
                    .map { it.placeName },
            )
        }
    }

    @Test
    fun failedSaveKeepsTripOpenAndRetrySavesItOnce() {
        val source = MutableStateFlow(DestinationTestFixtures.active("시장"))
        val session = TripSessionViewModel(repository, FakeDestinationSource(source), ResolveDestinationCategory())
        var attempts = 0
        var finished = false
        compose.setContent {
            TripArchiveTheme {
                DestinationHome(
                    profile = Profile(profileId, "준", ProfileRelationship.PARENT),
                    onProfileClick = {},
                    onFinish = { finished = true },
                    source = source,
                    renderCharacter = false,
                    onArrival = session::recordArrival,
                    simulateArrival = session::createDemoArrival,
                    saveTrip = { pending ->
                        attempts++
                        if (attempts == 1) false else session.save(profileId, pending)
                    },
                )
            }
        }
        compose.onNodeWithText("도착 재현").performClick()
        compose.onNodeWithText("여기서 여행 마치기").performClick()
        compose.onNodeWithText("마치기").performClick()
        compose.onNodeWithText("여행 기록을 저장하지 못했어요. 다시 시도해 주세요.").assertIsDisplayed()
        runBlocking { assertEquals(0, repository.observeRecords(profileId).first().size) }
        compose.onNodeWithText("마치기").performClick()
        compose.waitUntil(10_000) { finished }
        runBlocking {
            val record = repository.observeRecords(profileId).first().single()
            assertEquals(
                listOf("시장"),
                repository
                    .observeRecord(profileId, record.id)
                    .first()!!
                    .stops
                    .map { it.placeName },
            )
        }
    }
}
