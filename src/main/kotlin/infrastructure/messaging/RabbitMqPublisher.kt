package com.doduohor.infrastructure.messaging

class RabbitMqPublisher(
    private val rabbitMqConnection: RabbitMqConnection,
    private val exchange: String,
    private val routingKey: String
) : MessagePublisher{
    override fun publish(message: String){
        val byteMsg: ByteArray = message.encodeToByteArray()
        rabbitMqConnection.channel.basicPublish(
            exchange,
            routingKey,
            null,
            byteMsg
        )
    }
}