package com.triparchive.data.trip

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface TripSessionDao {
    @Transaction
    @Query("SELECT * FROM trip_sessions WHERE profileId = :profileId")
    fun observeWithProps(profileId: Long): Flow<TripSessionWithProps?>

    @Query("SELECT * FROM trip_session_props WHERE sessionId = :sessionId ORDER BY acquiredOrder")
    fun getProps(sessionId: String): List<TripSessionPropEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insertProp(prop: TripSessionPropEntity): Long

    @Query("DELETE FROM trip_session_props WHERE sessionId = :sessionId")
    fun deleteProps(sessionId: String)

    @Query("SELECT * FROM trip_sessions WHERE profileId = :profileId")
    fun observeByProfileId(profileId: Long): Flow<TripSessionEntity?>

    @Query("SELECT * FROM trip_sessions WHERE profileId = :profileId")
    fun getByProfileId(profileId: Long): TripSessionEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    fun insert(session: TripSessionEntity)

    @Update
    fun update(session: TripSessionEntity): Int
}
