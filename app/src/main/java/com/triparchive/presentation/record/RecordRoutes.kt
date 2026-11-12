package com.triparchive.presentation.record

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
internal fun RecordListRoute(
    profileId: Long,
    onRecordClick: (Long) -> Unit,
    onBack: () -> Unit,
) {
    val viewModel: RecordViewModel = hiltViewModel()
    LaunchedEffect(profileId) { viewModel.observeList(profileId) }
    val state by viewModel.list.collectAsStateWithLifecycle()
    RecordListScreen(state, onBack, onRecordClick, { viewModel.observeList(profileId, force = true) })
}

@Composable
internal fun RecordDetailRoute(
    profileId: Long,
    recordId: Long,
    onBack: () -> Unit,
) {
    val viewModel: RecordViewModel = hiltViewModel()
    LaunchedEffect(profileId, recordId) { viewModel.observeDetail(profileId, recordId) }
    val state by viewModel.detail.collectAsStateWithLifecycle()
    RecordDetailScreen(state, onBack, { viewModel.observeDetail(profileId, recordId, force = true) })
}
