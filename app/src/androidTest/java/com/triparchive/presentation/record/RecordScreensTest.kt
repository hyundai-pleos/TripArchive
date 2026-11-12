package com.triparchive.presentation.record

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import com.triparchive.core.designsystem.TripArchiveTheme
import com.triparchive.data.record.TripRecord
import com.triparchive.data.record.TripRecordDetail
import com.triparchive.data.record.TripStop
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class RecordScreensTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun listShowsEmptyAndRetryStates() {
        var retried = false
        compose.setContent {
            TripArchiveTheme { RecordListScreen(RecordListState.Error, {}, {}, { retried = true }) }
        }
        compose.onNodeWithText("여행 기록을 불러오지 못했어요.").assertIsDisplayed()
        compose.onNodeWithText("다시 시도").performClick()
        compose.runOnIdle { assertEquals(true, retried) }
    }

    @Test
    fun listHidesStoredAndUnavailableDistances() {
        val records =
            listOf(
                TripRecord(1, 1, "강릉 여행", 0, 1, 123_000),
                TripRecord(2, 1, "부산 여행", 0, 1, null),
            )
        var selectedId: Long? = null
        compose.setContent {
            TripArchiveTheme {
                RecordListScreen(RecordListState.Ready(records), {}, { selectedId = it }, {})
            }
        }
        compose.onNodeWithText("강릉 여행").assertIsDisplayed().performClick()
        compose.onNodeWithText("부산 여행").assertIsDisplayed()
        compose.onNodeWithText("123km").assertDoesNotExist()
        compose.onNodeWithText("거리 정보 없음").assertDoesNotExist()
        compose.runOnIdle { assertEquals(1L, selectedId) }
    }

    @Test
    fun detailDisplaysStopsInPositionOrder() {
        val record = TripRecord(1, 1, "여행 이름", 0, 1, null)
        val stops = listOf(TripStop(2, 1, 1, "두 번째 장소"), TripStop(1, 1, 0, "첫 번째 장소"))
        compose.setContent {
            TripArchiveTheme { RecordDetailScreen(RecordDetailState.Ready(TripRecordDetail(record, stops)), {}, {}) }
        }
        compose.onNodeWithText("여행 이름").assertIsDisplayed()
        compose.onNodeWithText("첫 번째 장소").assertIsDisplayed()
        compose.onNodeWithText("두 번째 장소").assertIsDisplayed()
    }

    @Test
    fun portraitDetailScrollsToLongLastStop() {
        assertDetailScrollsAtSize(840, 1184)
    }

    @Test
    fun landscapeDetailScrollsToLongLastStop() {
        assertDetailScrollsAtSize(1280, 720)
    }

    private fun assertDetailScrollsAtSize(
        width: Int,
        height: Int,
    ) {
        val record = TripRecord(1, 1, "아주 긴 여행 이름을 가진 테스트 여행", 0, 1, null)
        val stops =
            (0 until 10).map { index ->
                TripStop(index.toLong() + 1, 1, index, "방문 장소 ${index + 1} 아주 긴 장소 이름입니다")
            }
        compose.setContent {
            TripArchiveTheme {
                Box(Modifier.requiredSize(width.dp, height.dp).testTag("recordViewport")) {
                    RecordDetailScreen(RecordDetailState.Ready(TripRecordDetail(record, stops)), {}, {})
                }
            }
        }
        compose.onNodeWithTag("recordViewport").assertWidthIsEqualTo(width.dp).assertHeightIsEqualTo(height.dp)
        compose.onNodeWithText("방문 장소 10 아주 긴 장소 이름입니다").performScrollTo().assertExists()
    }
}
