package com.triparchive

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.room.Room
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.triparchive.data.local.TripArchiveDatabase
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test

class StartupIntegrationTest {
    @get:Rule
    val compose = createEmptyComposeRule()

    @Test
    fun freshActivityLaunchesRouteUsingPersistedProfiles() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val database = Room.databaseBuilder(context, TripArchiveDatabase::class.java, "triparchive.db").build()
        val count =
            try {
                runBlocking { database.profileDao().count() }
            } finally {
                database.close()
            }
        val expected = if (count == 0) "여행 시작하기" else "누구와 여행을 시작할까요?"
        repeat(2) {
            ActivityScenario.launch(MainActivity::class.java).use {
                compose.waitUntil(5000) {
                    compose
                        .onAllNodes(
                            androidx.compose.ui.test
                                .hasText(expected),
                        ).fetchSemanticsNodes()
                        .isNotEmpty()
                }
                compose.onNodeWithText(expected).assertIsDisplayed()
            }
        }
    }
}
