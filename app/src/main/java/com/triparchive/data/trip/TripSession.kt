package com.triparchive.data.trip

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.triparchive.data.profile.Profile
import com.triparchive.domain.navigation.DestinationCategory

@Entity(
    tableName = "trip_sessions",
    indices = [Index(value = ["sessionId"], unique = true)],
    foreignKeys =
        [
            ForeignKey(
                entity = Profile::class,
                parentColumns = ["id"],
                childColumns = ["profileId"],
                onDelete = ForeignKey.CASCADE,
            ),
        ],
)
data class TripSessionEntity(
    @PrimaryKey val profileId: Long,
    val sessionId: String,
    val lookId: String,
    val relationship: String,
    val status: String,
    val updatedAtMillis: Long,
)

enum class TripSessionStatus { ACTIVE, READY }

data class TripSession(
    val profileId: Long,
    val sessionId: String,
    val lookId: String,
    val relationship: String,
    val status: TripSessionStatus,
    val updatedAtMillis: Long,
    val props: List<DestinationCategory> = emptyList(),
)

internal fun TripSessionEntity.toDomain() =
    TripSession(
        profileId = profileId,
        sessionId = sessionId,
        lookId = lookId,
        relationship = relationship,
        status = TripSessionStatus.valueOf(status),
        updatedAtMillis = updatedAtMillis,
    )
