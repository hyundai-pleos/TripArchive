package com.triparchive.presentation.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.BoxWithConstraintsScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.paint
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.triparchive.R
import com.triparchive.core.designsystem.TripArchiveTheme
import com.triparchive.data.profile.Profile
import com.triparchive.data.profile.ProfileRelationship

@Composable
fun HomeScreen(
    profile: Profile,
    onProfileClick: () -> Unit = {},
    renderCharacter: Boolean = true,
    modifier: Modifier = Modifier,
    background: Painter? = painterResource(R.drawable.home_street_background),
) {
    BoxWithConstraints(
        modifier
            .fillMaxSize()
            .then(if (background == null) Modifier else Modifier.homeBackground(background))
            .windowInsetsPadding(WindowInsets.systemBars.union(WindowInsets.displayCutout)),
        contentAlignment = Alignment.TopCenter,
    ) {
        val scale = minOf(maxWidth.value / 840f, maxHeight.value / 1184f)
        if (renderCharacter) {
            HomeCharacter(profile.relationship, scale)
        }

        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(start = (56 * scale).dp, end = (56 * scale).dp, top = (48 * scale).dp)
                    .height((80 * scale).dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                Modifier
                    .weight(
                        1f,
                    ).clickable(
                        interactionSource = null,
                        indication = null,
                        role = Role.Button,
                        onClick = onProfileClick,
                    ),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier.size((72 * scale).dp).background(Color(0xFFDCEBFF), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Image(painterResource(R.drawable.onboarding_profile), null, Modifier.size((56 * scale).dp))
                }
                Text(
                    text = "${profile.nickname}님",
                    modifier = Modifier.padding(start = (16 * scale).dp, end = (16 * scale).dp),
                    color = Color(0xFF191F28),
                    fontSize = (24 * scale).sp,
                    lineHeight = (32 * scale).sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Box(
                Modifier
                    .size((72 * scale).dp)
                    .background(Color(0xFFFF66AF), CircleShape)
                    .semantics {
                        role = Role.Button
                        contentDescription = "지도"
                        disabled()
                    },
                contentAlignment = Alignment.Center,
            ) {
                Image(painterResource(R.drawable.home_map), null, Modifier.size((56 * scale).dp))
            }
        }
    }
}

internal fun Modifier.homeBackground(painter: Painter): Modifier =
    background(Brush.verticalGradient(listOf(Color(0xFFC9FFFF), Color.White)))
        .paint(painter, sizeToIntrinsics = false, contentScale = ContentScale.Crop)

@Composable
internal fun RetainedHomeCharacter(
    relationship: ProfileRelationship,
    visible: Boolean,
    active: Boolean,
    prepare: Boolean,
) {
    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .zIndex(-1f)
            .windowInsetsPadding(WindowInsets.systemBars.union(WindowInsets.displayCutout)),
    ) {
        val scale = minOf(maxWidth.value / 840f, maxHeight.value / 1184f)
        val visibility = Modifier.graphicsLayer { alpha = if (visible) 1f else 0f }
        HomeCharacter(relationship, scale, visibility, active, prepare)
    }
}

@Composable
private fun BoxWithConstraintsScope.HomeCharacter(
    relationship: ProfileRelationship,
    scale: Float,
    modifier: Modifier = Modifier,
    active: Boolean = true,
    prepare: Boolean = false,
) {
    ProfileCharacter(
        relationship = relationship,
        modifier =
            Modifier
                .align(Alignment.Center)
                .then(modifier)
                .width((640 * scale).dp)
                .height((760 * scale).dp),
        active = active,
        prepare = prepare,
    )
}

@Preview(widthDp = 840, heightDp = 1184, showBackground = true)
@Preview(widthDp = 1280, heightDp = 720, showBackground = true)
@Composable
private fun HomePreview() {
    TripArchiveTheme { HomeScreen(Profile(nickname = "사용자1", relationship = ProfileRelationship.PARENT)) }
}
