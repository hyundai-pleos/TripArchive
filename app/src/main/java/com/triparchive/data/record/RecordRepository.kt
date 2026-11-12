package com.triparchive.data.record

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

interface RecordRepository {
    fun observeRecords(profileId: Long): Flow<List<TripRecord>>

    fun observeRecord(
        profileId: Long,
        recordId: Long,
    ): Flow<TripRecordDetail?>

    suspend fun saveCompletedRecord(
        profileId: Long,
        record: NewTripRecord,
    ): Long
}

class RoomRecordRepository
    @Inject
    constructor(
        private val dao: RecordDao,
    ) : RecordRepository {
        override fun observeRecords(profileId: Long): Flow<List<TripRecord>> = dao.observeRecords(profileId)

        override fun observeRecord(
            profileId: Long,
            recordId: Long,
        ): Flow<TripRecordDetail?> =
            dao.observeRecord(profileId, recordId).map { rows ->
                rows.firstOrNull()?.let { TripRecordDetail(it.record, it.stops.sortedBy(TripStop::position)) }
            }

        override suspend fun saveCompletedRecord(
            profileId: Long,
            record: NewTripRecord,
        ): Long = dao.insertCompletedRecord(profileId, record)
    }
