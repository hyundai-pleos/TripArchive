package com.triparchive.presentation.profile

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.triparchive.data.profile.Profile
import com.triparchive.data.profile.ProfileRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
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
        private val savedState: SavedStateHandle,
    ) : ViewModel() {
        private val mutableState = MutableStateFlow<ProfileListState>(ProfileListState.Loading)
        val state = mutableState.asStateFlow()
        val selectedId = savedState.getStateFlow<Long?>("selectedProfileId", null)
        private var observation: Job? = null

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

        fun select(id: Long) {
            val profiles = (state.value as? ProfileListState.Ready)?.profiles ?: return
            if (profiles.none { it.id == id }) return
            savedState["selectedProfileId"] = id
        }
    }
