package com.triparchive.navigation

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.togetherWith
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
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.zIndex
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import com.triparchive.R
import com.triparchive.data.profile.MAX_PROFILES
import com.triparchive.data.profile.ProfileRelationship
import com.triparchive.presentation.home.CharacterRenderResources
import com.triparchive.presentation.home.DestinationHome
import com.triparchive.presentation.home.RetainedHomeCharacter
import com.triparchive.presentation.home.homeBackground
import com.triparchive.presentation.management.ManagementEvent
import com.triparchive.presentation.management.ProfileEditScreen
import com.triparchive.presentation.management.ProfileManagementScreen
import com.triparchive.presentation.management.ProfileManagementViewModel
import com.triparchive.presentation.map.VisitMapRoute
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
private data class Home(
    val profileId: Long,
) : NavKey

@Serializable
private data class VisitMap(
    val profileId: Long,
) : NavKey

@Serializable
private data class ManageProfile(
    val profileId: Long,
) : NavKey

@Serializable
private data class EditProfile(
    val profileId: Long,
) : NavKey

@Serializable
private data object Nickname : NavKey

@Serializable
private data object Relationship : NavKey

private val instantTransitionMetadata =
    NavDisplay.transitionSpec { EnterTransition.None togetherWith ExitTransition.None } +
        NavDisplay.popTransitionSpec { EnterTransition.None togetherWith ExitTransition.None } +
        NavDisplay.predictivePopTransitionSpec { EnterTransition.None togetherWith ExitTransition.None }

