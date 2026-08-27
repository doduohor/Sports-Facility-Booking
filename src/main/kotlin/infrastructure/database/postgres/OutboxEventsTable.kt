package com.doduohor.infrastructure.database.postgres

import org.jetbrains.exposed.v1.core.Table
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import org.jetbrains.exposed.v1.javatime.timestampWithTimeZone
import org.jetbrains.exposed.v1.json.jsonb

object OutboxEventsTable: Table("outbox_events") {
    val id = long("id").autoIncrement()
    val eventId = uuid("event_id")
    val eventType = varchar("event_type", 32)
    val payload = jsonb<JsonElement>("payload", Json.Default)
    val status = varchar("status",32)
    val createdAt = timestampWithTimeZone("created_at")
    val publishedAt = timestampWithTimeZone("published_at").nullable()
    val attempt = integer("attempt")
    val errorMessage = text("error_message").nullable()
    override val primaryKey = PrimaryKey(id)
}