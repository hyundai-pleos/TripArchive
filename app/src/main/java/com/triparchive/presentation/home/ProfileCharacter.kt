package com.triparchive.presentation.home

import android.car.Car
import android.content.pm.PackageManager
import android.opengl.Matrix
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.rememberLifecycleOwner
import com.google.android.filament.Engine
import com.triparchive.data.character.CharacterLook
import com.triparchive.domain.navigation.DestinationCategory
import io.github.sceneview.RenderQuality
import io.github.sceneview.SceneNodeManager
import io.github.sceneview.SceneView
import io.github.sceneview.SurfaceType
import io.github.sceneview.loaders.ModelLoader
import io.github.sceneview.math.Position
import io.github.sceneview.model.ModelInstance
import io.github.sceneview.model.getAnimationIndex
import io.github.sceneview.rememberCameraNode
import io.github.sceneview.rememberCollisionSystem
import io.github.sceneview.rememberEngine
import io.github.sceneview.rememberMainLightNode
import io.github.sceneview.rememberModelLoader
import io.github.sceneview.rememberScene
import io.github.sceneview.rememberView
import io.github.sceneview.node.ModelNode as RiggedModelNode

@Composable
internal fun ProfileCharacter(
    look: CharacterLook,
    modifier: Modifier = Modifier,
    active: Boolean = true,
    prepare: Boolean = false,
    destinationCategory: DestinationCategory? = null,
) {
    if (LocalInspectionMode.current) return

    val sharedLoader = LocalCharacterModelLoader.current
    val configuration = LocalConfiguration.current
    val context = LocalContext.current
    var speedPermissionGranted by
        remember {
            mutableStateOf(
                androidx.core.content.ContextCompat
                    .checkSelfPermission(context, Car.PERMISSION_SPEED) ==
                    PackageManager.PERMISSION_GRANTED,
            )
        }
    var permissionRequestMade by remember { mutableStateOf(false) }
    var motionPresentation by remember { mutableStateOf(CharacterMotionPresentation()) }
    val speedPermissionLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            speedPermissionGranted = granted
        }
    LaunchedEffect(active, motionPresentation.drivingRestricted, speedPermissionGranted, permissionRequestMade) {
        if (active && !motionPresentation.drivingRestricted && !speedPermissionGranted && !permissionRequestMade) {
            permissionRequestMade = true
            speedPermissionLauncher.launch(Car.PERMISSION_SPEED)
        }
    }
    DisposableEffect(context, active, speedPermissionGranted) {
        if (active) {
            val monitor = VehicleMotionMonitor(context) { motionPresentation = it }
            monitor.start()
            onDispose {
                monitor.close()
                motionPresentation = CharacterMotionPresentation()
            }
        } else {
            motionPresentation = CharacterMotionPresentation()
            onDispose { }
        }
    }
    val engine = sharedLoader?.engine ?: rememberCharacterEngine()
    val modelLoader = sharedLoader ?: rememberModelLoader(engine)
    val renderView = rememberView(engine)
    var prepared by remember(look.id) { mutableStateOf(false) }
    var modelAttached by remember(look.id) { mutableStateOf(false) }
    val renderLifecycle =
        rememberLifecycleOwner(
            maxLifecycle =
                if (active || (prepare && !prepared)) Lifecycle.State.RESUMED else Lifecycle.State.STARTED,
        ).lifecycle
    val camera =
        rememberCameraNode(engine) {
            position = Position(z = 2.05f)
            lookAt(Position(0f, 0f, 0f))
        }

    val scene = rememberScene(engine)
    val collisionSystem = rememberCollisionSystem(renderView)
    val sceneNodeManager = remember(scene, collisionSystem) { SceneNodeManager(scene, collisionSystem) }
    val mainLightNode =
        rememberMainLightNode(engine) {
            intensity = 60_000f
        }
    val destinationPropNodes = rememberDestinationPropNodes(modelLoader, engine, sceneNodeManager, destinationCategory)
    val animationClock = remember { CharacterFrameClock() }
    val characterNode =
        if (look.baseAssetPath != null && look.compositeAssetPath != null) {
            rememberCharacterNode(
                look = look,
                modelLoader = modelLoader,
                engine = engine,
                sceneNodeManager = sceneNodeManager,
                motionPresentation = motionPresentation,
                animate = active || (prepare && !prepared),
                onReady = { modelAttached = true },
                animationClock = animationClock,
            )
        } else {
            key(look.id) {
                rememberCharacterNode(
                    look = look,
                    modelLoader = modelLoader,
                    engine = engine,
                    sceneNodeManager = sceneNodeManager,
                    motionPresentation = motionPresentation,
                    animate = active || (prepare && !prepared),
                    onReady = { modelAttached = true },
                    animationClock = animationClock,
                )
            }
        }
    val sceneNodes = destinationPropNodes + listOfNotNull(characterNode)

    // Recreate only the TextureView and renderer after resize; the scene graph and model nodes stay alive.
    key(configuration.screenWidthDp, configuration.screenHeightDp) {
        SceneView(
            modifier = modifier,
            surfaceType = SurfaceType.TextureSurface,
            isOpaque = false,
            renderQuality = RenderQuality.Performance,
            engine = engine,
            modelLoader = modelLoader,
            view = renderView,
            scene = scene,
            collisionSystem = collisionSystem,
            autoCenterContent = false,
            mainLightNode = mainLightNode,
            fillLightNode = null,
            cameraNode = camera,
            cameraManipulator = null,
            lifecycle = renderLifecycle,
            onFrame = { frameTimeNanos ->
                if (modelAttached) prepared = true
                animationClock.onFrame?.invoke(frameTimeNanos)
                sceneNodes.forEach { it.onFrame(frameTimeNanos) }
            },
        )
    }
    LaunchedEffect(renderView) {
        // Keep the character sharp at the surface's native resolution.
        renderView.dynamicResolutionOptions =
            renderView.dynamicResolutionOptions.apply {
                enabled = false
            }
    }
}

