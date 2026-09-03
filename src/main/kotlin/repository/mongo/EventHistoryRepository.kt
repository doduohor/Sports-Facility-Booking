package com.doduohor.repository.mongo

import com.doduohor.events.EventHistoryDocument

interface EventHistoryRepository {
    suspend fun save(event: EventHistoryDocument): EventHistoryDocument
    suspend fun findById(eventId: String): EventHistoryDocument?
    suspend fun findByType(type: String): List<EventHistoryDocument>
    suspend fun createIndexes()
    suspend fun tryStartProcessing(event: EventHistoryDocument): MarkStartProcessingResult
    suspend fun markProcessed(eventId: String): MarkProcessedResult
    suspend fun markFailed(eventId: String, errorMessage: String): MarkFailedResult
}

sealed interface MarkProcessedResult {
    data object Updated : MarkProcessedResult
    data object NotFound : MarkProcessedResult
    data object NotProcessing : MarkProcessedResult
}

sealed interface MarkFailedResult {
    data object Updated : MarkFailedResult
    data object NotFound : MarkFailedResult
    data object NotProcessing : MarkFailedResult
}

sealed interface MarkStartProcessingResult{
    data object Started: MarkStartProcessingResult
    data object AlreadyProcessing: MarkStartProcessingResult
    data object AlreadyProcessed: MarkStartProcessingResult
    data object AttemptsExceeded: MarkStartProcessingResult
}
