package com.doduohor.infrastructure.messaging

interface MessagePublisher {
    suspend fun publish(message: String)
}
