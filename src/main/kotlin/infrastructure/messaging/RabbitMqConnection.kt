package com.doduohor.infrastructure.messaging

import com.rabbitmq.client.Channel
import com.rabbitmq.client.Connection

class RabbitMqConnection(
    val connection: Connection,
    val channel: Channel
){
    fun close(){
        if (channel.isOpen) {
            channel.close()
        }
        if (connection.isOpen) {
            connection.close()
        }
    }
}
