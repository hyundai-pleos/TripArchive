package com.triparchive.data.trip

import com.triparchive.data.character.CharacterLookCatalog
import com.triparchive.data.local.TripArchiveDatabase
import com.triparchive.data.profile.Profile
import com.triparchive.data.profile.ProfileRelationship
import com.triparchive.domain.navigation.DestinationCategory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.util.UUID
import java.util.concurrent.Callable
import javax.inject.Inject

interface TripSessionRepository {
    suspend fun openOrResume(profile: Profile): TripSession

    suspend fun finish(
        profileId: Long,
        sessionId: String,
    ): TripSession?

    suspend fun acquireProp(
        profileId: Long,
        sessionId: String,
        category: DestinationCategory,
    ): Boolean

    fun observe(profileId: Long): Flow<TripSession?>
}

class RoomTripSessionRepository
    @Inject
    constructor(
        private val database: TripArchiveDatabase,
        private val dao: TripSessionDao,
    ) : TripSessionRepository {
        override suspend fun openOrResume(profile: Profile): TripSession =
            withContext(Dispatchers.IO) {
                database.runInTransaction(
                    Callable {
                        val current = dao.getByProfileId(profile.id)
                        val relationship = profile.relationship.name
                        val currentLook = current?.let { CharacterLookCatalog.find(it.lookId) }
                        val next =
                            when {
                                current == null -> {
                                    newSession(profile.id, profile.relationship, TripSessionStatus.ACTIVE)
                                }

                                current.status == TripSessionStatus.READY.name -> {
                                    val matchingLook = currentLook?.takeIf { current.relationship == relationship }
                                    current.copy(
                                        lookId =
                                            matchingLook?.id ?: CharacterLookCatalog.choose(profile.relationship).id,
                                        relationship = relationship,
                                        status = TripSessionStatus.ACTIVE.name,
                                        updatedAtMillis = System.currentTimeMillis(),
                                    )
                                }

                                current.relationship != relationship || currentLook == null -> {
                                    current.copy(
                                        lookId = CharacterLookCatalog.choose(profile.relationship).id,
                                        relationship = relationship,
                                        updatedAtMillis = System.currentTimeMillis(),
                                    )
                                }

                                else -> {
                                    current
                                }
                            }
                        if (current == null) {
                            dao.insert(next)
                        } else if (current != next) {
                            dao.update(next)
                        }
                        TripSessionWithProps(next, dao.getProps(next.sessionId)).toDomain()
                    },
                )
            }

        override suspend fun finish(
            profileId: Long,
            sessionId: String,
        ): TripSession? =
            withContext(Dispatchers.IO) {
                database.runInTransaction(
                    Callable {
                        val current = dao.getByProfileId(profileId)
                        if (current == null || current.sessionId != sessionId ||
                            current.status != TripSessionStatus.ACTIVE.name
                        ) {
                            null
                        } else {
                            val relationship = ProfileRelationship.valueOf(current.relationship)
                            val next =
                                current.copy(
                                    sessionId = UUID.randomUUID().toString(),
                                    lookId = CharacterLookCatalog.choose(relationship, current.lookId).id,
                                    status = TripSessionStatus.READY.name,
                                    updatedAtMillis = System.currentTimeMillis(),
                                )
                            dao.deleteProps(current.sessionId)
                            check(dao.update(next) == 1)
                            next.toDomain()
                        }
                    },
                )
            }

        override suspend fun acquireProp(
            profileId: Long,
            sessionId: String,
            category: DestinationCategory,
        ): Boolean =
            withContext(Dispatchers.IO) {
                database.runInTransaction(
                    Callable {
                        val session = dao.getByProfileId(profileId)
                        if (session?.sessionId != sessionId || session.status != TripSessionStatus.ACTIVE.name) {
                            return@Callable false
                        }
                        val props = dao.getProps(sessionId)
                        if (props.any { it.category == category.name }) return@Callable true
                        if (props.size >= DestinationCategory.entries.size) return@Callable false
                        dao.insertProp(TripSessionPropEntity(sessionId, category.name, props.size)) != -1L
                    },
                )
            }

        override fun observe(profileId: Long): Flow<TripSession?> =
            dao.observeWithProps(profileId).map { it?.toDomain() }

        private fun newSession(
            profileId: Long,
            relationship: ProfileRelationship,
            status: TripSessionStatus,
        ) = TripSessionEntity(
            profileId = profileId,
            sessionId = UUID.randomUUID().toString(),
            lookId = CharacterLookCatalog.choose(relationship).id,
            relationship = relationship.name,
            status = status.name,
            updatedAtMillis = System.currentTimeMillis(),
        )
    }
