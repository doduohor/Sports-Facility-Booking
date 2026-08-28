package com.doduohor.worker

import com.doduohor.infrastructure.database.mongo.MongoConfig
import com.doduohor.infrastructure.database.mongo.MongoFactory
import com.doduohor.infrastructure.database.mongo.MongoConnection
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
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.time.Duration.Companion.seconds

interface WorkerConnection {
    fun close()
}

interface WorkerResource {
    fun close()
}

interface ShutdownHookRegistry {
    fun register(stop: () -> Unit)
}

private object RuntimeShutdownHooks : ShutdownHookRegistry {
    override fun register(stop: () -> Unit) {
        Runtime.getRuntime().addShutdownHook(Thread(stop))
    }
}

internal suspend fun <T> connectWithRetry(
    maxAttempts: Int = 5,
    retryDelay: suspend () -> Unit = { kotlinx.coroutines.delay(2.seconds) },
    connect: () -> T
): T {
    require(maxAttempts > 0) { "maxAttempts must be positive" }
    var lastFailure: Throwable? = null
    repeat(maxAttempts) { attempt ->
        currentCoroutineContext().ensureActive()
        try {
            return connect()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (exception: Throwable) {
            lastFailure = exception
            if (attempt + 1 < maxAttempts) retryDelay()
        }
    }
    throw requireNotNull(lastFailure)
}

private class RabbitWorkerConnection(
    internal val delegate: RabbitMqConnection
) : WorkerConnection {
    override fun close() = delegate.close()
}

private class MongoWorkerResource(
    private val closeAction: () -> Unit
) : WorkerResource {
    override fun close() = closeAction()
}

class WorkerLifecycle(
    private val scope: CoroutineScope,
    private val connectionFactory: suspend () -> WorkerConnection,
    private val startConsumer: suspend (WorkerConnection) -> Unit,
    private val poll: suspend () -> Unit,
    private val maxConnectionAttempts: Int = 5,
    private val retryDelay: suspend () -> Unit = { kotlinx.coroutines.delay(2.seconds) },
    private val pollDelay: suspend () -> Unit = { kotlinx.coroutines.delay(2.seconds) },
    private val resources: List<WorkerResource> = emptyList(),
    private val resourceFactory: suspend () -> List<WorkerResource> = { resources },
    private val shutdownHooks: ShutdownHookRegistry = RuntimeShutdownHooks
) : AutoCloseable {
    private val lock = Any()
    private val connectionClosed = AtomicBoolean(false)
    private val resourcesClosed = AtomicBoolean(false)
    private var stopped = false
    private var run: Deferred<Unit>? = null
    private var workerJob: Job? = null
    private var connection: WorkerConnection? = null
    private var activeResources: List<WorkerResource> = emptyList()
    private var hookRegistered = false

    fun start(): Deferred<Unit> = synchronized(lock) {
        if (stopped) {
            return CompletableDeferred<Unit>().apply { cancel() }
        }
        run ?: CompletableDeferred<Unit>().also { result ->
            run = result
            workerJob = scope.launch {
                try {
                    val failure = execute()
                    if (failure == null) result.complete(Unit)
                    else result.completeExceptionally(failure)
                } catch (cancelled: CancellationException) {
                    result.cancel(cancelled)
                }
            }
        }
    }

    fun stop() {
        synchronized(lock) {
            if (stopped) return
            stopped = true
            workerJob?.cancel()
            run?.cancel()
        }
        closeResources(null)
    }

    override fun close() = stop()

    private suspend fun execute(): Throwable? {
        var failure: Throwable? = null
        try {
            val connectedResources = resourceFactory()
            val resourcesNeedImmediateClose = synchronized(lock) {
                if (resourcesClosed.get()) true
                else {
                    activeResources = connectedResources
                    false
                }
            }
            if (resourcesNeedImmediateClose) {
                connectedResources.forEach(WorkerResource::close)
            }
            val connected = connectWithRetry()
            synchronized(lock) { connection = connected }
            currentCoroutineContext().ensureActive()

            try {
                startConsumer(connected)
                currentCoroutineContext().ensureActive()
                registerShutdownHook()
                while (currentCoroutineContext().isActive) {
                    try {
                        poll()
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (exception: Exception) {
                        LoggerFactory.getLogger("WorkerLifecycle")
                            .warn("Worker polling iteration failed", exception)
                    }
                    if (currentCoroutineContext().isActive) pollDelay()
                }
            } catch (exception: Throwable) {
                throw exception
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (exception: Throwable) {
            failure = exception
        } finally {
            closeResources(failure)
        }
        return failure
    }

    private suspend fun connectWithRetry(): WorkerConnection {
        require(maxConnectionAttempts > 0) { "maxConnectionAttempts must be positive" }
        var lastFailure: Throwable? = null
        repeat(maxConnectionAttempts) { attempt ->
            currentCoroutineContext().ensureActive()
            try {
                return connectionFactory()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (exception: Throwable) {
                lastFailure = exception
                if (attempt + 1 < maxConnectionAttempts) retryDelay()
            }
        }
        throw requireNotNull(lastFailure)
    }

    private fun registerShutdownHook() {
        synchronized(lock) {
            if (hookRegistered || stopped) return
            hookRegistered = true
            shutdownHooks.register(::stop)
        }
    }

    private fun closeResources(primary: Throwable?) {
        val closeFailures = mutableListOf<Throwable>()
        if (connectionClosed.compareAndSet(false, true)) {
            synchronized(lock) { connection }?.let { resource ->
                try {
                    resource.close()
                } catch (exception: Throwable) {
                    closeFailures += exception
                }
            }
        }
        if (resourcesClosed.compareAndSet(false, true)) {
            val resourcesToClose = synchronized(lock) { activeResources }.ifEmpty { resources }
            resourcesToClose.forEach { resource ->
                try {
                    resource.close()
                } catch (exception: Throwable) {
                    closeFailures += exception
                }
            }
        }
        closeFailures.drop(1).forEach { closeFailures.first().addSuppressed(it) }
        closeFailures.firstOrNull()?.let { closeError ->
            if (primary != null) primary.addSuppressed(closeError) else throw closeError
        }
    }
}

fun main() = runBlocking {
    val logger = LoggerFactory.getLogger("WorkerMain")
    val databaseFactory = DatabaseFactory()
    val databaseConfig = DatabaseConfig.fromEnv()
    val rabbitConfig = RabbitMqConfig.fromEnv()
    val telegramConfig = TelegramConfig.fromEnv()
    val mongoConfig = MongoConfig.fromEnv()
    var mongoConnection: MongoConnection? = null
    lateinit var historyRepository: MongoEventHistoryRepository
    lateinit var outboxRepository: PostgresOutboxEventsRepository
    lateinit var outboxPublisher: OutboxPublisher
    val lifecycle = WorkerLifecycle(
        scope = this,
        connectionFactory = { RabbitWorkerConnection(RabbitMqFactory.connect(rabbitConfig)) },
        startConsumer = { connection ->
            val rabbitConnection = (connection as RabbitWorkerConnection).delegate
            outboxPublisher = OutboxPublisher(
                outboxEventsRepository = outboxRepository,
                messagePublisher = RabbitMqPublisher(
                    rabbitMqConnection = rabbitConnection,
                    exchange = rabbitConfig.exchange,
                    routingKey = rabbitConfig.routingKey
                )
            )
            val messageHandler = MessageHandler(
                notificationSender = TelegramNotificationSender(telegramConfig),
                eventHistoryRepository = historyRepository,
                clock = SystemClock
            )
            RabbitMqConsumer(messageHandler).startConsuming(
                rabbitConnection,
                rabbitConfig
            )
        },
        poll = { outboxPublisher.publishMessage() },
        resourceFactory = {
            try {
                val database = connectWithRetry { databaseFactory.connect(databaseConfig) }
                val connectedMongo = connectWithRetry { MongoFactory.connect(mongoConfig) }
                mongoConnection = connectedMongo
                historyRepository = MongoEventHistoryRepository(connectedMongo.database, SystemClock)
                historyRepository.createIndexes()
                outboxRepository = PostgresOutboxEventsRepository(database, SystemClock)
                listOf(
                    MongoWorkerResource { connectedMongo.client.close() },
                    object : WorkerResource {
                        override fun close() = databaseFactory.close()
                    }
                )
            } catch (exception: Throwable) {
                mongoConnection?.client?.close()
                databaseFactory.close()
                throw exception
            }
        }
    )

    try {
        lifecycle.start().await()
    } catch (exception: CancellationException) {
        logger.info("Worker stopped")
    }
}
