package com.triparchive

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import org.junit.Rule
import org.junit.Test

class OnboardingTest {
    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun startOpensProfileAndBackReturnsToWelcome() {
        compose.onNodeWithText("여행 시작하기").assertIsDisplayed().performClick()
        compose.onNodeWithText("함께할 사람의 닉네임을 알려주세요").assertIsDisplayed()
        compose.onNodeWithContentDescription("닉네임 지우기").performClick()
        compose.onNodeWithText("다음으로").assertIsNotEnabled()
        compose.onNodeWithContentDescription("닉네임 입력").performTextInput("준")
        compose.onNodeWithText("다음으로").performClick()
        compose.onNodeWithText("함께할 사람과 나와의 관계를 선택해주세요").assertIsDisplayed()
        compose.onNodeWithText("아이").performClick()
        compose.onNodeWithText("프로필 만들기").performClick()
        compose.onNodeWithText("준 · 아이").assertIsDisplayed()
        compose.onNodeWithText("확인").performClick()
        compose.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        compose.onNodeWithText("준").assertIsDisplayed()
        compose.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        compose.onNodeWithText("여행 시작하기").assertIsDisplayed()
    }
}
