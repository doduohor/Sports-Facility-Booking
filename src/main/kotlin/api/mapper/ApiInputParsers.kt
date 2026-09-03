package com.doduohor.api.mapper

import com.doduohor.domain.model.EquipmentType
import com.doduohor.domain.model.FacilityType
import com.doduohor.domain.model.MeasurementType
import com.doduohor.domain.model.MeasurementUnit
import com.doduohor.domain.model.IncidentSeverity
import com.doduohor.domain.model.IncidentType
import com.doduohor.domain.shared.ParsingResult

object ApiInputParsers {
    fun parseFacilityType(value: String): ParsingResult<FacilityType>{
        val facilityType = FacilityType.fromString(value)
            ?: return ParsingResult.Error(
                field = "facilityType",
                value = value,
                expected = FacilityType.entries.map { it.name }
            )
        return ParsingResult.Success(facilityType)
    }

    fun parseEquipmentType(value: String): ParsingResult<EquipmentType>{
        val equipmentType = EquipmentType.fromString(value)
            ?: return ParsingResult.Error(
                field = "equipmentType",
                value = value,
                expected = EquipmentType.entries.map { it.name }
            )
        return ParsingResult.Success(equipmentType)
    }

    fun parseMeasurementType(value: String): ParsingResult<MeasurementType>{
        val measurementType = MeasurementType.fromString(value)
            ?: return ParsingResult.Error(
                field = "measurementType",
                value = value,
                expected = MeasurementType.entries.map { it.name }
            )
        return ParsingResult.Success(measurementType)
    }

    fun parseMeasurementUnit(value: String): ParsingResult<MeasurementUnit>{
        val measurementUnit = MeasurementUnit.fromString(value)
            ?: return ParsingResult.Error(
                field = "measurementUnit",
                value = value,
                expected = MeasurementUnit.entries.map { it.name }
            )
        return ParsingResult.Success(measurementUnit)
    }

    fun parseIncidentType(value: String): ParsingResult<IncidentType>{
        val incidentType = IncidentType.fromString(value)
            ?: return ParsingResult.Error(
                field = "incidentType",
                value = value,
                expected = IncidentType.entries.map { it.name }
            )
        return ParsingResult.Success(incidentType)
    }

    fun parseIncidentSeverity(value: String): ParsingResult<IncidentSeverity>{
        val incidentSeverity = IncidentSeverity.fromString(value)
            ?: return ParsingResult.Error(
                field = "incidentSeverity",
                value = value,
                expected = IncidentSeverity.entries.map { it.name }
            )
        return ParsingResult.Success(incidentSeverity)
    }
}