@Composable
private fun rememberDestinationPropNodes(
    modelLoader: ModelLoader,
    engine: Engine,
    sceneNodeManager: SceneNodeManager,
    selectedCategory: DestinationCategory?,
): List<RiggedModelNode> {
    val nodes = mutableListOf<RiggedModelNode>()
    DestinationCategory.values().forEach { category ->
        key(category) {
            val modelInstance =
                rememberRequestModelInstance(
                    modelLoader,
                    engine,
                    "models/destination_props/${category.assetFileName}",
                )
            if (modelInstance != null) {
                val node =
                    remember(modelInstance) {
                        RiggedModelNode(
                            modelInstance = modelInstance,
                            autoAnimate = false,
                            scaleToUnits = 0.22f,
                            centerOrigin = Position(0f, 0f, 0f),
                        ).apply {
                            position = Position(x = 0.5f, y = -0.35f, z = 0f)
                        }
                    }
                SideEffect { node.isVisible = selectedCategory == category }
                DisposableEffect(sceneNodeManager, node) {
                    sceneNodeManager.addNode(node)
                    onDispose {
                        sceneNodeManager.removeNode(node)
                        node.destroy()
                    }
                }
                nodes += node
            }
        }
    }
    return nodes
}

@Composable
private fun rememberCharacterNode(
    look: CharacterLook,
    modelLoader: ModelLoader,
    engine: Engine,
    sceneNodeManager: SceneNodeManager,
    motionPresentation: CharacterMotionPresentation,
    animate: Boolean,
    onReady: () -> Unit,
    animationClock: CharacterFrameClock,
): RiggedModelNode? {
    val baseAssetPath = look.baseAssetPath
    val compositeAssetPath = look.compositeAssetPath
    if (baseAssetPath != null && compositeAssetPath != null) {
        val base = rememberRequestModelInstance(modelLoader, engine, baseAssetPath)
        val composite = rememberRequestModelInstance(modelLoader, engine, compositeAssetPath)
        if (base == null || composite == null) return null

        val characterNode =
            remember(base) {
                RiggedModelNode(
                    modelInstance = base,
                    autoAnimate = false,
                    scaleToUnits = 1f,
                    centerOrigin = Position(0f, 0f, 0f),
                )
            }
        val modelNode = remember(composite) { RiggedModelNode(modelInstance = composite, autoAnimate = false) }
        val binding =
            remember(characterNode, modelNode) {
                AdultCompositeBinding().apply {
                    this.characterNode = characterNode
                    this.modelNode = modelNode
                }
            }
        DisposableEffect(sceneNodeManager, characterNode) {
            sceneNodeManager.addNode(characterNode)
            onDispose {
                sceneNodeManager.removeNode(characterNode)
                characterNode.destroy()
            }
        }
        DisposableEffect(characterNode, modelNode) {
            modelNode.parent = characterNode
            onDispose {
                modelNode.parent = null
                modelNode.destroy()
            }
        }
        DisposableEffect(engine, characterNode, modelNode, binding) {
            hideAllAdultBaseMeshes(engine, base)
            applyAdultCompositeLook(engine, modelNode, look)
            binding.lastAppliedLookId = look.id
            attachAdultAccessoriesToHead(engine, binding)
            onDispose { }
        }
        DisposableEffect(engine, modelNode, look.id, binding) {
            if (binding.lastAppliedLookId != look.id) {
                applyAdultCompositeLook(engine, modelNode, look)
                binding.lastAppliedLookId = look.id
            }
            onDispose { }
        }
        SideEffect(onReady)
        val latestMotionPresentation by rememberUpdatedState(motionPresentation)
        DisposableEffect(binding, animate) {
            val timeline = CharacterAnimationTimeline()
            var previousFrameNanos: Long? = null
            animationClock.onFrame =
                if (!animate) {
                    null
                } else {
                    { frameNanos ->
                        val deltaSeconds =
                            previousFrameNanos?.let { (frameNanos - it).coerceAtLeast(0L) / 1_000_000_000f } ?: 0f
                        previousFrameNanos = frameNanos
                        val presentation = latestMotionPresentation
                        val frame =
                            timeline.advance(
                                presentation.animationClip,
                                presentation.targetAnimationSpeed,
                                deltaSeconds,
                            )
                        applyCharacterAnimationFrame(binding.characterNode, frame, engine)
                        applyCharacterAnimationFrame(binding.modelNode, frame, engine)
                    }
                }
            onDispose { animationClock.onFrame = null }
        }
        return characterNode
    }

    val outfitAssetPath = look.outfitAssetPath ?: return null
    val accessoryAssetPath = look.accessoryAssetPath ?: return null
    val outfit = rememberRequestModelInstance(modelLoader, engine, outfitAssetPath) ?: return null
    val accessory = rememberRequestModelInstance(modelLoader, engine, accessoryAssetPath) ?: return null
    val outfitNode =
        remember(outfit) {
            RiggedModelNode(
                modelInstance = outfit,
                autoAnimate = false,
                scaleToUnits = 1f,
                centerOrigin = Position(0f, 0f, 0f),
            )
        }
    val accessoryNode = remember(accessory) { RiggedModelNode(modelInstance = accessory, autoAnimate = false) }
    val attachment =
        remember(outfitNode, accessoryNode) {
            AccessoryRigBinding().apply {
                characterNode = outfitNode
                this.accessoryNode = accessoryNode
            }
        }
    DisposableEffect(sceneNodeManager, outfitNode, accessoryNode, attachment) {
        sceneNodeManager.addNode(outfitNode)
        attachAccessoryToHead(engine, attachment)
        onDispose {
            accessoryNode.parent = null
            sceneNodeManager.removeNode(outfitNode)
            outfitNode.destroy()
            accessoryNode.destroy()
        }
    }
    SideEffect {
        attachment.characterNode = outfitNode
        attachment.accessoryNode = accessoryNode
        onReady()
    }
    val latestMotionPresentation by rememberUpdatedState(motionPresentation)
    DisposableEffect(attachment, animate) {
        val timeline = CharacterAnimationTimeline()
        var previousFrameNanos: Long? = null
        animationClock.onFrame =
            if (!animate) {
                null
            } else {
                { frameNanos ->
                    val deltaSeconds =
                        previousFrameNanos?.let { (frameNanos - it).coerceAtLeast(0L) / 1_000_000_000f } ?: 0f
                    previousFrameNanos = frameNanos
                    val presentation = latestMotionPresentation
                    val frame =
                        timeline.advance(
                            presentation.animationClip,
                            presentation.targetAnimationSpeed,
                            deltaSeconds,
                        )
                    applyCharacterAnimationFrame(attachment.characterNode, frame, engine)
                }
            }
        onDispose { animationClock.onFrame = null }
    }
    return outfitNode
}

