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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.structuralEqualityPolicy
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp

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
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .safeDrawingPadding()
                    .padding(horizontal = 32.dp, vertical = 20.dp),
        ) {
            VisitMapHeader(viewport = viewport)
            Spacer(Modifier.height(16.dp))
            BoxWithConstraints(
                modifier =
                    Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(28.dp)),
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
                            modifier =
                                Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(24.dp),
                        )
                    }

                    VisitMapContent.Loading -> {
                        LoadingMap()
                    }

                    is VisitMapContent.Error -> {
                        MapError(content, onRetry = { onAction(VisitMapAction.Retry) }, onBack = onBack)
                    }
                }

                MapAttribution(
                    modifier =
                        Modifier
                            .align(Alignment.BottomStart)
                            .padding(20.dp),
                )
            }
        }
    }
}

@Composable
private fun VisitMapHeader(viewport: () -> MapViewport) {
    val showsDistricts by remember(viewport) {
        derivedStateOf(structuralEqualityPolicy()) { viewport().showsDistricts }
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "TRIP ARCHIVE",
                style = MaterialTheme.typography.labelMedium,
                color = VisitMapPalette.secondaryText,
            )
            Spacer(Modifier.height(3.dp))
            Text(
                text = "다녀온 곳을 확인해요",
                style = MaterialTheme.typography.headlineSmall,
                color = VisitMapPalette.text,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "지금까지 여행한 지역이 색으로 채워져요",
                style = MaterialTheme.typography.bodySmall,
                color = VisitMapPalette.secondaryText,
            )
        }
        Surface(
            shape = RoundedCornerShape(100.dp),
            color = Color.White,
            contentColor = VisitMapPalette.text,
            shadowElevation = 1.dp,
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier =
                        Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(VisitMapPalette.accent),
                )
                Spacer(Modifier.width(9.dp))
                Text(
                    text = if (showsDistricts) "시 · 군 · 구 경계" else "시 · 도 경계",
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
    }
}

@Composable
private fun MapAttribution(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = Color.White.copy(alpha = 0.92f),
    ) {
        Column(Modifier.padding(horizontal = 13.dp, vertical = 9.dp)) {
            Text(
                text = "대한민국 행정구역 경계",
                style = MaterialTheme.typography.labelMedium,
                color = VisitMapPalette.text,
            )
            Text(
                text = "통계청 SGIS · 가공: vuski/admdongkor · CC BY 4.0",
                style = MaterialTheme.typography.labelSmall,
                color = VisitMapPalette.secondaryText,
            )
        }
    }
}

@Composable
private fun LoadingMap() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = Color.White.copy(alpha = 0.95f),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(12.dp))
                Text("대한민국 지도를 준비하고 있어요", color = VisitMapPalette.text)
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
