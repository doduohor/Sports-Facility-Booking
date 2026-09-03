package com.doduohor.events

import kotlinx.serialization.json.JsonObject

enum class EventHistoryStatus{
    PROCESSING,
    PROCESSED,
    FAILED
}


data class EventHistoryDocument(
    val eventId: String,
    val eventType: String,
    val eventCreatedAt: String,
    val receivedAt: String,
    val processedAt: String?,
    val processingStartedAt: String?,
    val data: JsonObject,
    val status: EventHistoryStatus,
    val errorMessage: String?,
    val attempt: Int
)
