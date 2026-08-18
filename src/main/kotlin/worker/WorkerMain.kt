package com.doduohor.worker
import com.doduohor.infrastructure.database.mongo.MongoConfig
import com.doduohor.infrastructure.database.mongo.MongoConnection
import com.doduohor.infrastructure.database.mongo.MongoFactory
import com.doduohor.infrastructure.messaging.RabbitMqConfig
import com.doduohor.infrastructure.messaging.RabbitMqConsumer
import com.doduohor.infrastructure.messaging.RabbitMqFactory
import com.doduohor.infrastructure.notification.TelegramConfig
import com.doduohor.infrastructure.notification.TelegramNotificationSender
import com.doduohor.repository.mongo.MongoEventHistoryRepository
import com.mongodb.reactivestreams.client.MongoClient
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import kotlin.system.exitProcess

fun main(){
    val logger = LoggerFactory.getLogger("WorkerMain")
    logger.info("Worker started")
    val rabbitConfig = RabbitMqConfig.fromEnv()
    val telegramConfig = TelegramConfig.fromEnv()
    val mongoConfig = MongoConfig.fromEnv()
    val mongoConnection = MongoFactory.connect(mongoConfig)
    val eventHistoryRepository = MongoEventHistoryRepository(mongoConnection.database)
    runBlocking { eventHistoryRepository.createIndexes() }
    val notificationHandler = TelegramNotificationSender(telegramConfig)
    val messageHandler = MessageHandler(
        notificationSender = notificationHandler,
        eventHistoryRepository = eventHistoryRepository
    )
    val rabbitConsumer = RabbitMqConsumer(messageHandler)
    repeat(5){
        try{
            val connection = RabbitMqFactory.connect(rabbitConfig)
            logger.info("Connection successful")
            Runtime.getRuntime().addShutdownHook(
                Thread { connection.close() }
            )
            Runtime.getRuntime().addShutdownHook(
                Thread { mongoConnection.client.close() }
            )
            rabbitConsumer.startConsuming(connection, rabbitConfig)
            while(true){
                Thread.sleep(2000)
            }
        } catch (exception: Exception){
            logger.warn("RabbitMq connection failed", exception)
        }
        Thread.sleep(2000)
    }
    logger.error("Error connecting to RabbitMq")
    exitProcess(1)
}