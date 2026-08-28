package com.doduohor.worker

import com.doduohor.infrastructure.database.mongo.MongoConfig
import com.doduohor.infrastructure.database.mongo.MongoFactory
import com.doduohor.infrastructure.messaging.RabbitMqConfig
import com.doduohor.infrastructure.messaging.RabbitMqConnection
import com.doduohor.infrastructure.messaging.RabbitMqConsumer
import com.doduohor.infrastructure.messaging.RabbitMqFactory
import com.doduohor.infrastructure.notification.TelegramConfig
import com.doduohor.infrastructure.notification.TelegramNotificationSender
import com.doduohor.infrastructure.time.SystemClock
import com.doduohor.repository.mongo.MongoEventHistoryRepository
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
    private val shutdownHooks: ShutdownHookRegistry = RuntimeShutdownHooks
) : AutoCloseable {
    private val lock = Any()
    private val connectionClosed = AtomicBoolean(false)
    private val resourcesClosed = AtomicBoolean(false)
    private var stopped = false
    private var run: Deferred<Unit>? = null
    private var workerJob: Job? = null
    private var connection: WorkerConnection? = null
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
            resources.forEach { resource ->
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
    val rabbitConfig = RabbitMqConfig.fromEnv()
    val telegramConfig = TelegramConfig.fromEnv()
    val mongoConnection = MongoFactory.connect(MongoConfig.fromEnv())
    val historyRepository = MongoEventHistoryRepository(mongoConnection.database, SystemClock)
    historyRepository.createIndexes()
    val messageHandler = MessageHandler(
        notificationSender = TelegramNotificationSender(telegramConfig),
        eventHistoryRepository = historyRepository,
        clock = SystemClock
    )
    val consumer = RabbitMqConsumer(messageHandler)
    val lifecycle = WorkerLifecycle(
        scope = this,
        connectionFactory = { RabbitWorkerConnection(RabbitMqFactory.connect(rabbitConfig)) },
        startConsumer = { connection ->
            consumer.startConsuming(
                (connection as RabbitWorkerConnection).delegate,
                rabbitConfig
            )
        },
        poll = {},
        resources = listOf(MongoWorkerResource { mongoConnection.client.close() })
    )

    try {
        lifecycle.start().await()
    } catch (exception: CancellationException) {
        logger.info("Worker stopped")
    }
}
