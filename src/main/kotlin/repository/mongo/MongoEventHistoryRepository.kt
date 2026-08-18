package com.doduohor.repository.mongo

import com.doduohor.events.EventHistoryDocument
import com.doduohor.events.EventHistoryStatus
import com.mongodb.MongoWriteException
import com.mongodb.client.model.Filters.eq
import com.mongodb.client.model.IndexOptions
import com.mongodb.client.model.Indexes
import com.mongodb.client.model.Updates.combine
import com.mongodb.client.model.Updates.set
import com.mongodb.kotlin.client.coroutine.MongoDatabase
import kotlinx.coroutines.flow.toList
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.bson.Document
import java.time.Instant

class MongoEventHistoryRepository(
    database: MongoDatabase
) : EventHistoryRepository {
    private val collection = database.getCollection<Document>("event_history")

    override suspend fun createIndexes(){
        collection.createIndex(
            Indexes.ascending("eventId"),
            IndexOptions().unique(true)
        )
    }

    override suspend fun tryStartProcessing(event: EventHistoryDocument): Boolean {
        return try {
            collection.insertOne(
                event.copy(
                    status = EventHistoryStatus.PROCESSING,
                    processedAt = null,
                    errorMessage = null
                ).toDocument()
            )
            true
        } catch (exception: MongoWriteException) {
            if (exception.error.code == DUPLICATE_KEY_ERROR_CODE) {
                false
            } else {
                throw exception
            }
        }
    }

    override suspend fun markProcessed(eventId: String) {
        collection.updateOne(
            eq("eventId", eventId),
            combine(
                set("status", EventHistoryStatus.PROCESSED.name),
                set("processedAt", Instant.now().toString()),
                set("errorMessage", null)
            )
        )
    }

    override suspend fun markFailed(eventId: String, errorMessage: String) {
        collection.updateOne(
            eq("eventId", eventId),
            combine(
                set("status", EventHistoryStatus.FAILED.name),
                set("processedAt", Instant.now().toString()),
                set("errorMessage", errorMessage)
            )
        )
    }

    override suspend fun save(event: EventHistoryDocument): EventHistoryDocument {
        collection.insertOne(event.toDocument())
        return event
    }

    override suspend fun findById(eventId: String): EventHistoryDocument? {
        return collection
            .find(eq("eventId", eventId))
            .toList()
            .firstOrNull()
            ?.toEventHistoryDocument()
    }

    override suspend fun findByType(type: String): List<EventHistoryDocument> {
        return collection
            .find(eq("eventType", type))
            .toList()
            .map { it.toEventHistoryDocument() }
    }

    private fun EventHistoryDocument.toDocument(): Document =
        Document()
            .append("eventId", eventId)
            .append("eventType", eventType)
            .append("eventCreatedAt", eventCreatedAt)
            .append("receivedAt", receivedAt)
            .append("processedAt", processedAt)
            .append("data", Document.parse(data.toString()))
            .append("status", status.name)
            .append("errorMessage", errorMessage)
            .append("attempt", attempt)

    private fun Document.toEventHistoryDocument(): EventHistoryDocument {
        val dataDocument = get("data", Document::class.java)

        return EventHistoryDocument(
            eventId = getString("eventId"),
            eventType = getString("eventType"),
            eventCreatedAt = getString("eventCreatedAt"),
            receivedAt = getString("receivedAt"),
            data = Json.parseToJsonElement(dataDocument.toJson()).jsonObject,
            status = EventHistoryStatus.valueOf(getString("status")),
            processedAt = getString("processedAt"),
            errorMessage = getString("errorMessage"),
            attempt = getInteger("attempt")
        )
    }

    private companion object {
        const val DUPLICATE_KEY_ERROR_CODE = 11000
    }
}
