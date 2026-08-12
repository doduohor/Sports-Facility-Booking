package com.doduohor.infrastructure.database

import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.javatime.timestamp

object IncidentTable : Table("incidents") {
    val id = long("id").autoIncrement()
    val facilityId = long("facility_id")
    val equipmentId = long("equipment_id")
    val measurementId = long("measurement_id")
    val type = varchar("type", length = 32)
    val severity = varchar("severity", length = 32)
    val status = varchar("status", length = 32)
    val measurementType = varchar("measurement_type", length = 32)
    val measurementUnit = varchar("measurement_unit", length = 32)
    val value = double("value")
    val createdAt = timestamp("created_at")
    override val primaryKey = PrimaryKey(id)
}
