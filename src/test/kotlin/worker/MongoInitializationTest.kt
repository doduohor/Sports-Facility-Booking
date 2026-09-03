package com.doduohor.worker

import com.doduohor.events.EventHistoryDocument
import com.doduohor.repository.mongo.EventHistoryRepository
import com.doduohor.repository.mongo.MarkFailedResult
import com.doduohor.repository.mongo.MarkProcessedResult
import com.doduohor.repository.mongo.MarkStartProcessingResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.runBlocking

class MongoInitializationTest {
    @Test
    fun `mongo indexes initialization retries transient failures`() = runBlocking {
        val repository = FlakyEventHistoryRepository(failuresBeforeSuccess = 2)

        initializeMongoEventHistory(
            repository = repository,
            maxAttempts = 3,
            retryDelay = { }
        )

        assertEquals(3, repository.attempts)
    }

    private class FlakyEventHistoryRepository(
        private val failuresBeforeSuccess: Int
    ) : EventHistoryRepository {
        var attempts = 0

        override suspend fun createIndexes() {
            attempts++
            if (attempts <= failuresBeforeSuccess) {
                error("MongoDB is still starting")
            }
        }

        override suspend fun save(event: EventHistoryDocument) = event
        override suspend fun findById(eventId: String): EventHistoryDocument? = null
        override suspend fun findByType(type: String): List<EventHistoryDocument> = emptyList()
        override suspend fun tryStartProcessing(event: EventHistoryDocument): MarkStartProcessingResult =
            MarkStartProcessingResult.Started
        override suspend fun markProcessed(eventId: String): MarkProcessedResult = MarkProcessedResult.Updated
        override suspend fun markFailed(eventId: String, errorMessage: String): MarkFailedResult = MarkFailedResult.Updated
    }
}
