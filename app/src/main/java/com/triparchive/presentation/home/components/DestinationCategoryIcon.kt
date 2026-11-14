package com.triparchive.presentation.home.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.triparchive.R
import com.triparchive.domain.navigation.DestinationCategory
import com.triparchive.presentation.home.label

@Composable
internal fun DestinationCategoryIcon(
    category: DestinationCategory?,
    modifier: Modifier = Modifier,
) {
    if (category == null) {
        Image(
            painter = painterResource(R.drawable.destination_flag),
            contentDescription = null,
            modifier = modifier,
        )
        return
    }
    Canvas(
        modifier.semantics { contentDescription = "${category.label} 아이콘" },
    ) {
        val iconColor = category.iconColor
        val lineWidth = size.minDimension * 0.075f
        val line = Stroke(width = lineWidth, cap = StrokeCap.Round, join = StrokeJoin.Round)

        fun x(fraction: Float) = size.width * fraction

        fun y(fraction: Float) = size.height * fraction

        fun iconLine(
            fromX: Float,
            fromY: Float,
            toX: Float,
            toY: Float,
        ) {
            drawLine(
                iconColor,
                Offset(x(fromX), y(fromY)),
                Offset(x(toX), y(toY)),
                lineWidth,
                cap = StrokeCap.Round,
            )
        }

        when (category) {
            DestinationCategory.MART -> {
                drawRoundRect(
                    iconColor,
                    topLeft = Offset(x(0.17f), y(0.32f)),
                    size = Size(x(0.66f), y(0.55f)),
                    cornerRadius = CornerRadius(lineWidth * 2f),
                    style = line,
                )
                val handle =
                    Path().apply {
                        moveTo(x(0.34f), y(0.32f))
                        lineTo(x(0.34f), y(0.2f))
                        quadraticTo(x(0.5f), y(0.06f), x(0.66f), y(0.2f))
                        lineTo(x(0.66f), y(0.32f))
                    }
                drawPath(handle, iconColor, style = line)
            }

            DestinationCategory.SCHOOL -> {
                val cap =
                    Path().apply {
                        moveTo(x(0.1f), y(0.36f))
                        lineTo(x(0.5f), y(0.14f))
                        lineTo(x(0.9f), y(0.36f))
                        lineTo(x(0.5f), y(0.58f))
                        close()
                    }
                drawPath(cap, iconColor, style = line)
                iconLine(0.24f, 0.46f, 0.24f, 0.68f)
                iconLine(0.24f, 0.68f, 0.5f, 0.82f)
                iconLine(0.5f, 0.82f, 0.76f, 0.68f)
                iconLine(0.76f, 0.68f, 0.76f, 0.46f)
                iconLine(0.86f, 0.39f, 0.86f, 0.67f)
            }

            DestinationCategory.TOURISM -> {
                val roof =
                    Path().apply {
                        moveTo(x(0.12f), y(0.33f))
                        lineTo(x(0.5f), y(0.12f))
                        lineTo(x(0.88f), y(0.33f))
                        close()
                    }
                drawPath(roof, iconColor, style = line)
                iconLine(0.2f, 0.38f, 0.8f, 0.38f)
                iconLine(0.16f, 0.86f, 0.84f, 0.86f)
                iconLine(0.25f, 0.4f, 0.25f, 0.82f)
                iconLine(0.42f, 0.4f, 0.42f, 0.82f)
                iconLine(0.58f, 0.4f, 0.58f, 0.82f)
                iconLine(0.75f, 0.4f, 0.75f, 0.82f)
            }

            DestinationCategory.FOOD -> {
                iconLine(0.22f, 0.13f, 0.22f, 0.48f)
                iconLine(0.12f, 0.13f, 0.12f, 0.31f)
                iconLine(0.32f, 0.13f, 0.32f, 0.31f)
                iconLine(0.12f, 0.31f, 0.32f, 0.31f)
                iconLine(0.22f, 0.48f, 0.22f, 0.87f)
                drawOval(
                    iconColor,
                    topLeft = Offset(x(0.64f), y(0.12f)),
                    size = Size(x(0.22f), y(0.36f)),
                    style = line,
                )
                iconLine(0.75f, 0.48f, 0.75f, 0.87f)
            }

            DestinationCategory.HOSPITAL -> {
                val cross =
                    Path().apply {
                        moveTo(x(0.39f), y(0.12f))
                        lineTo(x(0.61f), y(0.12f))
                        lineTo(x(0.61f), y(0.39f))
                        lineTo(x(0.88f), y(0.39f))
                        lineTo(x(0.88f), y(0.61f))
                        lineTo(x(0.61f), y(0.61f))
                        lineTo(x(0.61f), y(0.88f))
                        lineTo(x(0.39f), y(0.88f))
                        lineTo(x(0.39f), y(0.61f))
                        lineTo(x(0.12f), y(0.61f))
                        lineTo(x(0.12f), y(0.39f))
                        lineTo(x(0.39f), y(0.39f))
                        close()
                    }
                drawPath(cross, iconColor, style = line)
            }
        }
    }
}

private val DestinationCategory.iconColor: Color
    get() =
        when (this) {
            DestinationCategory.MART -> Color(0xFF3182CE)
            DestinationCategory.SCHOOL -> Color(0xFF805AD5)
            DestinationCategory.TOURISM -> Color(0xFFE6A12A)
            DestinationCategory.FOOD -> Color(0xFFE76F86)
            DestinationCategory.HOSPITAL -> Color(0xFF32A47A)
        }
