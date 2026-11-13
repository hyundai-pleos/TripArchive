package com.triparchive.data.record

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Relation
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

data class RecordWithStops(
    @Embedded val record: TripRecord,
    @Relation(parentColumn = "id", entityColumn = "tripId") val stops: List<TripStop>,
)

@Dao
interface RecordDao {
    @Query("SELECT * FROM trip_records WHERE profileId = :profileId ORDER BY endedAt DESC, id DESC")
    fun observeRecords(profileId: Long): Flow<List<TripRecord>>

    @Transaction
    @Query("SELECT * FROM trip_records WHERE profileId = :profileId AND id = :recordId")
    fun observeRecord(
        profileId: Long,
        recordId: Long,
    ): Flow<List<RecordWithStops>>

    @Insert
    suspend fun insertRecord(record: TripRecord): Long

    @Insert
    suspend fun insertStops(stops: List<TripStop>)

    @Transaction
    suspend fun insertCompletedRecord(
        profileId: Long,
        record: NewTripRecord,
    ): Long {
        val id =
            insertRecord(
                TripRecord(
                    profileId = profileId,
                    title = record.title,
                    startedAt = record.startedAt,
                    endedAt = record.endedAt,
                    distanceMeters = record.distanceMeters,
                ),
            )
        insertStops(
            record.stops.mapIndexed {
                index,
                name,
                ->
                TripStop(tripId = id, position = index, placeName = name)
            },
        )
        return id
    }
}
