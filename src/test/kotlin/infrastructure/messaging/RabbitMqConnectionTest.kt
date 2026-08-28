package com.doduohor.infrastructure.messaging

import java.lang.reflect.Proxy
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class RabbitMqConnectionTest {
    @Test
    fun `close closes channel and connection only once`() {
        val channelClosed = AtomicInteger()
        val connectionClosed = AtomicInteger()
        val connection = RabbitMqConnection(
            fakeResource(com.rabbitmq.client.Connection::class.java, connectionClosed),
            fakeResource(com.rabbitmq.client.Channel::class.java, channelClosed)
        )

        connection.close()
        connection.close()

        assertEquals(1, channelClosed.get())
        assertEquals(1, connectionClosed.get())
    }

    @Test
    fun `connection is closed when channel close fails`() {
        val connectionClosed = AtomicInteger()
        val channel = Proxy.newProxyInstance(
            com.rabbitmq.client.Channel::class.java.classLoader,
            arrayOf(com.rabbitmq.client.Channel::class.java)
        ) { _, method, _ ->
            if (method.name == "close") throw IllegalStateException("channel close failed")
            defaultValue(method.returnType)
        } as com.rabbitmq.client.Channel
        val connection = RabbitMqConnection(
            fakeResource(com.rabbitmq.client.Connection::class.java, connectionClosed),
            channel
        )

        assertFailsWith<IllegalStateException> { connection.close() }
        assertEquals(1, connectionClosed.get())
    }

    private fun <T> fakeResource(type: Class<T>, closeCount: AtomicInteger): T =
        Proxy.newProxyInstance(type.classLoader, arrayOf(type)) { _, method, _ ->
            if (method.name == "close") closeCount.incrementAndGet()
            defaultValue(method.returnType)
        } as T

    private fun defaultValue(type: Class<*>): Any? = when {
        !type.isPrimitive -> null
        type == Boolean::class.javaPrimitiveType -> false
        type == Int::class.javaPrimitiveType -> 0
        type == Long::class.javaPrimitiveType -> 0L
        type == Short::class.javaPrimitiveType -> 0.toShort()
        type == Byte::class.javaPrimitiveType -> 0.toByte()
        type == Float::class.javaPrimitiveType -> 0f
        type == Double::class.javaPrimitiveType -> 0.0
        type == Char::class.javaPrimitiveType -> '\u0000'
        else -> null
    }
}
