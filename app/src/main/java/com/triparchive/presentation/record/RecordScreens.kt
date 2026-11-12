package com.triparchive.presentation.record

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.triparchive.core.designsystem.TripArchiveTheme
import com.triparchive.data.record.TripRecord
import com.triparchive.presentation.management.BackControl
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val Ink = Color(0xFF191F28)
private val Gray = Color(0xFF6B7684)
private val TextColor = Color(0xFF333D4B)
private val Pink = Color(0xFFFF66AF)

@Composable
internal fun RecordListScreen(
    state: RecordListState,
    onBack: () -> Unit,
    onRecordClick: (Long) -> Unit,
    onRetry: () -> Unit,
) {
    ScreenContainer { scale ->
        val listState = rememberLazyListState()
        Column(Modifier.fillMaxSize()) {
            BackControl(scale, true, onBack)
            LazyColumn(state = listState, modifier = Modifier.weight(1f)) {
                item {
                    Spacer(Modifier.height((48 * scale).dp))
                    Header("우리의 여행을 다시 만나봐요", "차곡차곡 쌓인 여행 기록을 확인해요", scale)
                    Spacer(Modifier.height((20 * scale).dp))
                }
                when (state) {
                    RecordListState.Loading -> {
                        item { Loading() }
                    }

                    RecordListState.Error -> {
                        item { ErrorMessage("여행 기록을 불러오지 못했어요.", onRetry) }
                    }

                    is RecordListState.Ready -> {
                        if (state.records.isEmpty()) {
                            item { Message("아직 저장된 여행이 없어요.") }
                        } else {
                            items(state.records, key = { it.id }) { record ->
                                RecordCard(record, scale) { onRecordClick(record.id) }
                                Spacer(Modifier.height((18 * scale).dp))
                            }
                        }
                    }
                }
                item { Spacer(Modifier.height((56 * scale).dp)) }
            }
        }
    }
}

@Composable
internal fun RecordDetailScreen(
    state: RecordDetailState,
    onBack: () -> Unit,
    onRetry: () -> Unit,
) {
    ScreenContainer { scale ->
        Column(Modifier.fillMaxSize()) {
            BackControl(scale, true, onBack)
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                Spacer(Modifier.height((48 * scale).dp))
                when (state) {
                    RecordDetailState.Loading -> {
                        Loading()
                    }

                    RecordDetailState.Error -> {
                        ErrorMessage("여행 기록을 불러오지 못했어요.", onRetry)
                    }

                    RecordDetailState.NotFound -> {
                        Message("여행 기록을 찾을 수 없어요.")
                    }

                    is RecordDetailState.Ready -> {
                        val record = state.detail.record
                        Header(record.title, formatDates(record.startedAt, record.endedAt), scale)
                        Spacer(Modifier.height((20 * scale).dp))
                        val stops = state.detail.stops.sortedBy { it.position }
                        if (stops.isEmpty()) {
                            Message("저장된 방문 장소가 없어요.")
                        } else {
                            TripRouteDiagram(stops.size, scale)
                            Spacer(Modifier.height((20 * scale).dp))
                            stops.forEachIndexed { index, stop ->
                                StopRow(index + 1, stop.placeName, index != stops.lastIndex, scale)
                            }
                        }
                    }
                }
                Spacer(Modifier.height((56 * scale).dp))
            }
        }
    }
}

@Composable
private fun ScreenContainer(content: @Composable (Float) -> Unit) {
    Box(Modifier.fillMaxSize().background(Color.White).safeDrawingPadding(), contentAlignment = Alignment.TopCenter) {
        BoxWithConstraints(Modifier.widthIn(max = 840.dp).fillMaxSize()) {
            val scale = (maxWidth.value / 840f).coerceIn(0.45f, 1f)
            Box(Modifier.fillMaxSize().padding(horizontal = (56 * scale).dp).padding(top = (40 * scale).dp)) {
                content(scale)
            }
        }
    }
}

