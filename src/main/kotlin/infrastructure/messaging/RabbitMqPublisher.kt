package com.doduohor.infrastructure.messaging

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class RabbitMqPublisher(
    private val rabbitMqConnection: RabbitMqConnection,
    private val exchange: String,
    private val routingKey: String
) : MessagePublisher{
    override suspend fun publish(message: String) {
        withContext(Dispatchers.IO) {
            val byteMsg: ByteArray = message.encodeToByteArray()
            rabbitMqConnection.channel.basicPublish(
                exchange,
                routingKey,
                null,
                byteMsg
            )
        }
    }
}
