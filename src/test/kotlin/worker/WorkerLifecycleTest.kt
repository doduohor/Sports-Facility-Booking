package com.doduohor.worker

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame
import kotlin.test.assertTrue

class WorkerLifecycleTest {
    @Test
    fun `worker resource connection retries independently and returns the first successful resource`() = runBlocking {
        var attempts = 0
        var delays = 0
        val expected = "connected"

        val actual = connectWithRetry(
            maxAttempts = 3,
            retryDelay = { delays += 1 }
        ) {
            attempts += 1
            if (attempts < 3) error("database unavailable")
            expected
        }

        assertEquals(expected, actual)
        assertEquals(3, attempts)
        assertEquals(2, delays)
    }

    @Test
    fun `successful start starts consumer polls and registers one shutdown hook`() = runBlocking {
        val consumerStarted = CompletableDeferred<Unit>()
        val pollingStarted = CompletableDeferred<Unit>()
        val connection = FakeConnection()
        val mongo = FakeResource()
        val hooks = FakeShutdownHooks()
        val scope = testScope()
        val lifecycle = WorkerLifecycle(
            scope = scope,
            connectionFactory = { connection },
            startConsumer = { consumerStarted.complete(Unit) },
            poll = {
                pollingStarted.complete(Unit)
                CompletableDeferred<Unit>().await()
            },
            retryDelay = {},
            pollDelay = {},
            resources = listOf(mongo),
            shutdownHooks = hooks
        )

        val run = lifecycle.start()
        consumerStarted.await()
        pollingStarted.await()

        assertEquals(1, hooks.registrations)
        assertSame(run, lifecycle.start())

        lifecycle.stop()
        assertFailsWith<CancellationException> { run.await() }
        assertEquals(1, connection.closeCalls)
        assertEquals(1, mongo.closeCalls)
        scope.cancel()
    }

    @Test
    fun `temporary RabbitMQ failure is retried before successful start`() = runBlocking {
        val consumerStarted = CompletableDeferred<Unit>()
        val connection = FakeConnection()
        val scope = testScope()
        var attempts = 0
        var retryDelays = 0
        val lifecycle = WorkerLifecycle(
            scope = scope,
            maxConnectionAttempts = 3,
            connectionFactory = {
                attempts += 1
                if (attempts == 1) error("RabbitMQ unavailable")
                connection
            },
            startConsumer = { consumerStarted.complete(Unit) },
            poll = { CompletableDeferred<Unit>().await() },
            retryDelay = { retryDelays += 1 },
            pollDelay = {},
            resources = emptyList(),
            shutdownHooks = FakeShutdownHooks()
        )

        val run = lifecycle.start()
        consumerStarted.await()

        assertEquals(2, attempts)
        assertEquals(1, retryDelays)
        lifecycle.stop()
        assertFailsWith<CancellationException> { run.await() }
        scope.cancel()
    }

    @Test
    fun `connection failure after retry limit keeps last failure and closes resources`() = runBlocking {
        val lastFailure = IllegalStateException("RabbitMQ still unavailable")
        val mongo = FakeResource()
        val scope = testScope()
        var attempts = 0
        var retryDelays = 0
        val lifecycle = WorkerLifecycle(
            scope = scope,
            maxConnectionAttempts = 3,
            connectionFactory = {
                attempts += 1
                throw lastFailure
            },
            startConsumer = {},
            poll = {},
            retryDelay = { retryDelays += 1 },
            pollDelay = {},
            resources = listOf(mongo),
            shutdownHooks = FakeShutdownHooks()
        )

        val failure = assertFailsWith<IllegalStateException> { lifecycle.start().await() }

        assertEquals(lastFailure::class, failure::class)
        assertEquals(lastFailure.message, failure.message)
        assertEquals(3, attempts)
        assertEquals(2, retryDelays)
        assertEquals(1, mongo.closeCalls)
        scope.cancel()
    }

    @Test
    fun `stop during connection starts neither consumer nor polling and closes once`() = runBlocking {
        val connectionStarted = CompletableDeferred<Unit>()
        val resource = FakeResource()
        val scope = testScope()
        var consumerStarts = 0
        var polls = 0
        val lifecycle = WorkerLifecycle(
            scope = scope,
            connectionFactory = {
                connectionStarted.complete(Unit)
                CompletableDeferred<WorkerConnection>().await()
            },
            startConsumer = { consumerStarts += 1 },
            poll = { polls += 1 },
            retryDelay = {},
            pollDelay = {},
            resources = listOf(resource),
            shutdownHooks = FakeShutdownHooks()
        )

        val run = lifecycle.start()
        connectionStarted.await()
        lifecycle.stop()
        lifecycle.close()

        assertFailsWith<CancellationException> { run.await() }
        assertEquals(0, consumerStarts)
        assertEquals(0, polls)
        assertEquals(1, resource.closeCalls)
        scope.cancel()
    }