@Composable
private fun Header(
    title: String,
    subtitle: String,
    scale: Float,
) {
    Column {
        Text(
            title,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = Ink,
            fontSize = (48 * scale).sp,
            lineHeight = (64 * scale).sp,
        )
        Spacer(Modifier.height((16 * scale).dp))
        Text(
            subtitle,
            style = MaterialTheme.typography.bodyLarge,
            color = Gray,
            fontSize = (28 * scale).sp,
            lineHeight = (42 * scale).sp,
        )
    }
}

@Composable
private fun RecordCard(
    record: TripRecord,
    scale: Float,
    onClick: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .heightIn(min = (172 * scale).dp)
            .background(Color(0xFFF8F8F8), RoundedCornerShape((20 * scale).dp))
            .clickable(interactionSource = null, indication = null, role = Role.Button, onClick = onClick)
            .padding(horizontal = (20 * scale).dp, vertical = (24 * scale).dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                record.title,
                Modifier.weight(1f),
                color = Ink,
                fontWeight = FontWeight.SemiBold,
                fontSize = (34 * scale).sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.width((16 * scale).dp))
            Text(formatDistance(record.distanceMeters), color = Ink, fontSize = (26 * scale).sp)
        }
        Spacer(Modifier.height((10 * scale).dp))
        Text(
            formatDates(record.startedAt, record.endedAt),
            color = TextColor,
            fontWeight = FontWeight.Medium,
            fontSize = (24 * scale).sp,
            lineHeight = (40 * scale).sp,
        )
    }
}

@Composable
private fun StopRow(
    number: Int,
    name: String,
    hasNext: Boolean,
    scale: Float,
) {
    Row(Modifier.fillMaxWidth().heightIn(min = (92 * scale).dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width((48 * scale).dp).heightIn(min = (92 * scale).dp), contentAlignment = Alignment.Center) {
            if (number > 1) {
                Box(
                    Modifier
                        .align(Alignment.TopCenter)
                        .width((2 * scale).dp)
                        .height((46 * scale).dp)
                        .background(Color(0xFFD1D6DB)),
                )
            }
            if (hasNext) {
                Box(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .width((2 * scale).dp)
                        .height((46 * scale).dp)
                        .background(Color(0xFFD1D6DB)),
                )
            }
            Box(
                Modifier.size((48 * scale).dp).background(Color(0xFFE8F3FF), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text("$number", color = Color(0xFF1B64DA), fontWeight = FontWeight.SemiBold, fontSize = (26 * scale).sp)
            }
        }
        Spacer(Modifier.width((24 * scale).dp))
        Text(
            name,
            color = TextColor,
            fontWeight = FontWeight.SemiBold,
            fontSize = (32 * scale).sp,
            lineHeight = (44 * scale).sp,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun Loading() {
    Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
}

@Composable
private fun Message(text: String) {
    Text(
        text,
        Modifier.fillMaxWidth().padding(vertical = 24.dp),
        color = Gray,
        style = MaterialTheme.typography.bodyLarge,
    )
}

@Composable
private fun ErrorMessage(
    text: String,
    onRetry: () -> Unit,
) {
    Column {
        Message(text)
        Text(
            "다시 시도",
            Modifier
                .clickable(interactionSource = null, indication = null, role = Role.Button, onClick = onRetry)
                .padding(vertical = 12.dp),
            color = Pink,
            fontWeight = FontWeight.Bold,
        )
    }
}

private fun formatDates(
    start: Long,
    end: Long,
): String {
    val formatter = SimpleDateFormat("yyyy.MM.dd", Locale.KOREA)
    return "${formatter.format(Date(start))} ~ ${formatter.format(Date(end))}"
}

private fun formatDistance(meters: Long?): String = if (meters == null) "거리 정보 없음" else "${meters / 1000}km"

@Preview(widthDp = 420, heightDp = 592, showBackground = true)
@Composable
private fun RecordListPreview() {
    TripArchiveTheme {
        RecordListScreen(
            RecordListState.Ready(
                listOf(TripRecord(1, 1, "강릉 여행", 1793491200000, 1793664000000, 341000)),
            ),
            {
            },
            {},
            {},
        )
    }
}
