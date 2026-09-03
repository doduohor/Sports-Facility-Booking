package com.doduohor.infrastructure.notification

class TelegramNotificationSender(
    private val config: TelegramConfig,
    private val httpClient: TelegramHttpClient = JavaTelegramHttpClient()
) : NotificationSender {
    override fun send(message: String): NotificationSenderResult {
        return try {
            val response = httpClient.postForm(
                url = "https://api.telegram.org/bot${config.botToken}/sendMessage",
                form = mapOf(
                    "chat_id" to config.chatId.toString(),
                    "text" to message
                )
            )

            if (response.statusCode in 200..299) {
                NotificationSenderResult.Success
            } else {
                NotificationSenderResult.Failure(
                    "Telegram returned ${response.statusCode}: ${response.body}"
                )
            }
        } catch (exception: Exception) {
            NotificationSenderResult.Failure(
                exception.message ?: "Telegram request failed"
            )
        }
    }
}
