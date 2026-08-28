package com.doduohor.worker

import com.doduohor.infrastructure.database.mongo.MongoConfig
import com.doduohor.infrastructure.database.mongo.MongoFactory
import com.doduohor.infrastructure.database.postgres.DatabaseConfig
import com.doduohor.infrastructure.database.postgres.DatabaseFactory
import com.doduohor.infrastructure.messaging.RabbitMqConfig
import com.doduohor.infrastructure.messaging.RabbitMqConnection
import com.doduohor.infrastructure.messaging.RabbitMqConsumer
import com.doduohor.infrastructure.messaging.RabbitMqFactory
import com.doduohor.infrastructure.messaging.RabbitMqPublisher
import com.doduohor.infrastructure.notification.TelegramConfig
import com.doduohor.infrastructure.notification.TelegramNotificationSender
import com.doduohor.infrastructure.time.SystemClock
import com.doduohor.repository.mongo.MongoEventHistoryRepository
import com.doduohor.repository.postgres.PostgresOutboxEventsRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import org.slf4j.LoggerFactory

fun interface WorkerConnector {
    suspend fun connect(): WorkerRuntime
}

interface WorkerRuntime {
    suspend fun start(scope: CoroutineScope)
    suspend fun stop()
}

fun interface ShutdownHookRegistrar {
    fun register(callback: () -> Unit)
}

object RuntimeShutdownHookRegistrar : ShutdownHookRegistrar {
    override fun register(callback: () -> Unit) {
        Runtime.getRuntime().addShutdownHook(Thread(callback))
    }
}

class WorkerLifecycle(
    private val connector: WorkerConnector,
    private val shutdownHooks: ShutdownHookRegistrar,
    private val maxConnectionAttempts: Int = 5,
    private val retryDelay: suspend () -> Unit = { delay(2_000) }
) {
    private val logger = LoggerFactory.getLogger("WorkerLifecycle")
    private val lock = Any()
    private var job: Deferred<Unit>? = null
    private var stopRequested = false
    private var runtime: WorkerRuntime? = null
    private var shutdownHookRegistered = false

    fun start(scope: CoroutineScope): Deferred<Unit> = synchronized(lock) {
        check(maxConnectionAttempts > 0) { "maxConnectionAttempts must be positive" }
        job ?: scope.async {
            runLifecycle(this)
        }.also { job = it }
    }

    fun stop() {
        synchronized(lock) {
            stopRequested = true
            job?.cancel()
        }
    }

    suspend fun close() {
        stop()
        try {
            job?.await()
        } catch (exception: CancellationException) {
            if (!stopRequested) throw exception
        }
    }

    private suspend fun runLifecycle(scope: CoroutineScope) {
        var primaryFailure: Throwable? = null
        try {
            val connectedRuntime = connectWithRetry()
            synchronized(lock) {
                if (stopRequested) {
                    throw CancellationException("Worker stopped before startup")
                }
                runtime = connectedRuntime
            }
            connectedRuntime.start(scope)
            synchronized(lock) {
                if (!shutdownHookRegistered) {
                    shutdownHooks.register(::stop)
                    shutdownHookRegistered = true
                }
            }
            awaitCancellation()
        } catch (exception: CancellationException) {
            primaryFailure = exception
        } catch (exception: Throwable) {
            primaryFailure = exception
        }

        val currentRuntime = synchronized(lock) { runtime }
        var closeFailure: Throwable? = null
        if (currentRuntime != null) {
            try {
                currentRuntime.stop()
            } catch (exception: Throwable) {
                closeFailure = exception
            }
        }

        val failure = primaryFailure
        checkNotNull(failure)
        closeFailure?.let { failure.addSuppressed(it) }
        if (!(failure is CancellationException && stopRequested && closeFailure == null)) {
            throw failure
        }
    }

    private suspend fun connectWithRetry(): WorkerRuntime {
        var lastFailure: Throwable? = null
        repeat(maxConnectionAttempts) { attempt ->
            try {
                return connector.connect()
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Throwable) {
                lastFailure = exception
                logger.warn(
                    "RabbitMQ connection attempt {} of {} failed",
                    attempt + 1,
                    maxConnectionAttempts,
                    exception
                )
                if (attempt + 1 < maxConnectionAttempts) {
                    retryDelay()
                }
            }
        }
        throw checkNotNull(lastFailure)
    }
}

class RabbitWorkerRuntime(
    private val connection: RabbitMqConnection,
    private val consumer: RabbitMqConsumer,
    private val rabbitConfig: RabbitMqConfig,
    private val outboxPublisher: OutboxPublisher
) : WorkerRuntime {
    override suspend fun start(scope: CoroutineScope) {
        consumer.startConsuming(connection, rabbitConfig)
        outboxPublisher.start(scope)
    }

    override suspend fun stop() {
        var failure: Throwable? = null
        try {
            outboxPublisher.close()
        } catch (exception: Throwable) {
            failure = exception
        }
        try {
            consumer.stopConsuming()
        } catch (exception: Throwable) {
            failure?.addSuppressed(exception) ?: run { failure = exception }
        }
        try {
            connection.close()
        } catch (exception: Throwable) {
            failure?.addSuppressed(exception) ?: run { failure = exception }
        }
        failure?.let { throw it }
    }
}

class WorkerConnectorFactory(
    private val rabbitConfig: RabbitMqConfig,
    private val messageHandler: MessageHandler,
    private val outboxPublisherFactory: (RabbitMqConnection) -> OutboxPublisher
) : WorkerConnector {
    override suspend fun connect(): WorkerRuntime {
        val connection = RabbitMqFactory.connect(rabbitConfig)
        return try {
            RabbitWorkerRuntime(
                connection,
                RabbitMqConsumer(messageHandler),
                rabbitConfig,
                outboxPublisherFactory(connection)
            )
        } catch (exception: Throwable) {
            connection.close()
            throw exception
        }
    }
}

fun main() {
    val logger = LoggerFactory.getLogger("WorkerMain")
    val rabbitConfig = RabbitMqConfig.fromEnv()
    val databaseFactory = DatabaseFactory()
    val mongoConnection = MongoFactory.connect(MongoConfig.fromEnv())
    val database = databaseFactory.connect(DatabaseConfig.fromEnv())
    val clock = SystemClock
    val eventHistoryRepository = MongoEventHistoryRepository(mongoConnection.database, clock)
    val notificationHandler = TelegramNotificationSender(TelegramConfig.fromEnv())
    val messageHandler = MessageHandler(notificationHandler, eventHistoryRepository, clock)

    kotlinx.coroutines.runBlocking {
        eventHistoryRepository.createIndexes()
        val lifecycle = WorkerLifecycle(
            connector = WorkerConnectorFactory(
                rabbitConfig,
                messageHandler
            ) { connection ->
                OutboxPublisher(
                    PostgresOutboxEventsRepository(database, clock),
                    RabbitMqPublisher(connection, rabbitConfig.exchange, rabbitConfig.routingKey)
                )
            },
            shutdownHooks = RuntimeShutdownHookRegistrar
        )
        try {
            lifecycle.start(this).await()
        } finally {
            try {
                lifecycle.close()
            } finally {
                databaseFactory.close()
                mongoConnection.client.close()
            }
        }
    }
    logger.info("Worker stopped")
}
