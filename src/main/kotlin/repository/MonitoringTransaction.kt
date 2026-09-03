package com.doduohor.repository

import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

interface MonitoringTransaction {
    fun <T> execute(block: () -> T): T
}

class PostgresMonitoringTransaction(
    private val database: Database
) : MonitoringTransaction {
    override fun <T> execute(block: () -> T): T = transaction(database) { block() }
}
