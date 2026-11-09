package com.triparchive.presentation.onboarding

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.triparchive.R
import com.triparchive.core.designsystem.TripArchiveTheme

private val Pink = Color(0xFFFF66AF)
private val Ink = Color(0xFF191F28)

@Composable
fun OnboardingScreen(
    onStart: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(
        modifier.fillMaxSize().background(Color.White).safeDrawingPadding(),
        contentAlignment = Alignment.Center,
    ) {
        val scale = minOf(maxWidth.value / 840f, maxHeight.value / 1184f)
        Column(
            Modifier
                .size((840 * scale).dp, (1184 * scale).dp)
                .padding(
                    start = (56 * scale).dp,
                    end = (56 * scale).dp,
                    top = (40 * scale).dp,
                    bottom = (56 * scale).dp,
                ),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(
                Modifier.weight(1f).fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Image(
                    painterResource(R.drawable.onboarding_character),
                    null,
                    Modifier.size(
                        (410 * scale).dp,
                        (
                            466 *
                                scale
                        ).dp,
                    ),
                )
                Spacer(Modifier.height((40 * scale).dp))
                Heading("여행을\n시작해 볼까요?", scale)
            }
            Spacer(Modifier.height((32 * scale).dp))
            Button(
                onClick = onStart,
                modifier = Modifier.fillMaxWidth().height((104 * scale).dp),
                shape =
                    RoundedCornerShape(
                        (
                            24 *
                                scale
                        ).dp,
                    ),
                colors = ButtonDefaults.buttonColors(containerColor = Pink, contentColor = Color.White),
            ) {
                Text(
                    "여행 시작하기",
                    fontSize = (32 * scale).sp,
                    lineHeight = (44 * scale).sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

@Composable
private fun Heading(
    text: String,
    scale: Float,
) {
    Text(
        text,
        color = Ink,
        fontSize = (48 * scale).sp,
        lineHeight = (64 * scale).sp,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center,
    )
}

@Preview(widthDp = 840, heightDp = 1184)
@Preview(widthDp = 1280, heightDp = 720)
@Composable
private fun StartPreview() {
    TripArchiveTheme { OnboardingScreen({}) }
}
