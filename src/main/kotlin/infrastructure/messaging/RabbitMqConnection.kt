package com.doduohor.infrastructure.messaging

import com.rabbitmq.client.Channel
import com.rabbitmq.client.Connection
import java.util.concurrent.atomic.AtomicBoolean

class RabbitMqConnection(
    val connection: Connection,
    val channel: Channel
){
    private val closed = AtomicBoolean(false)

    fun close() {
        if (!closed.compareAndSet(false, true)) return

        var failure: Throwable? = null
        try {
            channel.close()
        } catch (exception: Throwable) {
            failure = exception
        }
        try {
            connection.close()
        } catch (exception: Throwable) {
            failure?.addSuppressed(exception) ?: run { failure = exception }
        }
        failure?.let { throw it }
    }
}
