package com.doduohor.infrastructure.messaging

import kotlinx.serialization.json.Json

object RabbitMqEventSerializer {
    fun serialize(event: RabbitMqEvent): String = Json.encodeToString(event)
}
