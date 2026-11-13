package com.triparchive.presentation.home

import com.triparchive.data.record.NewTripRecord
import com.triparchive.data.record.RecordRepository
import com.triparchive.data.record.TripRecord
import com.triparchive.data.record.TripRecordDetail
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TripSessionViewModelTest {
    @Test
    fun arrivalsRetainOrderIncludingRepeatedNamesAndCorrectProfile() =
        runBlocking {
            val repository = FakeRecords()
            val session = TripSessionViewModel(repository)
            session.recordArrival("시장")
            session.recordArrival("해변")
            session.recordArrival("시장")
            assertTrue(session.save(13, null))
            val (profile, record) = repository.saved.single()
            assertEquals(13L, profile)
            assertEquals(listOf("시장", "해변", "시장"), record.stops)
            assertNull(record.distanceMeters)
            assertTrue(record.startedAt <= record.endedAt)
            assertTrue(session.save(13, null))
            assertEquals(1, repository.saved.size)
        }

    @Test
    fun pendingArrivalIsIncludedOnFinishAndEmptyTripIsNotSaved() =
        runBlocking {
            val repository = FakeRecords()
            val session = TripSessionViewModel(repository)
            session.recordArrival("시장")
            assertTrue(session.save(3, "  해변  "))
            assertEquals(
                listOf("시장", "해변"),
                repository.saved
                    .single()
                    .second.stops,
            )
            assertTrue(TripSessionViewModel(repository).save(3, null))
            assertEquals(1, repository.saved.size)
        }

    @Test
    fun failedSaveCanRetryWithoutDuplicatingPendingArrival() =
        runBlocking {
            val repository = FakeRecords().apply { shouldFail = true }
            val session = TripSessionViewModel(repository)
            session.recordArrival("시장")
            assertFalse(session.save(1, "해변"))
            assertTrue(repository.saved.isEmpty())
            repository.shouldFail = false
            assertTrue(session.save(1, "해변"))
            assertEquals(
                listOf("시장", "해변"),
                repository.saved
                    .single()
                    .second.stops,
            )
        }

    @Test
    fun concurrentFinishDoesNotCreateTwoRecords() =
        runBlocking {
            val repository = FakeRecords()
            val entered = CompletableDeferred<Unit>()
            val release = CompletableDeferred<Unit>()
            repository.beforeSave = {
                entered.complete(Unit)
                release.await()
            }
            val session = TripSessionViewModel(repository)
            session.recordArrival("시장")
            val first = async { session.save(1, null) }
            entered.await()
            assertFalse(session.save(1, null))
            release.complete(Unit)
            assertTrue(first.await())
            assertTrue(session.save(1, null))
            assertEquals(1, repository.saved.size)
        }

    private class FakeRecords : RecordRepository {
        val saved = mutableListOf<Pair<Long, NewTripRecord>>()
        var shouldFail = false
        var beforeSave: suspend () -> Unit = {}

        override fun observeRecords(profileId: Long): Flow<List<TripRecord>> = flowOf(emptyList())

        override fun observeRecord(
            profileId: Long,
            recordId: Long,
        ): Flow<TripRecordDetail?> = flowOf(null)

        override suspend fun saveCompletedRecord(
            profileId: Long,
            record: NewTripRecord,
        ): Long {
            beforeSave()
            if (shouldFail) error("storage unavailable")
            saved += profileId to record
            return saved.size.toLong()
        }
    }
}
