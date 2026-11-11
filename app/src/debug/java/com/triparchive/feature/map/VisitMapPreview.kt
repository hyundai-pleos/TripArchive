package com.triparchive.feature.map

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import com.triparchive.core.designsystem.TripArchiveTheme
import com.triparchive.data.repository.map.BoundaryRepositoryImpl
import com.triparchive.domain.map.ObserveMapVisitIntensities
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking

@Preview(name = "시·도 방문 지도", widthDp = 1280, heightDp = 720)
@Composable
private fun ProvinceVisitsPreview() {
    VisitMapPreview(viewport = MapViewport())
}

@Preview(name = "시·군·구 방문 지도", widthDp = 1280, heightDp = 720)
@Composable
private fun DistrictVisitsPreview() {
    VisitMapPreview(viewport = MapViewport(zoom = 3.5f))
}

@Composable
private fun VisitMapPreview(viewport: MapViewport) {
    val context = LocalContext.current.applicationContext
    val content =
        remember(context) {
            runBlocking {
                val boundaries = BoundaryRepositoryImpl(context).getBoundaries()
                val visitIntensities = ObserveMapVisitIntensities { flowOf(PreviewVisitCounts) }(boundaries).first()
                VisitMapContent.Ready(boundaries, visitIntensities)
            }
        }
    TripArchiveTheme(darkTheme = false) {
        VisitMapScreen(content = content, viewport = { viewport }, onAction = {}, onBack = {})
    }
}

private val PreviewVisitCounts =
    mapOf(
        "11110" to 1,
        "26500" to 1,
        "41111" to 2,
        "43111" to 1,
        "43130" to 2,
        "43150" to 3,
        "44131" to 4,
        "50110" to 2,
        "51150" to 5,
        "52111" to 3,
    )

@Preview(name = "지도 로딩", widthDp = 1280, heightDp = 720)
@Composable
private fun LoadingPreview() {
    PreviewState(VisitMapContent.Loading)
}

@Preview(name = "지도 오류 - 자동 재시도 대기", widthDp = 1280, heightDp = 720)
@Composable
private fun ErrorPreview() {
    PreviewState(VisitMapContent.Error(secondsUntilRetry = 5))
}

@Preview(name = "지도 오류 - 재시도 중", widthDp = 1280, heightDp = 720)
@Composable
private fun RetryingPreview() {
    PreviewState(VisitMapContent.Error(secondsUntilRetry = 0, retrying = true))
}

@Composable
private fun PreviewState(content: VisitMapContent) {
    TripArchiveTheme(darkTheme = false) {
        VisitMapScreen(content = content, viewport = { MapViewport() }, onAction = {}, onBack = {})
    }
}
