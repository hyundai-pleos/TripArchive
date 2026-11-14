package com.triparchive.data.trip

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface TripSessionDao {
    @Query("SELECT * FROM trip_sessions WHERE profileId = :profileId")
    fun observeByProfileId(profileId: Long): Flow<TripSessionEntity?>

    @Query("SELECT * FROM trip_sessions WHERE profileId = :profileId")
    fun getByProfileId(profileId: Long): TripSessionEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    fun insert(session: TripSessionEntity)

    @Update
    fun update(session: TripSessionEntity): Int
}
