package com.triparchive.data.profile

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface ProfileDao {
    @Query("SELECT COUNT(*) FROM profiles")
    suspend fun count(): Int

    @Transaction
    suspend fun insertWithinLimit(profile: Profile): Long {
        if (count() >= MAX_PROFILES) throw ProfileLimitException()
        return insert(profile)
    }

    @Insert
    suspend fun insert(profile: Profile): Long

    @Query("SELECT * FROM profiles WHERE id = :id")
    suspend fun getById(id: Long): Profile?

    @Query("SELECT * FROM profiles ORDER BY id ASC")
    fun observeAll(): Flow<List<Profile>>
}

const val MAX_PROFILES = 4

class ProfileLimitException : IllegalStateException("프로필은 최대 4개까지 만들 수 있어요.")