    @Test
    fun `polling failure is isolated and next iteration still runs`() = runBlocking {
        val secondPollingStarted = CompletableDeferred<Unit>()
        val scope = testScope()
        var polls = 0
        val lifecycle = WorkerLifecycle(
            scope = scope,
            connectionFactory = { FakeConnection() },
            startConsumer = {},
            poll = {
                polls += 1
                if (polls == 1) error("temporary outbox failure")
                secondPollingStarted.complete(Unit)
                CompletableDeferred<Unit>().await()
            },
            retryDelay = {},
            pollDelay = {},
            resources = emptyList(),
            shutdownHooks = FakeShutdownHooks()
        )

        val run = lifecycle.start()
        secondPollingStarted.await()

        assertEquals(2, polls)
        lifecycle.stop()
        assertFailsWith<CancellationException> { run.await() }
        scope.cancel()
    }

    @Test
    fun `stop during polling prevents another polling iteration`() = runBlocking {
        val pollingStarted = CompletableDeferred<Unit>()
        val scope = testScope()
        var polls = 0
        val lifecycle = WorkerLifecycle(
            scope = scope,
            connectionFactory = { FakeConnection() },
            startConsumer = {},
            poll = {
                polls += 1
                pollingStarted.complete(Unit)
                CompletableDeferred<Unit>().await()
            },
            retryDelay = {},
            pollDelay = {},
            resources = emptyList(),
            shutdownHooks = FakeShutdownHooks()
        )

        val run = lifecycle.start()
        pollingStarted.await()

        lifecycle.stop()
        lifecycle.close()

        assertEquals(1, polls)
        assertFailsWith<CancellationException> { run.await() }
        scope.cancel()
    }

    @Test
    fun `main failure is retained when connection close also fails`() = runBlocking {
        val primary = IllegalStateException("consumer initialization failed")
        val closeFailure = IllegalArgumentException("connection close failed")
        val scope = testScope()
        val connection = FakeConnection(closeFailure)
        val lifecycle = WorkerLifecycle(
            scope = scope,
            connectionFactory = { connection },
            startConsumer = { throw primary },
            poll = {},
            retryDelay = {},
            pollDelay = {},
            resources = emptyList(),
            shutdownHooks = FakeShutdownHooks()
        )

        val failure = assertFailsWith<IllegalStateException> { lifecycle.start().await() }

        assertEquals(primary::class, failure::class)
        assertEquals(primary.message, failure.message)
        assertEquals(1, connection.closeCalls)
        scope.cancel()
    }

    @Test
    fun `resource factory runs before consumer and its resources are closed`() = runBlocking {
        val resource = FakeResource()
        val events = java.util.Collections.synchronizedList(mutableListOf<String>())
        val scope = testScope()
        val lifecycle = WorkerLifecycle(
            scope = scope,
            connectionFactory = { events += "connection"; FakeConnection() },
            resourceFactory = { events += "resources"; listOf(resource) },
            startConsumer = { events += "consumer" },
            poll = { CompletableDeferred<Unit>().await() },
            retryDelay = {},
            pollDelay = {},
            shutdownHooks = FakeShutdownHooks()
        )

        val run = lifecycle.start()
        while (true) {
            val complete = synchronized(events) {
                events == listOf("resources", "connection", "consumer")
            }
            if (complete) break
            kotlinx.coroutines.yield()
        }

        lifecycle.stop()
        assertFailsWith<CancellationException> { run.await() }
        assertEquals(1, resource.closeCalls)
        scope.cancel()
    }

    @Test
    fun `poll callback can publish outbox in the same lifecycle loop`() = runBlocking {
        val pollingStarted = CompletableDeferred<Unit>()
        val scope = testScope()
        var polls = 0
        val lifecycle = WorkerLifecycle(
            scope = scope,
            connectionFactory = { FakeConnection() },
            startConsumer = {},
            poll = {
                polls += 1
                pollingStarted.complete(Unit)
                CompletableDeferred<Unit>().await()
            },
            retryDelay = {},
            pollDelay = {},
            shutdownHooks = FakeShutdownHooks()
        )

        val run = lifecycle.start()
        pollingStarted.await()

        assertEquals(1, polls)
        lifecycle.stop()
        assertFailsWith<CancellationException> { run.await() }
        scope.cancel()
    }

    private fun testScope(): CoroutineScope = CoroutineScope(SupervisorJob())

    private class FakeConnection(
        private val closeFailure: Throwable? = null
    ) : WorkerConnection {
        var closeCalls = 0

        override fun close() {
            closeCalls += 1
            closeFailure?.let { throw it }
        }
    }

    private class FakeResource : WorkerResource {
        var closeCalls = 0

        override fun close() {
            closeCalls += 1
        }
    }

    private class FakeShutdownHooks : ShutdownHookRegistry {
        var registrations = 0

        override fun register(stop: () -> Unit) {
            registrations += 1
        }
    }
}
