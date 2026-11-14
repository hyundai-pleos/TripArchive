package com.triparchive.data.character

import com.triparchive.data.profile.ProfileRelationship
import kotlin.random.Random

data class CharacterLook(
    val id: String,
    val outfitId: String,
    val accessoryId: String,
    val outfitAssetPath: String?,
    val accessoryAssetPath: String?,
    val baseAssetPath: String? = null,
    val compositeAssetPath: String? = null,
)

object CharacterLookCatalog {
    private data class Outfit(
        val id: String,
        val assetName: String,
        val assetDirectory: String = "outfits",
    )

    private data class Accessory(
        val id: String,
    )

    private data class CharacterAssets(
        val folder: String,
        val outfits: List<Outfit>,
        val accessories: List<Accessory>,
        val baseAssetPath: String? = null,
        val compositeAssetPath: String? = null,
    )

    private val characters =
        mapOf(
            ProfileRelationship.CHILD to
                CharacterAssets(
                    folder = "child",
                    outfits =
                        listOf(
                            Outfit("family_1", "family_1"),
                            Outfit("family_2", "family_2"),
                            Outfit("family_3", "family_3"),
                            Outfit("family_4", "family_4"),
                            Outfit("family_5", "family_5"),
                            Outfit("family_6", "family_6"),
                        ),
                    accessories = listOf(Accessory("bear_headband"), Accessory("star_cap"), Accessory("goggles")),
                ),
            ProfileRelationship.PARTNER to
                CharacterAssets(
                    folder = "adult",
                    outfits =
                        listOf(
                            Outfit("couple_1", "couple_1"),
                            Outfit("couple_2", "couple_2"),
                            Outfit("couple_3", "couple_3"),
                            Outfit("couple_4", "couple_4"),
                            Outfit("couple_5", "couple_5"),
                            Outfit("couple_6", "couple_6"),
                        ),
                    accessories =
                        listOf(
                            Accessory("heart_sunglasses"),
                            Accessory("bow"),
                            Accessory("flower_crown"),
                        ),
                ),
            ProfileRelationship.PARENT to
                CharacterAssets(
                    folder = "parent",
                    outfits =
                        listOf(
                            Outfit("parent_1", "parent_1"),
                            Outfit("parent_2", "parent_2"),
                            Outfit("parent_3", "parent_3"),
                            Outfit("parent_4", "parent_4"),
                            Outfit("parent_5", "parent_5"),
                            Outfit("parent_6", "parent_6"),
                        ),
                    accessories = listOf(Accessory("beret"), Accessory("sunglasses"), Accessory("flower_pin")),
                ),
        )

    private val allLooks by lazy {
        characters.flatMap { (relationship, assets) ->
            assets.outfits.flatMap { outfit ->
                assets.accessories.map { accessory ->
                    val basePath = "characters/catalog/${assets.folder}"
                    val usesComposite = assets.compositeAssetPath != null
                    CharacterLook(
                        id = "${relationship.name.lowercase()}_${outfit.id}_${accessory.id}",
                        outfitId = outfit.id,
                        accessoryId = accessory.id,
                        outfitAssetPath =
                            if (usesComposite) null else "$basePath/${outfit.assetDirectory}/${outfit.assetName}.glb",
                        accessoryAssetPath =
                            if (usesComposite) null else "$basePath/accessories/${accessory.id}.glb",
                        baseAssetPath = assets.baseAssetPath,
                        compositeAssetPath = assets.compositeAssetPath,
                    )
                }
            }
        }
    }

    fun looksFor(relationship: ProfileRelationship): List<CharacterLook> =
        allLooks.filter { it.id.startsWith("${relationship.name.lowercase()}_") }

    fun find(lookId: String): CharacterLook? = allLooks.firstOrNull { it.id == lookId }

    fun choose(
        relationship: ProfileRelationship,
        excludingLookId: String? = null,
        random: Random = Random.Default,
    ): CharacterLook {
        val candidates = looksFor(relationship).filterNot { it.id == excludingLookId }
        require(candidates.isNotEmpty()) { "No character looks are available for $relationship" }
        return candidates[random.nextInt(candidates.size)]
    }
}
