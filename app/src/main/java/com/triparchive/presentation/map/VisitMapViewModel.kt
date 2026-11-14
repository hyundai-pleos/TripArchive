package com.triparchive.presentation.map

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.triparchive.data.profile.ProfileMapColor
import com.triparchive.data.profile.ProfileRepository
import com.triparchive.domain.map.GetAdministrativeBoundaries
import com.triparchive.domain.map.ObserveMapVisitIntensities
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
internal class VisitMapViewModel
    @Inject
    constructor(
        private val getAdministrativeBoundaries: GetAdministrativeBoundaries,
        private val observeMapVisitIntensities: ObserveMapVisitIntensities,
        private val profileRepository: ProfileRepository,
    ) : ViewModel() {
        private val _uiState = MutableStateFlow(VisitMapUiState())
        val uiState = _uiState.asStateFlow()
        private var loadJob: Job? = null
        private var currentProfileId: Long? = null

        fun setProfile(profileId: Long) {
            if (currentProfileId == profileId) return
            currentProfileId = profileId
            _uiState.update { it.copy(content = VisitMapContent.Loading, mapColor = ProfileMapColor.PINK) }
            load()
        }

        private fun load(isRetry: Boolean = false) {
            val profileId = currentProfileId ?: return
            loadJob?.cancel()
            loadJob =
                viewModelScope.launch {
                    if (isRetry) {
                        _uiState.update { it.copy(content = VisitMapContent.Error(0, retrying = true)) }
                    }
                    try {
                        val boundaries = getAdministrativeBoundaries()
                        val mapColors =
                            profileRepository
                                .observeAll()
                                .map { profiles ->
                                    ProfileMapColor.fromStorage(profiles.firstOrNull { it.id == profileId }?.mapColor)
                                }.distinctUntilChanged()
                        combine(
                            observeMapVisitIntensities(boundaries, profileId),
                            mapColors,
                        ) { visitIntensities, color ->
                            VisitMapContent.Ready(boundaries, visitIntensities) to color
                        }.collect { (content, color) ->
                            _uiState.update { it.copy(content = content, mapColor = color) }
                        }
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {
                        for (seconds in 5 downTo 1) {
                            _uiState.update { it.copy(content = VisitMapContent.Error(seconds)) }
                            delay(1_000)
                        }
                        load(isRetry = true)
                    }
                }
        }

        fun onAction(action: VisitMapAction) {
            if (action == VisitMapAction.Retry) {
                if ((_uiState.value.content as? VisitMapContent.Error)?.retrying == false) load(isRetry = true)
            } else {
                _uiState.update { it.copy(viewport = it.viewport.reduce(action)) }
            }
        }
    }
