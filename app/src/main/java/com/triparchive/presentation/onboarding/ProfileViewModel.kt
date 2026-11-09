package com.triparchive.presentation.onboarding

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.triparchive.data.profile.ProfileLimitException
import com.triparchive.data.profile.ProfileRelationship
import com.triparchive.data.profile.ProfileRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ProfileFormState(
    val nickname: String = "사용자1",
    val relationship: ProfileRelationship = ProfileRelationship.PARENT,
    val saving: Boolean = false,
    val savedId: Long? = null,
    val showSuccess: Boolean = false,
    val error: String? = null,
)

@HiltViewModel
class ProfileViewModel
    @Inject
    constructor(
        private val repository: ProfileRepository,
        private val savedState: SavedStateHandle,
    ) : ViewModel() {
        private val mutableState =
            MutableStateFlow(
                ProfileFormState(
                    nickname = savedState["nickname"] ?: "사용자1",
                    relationship = ProfileRelationship.valueOf(savedState["relationship"] ?: "PARENT"),
                    savedId = savedState["savedId"],
                ),
            )
        val state = mutableState.asStateFlow()

        fun updateNickname(value: String) {
            if (state.value.saving || state.value.savedId != null) return
            savedState["nickname"] = value
            mutableState.value = state.value.copy(nickname = value, error = null)
        }

        fun updateRelationship(value: ProfileRelationship) {
            if (state.value.saving || state.value.savedId != null) return
            savedState["relationship"] = value.name
            mutableState.value = state.value.copy(relationship = value, error = null)
        }

        fun save() {
            val form = state.value
            if (form.saving || form.savedId != null) return
            if (form.nickname.isBlank()) {
                mutableState.value = form.copy(error = "닉네임을 입력해 주세요.")
                return
            }
            mutableState.value = form.copy(saving = true, error = null)
            viewModelScope.launch {
                try {
                    val id = repository.create(form.nickname, form.relationship)
                    savedState["savedId"] = id
                    mutableState.value = form.copy(savedId = id, showSuccess = true)
                } catch (cancelled: CancellationException) {
                    mutableState.value = form
                    throw cancelled
                } catch (_: ProfileLimitException) {
                    mutableState.value = form.copy(error = "프로필은 최대 4개까지 만들 수 있어요.")
                } catch (_: Exception) {
                    mutableState.value = form.copy(error = "프로필을 저장하지 못했어요. 다시 시도해 주세요.")
                }
            }
        }

        fun dismissMessage() {
            mutableState.value = state.value.copy(showSuccess = false, error = null)
        }

        fun startNewProfile() {
            if (state.value.saving) return
            savedState.remove<Long>("savedId")
            savedState.remove<String>("nickname")
            savedState.remove<String>("relationship")
            mutableState.value = ProfileFormState()
        }
    }
