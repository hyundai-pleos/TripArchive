package com.triparchive.presentation.map

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.structuralEqualityPolicy
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.triparchive.R
import com.triparchive.core.designsystem.TripArchiveTheme
import java.util.Locale

@Composable
internal fun VisitMapControls(
    viewport: () -> MapViewport,
    onAction: (VisitMapAction) -> Unit,
    modifier: Modifier = Modifier,
    scale: Float = 1f,
) {
    val zoom by remember(viewport) { derivedStateOf(structuralEqualityPolicy()) { viewport().zoom } }
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy((8 * scale).dp),
    ) {
        Surface(
            shape = RoundedCornerShape((14 * scale).dp),
            color = Color.White.copy(alpha = 0.94f),
            shadowElevation = (2 * scale).dp,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                ZoomButton(
                    iconRes = R.drawable.map_zoom_in,
                    onClick = { onAction(VisitMapAction.ZoomIn) },
                    label = "확대",
                    scale = scale,
                )
                Box(
                    Modifier
                        .width((34 * scale).dp)
                        .height((1 * scale).dp)
                        .background(Color(0xFFE5EBEE)),
                )
                ZoomButton(iconRes = R.drawable.map_zoom_out, onClick = {
                    onAction(VisitMapAction.ZoomOut)
                }, label = "축소", scale = scale)
            }
        }
        TextButton(
            onClick = { onAction(VisitMapAction.ResetViewport) },
            modifier =
                Modifier
                    .clip(RoundedCornerShape((14 * scale).dp))
                    .background(Color.White.copy(alpha = 0.94f)),
        ) {
            Text("전체 보기", color = VisitMapPalette.text, fontSize = (22 * scale).sp)
        }
        Surface(
            shape = RoundedCornerShape((10 * scale).dp),
            color = Color.White.copy(alpha = 0.84f),
        ) {
            Text(
                text = String.format(Locale.US, "%.1f×", zoom),
                modifier = Modifier.padding(horizontal = (12 * scale).dp, vertical = (7 * scale).dp),
                style = MaterialTheme.typography.labelMedium,
                fontSize = (18 * scale).sp,
                color = VisitMapPalette.secondaryText,
            )
        }
    }
}

@Composable
private fun ZoomButton(
    @DrawableRes iconRes: Int,
    onClick: () -> Unit,
    label: String,
    scale: Float,
) {
    TextButton(
        onClick = onClick,
        modifier =
            Modifier
                .size((88 * scale).dp)
                .semantics { contentDescription = label },
        shape = RoundedCornerShape((14 * scale).dp),
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = null,
            modifier = Modifier.size((36 * scale).dp),
            tint = VisitMapPalette.text,
        )
    }
}

@Preview(name = "지도 조작 · 기본 배율", showBackground = true, backgroundColor = 0xFFE5EBEE)
@Composable
private fun VisitMapControlsPreview() {
    MapControlsPreview(initialViewport = MapViewport())
}

@Preview(name = "지도 조작 · 확대 상태", showBackground = true, backgroundColor = 0xFFE5EBEE)
@Composable
private fun VisitMapControlsZoomedPreview() {
    MapControlsPreview(initialViewport = MapViewport(zoom = 3.5f))
}

@Composable
private fun MapControlsPreview(initialViewport: MapViewport) {
    var viewport by remember { mutableStateOf(initialViewport) }
    TripArchiveTheme(darkTheme = false) {
        VisitMapControls(
            viewport = { viewport },
            onAction = { viewport = viewport.reduce(it) },
            modifier = Modifier.padding(24.dp),
        )
    }
}
