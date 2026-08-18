package com.doduohor.infrastructure.messaging

import io.ktor.server.config.ApplicationConfig

data class RabbitMqConfig(
    val host: String,
    val port: Int,
    val username: String,
    val password: String,
    val exchange: String,
    val queue: String,
    val routingKey: String,
    val deadLetterExchange: String,
    val deadLetterQueue: String,
    val deadLetterRoutingKey: String
){
    companion object {
        fun from(config: ApplicationConfig): RabbitMqConfig =
            RabbitMqConfig(
                host = config.property("rabbitmq.host").getString(),
                port = config.property("rabbitmq.port").getString().toInt(),
                username = config.property("rabbitmq.username").getString(),
                password = config.property("rabbitmq.password").getString(),
                exchange = config.property("rabbitmq.exchange").getString(),
                queue = config.property("rabbitmq.queue").getString(),
                routingKey = config.property("rabbitmq.routingKey").getString(),
                deadLetterExchange = config.property("rabbitmq.deadLetterExchange").getString(),
                deadLetterQueue = config.property("rabbitmq.deadLetterQueue").getString(),
                deadLetterRoutingKey = config.property("rabbitmq.deadLetterRoutingKey").getString()
            )
        fun fromEnv(): RabbitMqConfig =
            RabbitMqConfig(
                host = System.getenv("RABBIT_HOST"),
                port = System.getenv("RABBIT_PORT").toInt(),
                username = System.getenv("RABBIT_USER"),
                password = System.getenv("RABBIT_PASSWORD"),
                exchange = System.getenv("RABBIT_EXCHANGE"),
                queue = System.getenv("RABBIT_QUEUE"),
                routingKey = System.getenv("RABBIT_ROUTING_KEY"),
                deadLetterExchange = System.getenv("RABBIT_EXCHANGE_DLQ"),
                deadLetterQueue = System.getenv("RABBIT_QUEUE_DLQ"),
                deadLetterRoutingKey = System.getenv("RABBIT_ROUTING_KEY_DLQ")
            )
    }
}
