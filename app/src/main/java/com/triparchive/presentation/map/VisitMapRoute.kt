package com.triparchive.presentation.map

import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.structuralEqualityPolicy
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
internal fun VisitMapRoute(
    onBack: () -> Unit,
    onRecordsClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: VisitMapViewModel = hiltViewModel()
    val state = viewModel.uiState.collectAsStateWithLifecycle()
    val content by remember(state) { derivedStateOf(structuralEqualityPolicy()) { state.value.content } }
    // Read the viewport in the draw phase so panning doesn't recompose the screen.
    val viewport = remember(state) { { state.value.viewport } }

    VisitMapScreen(
        content = content,
        viewport = viewport,
        onAction = viewModel::onAction,
        onBack = onBack,
        onRecordsClick = onRecordsClick,
        modifier = modifier,
    )
}
