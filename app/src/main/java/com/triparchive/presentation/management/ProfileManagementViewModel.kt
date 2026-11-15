package com.triparchive.presentation.management

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.triparchive.data.profile.Profile
import com.triparchive.data.profile.ProfileMapColor
import com.triparchive.data.profile.ProfileRelationship
import com.triparchive.data.profile.ProfileRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ManagementState(
    val nickname: String = "",
    val relationship: ProfileRelationship = ProfileRelationship.PARENT,
    val showDeleteDialog: Boolean = false,
    val busy: Boolean = false,
    val error: String? = null,
)

sealed interface ManagementEvent {
    data object Saved : ManagementEvent

    data object Deleted : ManagementEvent
}

@HiltViewModel
class ProfileManagementViewModel
    @Inject
    constructor(
        private val repository: ProfileRepository,
    ) : ViewModel() {
        private val mutableState = MutableStateFlow(ManagementState())
        val state = mutableState.asStateFlow()
        private val eventChannel = Channel<ManagementEvent>(Channel.BUFFERED)
        val events = eventChannel.receiveAsFlow()

        fun startEdit(profile: Profile) {
            mutableState.value = ManagementState(nickname = profile.nickname, relationship = profile.relationship)
        }

        fun updateNickname(value: String) {
            if (!state.value.busy) mutableState.value = state.value.copy(nickname = value, error = null)
        }

        fun updateRelationship(value: ProfileRelationship) {
            if (!state.value.busy) mutableState.value = state.value.copy(relationship = value, error = null)
        }

        fun updateMapColor(
            id: Long,
            color: ProfileMapColor,
        ) {
            val current = state.value
            if (current.busy) return
            mutableState.value = current.copy(busy = true, error = null)
            viewModelScope.launch {
                try {
                    check(repository.updateMapColor(id, color))
                    mutableState.value = state.value.copy(busy = false, error = null)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    mutableState.value = state.value.copy(busy = false, error = "지도 색상을 저장하지 못했어요. 다시 시도해 주세요.")
                }
            }
        }

        fun showDelete() {
            if (!state.value.busy) mutableState.value = state.value.copy(showDeleteDialog = true, error = null)
        }

        fun cancelDelete() {
            if (!state.value.busy) mutableState.value = state.value.copy(showDeleteDialog = false, error = null)
        }

        fun reset() {
            if (!state.value.busy) mutableState.value = ManagementState()
        }

        fun save(id: Long) {
            val current = state.value
            if (current.busy) return
            if (current.nickname.isBlank()) {
                mutableState.value = current.copy(error = "닉네임을 입력해 주세요.")
                return
            }
            mutableState.value = current.copy(busy = true, error = null)
            viewModelScope.launch {
                try {
                    check(repository.update(id, current.nickname, current.relationship))
                    mutableState.value = ManagementState()
                    eventChannel.send(ManagementEvent.Saved)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    mutableState.value = current.copy(busy = false, error = "프로필을 수정하지 못했어요. 다시 시도해 주세요.")
                }
            }
        }

        fun delete(id: Long) {
            val current = state.value
            if (current.busy || !current.showDeleteDialog) return
            mutableState.value = current.copy(busy = true, error = null)
            viewModelScope.launch {
                try {
                    check(repository.delete(id))
                    mutableState.value = ManagementState()
                    eventChannel.send(ManagementEvent.Deleted)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    mutableState.value = current.copy(busy = false, error = "프로필을 삭제하지 못했어요. 다시 시도해 주세요.")
                }
            }
        }
    }
