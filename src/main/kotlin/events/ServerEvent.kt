package com.doduohor.events

import io.ktor.sse.ServerSentEvent
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import java.time.Instant

data class ServerEvent(
    val type: ServerEventType,
    val data: JsonElement,
    val createdAt: Instant
)

fun ServerEvent.toSse(): ServerSentEvent{
     return ServerSentEvent(
        data = Json.encodeToString(this.data),
        event = type.name.lowercase()
    )
}
