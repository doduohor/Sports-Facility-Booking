package com.doduohor.infrastructure.messaging

import com.doduohor.worker.MessageHandler
import com.doduohor.worker.MessageHandlerResult
import com.doduohor.worker.WorkerConnection
import com.doduohor.worker.WorkerConsumer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import java.util.Collections
import java.util.concurrent.atomic.AtomicBoolean
import org.slf4j.LoggerFactory

class RabbitMqConsumer(private val messageHandler: MessageHandler) : WorkerConsumer {
    private val logger = LoggerFactory.getLogger("RabbitMqConsumer")
    private var connection: RabbitMqConnection? = null
    private var config: RabbitMqConfig? = null
    private var consumerTag: String? = null
    private val accepting = AtomicBoolean(false)
    private val processingJobs = Collections.synchronizedSet(mutableSetOf<Job>())

    @Synchronized
    override fun configure(connection: WorkerConnection, config: RabbitMqConfig) {
        check(this.connection == null) { "RabbitMqConsumer is already started" }
        this.connection = connection as? RabbitMqConnection
            ?: error("RabbitMqConsumer requires RabbitMqConnection")
        this.config = config
    }

    @Synchronized
    override fun start(scope: CoroutineScope) {
        val currentConnection = checkNotNull(connection) { "RabbitMqConsumer is not configured" }
        val currentConfig = checkNotNull(config) { "RabbitMqConsumer is not configured" }
        check(consumerTag == null) { "RabbitMqConsumer is already started" }
        currentConnection.channel.basicQos(1)
        accepting.set(true)
        consumerTag = currentConnection.channel.basicConsume(currentConfig.queue, false, { _, delivery ->
            val job = synchronized(this) {
                if (!accepting.get()) return@basicConsume
                scope.launch(Dispatchers.IO) {
                    process(currentConnection, delivery)
                }.also { processingJobs += it }
            }
            job.invokeOnCompletion { processingJobs -= job }
            if (!accepting.get()) job.cancel()
        }, { cancelledTag -> logger.info("Consumer cancelled: $cancelledTag") })
    }

    fun startConsuming(connection: RabbitMqConnection, config: RabbitMqConfig) {
        configure(connection, config)
        start(CoroutineScope(Dispatchers.Default))
    }

    override suspend fun stop() {
        val currentConnection: RabbitMqConnection?
        val currentTag: String?
        synchronized(this) {
            accepting.set(false)
            currentConnection = connection
            currentTag = consumerTag
            consumerTag = null
            connection = null
            config = null
        }
        val connectionToStop = currentConnection
        val tagToStop = currentTag
        if (connectionToStop != null && tagToStop != null) {
            try {
                connectionToStop.channel.basicCancel(tagToStop)
            } finally {
                val jobs = synchronized(processingJobs) { processingJobs.toList() }
                jobs.forEach { it.cancelAndJoin() }
            }
        }
    }

    fun stopConsuming() = kotlinx.coroutines.runBlocking { stop() }

    private suspend fun process(connection: RabbitMqConnection, delivery: com.rabbitmq.client.Delivery) {
        val body = String(delivery.body, Charsets.UTF_8)
        val result = try {
            logger.info("Received message: {}", body)
            messageHandler.handle(body)
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            logger.error("Failed to process message: {}", body, exception)
            MessageHandlerResult.Failure
        }

        if (result == MessageHandlerResult.Failure) {
            logger.error("Failed to process message: {}", body)
            try {
                connection.channel.basicNack(delivery.envelope.deliveryTag, false, false)
            } catch (exception: Exception) {
                logger.error("Failed to reject message: {}", body, exception)
            }
            return
        }

        try {
            connection.channel.basicAck(delivery.envelope.deliveryTag, false)
            logger.info("Your message has been processed successfully: {}", body)
        } catch (exception: Exception) {
            logger.error("Failed to acknowledge message: {}", body, exception)
        }
    }
}
