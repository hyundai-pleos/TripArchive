package com.triparchive

import androidx.lifecycle.SavedStateHandle
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.triparchive.data.local.TripArchiveDatabase
import com.triparchive.data.profile.Profile
import com.triparchive.data.profile.ProfileRelationship
import com.triparchive.data.profile.ProfileRepository
import com.triparchive.data.profile.RoomProfileRepository
import com.triparchive.presentation.onboarding.ProfileViewModel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfileStorageTest {
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

        override suspend fun create(
            nickname: String,
            relationship: ProfileRelationship,
        ): Long {
            calls++
            return result.await()
        }

        override suspend fun getById(id: Long): Profile? = null

        override fun observeAll(): Flow<List<Profile>> = flowOf(emptyList())
    }
}
