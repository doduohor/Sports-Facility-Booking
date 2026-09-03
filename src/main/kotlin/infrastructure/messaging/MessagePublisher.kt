package com.doduohor.infrastructure.messaging

interface MessagePublisher {
    fun publish(message: String)
}