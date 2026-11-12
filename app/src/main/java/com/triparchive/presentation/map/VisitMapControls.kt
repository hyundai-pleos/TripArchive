package com.triparchive.presentation.map

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import java.util.Locale

@Composable
internal fun VisitMapControls(
    viewport: () -> MapViewport,
    onAction: (VisitMapAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val zoom by remember(viewport) { derivedStateOf(structuralEqualityPolicy()) { viewport().zoom } }
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = Color.White.copy(alpha = 0.94f),
            shadowElevation = 2.dp,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                ZoomButton(text = "+", onClick = { onAction(VisitMapAction.ZoomIn) }, label = "확대")
                Box(
                    Modifier
                        .width(34.dp)
                        .height(1.dp)
                        .background(Color(0xFFE5EBEE)),
                )
                ZoomButton(text = "−", onClick = { onAction(VisitMapAction.ZoomOut) }, label = "축소")
            }
        }
        TextButton(
            onClick = { onAction(VisitMapAction.ResetViewport) },
            modifier =
                Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color.White.copy(alpha = 0.94f)),
        ) {
            Text("전체 보기", color = VisitMapPalette.text)
        }
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = Color.White.copy(alpha = 0.84f),
        ) {
            Text(
                text = String.format(Locale.US, "%.1f×", zoom),
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                style = MaterialTheme.typography.labelMedium,
                color = VisitMapPalette.secondaryText,
            )
        }
    }
}

@Composable
private fun ZoomButton(
    text: String,
    onClick: () -> Unit,
    label: String,
) {
    TextButton(
        onClick = onClick,
        modifier =
            Modifier
                .size(54.dp)
                .semantics { contentDescription = label },
        shape = RoundedCornerShape(14.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.headlineSmall,
            color = VisitMapPalette.text,
        )
    }
}
