package com.triparchive

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.triparchive.data.local.MIGRATION_1_2
import com.triparchive.data.local.MIGRATION_2_3
import com.triparchive.data.local.MIGRATION_3_4
import com.triparchive.data.local.TripArchiveDatabase
import com.triparchive.data.profile.Profile
import com.triparchive.data.profile.ProfileRelationship
import com.triparchive.data.record.NewTripRecord
import com.triparchive.domain.record.TripStopDraft
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain

class RecordNavigationTest {
    private val compose = createAndroidComposeRule<MainActivity>()
    private lateinit var database: TripArchiveDatabase
    private var profileId = 0L
    private val fixture =
        object : ExternalResource() {
            override fun before() {
                val context = InstrumentationRegistry.getInstrumentation().targetContext
                database =
                    Room
                        .databaseBuilder(context, TripArchiveDatabase::class.java, "triparchive.db")
                        .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                        .build()
                profileId =
                    runBlocking {
                        val id =
                            database.profileDao().insert(
                                Profile(nickname = "기록테스트", relationship = ProfileRelationship.PARENT),
                            )
                        database.recordDao().insertCompletedRecord(
                            id,
                            NewTripRecord(
                                title = "테스트 여행",
                                startedAt = 1_700_000_000_000,
                                endedAt = 1_700_100_000_000,
                                distanceMeters = 341_000,
                                stops = listOf("서울시청", "안목해변").map { TripStopDraft(it) },
                            ),
                        )
                        id
                    }
            }

            override fun after() {
                runBlocking { database.profileDao().delete(profileId) }
                database.close()
            }
        }

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(fixture).around(compose)

    @Test
    fun homeMapRecordsDetailAndBack() {
        waitForText("기록테스트")
        compose.onNodeWithText("기록테스트").performClick()
        compose.onNodeWithContentDescription("지도").performClick()
        compose.onNodeWithText("기록 보기").assertIsDisplayed().performClick()
        waitForText("테스트 여행")
        compose.onNodeWithText("테스트 여행").performClick()
        waitForText("안목해변")
        compose.onNodeWithText("안목해변").assertIsDisplayed()

        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.onNodeWithText("테스트 여행").assertIsDisplayed()
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.onNodeWithText("기록 보기").assertIsDisplayed()
    }

    private fun waitForText(text: String) {
        compose.waitUntil(timeoutMillis = 15_000) {
            compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
        }
    }
}