@Composable
fun OnboardingNavigation(
    modifier: Modifier = Modifier,
    viewModel: ProfileViewModel = hiltViewModel(),
    selectionViewModel: ProfileSelectionViewModel = hiltViewModel(),
    managementViewModel: ProfileManagementViewModel = hiltViewModel(),
) {
    val backStack = rememberNavBackStack(Startup)
    val form by viewModel.state.collectAsState()
    val listState by selectionViewModel.state.collectAsState()
    val management by managementViewModel.state.collectAsState()
    LaunchedEffect(managementViewModel) {
        managementViewModel.events.collect { event ->
            when (event) {
                ManagementEvent.Saved -> {
                    if (backStack.lastOrNull() is EditProfile) backStack.removeLastOrNull()
                }

                ManagementEvent.Deleted -> {
                    selectionViewModel.clearSelection()
                    backStack.clear()
                    backStack.add(Profiles)
                }
            }
        }
    }
    val profiles = (listState as? ProfileListState.Ready)?.profiles
    LaunchedEffect(profiles, form.savedId, backStack.lastOrNull()) {
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
            } else if (
                when (val current = backStack.lastOrNull()) {
                    is Home -> profiles.none { it.id == current.profileId }
                    is VisitMap -> profiles.none { it.id == current.profileId }
                    is ManageProfile -> profiles.none { it.id == current.profileId }
                    is EditProfile -> profiles.none { it.id == current.profileId }
                    else -> false
                }
            ) {
                backStack.clear()
                backStack.add(if (profiles.isEmpty()) Welcome else Profiles)
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
    val currentRoute = backStack.lastOrNull()
    val onProfileList = currentRoute == Profiles
    val inProfileFlow = Profiles in backStack
    val homeBackground = if (inProfileFlow) painterResource(R.drawable.home_street_background) else null
    // Keep each profile's scene alive while the profile list stays in the back stack.
    val characterRelationships =
        if (inProfileFlow) profiles.map { it.relationship }.toSet() else emptySet()
    CharacterRenderResources(
        hasCharacters = characterRelationships.isNotEmpty(),
        modifier = modifier,
    ) { resourcesReady ->
        if (currentRoute is Home && homeBackground != null) {
            Box(Modifier.fillMaxSize().zIndex(-2f).homeBackground(homeBackground))
        }
        NavDisplay(
            backStack = backStack,
            entryDecorators =
                listOf(
                    rememberSaveableStateHolderNavEntryDecorator(),
                    rememberViewModelStoreNavEntryDecorator(),
                ),
            onBack = {
                if (!form.saving && !management.busy) {
                    if (management.showDeleteDialog) {
                        managementViewModel.cancelDelete()
                    } else if (backStack.size > 1) {
                        if (backStack.lastOrNull() is EditProfile) managementViewModel.reset()
                        backStack.removeLastOrNull()
                    }
                }
            },
            entryProvider =
                entryProvider {
                    entry<Startup> {
                        Box(
                            Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center,
                        ) { CircularProgressIndicator() }
                    }
                    entry<Welcome> { OnboardingScreen(onStart = addProfile) }
                    entry<Profiles> {
                        ProfileSelectionScreen(
                            profiles,
                            { id ->
                                if (backStack.lastOrNull() == Profiles && profiles.any { it.id == id }) {
                                    selectionViewModel.select(id)
                                    backStack.add(Home(id))
                                }
                            },
                            addProfile,
                        )
                    }
                    entry<Home>(metadata = instantTransitionMetadata) { home ->
                        profiles.firstOrNull { it.id == home.profileId }?.let { profile ->
                            DestinationHome(
                                profile = profile,
                                onProfileClick = {
                                    if (backStack.lastOrNull() == home) backStack.add(ManageProfile(profile.id))
                                },
                                onMapClick = {
                                    if (backStack.lastOrNull() == home) backStack.add(VisitMap(profile.id))
                                },
                                onFinish = {
                                    if (backStack.lastOrNull() == home) {
                                        selectionViewModel.clearSelection()
                                        backStack.removeLastOrNull()
                                    }
                                },
                                renderCharacter = false,
                                drawBackground = false,
                            )
                        }
                    }
                    entry<VisitMap> { route ->
                        VisitMapRoute(
                            onBack = {
                                if (backStack.lastOrNull() == route) backStack.removeLastOrNull()
                            },
                        )
                    }
                    entry<ManageProfile>(metadata = instantTransitionMetadata) { route ->
                        profiles.firstOrNull { it.id == route.profileId }?.let { profile ->
                            ProfileManagementScreen(
                                profile = profile,
                                showDeleteDialog = management.showDeleteDialog,
                                busy = management.busy,
                                error = management.error,
                                onBack = { if (!management.busy) backStack.removeLastOrNull() },
                                onEdit = {
                                    if (!management.busy && backStack.lastOrNull() == route) {
                                        managementViewModel.startEdit(profile)
                                        backStack.add(EditProfile(profile.id))
                                    }
                                },
                                onDelete = managementViewModel::showDelete,
                                onCancelDelete = managementViewModel::cancelDelete,
                                onConfirmDelete = { managementViewModel.delete(profile.id) },
                                onLogout = {
                                    if (!management.busy) {
                                        selectionViewModel.clearSelection()
                                        managementViewModel.reset()
                                        backStack.clear()
                                        backStack.add(Profiles)
                                    }
                                },
                            )
                        }
                    }
                    entry<EditProfile> { route ->
                        ProfileEditScreen(
                            nickname = management.nickname,
                            relationship = management.relationship,
                            busy = management.busy,
                            error = management.error,
                            onNicknameChange = managementViewModel::updateNickname,
                            onRelationshipChange = managementViewModel::updateRelationship,
                            onBack = {
                                if (!management.busy) {
                                    managementViewModel.reset()
                                    backStack.removeLastOrNull()
                                }
                            },
                            onSave = { managementViewModel.save(route.profileId) },
                        )
                    }
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
        val home = currentRoute as? Home
        val homeRelationship = profiles.firstOrNull { it.id == home?.profileId }?.relationship
        if (resourcesReady) {
            // Prepare the adult model first because its outfit uses an additional sheen shader.
            for (relationship in characterRelationships.sortedBy { it != ProfileRelationship.PARTNER }) {
                key(relationship) {
                    RetainedHomeCharacter(
                        relationship = relationship,
                        visible = relationship == homeRelationship,
                        active = relationship == homeRelationship,
                        prepare = onProfileList,
                    )
                }
            }
        }
        form.error?.let { message ->
            AlertDialog(
                onDismissRequest = viewModel::dismissMessage,
                title = { Text("저장 실패") },
                text = { Text(message) },
                confirmButton = { TextButton(onClick = viewModel::save) { Text("다시 시도") } },
                dismissButton = { TextButton(onClick = viewModel::dismissMessage) { Text("닫기") } },
            )
        }
    }
}
