package com.doduohor.infrastructure.messaging

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.runInterruptible

class RabbitMqPublisher(
    private val rabbitMqConnection: RabbitMqConnection,
    private val exchange: String,
    private val routingKey: String,
    private val publishTimeoutMillis: Long = 30_000
) : MessagePublisher{
    override suspend fun publish(message: String) {
        try {
            withTimeout(publishTimeoutMillis) {
                runInterruptible(Dispatchers.IO) {
                    val byteMsg: ByteArray = message.encodeToByteArray()
                    rabbitMqConnection.channel.basicPublish(exchange, routingKey, null, byteMsg)
                }
            }
        } catch (exception: TimeoutCancellationException) {
            rabbitMqConnection.channel.abort()
            throw exception
        } catch (exception: CancellationException) {
            rabbitMqConnection.channel.abort()
            throw exception
        }
    }
}
