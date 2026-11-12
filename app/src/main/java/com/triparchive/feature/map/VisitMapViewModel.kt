package com.triparchive.feature.map

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.triparchive.domain.map.GetAdministrativeBoundaries
import com.triparchive.domain.map.ObserveMapVisitIntensities
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
internal class VisitMapViewModel
    @Inject
    constructor(
        private val getAdministrativeBoundaries: GetAdministrativeBoundaries,
        private val observeMapVisitIntensities: ObserveMapVisitIntensities,
    ) : ViewModel() {
        private val _uiState = MutableStateFlow(VisitMapUiState())
        val uiState = _uiState.asStateFlow()
        private var loadJob: Job? = null

        init {
            load()
        }

        private fun load(isRetry: Boolean = false) {
            loadJob?.cancel()
            loadJob =
                viewModelScope.launch {
                    if (isRetry) {
                        _uiState.update { it.copy(content = VisitMapContent.Error(0, retrying = true)) }
                    }
                    try {
                        val boundaries = getAdministrativeBoundaries()
                        observeMapVisitIntensities(boundaries).collect { visitIntensities ->
                            _uiState.update {
                                it.copy(content = VisitMapContent.Ready(boundaries, visitIntensities))
                            }
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
