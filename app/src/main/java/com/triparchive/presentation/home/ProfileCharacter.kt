package com.triparchive.presentation.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalInspectionMode
import com.triparchive.data.profile.ProfileRelationship
import io.github.sceneview.SceneView
import io.github.sceneview.SurfaceType
import io.github.sceneview.math.Position
import io.github.sceneview.rememberCameraNode
import io.github.sceneview.rememberEngine
import io.github.sceneview.rememberMainLightNode
import io.github.sceneview.rememberModelInstance

@Composable
internal fun ProfileCharacter(
    relationship: ProfileRelationship,
    modifier: Modifier = Modifier,
) {
    if (LocalInspectionMode.current) return

    val assetPath =
        when (relationship) {
            ProfileRelationship.CHILD -> "characters/child_wardrobe.glb"
            ProfileRelationship.PARTNER -> "characters/adult_wardrobe.glb"
            ProfileRelationship.PARENT -> "characters/parent_wardrobe.glb"
        }

    val engine = rememberEngine()
    val camera = rememberCameraNode(engine) {
        position = Position(z = 2.05f)
        lookAt(Position(0f, 0f, 0f))
    }

    SceneView(
        modifier = modifier,
        surfaceType = SurfaceType.TextureSurface,
        isOpaque = false,
        engine = engine,
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
}
