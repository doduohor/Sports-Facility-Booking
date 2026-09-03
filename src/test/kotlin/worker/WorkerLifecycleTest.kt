package com.doduohor.worker

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import com.doduohor.infrastructure.messaging.RabbitMqConfig

class WorkerLifecycleTest {
    @Test
    fun `rabbit runtime stops outbox consumer and connection in order and is idempotent`() = runBlocking {
        val events = mutableListOf<String>()
        val runtime = RabbitWorkerRuntime(
            connection = FakeConnection(events),
            consumer = FakeConsumer(events),
            rabbitConfig = testRabbitConfig(),
            outboxPublisher = FakeOutbox(events)
        )

        runtime.start(CoroutineScope(Dispatchers.Default))
        runtime.stop()
        runtime.stop()

        assertEquals(listOf("consumer.start", "outbox.start", "outbox.close", "consumer.stop", "connection.close"), events)
    }

    @Test
    fun `rabbit runtime does not start outbox after cancellation between resources`() = runBlocking {
        val events = mutableListOf<String>()
        val runtime = RabbitWorkerRuntime(
            connection = FakeConnection(events),
            consumer = FakeConsumer(events, cancelAfterStart = true),
            rabbitConfig = testRabbitConfig(),
            outboxPublisher = FakeOutbox(events)
        )

        assertFailsWith<CancellationException> {
            runtime.start(CoroutineScope(Dispatchers.Default))
        }

        assertEquals(listOf("consumer.start"), events)
    }

    @Test
    fun `rabbit runtime closes every resource and preserves first close failure`() = runBlocking {
        val events = mutableListOf<String>()
        val outboxFailure = IllegalStateException("outbox close")
        val runtime = RabbitWorkerRuntime(
            connection = FakeConnection(events, IllegalArgumentException("connection close")),
            consumer = FakeConsumer(events, stopFailure = IllegalStateException("consumer close")),
            rabbitConfig = testRabbitConfig(),
            outboxPublisher = FakeOutbox(events, outboxFailure)
        )

        val thrown = assertFailsWith<IllegalStateException> { runtime.stop() }

        assertEquals(outboxFailure, thrown)
        assertEquals(listOf("outbox.close", "consumer.stop", "connection.close"), events)
        assertEquals(2, thrown.suppressed.size)
    }
    @Test
    fun `successful start registers one shutdown hook and stops runtime once`() = runBlocking {
        val runtime = FakeWorkerRuntime()
        val hooks = FakeShutdownHooks()
        val lifecycle = WorkerLifecycle(
            connector = FakeWorkerConnector(runtime),
            shutdownHooks = hooks,
            retryDelay = { }
        )

        lifecycle.start(CoroutineScope(Dispatchers.Default))
        runtime.started.await()
        withTimeout(2_000) {
            while (hooks.callbacks.isEmpty()) yield()
        }

        assertEquals(1, hooks.callbacks.size)
        lifecycle.stop()
        lifecycle.close()

        assertEquals(1, runtime.startCount)
        assertEquals(1, runtime.stopCount)
    }

    @Test
    fun `temporary connection failures are retried`() = runBlocking {
        val runtime = FakeWorkerRuntime()
        val connector = FakeWorkerConnector(runtime, failuresBeforeSuccess = 2)
        val lifecycle = WorkerLifecycle(connector, FakeShutdownHooks(), retryDelay = { })

        lifecycle.start(CoroutineScope(Dispatchers.Default))
        runtime.started.await()
        lifecycle.stop()
        withTimeout(2_000) { lifecycle.close() }

        assertEquals(3, connector.attempts)
    }

    @Test
    fun `connection retry exhaustion keeps the startup failure`() = runBlocking {
        val failure = IllegalStateException("Rabbit unavailable")
        val lifecycle = WorkerLifecycle(
            connector = FakeWorkerConnector(failure = failure),
            shutdownHooks = FakeShutdownHooks(),
            maxConnectionAttempts = 2,
            retryDelay = { }
        )

        val job = lifecycle.start(CoroutineScope(Dispatchers.Default))

        val thrown = assertFailsWith<IllegalStateException> {
            withTimeout(2_000) { job.await() }
        }
        assertEquals(failure::class, thrown::class)
        assertEquals(failure.message, thrown.message)
    }

    @Test
    fun `stop during connection prevents runtime start and hook registration`() = runBlocking {
        val connectionStarted = CompletableDeferred<Unit>()
        val releaseConnection = CompletableDeferred<Unit>()
        val connector = FakeWorkerConnector(
            connectionStarted = connectionStarted,
            blockConnection = releaseConnection
        )
        val hooks = FakeShutdownHooks()
        val lifecycle = WorkerLifecycle(connector, hooks, retryDelay = { delay(10) })

        val job = lifecycle.start(CoroutineScope(Dispatchers.Default))
        connectionStarted.await()
        lifecycle.stop()
        lifecycle.close()

        assertFalse(job.isActive)
        assertEquals(0, connector.runtime?.startCount ?: 0)
        assertTrue(hooks.callbacks.isEmpty())
    }

