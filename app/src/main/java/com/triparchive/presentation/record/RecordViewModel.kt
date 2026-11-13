package com.triparchive.presentation.record

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.triparchive.data.record.RecordRepository
import com.triparchive.data.record.TripRecord
import com.triparchive.data.record.TripRecordDetail
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

internal sealed interface RecordListState {
    data object Loading : RecordListState

    data object Error : RecordListState

    data class Ready(
        val records: List<TripRecord>,
    ) : RecordListState
}

internal sealed interface RecordDetailState {
    data object Loading : RecordDetailState

    data object Error : RecordDetailState

    data object NotFound : RecordDetailState

    data class Ready(
        val detail: TripRecordDetail,
    ) : RecordDetailState
}

@HiltViewModel
internal class RecordViewModel
    @Inject
    constructor(
        private val repository: RecordRepository,
    ) : ViewModel() {
        private val mutableList = MutableStateFlow<RecordListState>(RecordListState.Loading)
        val list = mutableList.asStateFlow()
        private val mutableDetail = MutableStateFlow<RecordDetailState>(RecordDetailState.Loading)
        val detail = mutableDetail.asStateFlow()
        private var listJob: Job? = null
        private var detailJob: Job? = null
        private var listProfileId: Long? = null
        private var detailKey: Pair<Long, Long>? = null

        fun observeList(
            profileId: Long,
            force: Boolean = false,
        ) {
            if (!force && listProfileId == profileId) return
            listProfileId = profileId
            listJob?.cancel()
            mutableList.value = RecordListState.Loading
            listJob =
                viewModelScope.launch {
                    try {
                        repository.observeRecords(profileId).collect { mutableList.value = RecordListState.Ready(it) }
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {
                        mutableList.value = RecordListState.Error
                    }
                }
        }

        fun observeDetail(
            profileId: Long,
            recordId: Long,
            force: Boolean = false,
        ) {
            val key = profileId to recordId
            if (!force && detailKey == key) return
            detailKey = key
            detailJob?.cancel()
            mutableDetail.value = RecordDetailState.Loading
            detailJob =
                viewModelScope.launch {
                    try {
                        repository.observeRecord(profileId, recordId).collect {
                            mutableDetail.value =
                                if (it == null) RecordDetailState.NotFound else RecordDetailState.Ready(it)
                        }
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {
                        mutableDetail.value = RecordDetailState.Error
                    }
                }
        }
    }
