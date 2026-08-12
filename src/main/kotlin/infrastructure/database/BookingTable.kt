package com.doduohor.infrastructure.database

import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.javatime.timestamp

object BookingTable: Table("bookings") {
    val id = long("id").autoIncrement()
    val facilityId = long("facility_id")
    val customerId = integer("customer_id")
    val startTime = timestamp("start_time")
    val endTime = timestamp("end_time")
    val status = varchar("status", 32)
    val createdAt = timestamp("created_at")
    override val primaryKey = PrimaryKey(id)
}