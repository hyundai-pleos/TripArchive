package com.triparchive

import androidx.lifecycle.SavedStateHandle
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.triparchive.data.local.TripArchiveDatabase
import com.triparchive.data.profile.Profile
import com.triparchive.data.profile.ProfileMapColor
import com.triparchive.data.profile.ProfileRelationship
import com.triparchive.data.profile.ProfileRepository
import com.triparchive.data.profile.RoomProfileRepository
import com.triparchive.presentation.management.ProfileManagementViewModel
import com.triparchive.presentation.onboarding.ProfileViewModel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfileStorageTest {
    @Test
    fun mapColorSaveRejectsConcurrentTapsAndAllowsRetry() =
        runBlocking {
            val repository = FakeRepository()
            val viewModel = withContext(Dispatchers.Main) { ProfileManagementViewModel(repository) }
            withContext(Dispatchers.Main) {
                viewModel.updateMapColor(7, ProfileMapColor.BLUE)
                viewModel.updateMapColor(7, ProfileMapColor.RED)
                assertTrue(viewModel.state.value.busy)
                assertEquals(1, repository.mapColorCalls)
            }
            repository.colorResult.completeExceptionally(IllegalStateException("Storage failed"))
            withTimeout(5000) { viewModel.state.first { !it.busy && it.error != null } }
            repository.colorResult = CompletableDeferred()
            withContext(Dispatchers.Main) { viewModel.updateMapColor(7, ProfileMapColor.RED) }
            repository.colorResult.complete(true)
            withTimeout(5000) { viewModel.state.first { !it.busy && it.error == null } }
            assertEquals(2, repository.mapColorCalls)
        }

    @Test
    fun mapColorIsSavedPerProfileAndProfileEditDoesNotOverwriteIt() =
        runBlocking {
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val name = "profile-map-color-${System.nanoTime()}.db"

            fun open() = Room.databaseBuilder(context, TripArchiveDatabase::class.java, name).build()
            var database = open()
            try {
                var repository = RoomProfileRepository(database.profileDao())
                val first = repository.create("첫째", ProfileRelationship.PARENT)
                val second = repository.create("둘째", ProfileRelationship.CHILD)
                assertEquals(ProfileMapColor.PINK.name, repository.getById(first)?.mapColor)
                assertTrue(repository.updateMapColor(first, ProfileMapColor.BLUE))
                assertFalse(repository.updateMapColor(-1, ProfileMapColor.RED))
                assertTrue(repository.update(first, "새 이름", ProfileRelationship.PARTNER))
                database.close()
                database = open()
                repository = RoomProfileRepository(database.profileDao())
                assertEquals(ProfileMapColor.BLUE.name, repository.getById(first)?.mapColor)
                assertEquals(ProfileMapColor.PINK.name, repository.getById(second)?.mapColor)
            } finally {
                database.close()
                context.deleteDatabase(name)
            }
        }

    @Test
    fun updatesAndDeletesPersistAndNotifyObservers() =
        runBlocking {
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val name = "profile-edit-test-${System.nanoTime()}.db"

            fun open() = Room.databaseBuilder(context, TripArchiveDatabase::class.java, name).build()
            var database = open()
            try {
                var repository = RoomProfileRepository(database.profileDao())
                val first = repository.create("첫째", ProfileRelationship.PARENT)
                val second = repository.create("둘째", ProfileRelationship.CHILD)
                val changed =
                    async {
                        withTimeout(5000) {
                            repository.observeAll().first { rows -> rows.any { it.nickname == "변경" } }
                        }
                    }
                assertTrue(repository.update(first, " 변경 ", ProfileRelationship.PARTNER))
                assertEquals(Profile(first, "변경", ProfileRelationship.PARTNER), changed.await().first())
                assertEquals(Profile(second, "둘째", ProfileRelationship.CHILD), repository.getById(second))
                assertFalse(repository.update(-1, "없음", ProfileRelationship.PARENT))
                try {
                    repository.update(first, "   ", ProfileRelationship.PARENT)
                    throw AssertionError("Blank nickname accepted")
                } catch (_: IllegalArgumentException) {
                    // Invalid updates must leave the stored profile intact.
                }
                database.close()
                database = open()
                repository = RoomProfileRepository(database.profileDao())
                assertEquals(Profile(first, "변경", ProfileRelationship.PARTNER), repository.getById(first))
                val deleted = async { withTimeout(5000) { repository.observeAll().first { rows -> rows.size == 1 } } }
                assertTrue(repository.delete(first))
                assertEquals(listOf(second), deleted.await().map { it.id })
                assertFalse(repository.delete(first))
                assertNull(repository.getById(first))
                assertEquals(Profile(second, "둘째", ProfileRelationship.CHILD), repository.getById(second))
                assertTrue(repository.delete(second))
                database.close()
                database = open()
                assertTrue(
                    database
                        .profileDao()
                        .observeAll()
                        .first()
                        .isEmpty(),
                )
            } finally {
                database.close()
                context.deleteDatabase(name)
            }
        }

    @Test
    fun profilesSurviveReopeningAndNotifyObservers() =
        runBlocking {
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val name = "profile-test-${System.nanoTime()}.db"

            fun open() = Room.databaseBuilder(context, TripArchiveDatabase::class.java, name).build()
            var database = open()
            try {
                val repository = RoomProfileRepository(database.profileDao())
                assertTrue(repository.observeAll().first().isEmpty())
                val update = async { withTimeout(5000) { repository.observeAll().first { it.size == 2 } } }
                val first = repository.create(" 준 ", ProfileRelationship.PARENT)
                val second = repository.create("준", ProfileRelationship.CHILD)
                assertTrue(first != second)
                assertEquals(listOf(first, second), update.await().map { it.id })
                assertEquals("준", repository.getById(first)?.nickname)
                try {
                    repository.create("  ", ProfileRelationship.PARTNER)
                    throw AssertionError("Blank nickname accepted")
                } catch (_: IllegalArgumentException) {
                    // Blank input must not reach the database.
                }
                database.close()
                database = open()
                assertEquals(
                    2,
                    database
                        .profileDao()
                        .observeAll()
                        .first()
                        .size,
                )
                assertEquals(ProfileRelationship.CHILD, database.profileDao().getById(second)?.relationship)
                val concurrentRepository = RoomProfileRepository(database.profileDao())
                val results =
                    (1..8)
                        .map {
                            async(Dispatchers.IO) {
                                runCatching { concurrentRepository.create("추가", ProfileRelationship.PARTNER) }
                            }
                        }.awaitAll()
                assertEquals(2, results.count { it.isSuccess })
                assertEquals(4, concurrentRepository.observeAll().first().size)
            } finally {
                database.close()
                context.deleteDatabase(name)
            }
        }

    @Test
    fun savingRejectsDuplicatesAndRetriesAfterFailure() =
        runBlocking {
            val repository = FakeRepository()
            val handle = SavedStateHandle()
            val viewModel = withContext(Dispatchers.Main) { ProfileViewModel(repository, handle) }
            withContext(Dispatchers.Main) {
                viewModel.updateNickname("  ")
                viewModel.save()
                assertEquals(0, repository.calls)
                viewModel.updateNickname("준")
                viewModel.save()
                viewModel.save()
                viewModel.updateNickname("변경")
                assertEquals("준", viewModel.state.value.nickname)
                assertEquals(1, repository.calls)
            }
            repository.result.completeExceptionally(IllegalStateException("Storage failed"))
            withTimeout(5000) { viewModel.state.first { !it.saving && it.error != null } }
            repository.result = CompletableDeferred()
            withContext(Dispatchers.Main) { viewModel.save() }
            repository.result.complete(42)
            withTimeout(5000) { viewModel.state.first { it.savedId != null } }
            withContext(Dispatchers.Main) {
                viewModel.dismissMessage()
                viewModel.save()
                assertEquals(2, repository.calls)
                val restored = ProfileViewModel(repository, handle)
                restored.save()
                assertEquals(2, repository.calls)
                assertNotNull(restored.state.value.savedId)
            }
        }

    private class FakeRepository : ProfileRepository {
        var calls = 0
        var result = CompletableDeferred<Long>()
        var mapColorCalls = 0
        var colorResult = CompletableDeferred<Boolean>()

        override suspend fun create(
            nickname: String,
            relationship: ProfileRelationship,
        ): Long {
            calls++
            return result.await()
        }

        override suspend fun getById(id: Long): Profile? = null

        override suspend fun update(
            id: Long,
            nickname: String,
            relationship: ProfileRelationship,
        ): Boolean = false

        override suspend fun updateMapColor(
            id: Long,
            color: ProfileMapColor,
        ): Boolean {
            mapColorCalls++
            return colorResult.await()
        }

        override suspend fun delete(id: Long): Boolean = false

        override fun observeAll(): Flow<List<Profile>> = flowOf(emptyList())
    }
}
