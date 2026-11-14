package com.triparchive.presentation.home

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.google.android.filament.Engine
import com.google.android.filament.gltfio.FilamentAsset
import com.google.android.filament.gltfio.ResourceLoader
import io.github.sceneview.loaders.ModelLoader
import io.github.sceneview.model.ModelInstance
import io.github.sceneview.utils.loadFileBuffer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.URI
import java.util.IdentityHashMap

// Read and written only on the Compose main thread. Removing before native teardown
// prevents retained animation callbacks from using an already released asset.
private val liveRequestInstances = IdentityHashMap<ModelInstance, Unit>()

internal fun isRequestModelInstanceAlive(instance: ModelInstance): Boolean = liveRequestInstances.containsKey(instance)

/**
 * A small, request-owned cleanup state for a glTF resource load.
 *
 * SceneView's shared [ModelLoader] uses one [ResourceLoader] for all its models. A model request
 * that owns its ResourceLoader can cancel and evict only its own pending texture work.
 */
internal class ModelRequestLifetime(
    private val cancelLoad: () -> Unit,
    private val evictResourceData: () -> Unit,
    private val releaseSourceData: () -> Unit,
    private val destroyAsset: () -> Unit,
    private val destroyResourceLoader: () -> Unit,
) {
    private var loadStarted = false
    private var loadCompleted = false
    private var closed = false
    private var resourcesEvicted = false
    private var sourceDataReleased = false
    private var assetDestroyed = false
    private var loaderDestroyed = false

    val canPublish: Boolean
        get() = loadCompleted && !closed

    fun beginLoading() {
        check(!closed && !loadStarted) { "A model request can only start one load" }
        loadStarted = true
    }

    fun completeLoading(): Boolean {
        if (closed || !loadStarted || loadCompleted) return false
        evictResourcesOnce()
        if (!sourceDataReleased) {
            releaseSourceData()
            sourceDataReleased = true
        }
        destroyResourceLoaderOnce()
        loadCompleted = true
        return true
    }

    fun close() {
        if (closed) return
        closed = true

        if (loadStarted && !loadCompleted) {
            cancelLoad()
            evictResourcesOnce()
        }
        destroyAssetOnce()
        destroyResourceLoaderOnce()
    }

    private fun evictResourcesOnce() {
        if (resourcesEvicted) return
        evictResourceData()
        resourcesEvicted = true
    }

    private fun destroyAssetOnce() {
        if (assetDestroyed) return
        assetDestroyed = true
        destroyAsset()
    }

    private fun destroyResourceLoaderOnce() {
        if (loaderDestroyed) return
        loaderDestroyed = true
        destroyResourceLoader()
    }
}

/**
 * Owns exactly one asset and one ResourceLoader for the duration of an asynchronous model request.
 * Native loader calls stay on the Compose main thread. Asset and external URI bytes are read on IO.
 */
internal class RequestModelInstanceOwner(
    private val sharedModelLoader: ModelLoader,
    engine: Engine,
    private val assetFileLocation: String,
) {
    private val resourceLoader = ResourceLoader(engine, true)
    private var asset: FilamentAsset? = null
    private var publishedInstance: ModelInstance? = null
    private var disposed = false
    private var resourceLoaderDestroyed = false
    private val lifetime =
        ModelRequestLifetime(
            cancelLoad = { resourceLoader.asyncCancelLoad() },
            evictResourceData = { resourceLoader.evictResourceData() },
            releaseSourceData = { asset?.releaseSourceData() },
            destroyAsset = {
                asset?.let(sharedModelLoader::destroyModel)
                asset = null
            },
            destroyResourceLoader = {
                if (!resourceLoaderDestroyed) {
                    resourceLoader.destroy()
                    resourceLoaderDestroyed = true
                }
            },
        )

    suspend fun load(context: Context): ModelInstance? {
        try {
            val source =
                context.loadFileBuffer(assetFileLocation)
                    ?: throw IOException("Could not read model asset: $assetFileLocation")
            currentCoroutineContext().ensureActive()
            if (disposed) return null

            val createdAsset =
                sharedModelLoader.assetLoader.createAsset(source)
                    ?: throw IOException("Could not create model asset: $assetFileLocation")
            asset = createdAsset

            val resourceBuffers = readResourceBuffers(context, createdAsset)
            currentCoroutineContext().ensureActive()
            if (disposed) return null

            resourceBuffers.forEach { (uri, buffer) -> resourceLoader.addResourceData(uri, buffer) }
            lifetime.beginLoading()
            check(resourceLoader.asyncBeginLoad(createdAsset)) {
                "Could not start model resource load: $assetFileLocation"
            }

            while (true) {
                currentCoroutineContext().ensureActive()
                if (disposed) return null
                resourceLoader.asyncUpdateLoad()
                val progress = resourceLoader.asyncGetLoadProgress()
                check(progress.isFinite()) { "Invalid model load progress: $assetFileLocation" }
                if (progress >= 1f) break
                currentCoroutineContext().ensureActive()
                if (disposed) return null
                // This keeps texture finalization moving even if the SceneView lifecycle is paused.
                delay(16)
            }

            if (!lifetime.completeLoading() || !lifetime.canPublish) return null
            return asset?.instance?.also {
                publishedInstance = it
                liveRequestInstances[it] = Unit
            }
        } catch (cancellation: CancellationException) {
            close()
            throw cancellation
        } catch (failure: Exception) {
            Log.e("RequestModelInstance", "Failed to load $assetFileLocation", failure)
            close()
            return null
        }
    }

    fun close() {
        if (disposed) return
        disposed = true
        publishedInstance?.let(liveRequestInstances::remove)
        publishedInstance = null
        lifetime.close()
    }

    private suspend fun readResourceBuffers(
        context: Context,
        model: FilamentAsset,
    ): List<Pair<String, java.nio.Buffer>> {
        val resourceUris = model.resourceUris.toList()
        return withContext(Dispatchers.IO) {
            resourceUris.map { resourceUri ->
                val location = resolveResourceLocation(assetFileLocation, resourceUri)
                val buffer =
                    context.loadFileBuffer(location)
                        ?: throw IOException("Could not read model resource '$resourceUri' for $assetFileLocation")
                resourceUri to buffer
            }
        }
    }
}

internal fun resolveResourceLocation(
    assetFileLocation: String,
    resourceUri: String,
): String {
    if (Uri.parse(resourceUri).scheme != null) return resourceUri

    val assetUri = Uri.parse(assetFileLocation)
    if (assetUri.scheme == "http" || assetUri.scheme == "https") {
        return URI(assetFileLocation)
            .resolve(resourceUri)
            .toString()
    }
    return ModelLoader.getFolderPath(assetFileLocation, resourceUri)
}

@Composable
internal fun rememberRequestModelInstance(
    sharedModelLoader: ModelLoader,
    engine: Engine,
    assetFileLocation: String,
): ModelInstance? {
    val context = LocalContext.current.applicationContext
    val owner =
        remember(sharedModelLoader, engine, assetFileLocation) {
            RequestModelInstanceOwner(sharedModelLoader, engine, assetFileLocation)
        }
    var instance by remember(owner) { mutableStateOf<ModelInstance?>(null) }

    DisposableEffect(owner) {
        onDispose(owner::close)
    }
    LaunchedEffect(owner) {
        instance = owner.load(context)
    }

    return instance
}
