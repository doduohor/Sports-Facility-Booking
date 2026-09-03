package com.doduohor.events

interface OutboxEventWriter {
    fun saveEvent(event: NewOutboxEvents): SaveEventResult
}
