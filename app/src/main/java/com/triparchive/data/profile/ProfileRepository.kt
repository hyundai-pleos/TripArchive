package com.triparchive.data.profile

import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

interface ProfileRepository {
    suspend fun create(
        nickname: String,
        relationship: ProfileRelationship,
    ): Long

    suspend fun getById(id: Long): Profile?

    suspend fun update(
        id: Long,
        nickname: String,
        relationship: ProfileRelationship,
    ): Boolean

    suspend fun updateMapColor(
        id: Long,
        color: ProfileMapColor,
    ): Boolean

    suspend fun delete(id: Long): Boolean

    fun observeAll(): Flow<List<Profile>>
}

class RoomProfileRepository
    @Inject
    constructor(
        private val dao: ProfileDao,
    ) : ProfileRepository {
        override suspend fun create(
            nickname: String,
            relationship: ProfileRelationship,
        ): Long {
            val trimmed = nickname.trim()
            require(trimmed.isNotEmpty()) { "Nickname must not be blank" }
            return dao.insertWithinLimit(Profile(nickname = trimmed, relationship = relationship))
        }

        override suspend fun getById(id: Long): Profile? = dao.getById(id)

        override suspend fun update(
            id: Long,
            nickname: String,
            relationship: ProfileRelationship,
        ): Boolean {
            val trimmed = nickname.trim()
            require(trimmed.isNotEmpty()) { "Nickname must not be blank" }
            return dao.update(id, trimmed, relationship) > 0
        }

        override suspend fun updateMapColor(
            id: Long,
            color: ProfileMapColor,
        ): Boolean = dao.updateMapColor(id, color.name) > 0

        override suspend fun delete(id: Long): Boolean = dao.delete(id) > 0

        override fun observeAll(): Flow<List<Profile>> = dao.observeAll()
    }
