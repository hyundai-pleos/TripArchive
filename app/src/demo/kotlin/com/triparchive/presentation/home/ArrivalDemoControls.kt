package com.triparchive.presentation.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.triparchive.core.designsystem.TripArchiveTheme
import com.triparchive.domain.navigation.Destination
import com.triparchive.domain.navigation.DestinationCategory
import com.triparchive.domain.navigation.DestinationState

@Composable
internal fun ArrivalDemoControls(
    destination: DestinationState,
    demoInput: DestinationDemoInput,
    isSimulating: Boolean,
    canSimulate: Boolean,
    onArrive: (Destination) -> Unit,
    onReset: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!demoInput.enabled) return
    val current = demoInput.destination.value
    val effectiveDestination = current ?: destination
    val active = effectiveDestination.current?.takeIf { effectiveDestination.routing }
    val name = active?.name
    Column(
        modifier
            .widthIn(max = 190.dp)
            .background(Color(0xFFFFF2CC), RoundedCornerShape(12.dp))
            .padding(8.dp),
    ) {
        Text("DEMO · 목적지 입력")
        DestinationCategory.values().forEach { category ->
            DemoOption(
                label = category.label,
                selected = current?.current?.category == category && current.routing,
                enabled = canSimulate,
                onClick = {
                    demoInput.selectDestination(category.sampleDestination, category)
                },
            )
        }
        DemoOption(
            label = "분류 없음",
            selected = current != null && current.current?.category == null && current.routing,
            enabled = canSimulate,
            onClick = { demoInput.selectDestination("성심당 DCC점", null) },
        )
        DemoOption(
            label = "경로 취소",
            selected = current != null && !current.routing,
            enabled = canSimulate && current?.routing == true,
            onClick = demoInput.cancelRoute,
        )
        DemoOption(
            label = "도착 재현",
            selected = isSimulating,
            enabled = canSimulate && name != null,
            onClick = {
                if (current?.routing == true) {
                    demoInput.arrive()
                } else {
                    active?.let(onArrive)
                }
            },
        )
        DemoOption(
            label = "SDK 상태로 복귀",
            selected = false,
            enabled = current != null,
            onClick = {
                demoInput.reset()
                onReset()
            },
        )
    }
}

@Composable
private fun DemoOption(
    label: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Text(
        text = if (selected) "✓ $label" else label,
        color = if (enabled) Color.Black else Color.Gray,
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(
                    enabled = enabled,
                    role = Role.Button,
                    interactionSource = null,
                    indication = null,
                    onClick = onClick,
                ).padding(vertical = 6.dp, horizontal = 4.dp),
    )
}

private val DestinationCategory.sampleDestination: String
    get() =
        when (this) {
            DestinationCategory.MART -> "이마트 강릉점"
            DestinationCategory.SCHOOL -> "강릉초등학교"
            DestinationCategory.TOURISM -> "경포대"
            DestinationCategory.FOOD -> "성심당 DCC점"
            DestinationCategory.HOSPITAL -> "강릉아산병원"
        }

@Preview
@Composable
private fun ArrivalDemoControlsPreview() {
    TripArchiveTheme {
        ArrivalDemoControls(
            DestinationState(current = Destination(1, "성심당 DCC점", 36.0, 127.0), routing = true),
            DestinationDemoInput(
                destination = androidx.compose.runtime.mutableStateOf(null),
                enabled = true,
                selectDestination = { _, _ -> },
                cancelRoute = {},
                arrive = {},
                acknowledge = {},
                reset = {},
            ),
            false,
            true,
            {},
            {},
        )
    }
}
