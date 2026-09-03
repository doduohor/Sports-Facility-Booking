package com.doduohor.infrastructure.notification

interface NotificationSender {
    fun send(message: String): NotificationSenderResult
}


sealed interface NotificationSenderResult {
    data object Success : NotificationSenderResult
    data class Failure(val reason: String) : NotificationSenderResult
}