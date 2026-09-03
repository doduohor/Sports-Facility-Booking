package com.doduohor.infrastructure.notification

import io.ktor.server.config.ApplicationConfig

data class TelegramConfig(
    val botToken : String,
    val chatId: Long
){
    companion object{
        fun from(config: ApplicationConfig): TelegramConfig =
            TelegramConfig(
                botToken = config.property("telegram.bot_token").getString(),
                chatId = config.property("telegram.chat_id").getString().toLong()
            )
        fun fromEnv(): TelegramConfig =
            TelegramConfig(
                botToken = requireNotNull(System.getenv("TELEGRAM_BOT_TOKEN")) {
                    "TELEGRAM_BOT_TOKEN is required"
                },
                chatId = requireNotNull(System.getenv("TELEGRAM_CHAT_ID")) {
                    "TELEGRAM_CHAT_ID is required"
                }.toLong()
            )
    }
}
