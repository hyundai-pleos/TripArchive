package com.triparchive.data.character

import com.triparchive.data.profile.ProfileRelationship
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import kotlin.random.Random

class CharacterLookCatalogTest {
    @Test
    fun catalogProvidesAgeSpecificOutfitAndAccessoryCombinations() {
        assertEquals(18, CharacterLookCatalog.looksFor(ProfileRelationship.CHILD).size)
        assertEquals(18, CharacterLookCatalog.looksFor(ProfileRelationship.PARTNER).size)
        assertEquals(18, CharacterLookCatalog.looksFor(ProfileRelationship.PARENT).size)

        val childLook = CharacterLookCatalog.looksFor(ProfileRelationship.CHILD).first()
        val adultLook = CharacterLookCatalog.looksFor(ProfileRelationship.PARTNER).first()
        val parentLook = CharacterLookCatalog.looksFor(ProfileRelationship.PARENT).first()
        assertEquals("characters/catalog/child/outfits/family_1.glb", childLook.outfitAssetPath)
        assertEquals("characters/catalog/adult/outfits/couple_1.glb", adultLook.outfitAssetPath)
        assertEquals("characters/catalog/parent/outfits/parent_1.glb", parentLook.outfitAssetPath)
        assertEquals("characters/catalog/child/accessories/bear_headband.glb", childLook.accessoryAssetPath)
        assertEquals("characters/catalog/adult/accessories/heart_sunglasses.glb", adultLook.accessoryAssetPath)
        assertEquals("characters/catalog/parent/accessories/beret.glb", parentLook.accessoryAssetPath)
    }

    @Test
    fun selectingNextLookNeverRepeatsThePreviousCombination() {
        ProfileRelationship.values().forEach { relationship ->
            repeat(20) { seed ->
                val previous = CharacterLookCatalog.choose(relationship, random = Random(seed))
                val next = CharacterLookCatalog.choose(relationship, previous.id, Random(seed + 1))
                assertNotEquals(previous.id, next.id)
                assertNotNull(CharacterLookCatalog.find(next.id))
            }
        }
    }
}
