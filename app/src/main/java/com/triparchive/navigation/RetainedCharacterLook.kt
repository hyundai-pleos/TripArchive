package com.triparchive.navigation

import com.triparchive.data.character.CharacterLook
import com.triparchive.data.character.CharacterLookCatalog
import com.triparchive.data.profile.ProfileRelationship
import com.triparchive.data.trip.TripSession

internal fun cachedCharacterLook(
    session: TripSession?,
    relationship: ProfileRelationship,
): CharacterLook? =
    session
        ?.takeIf { it.relationship == relationship.name }
        ?.let { matchingSession ->
            CharacterLookCatalog.looksFor(relationship).firstOrNull { it.id == matchingSession.lookId }
        }

internal fun retainedCharacterLook(
    sessions: Iterable<TripSession?>,
    relationship: ProfileRelationship,
): CharacterLook =
    sessions.firstNotNullOfOrNull { cachedCharacterLook(it, relationship) }
        ?: CharacterLookCatalog.looksFor(relationship).first()