// Submit bone/morph updates only when SceneView renders. A Compose frame loop keeps
// running while a permission dialog pauses the renderer and can overflow Filament's
// command buffer before beginFrame/endFrame can drain it.
private class CharacterFrameClock {
    var onFrame: ((Long) -> Unit)? = null
}

private class AdultCompositeBinding {
    var characterNode: RiggedModelNode? = null
    var modelNode: RiggedModelNode? = null
    var lastAppliedLookId: String? = null
}

private fun applyCharacterAnimationFrame(
    character: RiggedModelNode?,
    frame: CharacterAnimationFrame,
    engine: Engine,
) {
    if (character == null) return
    val animator = character.animator
    val requestedCurrentIndex = animator.getAnimationIndex(frame.clip.assetName)
    val currentIndex = requestedCurrentIndex ?: animator.getAnimationIndex("Idle") ?: return
    val currentClip = if (requestedCurrentIndex == null) CharacterAnimationClip.IDLE else frame.clip
    animator.applyAnimation(currentIndex, animationTime(animator, currentIndex, frame.clipTimeSeconds))
    val previousClip = frame.previousClip
    var previousIndex: Int? = null
    var resolvedPreviousClip = CharacterAnimationClip.IDLE
    if (previousClip != null) {
        val requestedPreviousIndex = animator.getAnimationIndex(previousClip.assetName)
        previousIndex = requestedPreviousIndex ?: animator.getAnimationIndex("Idle")
        resolvedPreviousClip = if (requestedPreviousIndex == null) CharacterAnimationClip.IDLE else previousClip
        if (previousIndex != null) {
            animator.applyCrossFade(
                previousIndex,
                animationTime(animator, previousIndex, frame.previousTimeSeconds),
                frame.crossFadeAlpha,
            )
        }
    }
    val currentWave =
        gaitMorphWave(
            currentClip,
            frame.clipTimeSeconds,
            animator.getAnimationDuration(currentIndex),
        )
    val previousWave =
        if (previousClip != null && previousIndex != null) {
            gaitMorphWave(
                resolvedPreviousClip,
                frame.previousTimeSeconds,
                animator.getAnimationDuration(previousIndex),
            )
        } else {
            0f
        }
    applyGaitMorphWeights(
        character = character,
        engine = engine,
        weights =
            blendedGaitMorphWeights(
                currentWave,
                previousWave,
                if (previousClip ==
                    null
                ) {
                    1f
                } else {
                    frame.crossFadeAlpha
                },
            ),
    )
    animator.updateBoneMatrices()
    character.nodes.forEach { it.onWorldTransformChanged() }
}

