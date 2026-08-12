package com.doduohor.infrastructure.database

import org.jetbrains.exposed.v1.core.Table

object EquipmentTable : Table("equipments") {
    val id = long("id").autoIncrement()
    val facilityId = long("facility_id")
    val name = varchar("name", length = 255)
    val type = varchar("type", length = 32)
    val status = varchar("status", length = 32)
    override val primaryKey = PrimaryKey(id)
}
