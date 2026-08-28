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
    private var connection: RabbitMqConnection? = null
    private var consumerTag: String? = null

    @Synchronized
    fun startConsuming(connection: RabbitMqConnection, config: RabbitMqConfig) {
        check(this.connection == null) { "RabbitMqConsumer is already started" }
        this.connection = connection
        consumerTag = connection.channel.basicConsume(config.queue, false, { _, delivery ->
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
        }, { cancelledTag ->
            logger.info("Consumer cancelled: $cancelledTag")
        })
    }

    @Synchronized
    fun stopConsuming() {
        val currentConnection = connection ?: return
        val currentTag = consumerTag ?: return
        try {
            currentConnection.channel.basicCancel(currentTag)
        } finally {
            consumerTag = null
            connection = null
        }
    }
}
