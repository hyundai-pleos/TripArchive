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

data class StopLocationCount(
    val latitude: Double,
    val longitude: Double,
    val count: Int,
)

data class DistrictLocationVisitCount(
    val districtCode: String?,
    val latitude: Double?,
    val longitude: Double?,
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

    @Query(
        "SELECT trip_stops.districtCode AS districtCode, trip_stops.latitude AS latitude, " +
            "trip_stops.longitude AS longitude, COUNT(*) AS visitCount " +
            "FROM trip_stops INNER JOIN trip_records ON trip_stops.tripId = trip_records.id " +
            "WHERE trip_records.profileId = :profileId AND (trip_stops.districtCode IS NOT NULL OR " +
            "(trip_stops.latitude IS NOT NULL AND trip_stops.longitude IS NOT NULL)) " +
            "GROUP BY trip_stops.districtCode, trip_stops.latitude, trip_stops.longitude",
    )
    fun observeDistrictLocationVisitCounts(profileId: Long): Flow<List<DistrictLocationVisitCount>>

    @Query("SELECT * FROM trip_records WHERE profileId = :profileId ORDER BY endedAt DESC, id DESC")
    fun observeRecords(profileId: Long): Flow<List<TripRecord>>

    @Transaction
    @Query("SELECT * FROM trip_records WHERE profileId = :profileId AND id = :recordId")
    fun observeRecord(
        profileId: Long,
        recordId: Long,
    ): Flow<List<RecordWithStops>>

    @Query(
        """
        SELECT latitude, longitude, COUNT(*) AS count FROM trip_stops
        WHERE latitude IS NOT NULL AND longitude IS NOT NULL
        GROUP BY latitude, longitude
        """,
    )
    fun observeStopLocationCounts(): Flow<List<StopLocationCount>>

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
                stop,
                ->
                TripStop(
                    tripId = id,
                    position = index,
                    placeName = stop.placeName,
                    category = stop.category?.name,
                    districtCode = stop.districtCode,
                    latitude = stop.latitude,
                    longitude = stop.longitude,
                )
            },
        )
        return id
    }
}
