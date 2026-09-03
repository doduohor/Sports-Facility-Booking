package com.doduohor.infrastructure.messaging

import com.doduohor.worker.MessageHandler
import com.doduohor.worker.MessageHandlerResult
import kotlinx.coroutines.runBlocking
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class RabbitMqConsumer(
    private val messageHandler: MessageHandler
){
    private val logger = LoggerFactory.getLogger("RabbitMqConsumer")

    fun startConsuming(connection: RabbitMqConnection, config: RabbitMqConfig){
        connection.channel.basicConsume(config.queue, false, { consumerTag, delivery ->
            val body = String(delivery.body, Charsets.UTF_8)
            try {
                logger.info("Received message: {}", body)
                when(runBlocking { messageHandler.handle(body) }){
                    MessageHandlerResult.Failure -> {
                        logger.error("Failed to process message: {}", body)
                        connection.channel.basicNack(delivery.envelope.deliveryTag, false, false)
                        return@basicConsume
                    }
                    MessageHandlerResult.Success -> logger.info("Your message has been processed successfully: {}", body)
                }
                connection.channel.basicAck(delivery.envelope.deliveryTag, false)
            } catch (e: Exception) {
                logger.error("Failed to process message: {}", body, e)
                connection.channel.basicNack(delivery.envelope.deliveryTag, false, false)
            }
        }, { consumerTag ->
            logger.info("Consumer cancelled: $consumerTag")
        })
    }
}