private fun applyGaitMorphWeights(
    character: RiggedModelNode,
    engine: Engine,
    weights: FloatArray,
) {
    val manager = engine.renderableManager
    character.modelInstance.entities.forEach { entity ->
        val renderableInstance = manager.getInstance(entity)
        if (renderableInstance == 0 || manager.getMorphTargetCount(renderableInstance) != weights.size) return@forEach
        manager.setMorphWeights(renderableInstance, weights, 0)
    }
}

private fun animationTime(
    animator: com.google.android.filament.gltfio.Animator,
    index: Int,
    timeSeconds: Float,
): Float {
    val duration = animator.getAnimationDuration(index)
    if (!duration.isFinite() || duration <= 0f) return 0f
    return timeSeconds % duration
}

private fun attachAdultAccessoriesToHead(
    engine: Engine,
    binding: AdultCompositeBinding,
) {
    val character = binding.characterNode ?: return
    val composite = binding.modelNode ?: return
    val headEntity =
        character.modelInstance.entities.firstOrNull {
            character.model.getName(it) in setOf("BODY|head.001", "BODY|head", "head.001", "head")
        }
    val headNode = character.nodes.firstOrNull { it.entity == headEntity }
    if (headEntity == null || headNode == null) return

    val transformManager = engine.transformManager
    val headInstance = transformManager.getInstance(headEntity)
    if (headInstance == 0) return
    val headWorld = transformManager.getWorldTransform(headInstance, FloatArray(16))
    val inverseHeadWorld = FloatArray(16)
    if (!Matrix.invertM(inverseHeadWorld, 0, headWorld, 0)) return

    for (entity in composite.modelInstance.entities) {
        val name = composite.model.getName(entity) ?: continue
        if (!name.substringBefore('|').startsWith("ACC_")) continue
        val accessoryNode = composite.nodes.firstOrNull { it.entity == entity } ?: continue
        if (accessoryNode.parent == headNode) continue
        val accessoryInstance = transformManager.getInstance(entity)
        if (accessoryInstance == 0) continue
        val accessoryWorld = transformManager.getWorldTransform(accessoryInstance, FloatArray(16))
        val accessoryLocal = FloatArray(16)
        Matrix.multiplyMM(accessoryLocal, 0, inverseHeadWorld, 0, accessoryWorld, 0)
        accessoryNode.parent = headNode
        transformManager.setTransform(accessoryInstance, accessoryLocal)
    }
}

