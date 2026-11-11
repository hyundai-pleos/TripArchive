package com.triparchive.presentation.profile

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.triparchive.data.profile.Profile
import com.triparchive.data.profile.ProfileRepository
import com.triparchive.data.trip.TripSession
import com.triparchive.data.trip.TripSessionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface ProfileListState {
    data object Loading : ProfileListState

    data class Ready(
        val profiles: List<Profile>,
    ) : ProfileListState

    data object Error : ProfileListState
}

@HiltViewModel
class ProfileSelectionViewModel
    @Inject
    constructor(
        private val repository: ProfileRepository,
        private val tripSessionRepository: TripSessionRepository,
        private val savedState: SavedStateHandle,
    ) : ViewModel() {
        private val mutableState = MutableStateFlow<ProfileListState>(ProfileListState.Loading)
        val state = mutableState.asStateFlow()
        val selectedId = savedState.getStateFlow<Long?>("selectedProfileId", null)
        private val mutableTripSessions = MutableStateFlow<Map<Long, TripSession>>(emptyMap())
        val tripSessions = mutableTripSessions.asStateFlow()
        private val mutableOpeningTripProfiles = MutableStateFlow<Set<Long>>(emptySet())
        val openingTripProfiles = mutableOpeningTripProfiles.asStateFlow()
        private val mutableTripSessionErrors = MutableStateFlow<Map<Long, String>>(emptyMap())
        val tripSessionErrors = mutableTripSessionErrors.asStateFlow()
        private var observation: Job? = null
        private val tripOpenJobs = mutableMapOf<Long, Job>()

        init {
            retry()
        }

        fun retry() {
            observation?.cancel()
            mutableState.value = ProfileListState.Loading
            observation =
                viewModelScope.launch {
                    try {
                        repository.observeAll().collect { profiles ->
                            mutableState.value = ProfileListState.Ready(profiles)
                            if (selectedId.value != null && profiles.none { it.id == selectedId.value }) {
                                savedState["selectedProfileId"] = null
                            }
                        }
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {
                        mutableState.value = ProfileListState.Error
                    }
                }
        }

        fun clearSelection() {
            savedState["selectedProfileId"] = null
        }

        fun select(id: Long) {
            val profiles = (state.value as? ProfileListState.Ready)?.profiles ?: return
            if (profiles.none { it.id == id }) return
            savedState["selectedProfileId"] = id
        }

        fun openOrResumeTrip(profile: Profile) {
            if (tripOpenJobs[profile.id]?.isActive == true) return
            mutableTripSessionErrors.update { it - profile.id }
            mutableOpeningTripProfiles.update { it + profile.id }
            tripOpenJobs[profile.id] =
                viewModelScope.launch {
                    try {
                        val session = tripSessionRepository.openOrResume(profile)
                        mutableTripSessions.update { it + (profile.id to session) }
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {
                        mutableTripSessionErrors.update { it + (profile.id to "여행 정보를 불러오지 못했어요.") }
                    } finally {
                        mutableOpeningTripProfiles.update { it - profile.id }
                        tripOpenJobs.remove(profile.id)
                    }
                }
        }

        suspend fun finishTrip(
            profileId: Long,
            sessionId: String,
        ): Boolean =
            try {
                val nextSession = tripSessionRepository.finish(profileId, sessionId) ?: return false
                mutableTripSessions.update { it + (profileId to nextSession) }
                mutableTripSessionErrors.update { it - profileId }
                true
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                mutableTripSessionErrors.update { it + (profileId to "여행을 마치지 못했어요. 다시 시도해 주세요.") }
                false
            }
    }
