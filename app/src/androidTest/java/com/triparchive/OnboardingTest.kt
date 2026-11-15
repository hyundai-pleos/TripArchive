package com.triparchive

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.lifecycle.SavedStateHandle
import androidx.test.platform.app.InstrumentationRegistry
import com.triparchive.core.designsystem.TripArchiveTheme
import com.triparchive.data.character.CharacterLookCatalog
import com.triparchive.data.profile.Profile
import com.triparchive.data.profile.ProfileRelationship
import com.triparchive.data.profile.ProfileRepository
import com.triparchive.data.trip.TripSession
import com.triparchive.data.trip.TripSessionRepository
import com.triparchive.data.trip.TripSessionStatus
import com.triparchive.navigation.OnboardingNavigation
import com.triparchive.presentation.management.ProfileManagementViewModel
import com.triparchive.presentation.onboarding.ProfileViewModel
import com.triparchive.presentation.profile.ProfileSelectionViewModel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.io.File

class OnboardingTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun emptyStartupCreatesProfileAndNewSessionShowsSelection() {
        val repository = FakeProfiles(emptyList())
        val session = mutableIntStateOf(0)
        compose.setContent {
            key(session.intValue) {
                val form = remember { ProfileViewModel(repository, SavedStateHandle()) }
                val selection =
                    remember { ProfileSelectionViewModel(repository, FakeTripSessions(), SavedStateHandle()) }
                val management = remember { ProfileManagementViewModel(repository) }
                TripArchiveTheme {
                    OnboardingNavigation(
                        viewModel = form,
                        selectionViewModel = selection,
                        managementViewModel = management,
                    )
                }
            }
        }
        compose.onNodeWithText("여행 시작하기").performClick()
        compose.onNodeWithContentDescription("닉네임 지우기").performClick()
        compose.onNodeWithText("다음으로").assertIsNotEnabled()
        compose.onNodeWithContentDescription("닉네임 입력").performTextInput("준")
        compose.onNodeWithText("다음으로").performClick()
        compose.onNodeWithText("아이").performClick()
        compose.onNodeWithText("프로필 만들기").performClick()
        compose.onNodeWithText("누구와 여행을 시작할까요?").assertIsDisplayed()
        compose.onNodeWithText("준").performClick()
        compose.onNodeWithText("준님").assertIsDisplayed()
        compose.runOnIdle { session.intValue++ }
        compose.onNodeWithText("누구와 여행을 시작할까요?").assertIsDisplayed()
        compose.onNodeWithText("여행 시작하기").assertDoesNotExist()
        compose.onNodeWithText("준님").assertDoesNotExist()
        compose.onNodeWithText("프로필 추가").performClick()
        compose.onNodeWithContentDescription("뒤로").performClick()
        compose.onNodeWithText("누구와 여행을 시작할까요?").assertIsDisplayed()
        assertEquals(1, repository.profiles.value.size)
    }

    @Test
    fun fourProfilesHideAddAndSelectionUsesId() {
        val repository = FakeProfiles((1L..4L).map { Profile(it, "사용자$it", ProfileRelationship.PARENT) })
        lateinit var selection: ProfileSelectionViewModel
        compose.setContent {
            val form = remember { ProfileViewModel(repository, SavedStateHandle()) }
            selection = remember { ProfileSelectionViewModel(repository, FakeTripSessions(), SavedStateHandle()) }
            val management = remember { ProfileManagementViewModel(repository) }
            TripArchiveTheme {
                OnboardingNavigation(viewModel = form, selectionViewModel = selection, managementViewModel = management)
            }
        }
        compose.onNodeWithText("프로필 추가").assertDoesNotExist()
        val image = compose.onRoot().captureToImage().asAndroidBitmap()
        val directory =
            File(
                InstrumentationRegistry.getArguments().getString("additionalTestOutputDir")
                    ?: InstrumentationRegistry
                        .getInstrumentation()
                        .targetContext.cacheDir.absolutePath,
            )
        check(directory.exists() || directory.mkdirs())
        val bytes = java.io.ByteArrayOutputStream()
        check(image.compress(Bitmap.CompressFormat.PNG, 100, bytes))
        File(
            directory,
            "profile-selection.base64",
        ).writeText(android.util.Base64.encodeToString(bytes.toByteArray(), android.util.Base64.NO_WRAP))
        compose.onNodeWithText("사용자4").performClick()
        compose.runOnIdle { assertEquals(4L, selection.selectedId.value) }
        compose.onNodeWithText("사용자4님").assertIsDisplayed()
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.onNodeWithText("누구와 여행을 시작할까요?").assertIsDisplayed()
        compose.onNodeWithText("사용자1").performClick()
        compose.runOnIdle { assertEquals(1L, selection.selectedId.value) }
        compose.onNodeWithText("사용자1님").assertIsDisplayed()
        compose.onNodeWithText("사용자4님").assertDoesNotExist()
    }

    @Test
    fun failedReadDoesNotShowWelcomeAndCanRetry() {
        val repository = FakeProfiles(emptyList()).apply { fail = true }
        compose.setContent {
            val form = remember { ProfileViewModel(repository, SavedStateHandle()) }
            val selection = remember { ProfileSelectionViewModel(repository, FakeTripSessions(), SavedStateHandle()) }
            val management = remember { ProfileManagementViewModel(repository) }
            TripArchiveTheme {
                OnboardingNavigation(viewModel = form, selectionViewModel = selection, managementViewModel = management)
            }
        }
        compose.onNodeWithText("프로필을 불러오지 못했어요.").assertIsDisplayed()
        compose.onNodeWithText("여행 시작하기").assertDoesNotExist()
        repository.fail = false
        compose.onNodeWithText("다시 시도").performClick()
        compose.onNodeWithText("여행 시작하기").assertIsDisplayed()
    }

    @Test
    fun editProfileCanBeCancelledOrSaved() {
        val repository = FakeProfiles(listOf(Profile(1, "준", ProfileRelationship.PARENT)))
        openManagement(repository, "준")
        compose.onNodeWithText("프로필 수정").performClick()
        compose.onNodeWithContentDescription("닉네임 지우기").performClick()
        compose.onNodeWithText("프로필 수정").assertIsNotEnabled()
        compose.onNodeWithContentDescription("닉네임 입력").performTextInput("수정")
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.runOnIdle { assertEquals("준", repository.profiles.value[0].nickname) }
        compose.onNodeWithText("프로필 수정").performClick()
        compose.onNodeWithContentDescription("닉네임 지우기").performClick()
        compose.onNodeWithContentDescription("닉네임 입력").performTextInput("수정")
        compose.onNodeWithText("아이").performClick()
        compose.onNodeWithText("프로필 수정").performClick()
        compose.runOnIdle {
            assertEquals("수정", repository.profiles.value[0].nickname)
            assertEquals(ProfileRelationship.CHILD, repository.profiles.value[0].relationship)
        }
    }

    @Test
    fun deleteCancellationAndDeletionWithRemainingProfile() {
        val repository =
            FakeProfiles(
                listOf(Profile(1, "준", ProfileRelationship.PARENT), Profile(2, "여행", ProfileRelationship.CHILD)),
            )
        openManagement(repository, "준")
        compose.onNodeWithText("프로필 삭제").performClick()
        compose.onNodeWithText("취소").performClick()
        compose.runOnIdle { assertEquals(2, repository.profiles.value.size) }
        compose.onNodeWithText("프로필 삭제").performClick()
        compose.onNodeWithText("삭제", substring = false).performClick()
        compose.onNodeWithText("누구와 여행을 시작할까요?").assertIsDisplayed()
        compose.onNodeWithText("여행").assertIsDisplayed()
        compose.runOnIdle { assertEquals(listOf(2L), repository.profiles.value.map { it.id }) }
    }

    @Test
    fun deletingLastProfileReturnsToWelcome() {
        val repository = FakeProfiles(listOf(Profile(1, "준", ProfileRelationship.PARENT)))
        openManagement(repository, "준")
        compose.onNodeWithText("프로필 삭제").performClick()
        compose.onNodeWithText("삭제", substring = false).performClick()
        compose.onNodeWithText("여행 시작하기").assertIsDisplayed()
        compose.runOnIdle { assertEquals(0, repository.profiles.value.size) }
    }

    @Test
    fun logoutReturnsToSelectionWithoutDeletingProfile() {
        val repository = FakeProfiles(listOf(Profile(1, "준", ProfileRelationship.PARENT)))
        openManagement(repository, "준")
        compose.onNodeWithText("로그아웃").performClick()
        compose.onNodeWithText("누구와 여행을 시작할까요?").assertIsDisplayed()
        compose.onNodeWithText("준").assertIsDisplayed()
        compose.runOnIdle { assertEquals(1, repository.profiles.value.size) }
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.onNodeWithText("준님").assertDoesNotExist()
    }

    @Test
    fun savingAndDeletingDisableRepeatedActions() {
        val repository = FakeProfiles(listOf(Profile(1, "준", ProfileRelationship.PARENT)))
        repository.updateGate = CompletableDeferred()
        repository.deleteGate = CompletableDeferred()
        openManagement(repository, "준")
        compose.onNodeWithText("프로필 수정").performClick()
        compose.onNodeWithText("프로필 수정").performClick()
        compose.onNodeWithText("저장 중…").assertIsNotEnabled()
        compose.runOnIdle {
            assertEquals(1, repository.updateCalls)
            repository.updateGate?.complete(Unit)
        }
        compose.onNodeWithText("프로필 삭제").performClick()
        compose.onNodeWithText("삭제", substring = false).performClick()
        compose.onNodeWithText("삭제 중…").assertIsNotEnabled()
        compose.runOnIdle {
            assertEquals(1, repository.deleteCalls)
            repository.deleteGate?.complete(Unit)
        }
        compose.onNodeWithText("여행 시작하기").assertIsDisplayed()
    }

    @Test
    fun editAndDeleteFailuresKeepProfile() {
        val repository =
            FakeProfiles(listOf(Profile(1, "준", ProfileRelationship.PARENT))).apply {
                failUpdate = true
                failDelete = true
            }
        openManagement(repository, "준")
        compose.onNodeWithText("프로필 수정").performClick()
        compose.onNodeWithContentDescription("닉네임 지우기").performClick()
        compose.onNodeWithContentDescription("닉네임 입력").performTextInput("수정")
        compose.onNodeWithText("프로필 수정").performClick()
        compose.onNodeWithText("프로필을 수정하지 못했어요. 다시 시도해 주세요.").assertIsDisplayed()
        compose.runOnIdle { assertEquals("준", repository.profiles.value[0].nickname) }
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.onNodeWithText("프로필 삭제").performClick()
        compose.onNodeWithText("삭제", substring = false).performClick()
        compose.onNodeWithText("프로필을 삭제하지 못했어요. 다시 시도해 주세요.").assertIsDisplayed()
        compose.runOnIdle { assertEquals("준", repository.profiles.value[0].nickname) }
    }

    private fun openManagement(
        repository: FakeProfiles,
        nickname: String,
    ) {
        compose.setContent {
            val form = remember { ProfileViewModel(repository, SavedStateHandle()) }
            val selection = remember { ProfileSelectionViewModel(repository, FakeTripSessions(), SavedStateHandle()) }
            val management = remember { ProfileManagementViewModel(repository) }
            TripArchiveTheme {
                OnboardingNavigation(viewModel = form, selectionViewModel = selection, managementViewModel = management)
            }
        }
        compose.onNodeWithText(nickname).performClick()
        compose.onNodeWithText("${nickname}님").performClick()
        compose.onNodeWithText("프로필 삭제").assertIsDisplayed()
    }

    private class FakeProfiles(
        initial: List<Profile>,
    ) : ProfileRepository {
        val profiles = MutableStateFlow(initial)
        var fail = false
        var failUpdate = false
        var failDelete = false
        var updateGate: CompletableDeferred<Unit>? = null
        var deleteGate: CompletableDeferred<Unit>? = null
        var updateCalls = 0
        var deleteCalls = 0

        override suspend fun create(
            nickname: String,
            relationship: ProfileRelationship,
        ): Long {
            val id = (profiles.value.maxOfOrNull { it.id } ?: 0) + 1
            profiles.value += Profile(id, nickname.trim(), relationship)
            return id
        }

        override suspend fun getById(id: Long): Profile? = profiles.value.find { it.id == id }

        override suspend fun update(
            id: Long,
            nickname: String,
            relationship: ProfileRelationship,
        ): Boolean {
            updateCalls++
            updateGate?.await()
            if (failUpdate) error("Update failed")
            require(nickname.isNotBlank())
            if (profiles.value.none { it.id == id }) return false
            profiles.value =
                profiles.value.map {
                    if (it.id == id) it.copy(nickname = nickname.trim(), relationship = relationship) else it
                }
            return true
        }

        override suspend fun updateMapColor(
            id: Long,
            color: com.triparchive.data.profile.ProfileMapColor,
        ): Boolean {
            if (profiles.value.none { it.id == id }) return false
            profiles.value = profiles.value.map { if (it.id == id) it.copy(mapColor = color.name) else it }
            return true
        }

        override suspend fun delete(id: Long): Boolean {
            deleteCalls++
            deleteGate?.await()
            if (failDelete) error("Delete failed")
            if (profiles.value.none { it.id == id }) return false
            profiles.value = profiles.value.filterNot { it.id == id }
            return true
        }

        override fun observeAll(): Flow<List<Profile>> =
            flow {
                if (fail) error("Read failed")
                profiles.collect { emit(it) }
            }
    }

    private class FakeTripSessions : TripSessionRepository {
        override suspend fun acquireProp(
            profileId: Long,
            sessionId: String,
            category: com.triparchive.domain.navigation.DestinationCategory,
        ) = true

        override suspend fun openOrResume(profile: Profile) =
            TripSession(
                profileId = profile.id,
                sessionId = "session-${profile.id}",
                lookId = CharacterLookCatalog.looksFor(profile.relationship).first().id,
                relationship = profile.relationship.name,
                status = TripSessionStatus.ACTIVE,
                updatedAtMillis = 0L,
            )

        override suspend fun finish(
            profileId: Long,
            sessionId: String,
        ): TripSession? = null

        override fun observe(profileId: Long) = flowOf<TripSession?>(null)
    }
}
