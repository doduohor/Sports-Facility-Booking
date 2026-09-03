package com.doduohor.infrastructure.notification

object NotificationSenderFactory {
    fun fromEnv(
        mode: String = System.getenv("TELEGRAM_NOTIFICATION_MODE") ?: "telegram",
        telegramConfig: TelegramConfig? = null
    ): NotificationSender = when (mode.trim().ifEmpty { "telegram" }.lowercase()) {
        "stub" -> StubNotificationSender
        "telegram" -> TelegramNotificationSender(telegramConfig ?: TelegramConfig.fromEnv())
        else -> error("Unsupported TELEGRAM_NOTIFICATION_MODE: $mode")
    }
}

private object StubNotificationSender : NotificationSender {
    override fun send(message: String): NotificationSenderResult = NotificationSenderResult.Success
}
