package com.triparchive.presentation.map

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.structuralEqualityPolicy
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
internal fun VisitMapRoute(
    profileId: Long,
    onBack: () -> Unit,
    onRecordsClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: VisitMapViewModel = hiltViewModel()
    LaunchedEffect(viewModel, profileId) { viewModel.setProfile(profileId) }
    val state = viewModel.uiState.collectAsStateWithLifecycle()
    val content by remember(state) { derivedStateOf(structuralEqualityPolicy()) { state.value.content } }
    val mapColor by remember(state) { derivedStateOf(structuralEqualityPolicy()) { state.value.mapColor } }
    // Read the viewport in the draw phase so panning doesn't recompose the screen.
    val viewport = remember(state) { { state.value.viewport } }

    VisitMapScreen(
        content = content,
        viewport = viewport,
        mapColor = mapColor,
        onAction = viewModel::onAction,
        onBack = onBack,
        onRecordsClick = onRecordsClick,
        modifier = modifier,
    )
}
