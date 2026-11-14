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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.triparchive.R
import com.triparchive.core.designsystem.Pretendard
import com.triparchive.core.designsystem.TripArchiveTheme
import com.triparchive.data.profile.ProfileRelationship
import com.triparchive.presentation.LocalDrivingRestricted

@Composable
fun ProfileEditScreen(
    nickname: String,
    relationship: ProfileRelationship,
    busy: Boolean,
    error: String?,
    onNicknameChange: (String) -> Unit,
    onRelationshipChange: (ProfileRelationship) -> Unit,
    onBack: () -> Unit,
    onSave: () -> Unit,
) {
    val drivingRestricted = LocalDrivingRestricted.current
    BoxWithConstraints(
        Modifier.fillMaxSize().background(Color.White).safeDrawingPadding(),
        contentAlignment = Alignment.Center,
    ) {
        val s = minOf(maxWidth.value / 840f, maxHeight.value / 1184f)
        Column(
            Modifier
                .size((840 * s).dp, (1184 * s).dp)
                .padding(horizontal = (56 * s).dp)
                .padding(top = (40 * s).dp, bottom = (56 * s).dp),
        ) {
            BackControl(s, !busy, onBack)
            Spacer(Modifier.height((48 * s).dp))
            Title("누구와 함께 하나요?", "함께할 사람의 닉네임과 관계를 알려주세요", s)
            Spacer(Modifier.height((48 * s).dp))
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { Avatar(188 * s, 134 * s) }
            Spacer(Modifier.height((27 * s).dp))
            Text("닉네임", color = Label, fontSize = (26 * s).sp, lineHeight = (36 * s).sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height((16 * s).dp))
            Row(
                Modifier
                    .fillMaxWidth()
                    .height((96 * s).dp)
                    .border((2 * s).dp, Pink, RoundedCornerShape((16 * s).dp))
                    .padding(start = (28 * s).dp, end = (20 * s).dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BasicTextField(
                    value = nickname,
                    onValueChange = onNicknameChange,
                    readOnly = busy || drivingRestricted,
                    singleLine = true,
                    modifier = Modifier.weight(1f).semantics { contentDescription = "닉네임 입력" },
                    textStyle =
                        TextStyle(
                            fontFamily = Pretendard,
                            fontWeight = FontWeight.Medium,
                            fontSize = (36 * s).sp,
                            color = Ink,
                        ),
                )
                Box(
                    Modifier
                        .size(
                            (48 * s).dp,
                        ).clickable(
                            interactionSource = null,
                            indication = null,
                            enabled = !busy && !drivingRestricted,
                            role = Role.Button,
                        ) {
                            onNicknameChange("")
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        Modifier.size((28 * s).dp).background(Color(0xFFE5E8EB), CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Image(painterResource(R.drawable.profile_clear), "닉네임 지우기", Modifier.size((20 * s).dp))
                    }
                }
            }
            Spacer(Modifier.height((27 * s).dp))
            Row(
                Modifier.fillMaxWidth().height((263 * s).dp).selectableGroup(),
                horizontalArrangement = Arrangement.spacedBy((24 * s).dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                listOf(
                    ProfileRelationship.PARENT to "부모님",
                    ProfileRelationship.CHILD to "아이",
                    ProfileRelationship.PARTNER to "연인",
                ).forEach { (value, label) ->
                    Column(
                        Modifier
                            .width((200 * s).dp)
                            .height((228 * s).dp)
                            .border(
                                (3 * s).dp,
                                if (relationship ==
                                    value
                                ) {
                                    Pink
                                } else {
                                    Color.Transparent
                                },
                                RoundedCornerShape((20 * s).dp),
                            ).selectable(
                                selected = relationship == value,
                                enabled = !busy && !drivingRestricted,
                                interactionSource = null,
                                indication = null,
                                role = Role.RadioButton,
                                onClick = { onRelationshipChange(value) },
                            ).padding(vertical = (10 * s).dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Avatar(156 * s, 100 * s)
                        Spacer(Modifier.height((20 * s).dp))
                        Text(
                            label,
                            color = Label,
                            fontWeight = FontWeight.Medium,
                            fontSize = (28 * s).sp,
                            lineHeight =
                                (
                                    44 *
                                        s
                                ).sp,
                        )
                    }
                }
            }
            if (error != null) Text(error, color = Pink, fontSize = (24 * s).sp)
            Spacer(Modifier.weight(1f))
            ActionButton(
                if (busy) "저장 중…" else "프로필 수정",
                Pink,
                s,
                !busy && !drivingRestricted && nickname.isNotBlank(),
                onSave,
                Modifier.fillMaxWidth(),
            )
        }
    }
}

@Preview(widthDp = 840, heightDp = 1184, showBackground = true)
@Composable
private fun ProfileEditPreview() {
    TripArchiveTheme { ProfileEditScreen("사용자1", ProfileRelationship.PARENT, false, null, {}, {}, {}, {}) }
}
