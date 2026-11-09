package com.triparchive.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import com.triparchive.data.profile.ProfileRelationship
import com.triparchive.presentation.onboarding.OnboardingScreen
import com.triparchive.presentation.onboarding.ProfileScreen
import com.triparchive.presentation.onboarding.ProfileViewModel
import kotlinx.serialization.Serializable

@Serializable
private data object Welcome : NavKey

@Serializable
private data object Nickname : NavKey

@Serializable
private data object Relationship : NavKey

@Composable
fun OnboardingNavigation(
    modifier: Modifier = Modifier,
    viewModel: ProfileViewModel = hiltViewModel(),
) {
    val backStack = rememberNavBackStack(Welcome)
    val form by viewModel.state.collectAsState()
    val nickname = form.nickname
    val relationship =
        when (form.relationship) {
            ProfileRelationship.PARENT -> "부모님"
            ProfileRelationship.CHILD -> "아이"
            ProfileRelationship.PARTNER -> "연인"
        }
    val updateRelationship: (String) -> Unit = { label ->
        when (label) {
            "부모님" -> viewModel.updateRelationship(ProfileRelationship.PARENT)
            "아이" -> viewModel.updateRelationship(ProfileRelationship.CHILD)
            "연인" -> viewModel.updateRelationship(ProfileRelationship.PARTNER)
        }
    }
    Box(modifier) {
        NavDisplay(
            backStack = backStack,
            onBack = { if (!form.saving && backStack.size > 1) backStack.removeLastOrNull() },
            entryProvider =
                entryProvider {
                    entry<Welcome> {
                        OnboardingScreen(onStart = {
                            if (backStack.lastOrNull() ==
                                Welcome
                            ) {
                                viewModel.startNewProfile()
                                backStack.add(Nickname)
                            }
                        })
                    }
                    entry<Nickname> {
                        ProfileScreen(
                            false,
                            nickname,
                            relationship,
                            viewModel::updateNickname,
                            updateRelationship,
                            { backStack.removeLastOrNull() },
                            {
                                if (nickname.isNotBlank() &&
                                    backStack.lastOrNull() == Nickname
                                ) {
                                    backStack.add(Relationship)
                                }
                            },
                            saving = form.saving,
                            saved = form.savedId != null,
                        )
                    }
                    entry<Relationship> {
                        ProfileScreen(
                            true,
                            nickname,
                            relationship,
                            viewModel::updateNickname,
                            updateRelationship,
                            { backStack.removeLastOrNull() },
                            viewModel::save,
                            saving = form.saving,
                            saved = form.savedId != null,
                        )
                    }
                },
        )
        form.error?.let { message ->
            AlertDialog(
                onDismissRequest = viewModel::dismissMessage,
                title = { Text("저장 실패") },
                text = { Text(message) },
                confirmButton = { TextButton(onClick = viewModel::save) { Text("다시 시도") } },
                dismissButton = { TextButton(onClick = viewModel::dismissMessage) { Text("닫기") } },
            )
        }
        if (form.showSuccess) {
            AlertDialog(
                onDismissRequest = viewModel::dismissMessage,
                title = { Text("프로필을 만들었어요") },
                text = { Text("${nickname.trim()} · $relationship") },
                confirmButton = { TextButton(onClick = viewModel::dismissMessage) { Text("확인") } },
            )
        }
    }
}
