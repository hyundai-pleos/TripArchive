package com.triparchive.presentation.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.triparchive.domain.navigation.CategoryLookupStatus
import com.triparchive.domain.navigation.Destination
import com.triparchive.domain.navigation.DestinationCategory
import com.triparchive.domain.navigation.DestinationState

@Composable
internal fun CategoryLookupIndicator(
    state: DestinationState,
    modifier: Modifier = Modifier,
) {
    val destination = state.current ?: state.pendingArrival
    val title =
        when (destination?.categoryStatus ?: CategoryLookupStatus.IDLE) {
            CategoryLookupStatus.IDLE -> "목적지 대기 중"
            CategoryLookupStatus.LOADING -> "카테고리 조회 중"
            CategoryLookupStatus.SUCCESS -> "카테고리 조회 성공"
            CategoryLookupStatus.FAILURE -> "카테고리 조회 실패"
        }
    val label =
        when (destination?.category) {
            DestinationCategory.MART -> "마트"
            DestinationCategory.SCHOOL -> "학교"
            DestinationCategory.TOURISM -> "관광"
            DestinationCategory.FOOD -> "음식점"
            DestinationCategory.HOSPITAL -> "병원"
            null -> null
        }
    Column(
        modifier =
            modifier
                .testTag("category-lookup-status")
                .background(Color(0xE6222222), RoundedCornerShape(10.dp))
                .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Text("[임시] $title", color = Color.White, fontSize = 14.sp)
        destination?.name?.let { Text("목적지: $it", color = Color.White, fontSize = 13.sp) }
        label?.let { Text("분류: $it", color = Color.White, fontSize = 13.sp) }
        destination?.categoryFailure?.let { Text(it, color = Color(0xFFFFC3C3), fontSize = 12.sp) }
    }
}

@Preview
@Composable
private fun CategoryLookupIndicatorPreview() {
    CategoryLookupIndicator(
        DestinationState(
            current = Destination(1, "서울역", 37.55, 126.97, DestinationCategory.TOURISM, CategoryLookupStatus.SUCCESS),
        ),
    )
}
