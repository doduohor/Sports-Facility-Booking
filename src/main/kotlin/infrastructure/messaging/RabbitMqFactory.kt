package com.doduohor.infrastructure.messaging

import com.rabbitmq.client.ConnectionFactory

object RabbitMqFactory {
    fun connect(config: RabbitMqConfig): RabbitMqConnection{
        val factory = ConnectionFactory()
        factory.host = config.host
        factory.port = config.port
        factory.username = config.username
        factory.password = config.password
        val connection = factory.newConnection()
        val channel = connection.createChannel()
        val queueArguments = mapOf(
            "x-dead-letter-exchange" to config.deadLetterExchange,
            "x-dead-letter-routing-key" to config.deadLetterRoutingKey
        )
        channel.exchangeDeclare(config.deadLetterExchange, "direct", true)
        channel.queueDeclare(config.deadLetterQueue, true, false, false, null)
        channel.queueBind(config.deadLetterQueue, config.deadLetterExchange, config.deadLetterRoutingKey)

        channel.exchangeDeclare(config.exchange,"direct", true)
        channel.queueDeclare(config.queue, true, false, false, queueArguments)
        channel.queueBind(config.queue, config.exchange, config.routingKey)

        return RabbitMqConnection(
            connection = connection,
            channel = channel
        )
    }
}
