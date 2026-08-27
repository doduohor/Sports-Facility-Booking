package com.doduohor.service

import com.doduohor.events.ServerEvent

data class MonitoringTransactionResult(
    val result: MonitoringServiceResult,
    val events: List<ServerEvent>
)
