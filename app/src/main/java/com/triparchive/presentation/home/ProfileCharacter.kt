package com.triparchive.presentation.home

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalInspectionMode
import com.google.android.filament.Material
import com.triparchive.data.profile.ProfileRelationship
import io.github.sceneview.RenderQuality
import io.github.sceneview.SceneView
import io.github.sceneview.SurfaceType
import io.github.sceneview.loaders.ModelLoader
import io.github.sceneview.math.Position
import io.github.sceneview.rememberCameraNode
import io.github.sceneview.rememberEngine
import io.github.sceneview.rememberMainLightNode
import io.github.sceneview.rememberModelInstance
import io.github.sceneview.rememberModelLoader
import io.github.sceneview.rememberView

@Composable
internal fun ProfileCharacter(
    relationship: ProfileRelationship,
    modifier: Modifier = Modifier,
) {
    if (LocalInspectionMode.current) return

    val assetPath = relationship.characterAssetPath()
    val sharedLoader = LocalCharacterModelLoader.current
    val engine = sharedLoader?.engine ?: rememberEngine()
    val modelLoader = sharedLoader ?: rememberModelLoader(engine)
    val renderView = rememberView(engine)
    val camera = rememberCameraNode(engine) {
        position = Position(z = 2.05f)
        lookAt(Position(0f, 0f, 0f))
    }

    SceneView(
        modifier = modifier,
        surfaceType = SurfaceType.TextureSurface,
        isOpaque = false,
        renderQuality = RenderQuality.Performance,
        engine = engine,
        modelLoader = modelLoader,
        view = renderView,
        autoCenterContent = false,
        mainLightNode = rememberMainLightNode(engine) {
            intensity = 60_000f
        },
        fillLightNode = null,
        cameraNode = camera,
        cameraManipulator = null,
    ) {
        key(assetPath) {
            val model = rememberModelInstance(modelLoader, assetPath)
            if (model != null) {
                ModelNode(
                    modelInstance = model,
                    animationName = "Idle",
                    animationLoop = true,
                    scaleToUnits = 1f,
                    centerOrigin = Position(0f, 0f, 0f),
                )
            }
        }
    }
    LaunchedEffect(renderView) {
        // Keep the character sharp at the surface's native resolution.
        renderView.dynamicResolutionOptions = renderView.dynamicResolutionOptions.apply {
            enabled = false
        }
    }
}

private val LocalCharacterModelLoader = staticCompositionLocalOf<ModelLoader?> { null }

@Composable
internal fun CharacterRenderResources(
    relationshipsToPrepare: Set<ProfileRelationship>,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    if (LocalInspectionMode.current) {
        Box(modifier) { content() }
        return
    }

    // Let the profile list draw before creating the engine and preparing its materials.
    var prepare by remember { mutableStateOf(false) }
    LaunchedEffect(relationshipsToPrepare) {
        if (relationshipsToPrepare.isNotEmpty()) {
            withFrameNanos { }
            withFrameNanos { }
            prepare = true
        }
    }
    val loader = if (prepare) rememberModelLoader(rememberEngine()) else null
    if (loader != null) {
        val preparedRelationships = remember(loader) { mutableSetOf<ProfileRelationship>() }
        LaunchedEffect(loader, relationshipsToPrepare) {
            for (relationship in relationshipsToPrepare - preparedRelationships) {
                val model = loader.createModel(relationship.characterAssetPath())
                try {
                    model.instance.materialInstances
                        .distinctBy { it.material.name }
                        .forEach { instance ->
                            instance.material.compile(
                                Material.CompilerPriorityQueue.LOW,
                                Material.UserVariantFilterBit.DIRECTIONAL_LIGHTING,
                                null,
                                null,
                            )
                        }
                    loader.engine.flush()
                } finally {
                    loader.destroyModel(model)
                }
                preparedRelationships += relationship
                withFrameNanos { }
            }
        }
    }
    CompositionLocalProvider(LocalCharacterModelLoader provides loader) {
        Box(modifier) { content() }
    }
}

private fun ProfileRelationship.characterAssetPath(): String =
    when (this) {
        ProfileRelationship.CHILD -> "characters/child_wardrobe.glb"
        ProfileRelationship.PARTNER -> "characters/adult_wardrobe.glb"
        ProfileRelationship.PARENT -> "characters/parent_wardrobe.glb"
    }
