package com.doduohor

import com.doduohor.infrastructure.messaging.RabbitMqConfig
import io.ktor.server.config.MapApplicationConfig
import kotlin.test.Test
import kotlin.test.assertEquals

class RabbitMqConfigTest {

    @Test
    fun `from reads rabbitmq config`() {
        val applicationConfig = MapApplicationConfig(
            "rabbitmq.host" to "rabbit",
            "rabbitmq.port" to "5672",
            "rabbitmq.username" to "testRabbit",
            "rabbitmq.password" to "change_me",
            "rabbitmq.exchange" to "sports.events",
            "rabbitmq.queue" to "sports.measurements",
            "rabbitmq.routingKey" to "measurement.created",
            "rabbitmq.deadLetterExchange" to "sports.events.dlq",
            "rabbitmq.deadLetterQueue" to "sports.measurements.dlq",
            "rabbitmq.deadLetterRoutingKey" to "measurement.created.dlq"
        )

        val rabbitMqConfig = RabbitMqConfig.from(applicationConfig)

        assertEquals("rabbit", rabbitMqConfig.host)
        assertEquals(5672, rabbitMqConfig.port)
        assertEquals("testRabbit", rabbitMqConfig.username)
        assertEquals("change_me", rabbitMqConfig.password)
        assertEquals("sports.events", rabbitMqConfig.exchange)
        assertEquals("sports.measurements", rabbitMqConfig.queue)
        assertEquals("measurement.created", rabbitMqConfig.routingKey)
        assertEquals("sports.events.dlq", rabbitMqConfig.deadLetterExchange)
        assertEquals("sports.measurements.dlq", rabbitMqConfig.deadLetterQueue)
        assertEquals("measurement.created.dlq", rabbitMqConfig.deadLetterRoutingKey)
    }
}
