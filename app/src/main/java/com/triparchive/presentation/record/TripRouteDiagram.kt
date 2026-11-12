package com.triparchive.presentation.record

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.triparchive.R

internal fun routeAnchors(count: Int): List<Offset> =
    when (count) {
        0 -> {
            emptyList()
        }

        1 -> {
            listOf(Offset(0.5f, 0.55f))
        }

        2 -> {
            listOf(Offset(0.19f, 0.62f), Offset(0.76f, 0.66f))
        }

        3 -> {
            listOf(Offset(0.19f, 0.69f), Offset(0.76f, 0.72f), Offset(0.67f, 0.41f))
        }

        else -> {
            val rows = (count + 1) / 2
            List(count) { index ->
                val row = index / 2
                val x = if ((row % 2 == 0) == (index % 2 == 0)) 0.2f else 0.8f
                Offset(x, (row + 0.5f) / rows)
            }
        }
    }

@Composable
internal fun TripRouteDiagram(
    count: Int,
    scale: Float,
) {
    val rows = (count + 1) / 2
    val panelHeight = maxOf(347f, rows * 130f) * scale
    val anchors = routeAnchors(count)
    val markerSize = 48 * scale
    BoxWithConstraints(
        Modifier.fillMaxWidth().height(panelHeight.dp).clip(RoundedCornerShape((28 * scale).dp)),
    ) {
        Image(
            painter = painterResource(R.drawable.record_map_background),
            contentDescription = null,
            modifier = Modifier.fillMaxWidth().aspectRatio(728f / 347f).align(Alignment.TopCenter),
        )
        Box(
            Modifier.fillMaxSize().drawWithCache {
                val points = anchors.map { Offset(it.x * size.width, it.y * size.height) }
                val path = Path()
                if (points.isNotEmpty()) {
                    path.moveTo(points.first().x, points.first().y)
                    points.zipWithNext().forEach { (from, to) ->
                        val dx = to.x - from.x
                        val dy = to.y - from.y
                        path.cubicTo(from.x + dx * 0.35f, from.y, to.x - dx * 0.35f, to.y - dy * 0.1f, to.x, to.y)
                    }
                }
                onDrawBehind {
                    drawPath(
                        path,
                        Color(0xFFFF66AF),
                        style = Stroke(width = (8 * scale).dp.toPx(), cap = StrokeCap.Round),
                    )
                }
            },
        )
        anchors.forEachIndexed { index, anchor ->
            Box(
                Modifier
                    .offset(
                        x = maxWidth * anchor.x - (markerSize / 2).dp,
                        y = panelHeight.dp * anchor.y - (markerSize / 2).dp,
                    ).size(markerSize.dp)
                    .background(Color.White, CircleShape)
                    .border((4 * scale).dp, Color(0xFFFF66AF), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "${index + 1}",
                    color = Color(0xFFFF66AF),
                    fontWeight = FontWeight.Bold,
                    fontSize = (26 * scale).sp,
                )
            }
        }
    }
}
