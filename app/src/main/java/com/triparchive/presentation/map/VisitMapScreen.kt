package com.triparchive.presentation.map

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.structuralEqualityPolicy
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.triparchive.R
import com.triparchive.core.designsystem.TripArchiveTheme
import com.triparchive.data.repository.map.BoundaryRepositoryImpl
import com.triparchive.domain.map.ObserveMapVisitIntensities
import com.triparchive.presentation.management.BackControl
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking

@Composable
internal fun VisitMapScreen(
    content: VisitMapContent,
    viewport: () -> MapViewport,
    onAction: (VisitMapAction) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxSize(),
        color = VisitMapPalette.unvisited,
    ) {
        BoxWithConstraints(Modifier
            .fillMaxSize()
            .safeDrawingPadding()) {
            val scale = minOf(maxWidth.value / 840f, maxHeight.value / 1184f)
            Column(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(horizontal = (56 * scale).dp, vertical = (40 * scale).dp),
            ) {
                VisitMapHeader(viewport = viewport, onBack = onBack, scale = scale)
                Spacer(Modifier.height((24 * scale).dp))
                BoxWithConstraints(
                    modifier =
                        Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .clip(RoundedCornerShape((40 * scale).dp)),
                ) {
                    when (content) {
                        is VisitMapContent.Ready -> {
                            val density = LocalDensity.current
                            val width = with(density) { maxWidth.toPx() }
                            val height = with(density) { maxHeight.toPx() }
                            val boundaries = content.boundaries
                            val paths =
                                remember(boundaries, width, height) {
                                    BoundaryPaths.create(boundaries, width, height)
                                }

                            BoundaryCanvas(
                                paths = paths,
                                visitIntensities = content.visitIntensities,
                                viewport = viewport,
                                onAction = onAction,
                            )
                            VisitMapControls(
                                viewport = viewport,
                                onAction = onAction,
                                scale = scale,
                                modifier =
                                    Modifier
                                        .align(Alignment.TopEnd)
                                        .padding((32 * scale).dp),
                            )
                        }

                        VisitMapContent.Loading -> {
                            LoadingMap(scale = scale)
                        }

                        is VisitMapContent.Error -> {
                            MapError(content, onRetry = { onAction(VisitMapAction.Retry) }, onBack = onBack)
                        }
                    }

                    MapAttribution(
                        scale = scale,
                        modifier =
                            Modifier
                                .align(Alignment.BottomEnd)
                                .padding((28 * scale).dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun VisitMapHeader(
    viewport: () -> MapViewport,
    onBack: () -> Unit,
    scale: Float,
) {
    val showsDistricts by remember(viewport) {
        derivedStateOf(structuralEqualityPolicy()) { viewport().showsDistricts }
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BackControl(s = scale, enabled = true, onBack = onBack)
        Spacer(Modifier.width((16 * scale).dp))
        Column(modifier = Modifier.weight(1f)) {
            Spacer(Modifier.height((3 * scale).dp))
            Text(
                text = "다녀온 곳을 확인해요",
                style = MaterialTheme.typography.headlineSmall,
                fontSize = (36 * scale).sp,
                color = VisitMapPalette.text,
            )
            Spacer(Modifier.height((4 * scale).dp))
            Text(
                text = "지금까지 여행한 지역이 색으로 채워져요",
                style = MaterialTheme.typography.bodySmall,
                fontSize = (20 * scale).sp,
                color = VisitMapPalette.secondaryText,
            )
        }
        Surface(
            shape = RoundedCornerShape((100 * scale).dp),
            color = Color.White,
            contentColor = VisitMapPalette.text,
            shadowElevation = (1 * scale).dp,
        ) {
            Row(
                modifier = Modifier.padding(horizontal = (18 * scale).dp, vertical = (12 * scale).dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier =
                        Modifier
                            .size((8 * scale).dp)
                            .clip(CircleShape)
                            .background(VisitMapPalette.accent),
                )
                Spacer(Modifier.width((9 * scale).dp))
                Text(
                    text = if (showsDistricts) "시 · 군 · 구 경계" else "시 · 도 경계",
                    style = MaterialTheme.typography.labelLarge,
                    fontSize = (22 * scale).sp,
                )
            }
        }
    }
}

@Composable
private fun MapAttribution(scale: Float, modifier: Modifier = Modifier) {
    var showAttribution by rememberSaveable { mutableStateOf(false) }
    Surface(
        modifier = modifier,
        shape = CircleShape,
        color = Color.White.copy(alpha = 0.92f),
    ) {
        IconButton(
            onClick = { showAttribution = true },
            modifier = Modifier.size((64 * scale).dp.coerceAtLeast(48.dp)),
        ) {
            Icon(
                painter = painterResource(R.drawable.map_info),
                contentDescription = "지도 출처 및 라이선스",
                modifier = Modifier.size((32 * scale).dp),
                tint = VisitMapPalette.text,
            )
        }
    }
    if (showAttribution) {
        AlertDialog(
            onDismissRequest = { showAttribution = false },
            title = { Text("지도 출처 및 라이선스") },
            text = {
                SelectionContainer {
                    Column(Modifier.verticalScroll(rememberScrollState())) {
                        Text(
                            "본 데이터는 통계청 통계지리정보서비스(SGIS)에서 공공누리 제1유형으로 개방한 " +
                                "행정구역 경계를 가공한 것이며(가공: vuski/admdongkor), CC BY 4.0으로 배포됩니다.",
                        )
                        Spacer(Modifier.height(12.dp))
                        Text(
                            "원자료: 통계청 SGIS\nhttps://sgis.kostat.go.kr\n\n" +
                                "가공: vuski/admdongkor\nhttps://github.com/vuski/admdongkor\n\n" +
                                "공공누리 제1유형\nhttps://www.kogl.or.kr/info/licenseType1.do\n\n" +
                                "CC BY 4.0\nhttps://creativecommons.org/licenses/by/4.0/",
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showAttribution = false }) { Text("확인") }
            },
        )
    }
}

@Composable
private fun LoadingMap(scale: Float) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Surface(
            shape = RoundedCornerShape((20 * scale).dp),
            color = Color.White.copy(alpha = 0.95f),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = (20 * scale).dp, vertical = (16 * scale).dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CircularProgressIndicator(modifier = Modifier.size((22 * scale).dp), strokeWidth = (2 * scale).dp)
                Spacer(Modifier.width((12 * scale).dp))
                Text("대한민국 지도를 준비하고 있어요", color = VisitMapPalette.text, fontSize = (24 * scale).sp)
            }
        }
    }
}

@Composable
private fun MapError(
    error: VisitMapContent.Error,
    onRetry: () -> Unit,
    onBack: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = {},
        title = { Text("데이터를 불러오지 못했어요") },
        text = { Text("잠시 후 다시 시도해 주세요.") },
        confirmButton = {
            Button(onClick = onRetry, enabled = !error.retrying) {
                if (error.retrying) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                    Text("다시 시도 중")
                } else {
                    Text("다시 시도 (${error.secondsUntilRetry}초)")
                }
            }
        },
        dismissButton = { TextButton(onClick = onBack) { Text("뒤로 가기") } },
    )
}

@Preview(name = "시·도 방문 지도 · 세로", widthDp = 840, heightDp = 1184)
@Preview(name = "시·도 방문 지도 · 가로", widthDp = 1280, heightDp = 720)
@Composable
private fun ProvinceVisitsPreview() {
    VisitMapPreview(viewport = MapViewport())
}

@Preview(name = "시·군·구 방문 지도 · 세로", widthDp = 840, heightDp = 1184)
@Preview(name = "시·군·구 방문 지도 · 가로", widthDp = 1280, heightDp = 720)
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
