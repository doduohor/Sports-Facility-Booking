package com.doduohor.events

import kotlinx.coroutines.channels.Channel

class EventPublisher{
    private val subscribers = mutableSetOf<Channel<ServerEvent>>()

    fun subscribe() : Channel<ServerEvent> {
        val channel = Channel<ServerEvent>()
        subscribers.add(channel)
        return channel
    }

    fun unsubscribe(channel : Channel<ServerEvent>){
        subscribers.remove(channel)
        channel.close()
    }

    suspend fun publish(event: ServerEvent){
        subscribers.forEach { channel ->
            channel.send(event)
        }
    }
}