    @Test
    fun `shutdown hook stops lifecycle`() = runBlocking {
        val runtime = FakeWorkerRuntime()
        val hooks = FakeShutdownHooks()
        val lifecycle = WorkerLifecycle(FakeWorkerConnector(runtime), hooks, retryDelay = { })

        lifecycle.start(CoroutineScope(Dispatchers.Default))
        runtime.started.await()
        hooks.callbacks.single().invoke()
        lifecycle.close()

        assertEquals(1, runtime.stopCount)
    }

    @Test
    fun `start after stop is rejected`() = runBlocking {
        val lifecycle = WorkerLifecycle(FakeWorkerConnector(FakeWorkerRuntime()), FakeShutdownHooks())

        lifecycle.stop()

        assertFailsWith<IllegalStateException> {
            lifecycle.start(CoroutineScope(Dispatchers.Default))
        }
    }

    @Test
    fun `close failure is suppressed behind the primary failure`() = runBlocking {
        val startupFailure = IllegalStateException("startup failed")
        val closeFailure = IllegalArgumentException("close failed")
        val runtime = FakeWorkerRuntime(startFailure = startupFailure, stopFailure = closeFailure)
        val lifecycle = WorkerLifecycle(
            connector = FakeWorkerConnector(runtime),
            shutdownHooks = FakeShutdownHooks(),
            retryDelay = { }
        )

        val job = lifecycle.start(CoroutineScope(Dispatchers.Default))
        val thrown = try {
            withTimeout(2_000) { job.await() }
            error("Expected startup failure")
        } catch (exception: IllegalStateException) {
            exception
        }

        assertNotNull(thrown)
        assertEquals(1, runtime.stopCount)
        assertEquals(startupFailure::class, thrown::class)
        assertEquals(startupFailure.message, thrown.message)
        assertEquals(listOf(closeFailure), startupFailure.suppressed.toList())
    }

    private class FakeShutdownHooks : ShutdownHookRegistrar {
        val callbacks = mutableListOf<() -> Unit>()

        override fun register(callback: () -> Unit) {
            callbacks += callback
        }
    }

    private class FakeWorkerConnector(
        private val defaultRuntime: FakeWorkerRuntime? = null,
        private val failuresBeforeSuccess: Int = 0,
        private val failure: Exception? = null,
        private val connectionStarted: CompletableDeferred<Unit>? = null,
        private val blockConnection: CompletableDeferred<Unit>? = null,
        private val startFailure: Exception? = null
    ) : WorkerConnector {
        var attempts = 0
        var runtime: FakeWorkerRuntime? = null

        override suspend fun connect(): WorkerRuntime {
            attempts++
            connectionStarted?.complete(Unit)
            blockConnection?.await()
            failure?.let { throw it }
            if (attempts <= failuresBeforeSuccess) {
                throw IllegalStateException("temporary failure")
            }
            return (defaultRuntime ?: FakeWorkerRuntime(startFailure = startFailure)).also { runtime = it }
        }
    }

    private class FakeWorkerRuntime(
        private val startFailure: Exception? = null,
        private val stopFailure: Exception? = null
    ) : WorkerRuntime {
        val started = CompletableDeferred<Unit>()
        var startCount = 0
        var stopCount = 0

        override suspend fun start(scope: CoroutineScope) {
            startCount++
            startFailure?.let { throw it }
            started.complete(Unit)
        }

        override suspend fun stop() {
            stopCount++
            stopFailure?.let { throw it }
        }
    }

    private class FakeConnection(private val events: MutableList<String>, private val failure: Exception? = null) : WorkerConnection {
        override fun close() { events += "connection.close"; failure?.let { throw it } }
    }

    private class FakeConsumer(
        private val events: MutableList<String>,
        private val cancelAfterStart: Boolean = false,
        private val stopFailure: Exception? = null
    ) : WorkerConsumer {
        override fun configure(connection: WorkerConnection, config: RabbitMqConfig) { }

        override fun start(scope: CoroutineScope) {
            events += "consumer.start"
            if (cancelAfterStart) throw CancellationException("cancelled")
        }

        override suspend fun stop() { events += "consumer.stop"; stopFailure?.let { throw it } }
    }

    private class FakeOutbox(private val events: MutableList<String>, private val closeFailure: Exception? = null) : WorkerOutboxPublisher {
        override fun start(scope: CoroutineScope): kotlinx.coroutines.Job { events += "outbox.start"; return kotlinx.coroutines.Job() }
        override suspend fun close() { events += "outbox.close"; closeFailure?.let { throw it } }
    }

    private fun testRabbitConfig() = RabbitMqConfig("host", 1, "user", "password", "exchange", "queue", "key", "dlx", "dlq", "dlkey")
}
