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
        fun fromEnv(): RabbitMqConfig = fromEnv(System::getenv)

        internal fun fromEnv(readEnv: (String) -> String?): RabbitMqConfig =
            RabbitMqConfig(
                host = requiredEnv("RABBIT_HOST", readEnv),
                port = requiredEnv("RABBIT_PORT", readEnv).toIntOrNull()
                    ?.also { require(it in 1..65_535) { "RABBIT_PORT must be between 1 and 65535" } }
                    ?: error("RABBIT_PORT must be an integer"),
                username = requiredEnv("RABBIT_USER", readEnv),
                password = requiredEnv("RABBIT_PASSWORD", readEnv),
                exchange = requiredEnv("RABBIT_EXCHANGE", readEnv),
                queue = requiredEnv("RABBIT_QUEUE", readEnv),
                routingKey = requiredEnv("RABBIT_ROUTING_KEY", readEnv),
                deadLetterExchange = requiredEnv("RABBIT_EXCHANGE_DLQ", readEnv),
                deadLetterQueue = requiredEnv("RABBIT_QUEUE_DLQ", readEnv),
                deadLetterRoutingKey = requiredEnv("RABBIT_ROUTING_KEY_DLQ", readEnv)
            )

        private fun requiredEnv(name: String, readEnv: (String) -> String?): String =
            readEnv(name)?.takeIf { it.isNotBlank() }
                ?: error("Required environment variable is missing or blank: $name")
    }
}
