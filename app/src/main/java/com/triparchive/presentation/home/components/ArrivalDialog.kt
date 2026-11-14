package com.triparchive.presentation.home.components

import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import com.triparchive.core.designsystem.TripArchiveTheme
import com.triparchive.data.navigation.DestinationCategory
import com.triparchive.presentation.management.Gray
import com.triparchive.presentation.management.Ink
import com.triparchive.presentation.management.Pink

@Composable
internal fun ArrivalDialog(
    destination: String,
    secondsRemaining: Int,
    scale: Float,
    isDemo: Boolean,
    onContinue: () -> Unit,
    onFinish: () -> Unit,
    category: DestinationCategory? = null,
) {
    Dialog(
        onDismissRequest = onContinue,
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = false),
    ) {
        val window = (LocalView.current.parent as? DialogWindowProvider)?.window
        SideEffect {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            window?.setDimAmount(0f)
        }
        ArrivalDialogContent(destination, secondsRemaining, scale, isDemo, onContinue, onFinish, category)
    }
}

@Composable
private fun ArrivalDialogContent(
    destination: String,
    secondsRemaining: Int,
    scale: Float,
    isDemo: Boolean,
    onContinue: () -> Unit,
    onFinish: () -> Unit,
    category: DestinationCategory? = null,
) {
    Column(
        Modifier
            .width((758 * scale).dp)
            .height((700 * scale).dp)
            .background(Color.White, RoundedCornerShape((20 * scale).dp))
            .padding((10 * scale).dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy((32 * scale).dp, Alignment.CenterVertically),
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            if (isDemo) Text("데모 · 도착 재현 중", color = Gray, fontSize = (22 * scale).sp)
            Text(
                "${destination}에 도착했어요!",
                modifier = Modifier.fillMaxWidth().padding((10 * scale).dp),
                color = Ink,
                fontSize = (42 * scale).sp,
                lineHeight = (64 * scale).sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        // Only confirmed destination data is available; do not invent a departure or trip numbering.
        Column(
            Modifier
                .fillMaxWidth()
                .background(Color(0xFFF9FAFB), RoundedCornerShape((24 * scale).dp))
                .padding((24 * scale).dp),
        ) {
            Text("도착", color = Gray, fontSize = (24 * scale).sp, lineHeight = (34 * scale).sp)
            Row(verticalAlignment = Alignment.CenterVertically) {
                DestinationCategoryIcon(category, Modifier.size((48 * scale).dp))
                Spacer(Modifier.width((12 * scale).dp))
                if (category != null) {
                    Text(
                        category.label,
                        color = Pink,
                        fontSize = (24 * scale).sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.width((12 * scale).dp))
                }
                Text(
                    destination,
                    modifier = Modifier.weight(1f),
                    color = Color(0xFF333D4B),
                    fontSize = (32 * scale).sp,
                    lineHeight = (44 * scale).sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Column(Modifier.width((510 * scale).dp), verticalArrangement = Arrangement.spacedBy((12 * scale).dp)) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .height((104 * scale).dp)
                    .background(Pink, RoundedCornerShape((24 * scale).dp))
                    .clickable(role = Role.Button, interactionSource = null, indication = null, onClick = onContinue),
                horizontalArrangement = Arrangement.spacedBy((12 * scale).dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("도착 확인하고 계속하기", color = Color.White, fontSize = (32 * scale).sp, fontWeight = FontWeight.Bold)
                Box(
                    Modifier.size((36 * scale).dp).background(Color.White, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        secondsRemaining.toString(),
                        color = Pink,
                        fontSize = (22 * scale).sp,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
            Box(
                Modifier
                    .fillMaxWidth()
                    .height((96 * scale).dp)
                    .background(Color(0xFFF2F4F6), RoundedCornerShape((24 * scale).dp))
                    .clickable(role = Role.Button, interactionSource = null, indication = null, onClick = onFinish),
                contentAlignment = Alignment.Center,
            ) {
                Text("여기서 여행 마치기", color = Ink, fontSize = (32 * scale).sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Preview(widthDp = 758, heightDp = 700)
@Composable
private fun ArrivalDialogPreview() {
    TripArchiveTheme { ArrivalDialogContent("강릉 안목해변", 5, 1f, false, {}, {}) }
}