private fun applyAdultCompositeLook(
    engine: Engine,
    character: RiggedModelNode?,
    look: CharacterLook,
) {
    if (character == null) return
    val outfit =
        when (look.outfitId) {
            "couple_1" -> "heart_hoodie"
            "couple_2" -> "cream_knit"
            "couple_4" -> "lavender_jacket"
            else -> return
        }
    val manager = engine.renderableManager
    for (entity in character.modelInstance.entities) {
        val instance = manager.getInstance(entity)
        if (instance == 0) continue
        val name = character.model.getName(entity)
        val part = name?.substringBefore('|')
        val localName = name?.substringAfter('|', "")
        val visible =
            when {
                part == "BODY" -> localName?.let { it.startsWith("head") || it.startsWith("tuft_smooth_curl") } == true
                part == "FACE_default" -> true
                part == "OUTFIT_$outfit" -> true
                part == "ACC_${look.accessoryId}" -> true
                else -> false
            }
        manager.setLayerMask(instance, 0xff, if (visible) 1 else 0)
    }
}

private class AccessoryRigBinding {
    var characterNode: RiggedModelNode? = null
    var accessoryNode: RiggedModelNode? = null
}

private fun hideAllAdultBaseMeshes(
    engine: Engine,
    character: ModelInstance?,
) {
    if (character == null) return
    val manager = engine.renderableManager
    for (entity in character.entities) {
        val instance = manager.getInstance(entity)
        if (instance == 0) continue
        manager.setLayerMask(instance, 0xff, 0)
    }
}

private fun attachAccessoryToHead(
    engine: Engine,
    binding: AccessoryRigBinding,
) {
    val character = binding.characterNode ?: return
    val accessory = binding.accessoryNode ?: return
    val headEntity =
        character.modelInstance.entities.firstOrNull {
            character.model.getName(it) in setOf("BODY|head.001", "BODY|head", "head.001", "head")
        }
    val headNode = character.nodes.firstOrNull { it.entity == headEntity }
    if (headEntity == null || headNode == null) {
        android.util.Log.e("ProfileCharacter", "No head node found for accessory attachment")
        accessory.isVisible = false
        return
    }
    if (accessory.parent == headNode) return

    // Preserve the accessory's authored character-space placement when moving it under the
    // animated head node. Keep the outfit's existing character coordinate frame and model scale.
    val coordinateEntity =
        character.modelInstance.entities.firstOrNull {
            character.model.getName(it) == "Child" || character.model.getName(it) == "root"
        } ?: character.entity
    val transformManager = engine.transformManager
    val headInstance = transformManager.getInstance(headEntity)
    val coordinateInstance = transformManager.getInstance(coordinateEntity)
    val accessoryInstance = transformManager.getInstance(accessory.entity)
    if (headInstance == 0 || coordinateInstance == 0 || accessoryInstance == 0) {
        android.util.Log.e("ProfileCharacter", "Missing transform for accessory head attachment")
        accessory.isVisible = false
        return
    }

    val headWorld = transformManager.getWorldTransform(headInstance, FloatArray(16))
    val characterWorld = transformManager.getWorldTransform(coordinateInstance, FloatArray(16))
    val inverseHeadWorld = FloatArray(16)
    if (!Matrix.invertM(inverseHeadWorld, 0, headWorld, 0)) {
        android.util.Log.e("ProfileCharacter", "Could not invert animated head transform")
        accessory.isVisible = false
        return
    }
    val headLocalAccessoryTransform = FloatArray(16)
    Matrix.multiplyMM(headLocalAccessoryTransform, 0, inverseHeadWorld, 0, characterWorld, 0)

    // This updates both SceneView's Node graph and Filament's TransformManager hierarchy.
    // A TransformManager-only reparent is reverted by SceneView's top-level node synchronization.
    accessory.parent = headNode
    transformManager.setTransform(accessoryInstance, headLocalAccessoryTransform)
    accessory.isVisible = true
}

private val LocalCharacterModelLoader = staticCompositionLocalOf<ModelLoader?> { null }

@Composable
internal fun CharacterRenderResources(
    hasCharacters: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable (resourcesReady: Boolean) -> Unit,
) {
    if (LocalInspectionMode.current) {
        Box(modifier) { content(false) }
        return
    }

    // Let the profile list draw before creating the engine and preparing its materials.
    var prepare by remember { mutableStateOf(false) }
    LaunchedEffect(hasCharacters) {
        if (hasCharacters && !prepare) {
            withFrameNanos { }
            withFrameNanos { }
            prepare = true
        }
    }
    val loader = if (prepare) rememberModelLoader(rememberCharacterEngine()) else null
    CompositionLocalProvider(LocalCharacterModelLoader provides loader) {
        Box(modifier) { content(loader != null) }
    }
}

@Composable
private fun rememberCharacterEngine(): Engine {
    // The Pleos emulator exposes Vulkan through a CPU renderer and OpenGL through the host GPU.
    if (Build.HARDWARE == "ranchu") return rememberEngine()

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
