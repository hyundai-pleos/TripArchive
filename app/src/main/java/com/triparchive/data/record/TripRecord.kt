package com.triparchive.data.record

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.triparchive.data.profile.Profile

@Entity(
    tableName = "trip_records",
    foreignKeys = [
        ForeignKey(
            entity = Profile::class,
            parentColumns = ["id"],
            childColumns = ["profileId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("profileId")],
)
data class TripRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val profileId: Long,
    val title: String,
    val startedAt: Long,
    val endedAt: Long,
    val distanceMeters: Long?,
)

@Entity(
    tableName = "trip_stops",
    foreignKeys = [
        ForeignKey(
            entity = TripRecord::class,
            parentColumns = ["id"],
            childColumns = ["tripId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["tripId", "position"], unique = true)],
)
data class TripStop(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val tripId: Long,
    val position: Int,
    val placeName: String,
)

data class NewTripRecord(
    val title: String,
    val startedAt: Long,
    val endedAt: Long,
    val distanceMeters: Long?,
    val stops: List<String>,
)

data class TripRecordDetail(
    val record: TripRecord,
    val stops: List<TripStop>,
)
