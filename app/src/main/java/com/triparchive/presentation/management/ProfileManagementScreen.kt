package com.triparchive.presentation.management

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import com.triparchive.R
import com.triparchive.core.designsystem.TripArchiveTheme
import com.triparchive.data.profile.Profile
import com.triparchive.data.profile.ProfileMapColor
import com.triparchive.data.profile.ProfileRelationship
import com.triparchive.presentation.LocalDrivingRestricted

internal val Pink = Color(0xFFFF66AF)
internal val Ink = Color(0xFF191F28)
internal val Gray = Color(0xFF6B7684)
internal val Label = Color(0xFF4E5968)
internal val LightGray = Color(0xFFD0D0D0)

@Composable
fun ProfileManagementScreen(
    profile: Profile,
    showDeleteDialog: Boolean,
    busy: Boolean,
    error: String?,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onCancelDelete: () -> Unit,
    onConfirmDelete: () -> Unit,
    onLogout: () -> Unit,
    onMapColorChange: (ProfileMapColor) -> Unit = {},
) {
    val drivingRestricted = LocalDrivingRestricted.current
    BoxWithConstraints(
        Modifier.fillMaxSize().background(Color.White).safeDrawingPadding(),
        contentAlignment = Alignment.Center,
    ) {
        val scale = minOf(maxWidth.value / 840f, maxHeight.value / 1184f)
        Column(
            Modifier
                .size((840 * scale).dp, (1184 * scale).dp)
                .padding(horizontal = (56 * scale).dp)
                .padding(top = (40 * scale).dp, bottom = (56 * scale).dp),
        ) {
            BackControl(scale, !busy, onBack)
            Spacer(Modifier.height((48 * scale).dp))
            Title("프로필을 관리해요", "프로필을 수정하거나 삭제할 수 있어요", scale)
            Spacer(Modifier.height((107 * scale).dp))
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Avatar(240 * scale, 172 * scale)
                Spacer(Modifier.height((20 * scale).dp))
                Text(
                    profile.nickname,
                    Modifier.width((240 * scale).dp),
                    color = Label,
                    textAlign = TextAlign.Center,
                    fontWeight = FontWeight.Medium,
                    fontSize = (28 * scale).sp,
                    lineHeight = (44 * scale).sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.height((20 * scale).dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Action(
                    "프로필 수정",
                    R.drawable.management_edit,
                    Pink,
                    scale,
                    !busy && !drivingRestricted,
                    onEdit,
                    Modifier.weight(1f),
                )
                Action(
                    "프로필 삭제",
                    R.drawable.management_delete,
                    LightGray,
                    scale,
                    !busy && !drivingRestricted,
                    onDelete,
                    Modifier.weight(1f),
                )
                Action(
                    "로그아웃",
                    R.drawable.management_logout,
                    LightGray,
                    scale,
                    !busy && !drivingRestricted,
                    onLogout,
                    Modifier.weight(1f),
                )
            }
            Spacer(Modifier.height((28 * scale).dp))
            MapColorSelector(
                selected = ProfileMapColor.fromStorage(profile.mapColor),
                enabled = !busy && !drivingRestricted,
                scale = scale,
                onSelect = onMapColorChange,
            )
            if (!showDeleteDialog && error != null) {
                Text(error, color = Pink, fontSize = (26 * scale).sp)
            }
        }
        if (showDeleteDialog) {
            DeleteDialog(profile.nickname, busy, drivingRestricted, error, scale, onCancelDelete, onConfirmDelete)
        }
    }
}

@Composable
private fun MapColorSelector(
    selected: ProfileMapColor,
    enabled: Boolean,
    scale: Float,
    onSelect: (ProfileMapColor) -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        Text(
            "지도 색상 변경",
            color = Color.Black,
            fontSize = (28 * scale).sp,
            lineHeight = (44 * scale).sp,
            fontWeight = FontWeight.Medium,
        )
        Spacer(Modifier.height((20 * scale).dp))
        Row(
            Modifier.fillMaxWidth().padding(horizontal = (16 * scale).dp).selectableGroup(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ProfileMapColor.entries.forEach { choice ->
                val isSelected = selected == choice
                Box(
                    Modifier
                        .size((80 * scale).dp)
                        .background(Color(choice.argb), CircleShape)
                        .then(
                            if (isSelected) {
                                Modifier.border((3 * scale).dp, Color.Black, CircleShape)
                            } else {
                                Modifier
                            },
                        ).semantics { contentDescription = "지도 색상 ${choice.label}" }
                        .selectable(
                            selected = isSelected,
                            enabled = enabled,
                            interactionSource = null,
                            indication = null,
                            role = Role.RadioButton,
                            onClick = { onSelect(choice) },
                        ),
                )
            }
        }
    }
}

@Composable
private fun Action(
    label: String,
    icon: Int,
    color: Color,
    s: Float,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier,
) {
    Column(
        modifier.clickable(
            interactionSource = null,
            indication = null,
            enabled = enabled,
            role = Role.Button,
            onClick = onClick,
        ),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.size((144 * s).dp).background(color, CircleShape), contentAlignment = Alignment.Center) {
            Image(painterResource(icon), null, Modifier.size((70 * s).dp))
        }
        Spacer(Modifier.height((20 * s).dp))
        Text(label, color = Label, fontSize = (28 * s).sp, lineHeight = (44 * s).sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun DeleteDialog(
    nickname: String,
    busy: Boolean,
    drivingRestricted: Boolean,
    error: String?,
    s: Float,
    onCancel: () -> Unit,
    onConfirm: () -> Unit,
) {
    Dialog(
        onDismissRequest = { if (!busy) onCancel() },
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        val dialogWindow = (LocalView.current.parent as? DialogWindowProvider)?.window
        SideEffect { dialogWindow?.setDimAmount(0.2f) }
        Column(
            Modifier
                .width(
                    (758 * s).dp,
                ).heightIn(min = (514 * s).dp)
                .background(Color.White, RoundedCornerShape((20 * s).dp))
                .padding(horizontal = (44 * s).dp, vertical = (72 * s).dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                buildAnnotatedString {
                    append("정말 ")
                    withStyle(SpanStyle(color = Pink)) { append(nickname) }
                    append(" 프로필을 삭제할까요?")
                },
                color = Ink,
                fontSize = (42 * s).sp,
                lineHeight = (64 * s).sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height((10 * s).dp))
            Text(
                "삭제하면 해당 프로필과 관련된 모든 데이터가 삭제되며\n다시 되돌릴 수 없어요.",
                color = Gray,
                fontSize = (28 * s).sp,
                lineHeight = (42 * s).sp,
                textAlign = TextAlign.Center,
            )
            if (error != null) {
                Spacer(Modifier.height((8 * s).dp))
                Text(error, color = Pink, fontSize = (26 * s).sp, textAlign = TextAlign.Center)
            }
            Spacer(Modifier.height((32 * s).dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy((30 * s).dp)) {
                ActionButton(
                    if (busy) "삭제 중…" else "삭제",
                    Pink,
                    s,
                    !busy && !drivingRestricted,
                    onConfirm,
                    Modifier.weight(1f),
                )
                ActionButton("취소", LightGray, s, !busy, onCancel, Modifier.weight(1f))
            }
        }
    }
}

@Composable
internal fun ActionButton(
    text: String,
    color: Color,
    s: Float,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    textColor: Color = Color.White,
) {
    Box(
        modifier
            .height((96 * s).dp)
            .background(if (enabled) color else LightGray, RoundedCornerShape((16 * s).dp))
            .clickable(
                interactionSource = null,
                indication = null,
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            color = textColor,
            fontSize = (32 * s).sp,
            lineHeight = (44 * s).sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
internal fun BackControl(
    s: Float,
    enabled: Boolean,
    onBack: () -> Unit,
) {
    Box(
        Modifier
            .size(
                (64 * s).dp,
            ).clickable(
                interactionSource = null,
                indication = null,
                enabled = enabled,
                role = Role.Button,
                onClick = onBack,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Image(painterResource(R.drawable.profile_back), "뒤로", Modifier.size((19 * s).dp, (35 * s).dp))
    }
}

@Composable
internal fun Avatar(
    size: Float,
    imageSize: Float,
) {
    Box(Modifier.size(size.dp).background(Color(0xFFDCEBFF), CircleShape), contentAlignment = Alignment.Center) {
        Image(painterResource(R.drawable.onboarding_profile), null, Modifier.size(imageSize.dp))
    }
}

@Composable
internal fun Title(
    title: String,
    description: String,
    s: Float,
) {
    Text(title, color = Ink, fontSize = (48 * s).sp, lineHeight = (64 * s).sp, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height((16 * s).dp))
    Text(description, color = Gray, fontSize = (28 * s).sp, lineHeight = (42 * s).sp)
}

@Preview(widthDp = 840, heightDp = 1184, showBackground = true)
@Composable
private fun ManagementPreview() {
    TripArchiveTheme {
        ProfileManagementScreen(
            Profile(
                nickname = "사용자1",
                relationship = ProfileRelationship.PARENT,
            ),
            false,
            false,
            null,
            {
            },
            {},
            {},
            {},
            {},
            {},
        )
    }
}
