package com.doduohor.infrastructure.database

import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.javatime.timestamp

object MeasurementTable : Table("measurements") {
    val id = long("id").autoIncrement()
    val equipmentId = long("equipment_id")
    val type = varchar("type", length = 32)
    val unit = varchar("unit", length = 32)
    val value = double("value")
    val createdAt = timestamp("created_at")
    override val primaryKey = PrimaryKey(id)
}
