package com.doduohor.events

import io.ktor.server.routing.Route
import io.ktor.server.sse.sse
import io.ktor.sse.ServerSentEvent

fun Route.eventStreamRoutes(eventPublisher: EventPublisher ) {
    sse("/api/events/stream") {
        val channel = eventPublisher.subscribe()
        try{
            send(
                ServerSentEvent(
                    data = """{"message":"event stream connected"}""",
                    event = "connected"
                )
            )
            for(event in channel){
                send(event.toSse())
            }
        } finally {
            eventPublisher.unsubscribe(channel)
        }

    }
}
