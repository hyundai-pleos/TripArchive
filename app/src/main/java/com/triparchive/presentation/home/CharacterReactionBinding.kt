package com.triparchive.presentation.home

import com.google.android.filament.Engine
import io.github.sceneview.node.ModelNode

/** Resolved once per model instance; speed changes only update existing morph weights. */
internal class CharacterReactionBinding(
    engine: Engine,
    character: ModelNode,
) {
    private val manager = engine.renderableManager
    private val targets =
        character.modelInstance.entities.toList().mapNotNull { entity ->
            val instance = manager.getInstance(entity)
            if (instance == 0) return@mapNotNull null
            val names = character.model.getMorphTargetNames(entity)
            val indices = CharacterReaction.entries.map { names.indexOf("REACTION_${it.name}") }
            if (indices.any { it < 0 }) return@mapNotNull null
            Target(instance, indices, FloatArray(names.size))
        }

    fun apply(frame: CharacterReactionFrame) {
        targets.forEach { target ->
            target.indices.forEachIndexed { reactionIndex, morphIndex ->
                target.weights[morphIndex] = frame.morphWeights[reactionIndex]
            }
            manager.setMorphWeights(target.instance, target.weights, 0)
        }
    }

    private data class Target(
        val instance: Int,
        val indices: List<Int>,
        val weights: FloatArray,
    )
}
