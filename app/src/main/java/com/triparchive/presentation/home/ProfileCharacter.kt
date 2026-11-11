package com.triparchive.presentation.home

import android.content.pm.PackageManager
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.rememberLifecycleOwner
import com.google.android.filament.Engine
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
    visible: Boolean = true,
    prepare: Boolean = false,
) {
    if (LocalInspectionMode.current) return

    val assetPath = relationship.characterAssetPath()
    val sharedLoader = LocalCharacterModelLoader.current
    if (prepare && !visible && sharedLoader == null) return
    val engine = sharedLoader?.engine ?: rememberCharacterEngine()
    val modelLoader = sharedLoader ?: rememberModelLoader(engine)
    val renderView = rememberView(engine)
    var prepared by remember(assetPath) { mutableStateOf(false) }
    var modelAttached by remember(assetPath) { mutableStateOf(false) }
    val renderLifecycle =
        rememberLifecycleOwner(
            maxLifecycle =
                if (visible || (prepare && !prepared)) Lifecycle.State.RESUMED else Lifecycle.State.STARTED,
        ).lifecycle
    val camera =
        rememberCameraNode(engine) {
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
        mainLightNode =
            rememberMainLightNode(engine) {
                intensity = 60_000f
            },
        fillLightNode = null,
        cameraNode = camera,
        cameraManipulator = null,
        lifecycle = renderLifecycle,
        onFrame = {
            if (modelAttached) prepared = true
        },
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
                SideEffect { modelAttached = true }
            }
        }
    }
    LaunchedEffect(renderView) {
        // Keep the character sharp at the surface's native resolution.
        renderView.dynamicResolutionOptions =
            renderView.dynamicResolutionOptions.apply {
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
    val loader = if (prepare) rememberModelLoader(rememberCharacterEngine()) else null
    CompositionLocalProvider(LocalCharacterModelLoader provides loader) {
        Box(modifier) { content() }
    }
}

@Composable
private fun rememberCharacterEngine(): Engine {
    val packageManager = LocalContext.current.packageManager
    val supportsVulkan =
        remember(packageManager) {
            packageManager.hasSystemFeature(PackageManager.FEATURE_VULKAN_HARDWARE_VERSION, 0x00401000)
        }
    return rememberEngine(
        engineCreator = { eglContext ->
            val vulkan =
                if (supportsVulkan) {
                    try {
                        Engine.create(Engine.Backend.VULKAN)
                    } catch (_: IllegalStateException) {
                        null
                    }
                } else {
                    null
                }
            vulkan ?: (eglContext?.let { Engine.create(it) } ?: Engine.create())
        },
    )
}

private fun ProfileRelationship.characterAssetPath(): String =
    when (this) {
        ProfileRelationship.CHILD -> "characters/child_wardrobe.glb"
        ProfileRelationship.PARTNER -> "characters/adult_wardrobe.glb"
        ProfileRelationship.PARENT -> "characters/parent_wardrobe.glb"
    }
