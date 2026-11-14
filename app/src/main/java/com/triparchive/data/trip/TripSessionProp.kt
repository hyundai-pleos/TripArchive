package com.triparchive.data.trip

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Relation
import com.triparchive.domain.navigation.DestinationCategory

@Entity(
    tableName = "trip_session_props",
    primaryKeys = ["sessionId", "category"],
    foreignKeys = [
        ForeignKey(
            entity = TripSessionEntity::class,
            parentColumns = ["sessionId"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["sessionId", "acquiredOrder"], unique = true)],
)
data class TripSessionPropEntity(
    val sessionId: String,
    val category: String,
    val acquiredOrder: Int,
)

data class TripSessionWithProps(
    @Embedded val session: TripSessionEntity,
    @Relation(parentColumn = "sessionId", entityColumn = "sessionId")
    val props: List<TripSessionPropEntity>,
) {
    fun toDomain(): TripSession =
        session.toDomain().copy(
            props = props.sortedBy { it.acquiredOrder }.map { DestinationCategory.valueOf(it.category) },
        )
}
