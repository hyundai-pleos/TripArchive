package com.triparchive.data.profile

import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

interface ProfileRepository {
    suspend fun create(
        nickname: String,
        relationship: ProfileRelationship,
    ): Long

    suspend fun getById(id: Long): Profile?

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
            return dao.insert(Profile(nickname = trimmed, relationship = relationship))
        }

        override suspend fun getById(id: Long): Profile? = dao.getById(id)

        override fun observeAll(): Flow<List<Profile>> = dao.observeAll()
    }
