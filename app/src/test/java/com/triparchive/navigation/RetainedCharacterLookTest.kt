package com.triparchive.navigation

import com.triparchive.data.character.CharacterLookCatalog
import com.triparchive.data.profile.ProfileRelationship
import com.triparchive.data.trip.TripSession
import com.triparchive.data.trip.TripSessionStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RetainedCharacterLookTest {
    @Test
    fun matchingRelationshipUsesCachedLookFromItsCatalog() {
        val expected = CharacterLookCatalog.looksFor(ProfileRelationship.CHILD).last()
        val session = session(ProfileRelationship.CHILD.name, expected.id)

        assertEquals(expected, cachedCharacterLook(session, ProfileRelationship.CHILD))
        assertEquals(expected, retainedCharacterLook(listOf(session), ProfileRelationship.CHILD))
    }

    @Test
    fun relationshipMismatchFallsBackToEditedRelationshipsDefault() {
        val wrongSession = session(ProfileRelationship.CHILD.name, "child_family_1_bear_headband")

        assertNull(cachedCharacterLook(wrongSession, ProfileRelationship.PARENT))
        assertEquals(
            CharacterLookCatalog.looksFor(ProfileRelationship.PARENT).first(),
            retainedCharacterLook(listOf(wrongSession), ProfileRelationship.PARENT),
        )
    }

    @Test
    fun lookOutsideRelationshipCatalogFallsBackToDefault() {
        val wrongLook = session(ProfileRelationship.CHILD.name, "parent_parent_1_beret")

        assertNull(cachedCharacterLook(wrongLook, ProfileRelationship.CHILD))
        assertEquals(
            CharacterLookCatalog.looksFor(ProfileRelationship.CHILD).first(),
            retainedCharacterLook(listOf(wrongLook), ProfileRelationship.CHILD),
        )
    }

    @Test
    fun missingOrInvalidSessionUsesDefaultAndLaterValidSessionWins() {
        val expected = CharacterLookCatalog.looksFor(ProfileRelationship.PARTNER).last()
        val invalid = session(ProfileRelationship.PARTNER.name, "missing-look")
        val valid = session(ProfileRelationship.PARTNER.name, expected.id)

        assertEquals(
            CharacterLookCatalog.looksFor(ProfileRelationship.PARTNER).first(),
            retainedCharacterLook(listOf(null, invalid), ProfileRelationship.PARTNER),
        )
        assertEquals(expected, retainedCharacterLook(listOf(null, invalid, valid), ProfileRelationship.PARTNER))
    }

    private fun session(
        relationship: String,
        lookId: String,
    ): TripSession =
        TripSession(
            profileId = 1,
            sessionId = "session",
            lookId = lookId,
            relationship = relationship,
            status = TripSessionStatus.READY,
            updatedAtMillis = 0,
        )
}
