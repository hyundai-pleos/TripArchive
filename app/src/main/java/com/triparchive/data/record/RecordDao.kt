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

data class DistrictVisitCount(
    val districtCode: String,
    val visitCount: Int,
)

@Dao
interface RecordDao {
    @Query(
        "SELECT trip_stops.districtCode AS districtCode, COUNT(*) AS visitCount " +
            "FROM trip_stops INNER JOIN trip_records ON trip_stops.tripId = trip_records.id " +
            "WHERE trip_records.profileId = :profileId AND trip_stops.districtCode IS NOT NULL " +
            "GROUP BY trip_stops.districtCode",
    )
    fun observeDistrictVisitCounts(profileId: Long): Flow<List<DistrictVisitCount>>

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
                TripStop(
                    tripId = id,
                    position = index,
                    placeName = name.placeName,
                    category = name.category?.name,
                    districtCode = name.districtCode,
                )
            },
        )
        return id
    }
}
