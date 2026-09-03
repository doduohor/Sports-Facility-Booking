package com.doduohor

import com.doduohor.infrastructure.notification.NotificationSenderResult
import com.doduohor.infrastructure.notification.TelegramConfig
import com.doduohor.infrastructure.notification.TelegramHttpClient
import com.doduohor.infrastructure.notification.TelegramHttpResponse
import com.doduohor.infrastructure.notification.TelegramNotificationSender
import kotlin.test.Test
import kotlin.test.assertEquals

class TelegramNotificationSenderTest {
    private class FakeTelegramHttpClient(
        private val response: TelegramHttpResponse = TelegramHttpResponse(
            statusCode = 200,
            body = """{"ok":true}"""
        )
    ) : TelegramHttpClient {
        var requestedUrl: String? = null
        var requestedForm: Map<String, String>? = null

        override fun postForm(url: String, form: Map<String, String>): TelegramHttpResponse {
            requestedUrl = url
            requestedForm = form
            return response
        }
    }

    private class FailingTelegramHttpClient : TelegramHttpClient {
        override fun postForm(url: String, form: Map<String, String>): TelegramHttpResponse {
            throw IllegalStateException("network failed")
        }
    }

    @Test
    fun `send posts message to telegram api`() {
        val httpClient = FakeTelegramHttpClient()
        val sender = TelegramNotificationSender(
            config = TelegramConfig(botToken = "token-123", chatId = 42),
            httpClient = httpClient
        )

        val result = sender.send("High incident detected")

        assertEquals(NotificationSenderResult.Success, result)
        assertEquals("https://api.telegram.org/bottoken-123/sendMessage", httpClient.requestedUrl)
        assertEquals(
            mapOf(
                "chat_id" to "42",
                "text" to "High incident detected"
            ),
            httpClient.requestedForm
        )
    }

    @Test
    fun `send returns failure when telegram returns non successful status`() {
        val httpClient = FakeTelegramHttpClient(
            TelegramHttpResponse(statusCode = 401, body = """{"ok":false}""")
        )
        val sender = TelegramNotificationSender(
            config = TelegramConfig(botToken = "token-123", chatId = 42),
            httpClient = httpClient
        )

        val result = sender.send("High incident detected")

        assertEquals(
            NotificationSenderResult.Failure("""Telegram returned 401: {"ok":false}"""),
            result
        )
    }

    @Test
    fun `send returns failure when http client throws exception`() {
        val sender = TelegramNotificationSender(
            config = TelegramConfig(botToken = "token-123", chatId = 42),
            httpClient = FailingTelegramHttpClient()
        )

        val result = sender.send("High incident detected")

        assertEquals(NotificationSenderResult.Failure("network failed"), result)
    }
}
