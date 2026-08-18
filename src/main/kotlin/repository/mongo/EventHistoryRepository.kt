package com.doduohor.repository.mongo

import com.doduohor.events.EventHistoryDocument

interface EventHistoryRepository {
    suspend fun save(event: EventHistoryDocument): EventHistoryDocument
    suspend fun findById(eventId: String): EventHistoryDocument?
    suspend fun findByType(type: String): List<EventHistoryDocument>
    suspend fun createIndexes()
    suspend fun tryStartProcessing(event: EventHistoryDocument): Boolean
    suspend fun markProcessed(eventId: String)
    suspend fun markFailed(eventId: String, errorMessage: String)
}
