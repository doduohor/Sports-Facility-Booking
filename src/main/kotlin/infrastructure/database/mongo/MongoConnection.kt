package com.doduohor.infrastructure.database.mongo

import com.mongodb.kotlin.client.coroutine.MongoClient
import com.mongodb.kotlin.client.coroutine.MongoDatabase

class MongoConnection (
    val client: MongoClient,
    val database: MongoDatabase
)