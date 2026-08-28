package com.doduohor.infrastructure.database.mongo
import com.mongodb.kotlin.client.coroutine.MongoClient
import kotlinx.coroutines.runBlocking
import org.bson.BsonDocument
import org.bson.BsonInt32

object MongoFactory {

    fun connect(config: MongoConfig): MongoConnection {
        val connectionString =
            "mongodb://${config.username}:${config.password}" +
                    "@${config.host}:${config.port}/?authSource=admin"

        val client = MongoClient.create(connectionString)
        return try {
            val database = client.getDatabase(config.database)
            runBlocking {
                database.runCommand<BsonDocument>(
                    BsonDocument("ping", BsonInt32(1))
                )
            }

            MongoConnection(
                client = client,
                database = database
            )
        } catch (exception: Throwable) {
            client.close()
            throw exception
        }
    }
}
