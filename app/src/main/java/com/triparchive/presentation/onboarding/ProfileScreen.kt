package com.triparchive.presentation.onboarding

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.IconButton
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

private val ProfilePink = Color(0xFFFF66AF)

@Composable
fun ProfileScreen(
    relationshipStep: Boolean,
    nickname: String,
    relationship: String,
    onNicknameChange: (String) -> Unit,
    onRelationshipChange: (String) -> Unit,
    onBack: () -> Unit,
    onNext: () -> Unit,
    saving: Boolean = false,
    saved: Boolean = false,
) {
    BoxWithConstraints(
        Modifier.fillMaxSize().background(Color.White).safeDrawingPadding(),
        contentAlignment = Alignment.Center,
    ) {
        val s = minOf(maxWidth.value / 840, maxHeight.value / 1184)
        Column(
            Modifier
                .size((840 * s).dp, (1184 * s).dp)
                .padding(horizontal = (56 * s).dp)
                .padding(top = (40 * s).dp, bottom = (56 * s).dp),
        ) {
            IconButton(onClick = onBack, enabled = !saving, modifier = Modifier.size((64 * s).dp)) {
                Image(painterResource(R.drawable.profile_back), "뒤로", Modifier.size((19 * s).dp, (35 * s).dp))
            }
            Spacer(Modifier.height((48 * s).dp))
            Text(
                "누구와 함께 하나요?",
                color = Color(0xFF191F28),
                fontSize = (48 * s).sp,
                lineHeight = (64 * s).sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height((16 * s).dp))
            Text(
                if (relationshipStep) "함께할 사람과 나와의 관계를 선택해주세요" else "함께할 사람의 닉네임을 알려주세요",
                color = Color(0xFF6B7684),
                fontSize = (28 * s).sp,
                lineHeight = (42 * s).sp,
            )
            Spacer(Modifier.height((48 * s).dp))
            if (relationshipStep) {
                Spacer(Modifier.height((56 * s).dp))
                Row(
                    Modifier.fillMaxWidth().height((263 * s).dp).selectableGroup(),
                    horizontalArrangement = Arrangement.spacedBy((24 * s).dp, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    listOf("부모님", "아이", "연인").forEach { label ->
                        Column(
                            Modifier
                                .width((200 * s).dp)
                                .height((263 * s).dp)
                                .border(
                                    (3 * s).dp,
                                    if (relationship ==
                                        label
                                    ) {
                                        ProfilePink
                                    } else {
                                        Color.Transparent
                                    },
                                    RoundedCornerShape((20 * s).dp),
                                ).selectable(
                                    relationship == label,
                                    enabled = !saving && !saved,
                                    interactionSource = null,
                                    indication = null,
                                    role = Role.RadioButton,
                                    onClick = { onRelationshipChange(label) },
                                ).padding(vertical = (20 * s).dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Avatar(156 * s, 100 * s)
                            Spacer(Modifier.height((20 * s).dp))
                            Text(
                                label,
                                color = Color(0xFF4E5968),
                                fontSize = (28 * s).sp,
                                lineHeight = (44 * s).sp,
                                fontWeight = FontWeight.Medium,
                            )
                        }
                    }
                }
            } else {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { Avatar(188 * s, 134 * s) }
                Spacer(Modifier.height((56 * s).dp))
                Text("닉네임", color = Color(0xFF4E5968), fontSize = (26 * s).sp, lineHeight = (36 * s).sp)
                Spacer(Modifier.height((16 * s).dp))
                Row(
                    Modifier
                        .fillMaxWidth()
                        .height((96 * s).dp)
                        .border(
                            (2 * s).dp,
                            ProfilePink,
                            RoundedCornerShape(
                                (
                                    16 *
                                        s
                                ).dp,
                            ),
                        ).padding(start = (28 * s).dp, end = (20 * s).dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    BasicTextField(
                        value = nickname,
                        readOnly = saving || saved,
                        onValueChange = onNicknameChange,
                        singleLine = true,
                        modifier = Modifier.weight(1f).semantics { contentDescription = "닉네임 입력" },
                        textStyle =
                            TextStyle(
                                fontFamily = Pretendard,
                                fontWeight = FontWeight.Medium,
                                fontSize =
                                    (
                                        36 *
                                            s
                                    ).sp,
                                color = Color(0xFF191F28),
                            ),
                    )
                    IconButton(onClick = { onNicknameChange("") }, enabled = !saving && !saved) {
                        Box(
                            Modifier.size((28 * s).dp).background(Color(0xFFE5E8EB), CircleShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            Image(painterResource(R.drawable.profile_clear), "닉네임 지우기", Modifier.size((20 * s).dp))
                        }
                    }
                }
            }
            Spacer(Modifier.weight(1f))
            Button(
                onClick = onNext,
                enabled =
                    !saving && !(relationshipStep && saved) && nickname.isNotBlank() &&
                        (!relationshipStep || relationship.isNotBlank()),
                modifier = Modifier.fillMaxWidth().height((96 * s).dp),
                shape = RoundedCornerShape((16 * s).dp),
                colors = ButtonDefaults.buttonColors(containerColor = ProfilePink, contentColor = Color.White),
            ) {
                Text(
                    if (saving) {
                        "저장 중…"
                    } else if (relationshipStep &&
                        saved
                    ) {
                        "저장 완료"
                    } else if (relationshipStep) {
                        "프로필 만들기"
                    } else {
                        "다음으로"
                    },
                    fontSize = (32 * s).sp,
                    lineHeight = (44 * s).sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

@Composable
private fun Avatar(
    size: Float,
    imageSize: Float,
) {
    Box(Modifier.size(size.dp).background(Color(0xFFDCEBFF), CircleShape), contentAlignment = Alignment.Center) {
        Image(painterResource(R.drawable.onboarding_profile), null, Modifier.size(imageSize.dp))
    }
}

@Preview(widthDp = 840, heightDp = 1184)
@Composable
private fun NicknamePreview() {
    TripArchiveTheme { ProfileScreen(false, "사용자1", "부모님", {}, {}, {}, {}) }
}

@Preview(widthDp = 840, heightDp = 1184)
@Preview(widthDp = 1280, heightDp = 720)
@Composable
private fun RelationshipPreview() {
    TripArchiveTheme { ProfileScreen(true, "사용자1", "부모님", {}, {}, {}, {}) }
}
