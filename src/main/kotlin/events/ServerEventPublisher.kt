package com.doduohor.events

interface ServerEventPublisher {
    suspend fun publish(events: List<ServerEvent>)
}
