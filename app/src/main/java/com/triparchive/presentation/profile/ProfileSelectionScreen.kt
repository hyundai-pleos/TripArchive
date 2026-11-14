package com.triparchive.presentation.profile

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.triparchive.R
import com.triparchive.data.profile.MAX_PROFILES
import com.triparchive.data.profile.Profile
import com.triparchive.presentation.LocalDrivingRestricted

private val AvatarColors = listOf(Color(0xFFDCEBFF), Color(0xFFFF66AF), Color(0xFF6E675A), Color(0xFF4A3CF5))

@Composable
fun ProfileSelectionScreen(
    profiles: List<Profile>,
    onSelect: (Long) -> Unit,
    onAdd: () -> Unit,
) {
    val drivingRestricted = LocalDrivingRestricted.current
    BoxWithConstraints(
        Modifier.fillMaxSize().background(Color.White).safeDrawingPadding(),
        contentAlignment = Alignment.Center,
    ) {
        val s = minOf(maxWidth.value / 840, maxHeight.value / 1184)
        Column(
            Modifier.size((840 * s).dp, (1184 * s).dp).padding(top = (144 * s).dp, bottom = (48 * s).dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                "누구와 여행을 시작할까요?",
                color = Color(0xFF191F28),
                fontWeight = FontWeight.Bold,
                fontSize = (48 * s).sp,
                lineHeight =
                    (
                        64 *
                            s
                    ).sp,
            )
            Spacer(Modifier.height((16 * s).dp))
            Text("프로필을 눌러 시작해 주세요", color = Color(0xFF6B7684), fontSize = (28 * s).sp, lineHeight = (42 * s).sp)
            Spacer(Modifier.height((64 * s).dp))
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier.width((560 * s).dp).weight(1f),
                horizontalArrangement = Arrangement.spacedBy((80 * s).dp),
                verticalArrangement =
                    Arrangement.spacedBy(
                        (
                            52 *
                                s
                        ).dp,
                    ),
            ) {
                itemsIndexed(profiles, key = { _, profile -> profile.id }) { index, profile ->
                    ProfileTile(
                        profile.nickname,
                        AvatarColors[index % AvatarColors.size],
                        s,
                        false,
                        !drivingRestricted,
                    ) { onSelect(profile.id) }
                }
                if (profiles.size < MAX_PROFILES) {
                    item(key = "add") { ProfileTile("프로필 추가", Color(0xFFD0D0D0), s, true, !drivingRestricted, onAdd) }
                }
            }
        }
    }
}

@Composable
private fun ProfileTile(
    label: String,
    color: Color,
    scale: Float,
    add: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Column(
        Modifier
            .width((240 * scale).dp)
            .height((304 * scale).dp)
            .clickable(
                interactionSource = null,
                indication = null,
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.size((240 * scale).dp).background(color, CircleShape), contentAlignment = Alignment.Center) {
            Image(
                painterResource(if (add) R.drawable.profile_add else R.drawable.onboarding_profile),
                null,
                Modifier.size(((if (add) 92 else 172) * scale).dp),
            )
        }
        Spacer(Modifier.height((20 * scale).dp))
        Text(
            label,
            color = Color(0xFF4E5968),
            fontWeight = FontWeight.Medium,
            fontSize = (28 * scale).sp,
            lineHeight =
                (
                    44 *
                        scale
                ).sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}
