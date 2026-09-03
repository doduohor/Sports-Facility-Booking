package com.doduohor

import com.doduohor.infrastructure.notification.TelegramConfig
import io.ktor.server.config.MapApplicationConfig
import kotlin.test.Test
import kotlin.test.assertEquals

class TelegramConfigTest {
    @Test
    fun `from reads telegram config`() {
        val applicationConfig = MapApplicationConfig(
            "telegram.bot_token" to "token-123",
            "telegram.chat_id" to "-1001234567890"
        )

        val telegramConfig = TelegramConfig.from(applicationConfig)

        assertEquals("token-123", telegramConfig.botToken)
        assertEquals(-1001234567890L, telegramConfig.chatId)
    }
}
