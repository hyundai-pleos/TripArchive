package com.triparchive.navigation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import com.triparchive.data.profile.MAX_PROFILES
import com.triparchive.data.profile.ProfileRelationship
import com.triparchive.presentation.onboarding.OnboardingScreen
import com.triparchive.presentation.onboarding.ProfileScreen
import com.triparchive.presentation.onboarding.ProfileViewModel
import com.triparchive.presentation.profile.ProfileListState
import com.triparchive.presentation.profile.ProfileSelectionScreen
import com.triparchive.presentation.profile.ProfileSelectionViewModel
import kotlinx.serialization.Serializable

@Serializable
private data object Startup : NavKey

@Serializable
private data object Welcome : NavKey

@Serializable
private data object Profiles : NavKey

@Serializable
private data object Nickname : NavKey

@Serializable
private data object Relationship : NavKey

@Composable
fun OnboardingNavigation(
    modifier: Modifier = Modifier,
    viewModel: ProfileViewModel = hiltViewModel(),
    selectionViewModel: ProfileSelectionViewModel = hiltViewModel(),
) {
    val backStack = rememberNavBackStack(Startup)
    val form by viewModel.state.collectAsState()
    val listState by selectionViewModel.state.collectAsState()
    val selectedId by selectionViewModel.selectedId.collectAsState()
    val showSelection by selectionViewModel.showSelection.collectAsState()
    val profiles = (listState as? ProfileListState.Ready)?.profiles
    LaunchedEffect(profiles, form.savedId) {
        if (profiles != null) {
            if (backStack.lastOrNull() == Startup) {
                backStack.clear()
                backStack.add(if (profiles.isEmpty()) Welcome else Profiles)
            } else if (form.savedId != null && backStack.lastOrNull() == Relationship &&
                profiles.any { it.id == form.savedId }
            ) {
                backStack.clear()
                backStack.add(Profiles)
                viewModel.dismissMessage()
            } else if (backStack.lastOrNull() == Profiles && profiles.isEmpty()) {
                backStack.clear()
                backStack.add(Welcome)
            }
        }
    }
    if (profiles == null) {
        Column(
            modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            if (listState == ProfileListState.Error) {
                Text("프로필을 불러오지 못했어요.")
                Button(onClick = selectionViewModel::retry) { Text("다시 시도") }
            } else {
                CircularProgressIndicator()
            }
        }
        return
    }
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
    val addProfile: () -> Unit = {
        if (profiles.size < MAX_PROFILES && (backStack.lastOrNull() == Profiles || backStack.lastOrNull() == Welcome)) {
            viewModel.startNewProfile()
            backStack.add(Nickname)
        }
    }
    Box(modifier) {
        NavDisplay(
            backStack = backStack,
            onBack = { if (!form.saving && backStack.size > 1) backStack.removeLastOrNull() },
            entryProvider =
                entryProvider {
                    entry<Startup> {
                        Box(
                            Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center,
                        ) { CircularProgressIndicator() }
                    }
                    entry<Welcome> { OnboardingScreen(onStart = addProfile) }
                    entry<Profiles> { ProfileSelectionScreen(profiles, selectionViewModel::select, addProfile) }
                    entry<Nickname> {
                        ProfileScreen(
                            false,
                            form.nickname,
                            relationship,
                            viewModel::updateNickname,
                            updateRelationship,
                            { backStack.removeLastOrNull() },
                            {
                                if (form.nickname.isNotBlank() &&
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
                            form.nickname,
                            relationship,
                            viewModel::updateNickname,
                            updateRelationship,
                            { backStack.removeLastOrNull() },
                            viewModel::save,
                            saving = form.saving,
                            saved =
                                form.savedId != null,
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
        val selected = profiles.firstOrNull { it.id == selectedId }
        if (showSelection && selected != null) {
            AlertDialog(
                onDismissRequest = selectionViewModel::dismissSelection,
                title = { Text("프로필을 선택했어요") },
                text = { Text(selected.nickname) },
                confirmButton = { TextButton(onClick = selectionViewModel::dismissSelection) { Text("확인") } },
            )
        }
    }
}
