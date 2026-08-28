package com.doduohor.infrastructure.messaging

import com.rabbitmq.client.Channel
import com.rabbitmq.client.Connection
import com.rabbitmq.client.AlreadyClosedException
import java.util.concurrent.atomic.AtomicBoolean

class RabbitMqConnection(
    val connection: Connection,
    val channel: Channel
): com.doduohor.worker.WorkerConnection {
    private val closed = AtomicBoolean(false)

    override fun close() {
        if (!closed.compareAndSet(false, true)) return

        var failure: Throwable? = null
        try {
            channel.close()
        } catch (exception: AlreadyClosedException) {
            // Closing an already closed channel is safe during shutdown.
        } catch (exception: Throwable) {
            failure = exception
        }
        try {
            connection.close()
        } catch (exception: AlreadyClosedException) {
            // Closing an already closed connection is safe during shutdown.
        } catch (exception: Throwable) {
            failure?.addSuppressed(exception) ?: run { failure = exception }
        }
        failure?.let { throw it }
    }
}
