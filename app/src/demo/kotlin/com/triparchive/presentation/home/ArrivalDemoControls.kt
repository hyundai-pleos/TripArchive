package com.triparchive.presentation.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.triparchive.core.designsystem.TripArchiveTheme
import com.triparchive.data.navigation.DestinationState

@Composable
internal fun ArrivalDemoControls(
    destination: DestinationState,
    isSimulating: Boolean,
    canSimulate: Boolean,
    onArrive: (String) -> Unit,
    onReset: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val name = destination.name?.takeIf { it.isNotBlank() && destination.routing }
    val enabled = canSimulate && name != null
    Column(modifier.background(Color(0xFFFFF2CC)).padding(8.dp)) {
        Text(if (isSimulating) "데모 · 도착 재현 중" else "데모 · SDK 목적지 사용")
        if (name == null && !isSimulating) Text("순정 내비에서 목적지를 설정하세요")
        Text(
            "도착 재현",
            color = if (enabled) Color.Black else Color.Gray,
            modifier =
                Modifier
                    .sizeIn(minHeight = 48.dp)
                    .clickable(
                        enabled = enabled,
                        role = Role.Button,
                        interactionSource = null,
                        indication = null,
                    ) { name?.let(onArrive) }
                    .padding(12.dp),
        )
        if (isSimulating) {
            Text(
                "재현 초기화",
                modifier =
                    Modifier
                        .sizeIn(minHeight = 48.dp)
                        .clickable(role = Role.Button, interactionSource = null, indication = null, onClick = onReset)
                        .padding(12.dp),
            )
        }
    }
}

@Preview
@Composable
private fun ArrivalDemoControlsPreview() {
    TripArchiveTheme {
        ArrivalDemoControls(DestinationState("성심당 DCC점", routing = true), false, true, {}, {})
    }
}
