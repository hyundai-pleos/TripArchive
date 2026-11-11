package com.triparchive.presentation.home.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.triparchive.R
import com.triparchive.core.designsystem.TripArchiveTheme
import com.triparchive.presentation.management.ActionButton
import com.triparchive.presentation.management.Ink
import com.triparchive.presentation.management.Pink

@Composable
internal fun HomeDestinationSheet(
    nickname: String,
    destination: String?,
    scale: Float,
    onFinish: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(topStart = (40 * scale).dp, topEnd = (40 * scale).dp)
    Column(
        modifier
            .width((840 * scale).dp)
            .height(((if (destination == null) 339 else 343) * scale).dp)
            .background(Pink, shape)
            .padding(top = (3 * scale).dp)
            .background(Color.White, shape)
            .padding(horizontal = (28 * scale).dp, vertical = (20 * scale).dp),
        verticalArrangement = Arrangement.spacedBy((24 * scale).dp),
    ) {
        Text(
            buildAnnotatedString {
                withStyle(SpanStyle(color = Pink, fontSize = (40 * scale).sp, fontWeight = FontWeight.Bold)) {
                    append(nickname)
                }
                append("님과 여행하고 있어요.")
            },
            modifier = Modifier.fillMaxWidth().padding((10 * scale).dp),
            color = Ink,
            fontSize = (32 * scale).sp,
            lineHeight = (56 * scale).sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Row(
            Modifier.fillMaxWidth().height((63 * scale).dp).padding(horizontal = (10 * scale).dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Image(painterResource(R.drawable.destination_flag), null, Modifier.size((48 * scale).dp))
            Spacer(Modifier.width((10 * scale).dp))
            Text(
                destination ?: "목적지를 설정하면 기록이 시작됩니다",
                color = Ink,
                fontSize = (28 * scale).sp,
                lineHeight = (42 * scale).sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        ActionButton(
            "여행 마치기",
            Color(0xFFF2F4F6),
            scale,
            true,
            onFinish,
            Modifier.fillMaxWidth(),
            textColor = Color.Black,
        )
    }
}

@Preview(widthDp = 840, heightDp = 339)
@Composable
private fun NoDestinationPreview() {
    TripArchiveTheme { HomeDestinationSheet("사용자1", null, 1f, {}) }
}

@Preview(widthDp = 840, heightDp = 343)
@Composable
private fun ActiveDestinationPreview() {
    TripArchiveTheme { HomeDestinationSheet("사용자1", "강릉 안목해변", 1f, {}) }
}
