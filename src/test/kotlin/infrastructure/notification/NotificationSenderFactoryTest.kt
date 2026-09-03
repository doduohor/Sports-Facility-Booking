package com.doduohor.infrastructure.notification

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class NotificationSenderFactoryTest {
    @Test
    fun `stub mode returns successful sender without telegram credentials`() {
        val sender = NotificationSenderFactory.fromEnv(
            mode = "stub",
            telegramConfig = null
        )

        assertEquals(NotificationSenderResult.Success, sender.send("test incident"))
    }

    @Test
    fun `blank mode uses telegram sender`() {
        val sender = NotificationSenderFactory.fromEnv(
            mode = " ",
            telegramConfig = TelegramConfig(botToken = "token", chatId = 42)
        )

        assertIs<TelegramNotificationSender>(sender)
    }
}
