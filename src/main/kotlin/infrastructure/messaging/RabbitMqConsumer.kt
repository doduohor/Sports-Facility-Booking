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
        connection.channel.basicQos(1)
        connection.channel.basicConsume(config.queue, false, { consumerTag, delivery ->
            val body = String(delivery.body, Charsets.UTF_8)
            val result = try {
                logger.info("Received message: {}", body)
                runBlocking { messageHandler.handle(body) }
            } catch (e: Exception) {
                logger.error("Failed to process message: {}", body, e)
                MessageHandlerResult.Failure
            }

            if (result == MessageHandlerResult.Failure) {
                logger.error("Failed to process message: {}", body)
                connection.channel.basicNack(delivery.envelope.deliveryTag, false, false)
                return@basicConsume
            }

            logger.info("Your message has been processed successfully: {}", body)
            try {
                connection.channel.basicAck(delivery.envelope.deliveryTag, false)
            } catch (e: Exception) {
                logger.error("Failed to acknowledge message: {}", body, e)
            }
        }, { consumerTag ->
            logger.info("Consumer cancelled: $consumerTag")
        })
    }
}
