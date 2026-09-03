package com.doduohor.api.mapper

import com.doduohor.domain.model.EquipmentType
import com.doduohor.domain.model.FacilityType
import com.doduohor.domain.model.IncidentSeverity
import com.doduohor.domain.model.IncidentType
import com.doduohor.domain.model.MeasurementType
import com.doduohor.domain.model.MeasurementUnit
import com.doduohor.domain.shared.ParsingResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class ApiInputParsersTest {
    @Test
    fun `parse facility type returns typed value`() {
        val result = ApiInputParsers.parseFacilityType("POOL")

        val success = assertIs<ParsingResult.Success<FacilityType>>(result)
        assertEquals(FacilityType.POOL, success.value)
    }

    @Test
    fun `parse facility type ignores surrounding spaces and letter case`() {
        val result = ApiInputParsers.parseFacilityType("  pOoL  ")

        val success = assertIs<ParsingResult.Success<FacilityType>>(result)
        assertEquals(FacilityType.POOL, success.value)
    }

    @Test
    fun `parse facility type returns error for unknown value`() {
        val result = ApiInputParsers.parseFacilityType("CINEMA")

        val error = assertIs<ParsingResult.Error>(result)
        assertEquals("facilityType", error.field)
        assertEquals("CINEMA", error.value)
        assertEquals(listOf("GYM", "POOL", "STADIUM"), error.expected)
    }

    @Test
    fun `parse equipment type returns typed value`() {
        val result = ApiInputParsers.parseEquipmentType("  water_supply ")

        val success = assertIs<ParsingResult.Success<EquipmentType>>(result)
        assertEquals(EquipmentType.WATER_SUPPLY, success.value)
    }

    @Test
    fun `parse equipment type returns error for unknown value`() {
        val result = ApiInputParsers.parseEquipmentType("ELEVATOR")

        val error = assertIs<ParsingResult.Error>(result)
        assertEquals("equipmentType", error.field)
        assertEquals("ELEVATOR", error.value)
        assertEquals(
            listOf("VENTILATION", "HEATING", "WATER_SUPPLY", "FIRE_ALARM"),
            error.expected
        )
    }

    @Test
    fun `parse measurement type returns typed value`() {
        val result = ApiInputParsers.parseMeasurementType("  cO2 ")

        val success = assertIs<ParsingResult.Success<MeasurementType>>(result)
        assertEquals(MeasurementType.CO2, success.value)
    }

    @Test
    fun `parse measurement type returns error for unknown value`() {
        val result = ApiInputParsers.parseMeasurementType("PRESSURE")

        val error = assertIs<ParsingResult.Error>(result)
        assertEquals("measurementType", error.field)
        assertEquals("PRESSURE", error.value)
        assertEquals(listOf("TEMPERATURE", "HUMIDITY", "CO2", "SMOKE"), error.expected)
    }

    @Test
    fun `parse measurement unit returns typed value`() {
        val result = ApiInputParsers.parseMeasurementUnit("  percent ")

        val success = assertIs<ParsingResult.Success<MeasurementUnit>>(result)
        assertEquals(MeasurementUnit.PERCENT, success.value)
    }

    @Test
    fun `parse measurement unit returns error for unknown value`() {
        val result = ApiInputParsers.parseMeasurementUnit("KELVIN")

        val error = assertIs<ParsingResult.Error>(result)
        assertEquals("measurementUnit", error.field)
        assertEquals("KELVIN", error.value)
        assertEquals(listOf("CELSIUS", "PERCENT", "PPM"), error.expected)
    }

    @Test
    fun `parse incident type returns typed value`() {
        val result = ApiInputParsers.parseIncidentType("  high_co2 ")

        val success = assertIs<ParsingResult.Success<IncidentType>>(result)
        assertEquals(IncidentType.HIGH_CO2, success.value)
    }

    @Test
    fun `parse incident type returns error for unknown value`() {
        val result = ApiInputParsers.parseIncidentType("POWER_FAILURE")

        val error = assertIs<ParsingResult.Error>(result)
        assertEquals("incidentType", error.field)
        assertEquals("POWER_FAILURE", error.value)
        assertEquals(
            listOf("SMOKE_DETECTED", "HIGH_CO2", "HIGH_TEMPERATURE", "LOW_TEMPERATURE", "HIGH_HUMIDITY", "LOW_HUMIDITY"),
            error.expected
        )
    }

    @Test
    fun `parse incident severity returns typed value`() {
        val result = ApiInputParsers.parseIncidentSeverity(" critical ")

        val success = assertIs<ParsingResult.Success<IncidentSeverity>>(result)
        assertEquals(IncidentSeverity.CRITICAL, success.value)
    }

    @Test
    fun `parse incident severity returns error for unknown value`() {
        val result = ApiInputParsers.parseIncidentSeverity("URGENT")

        val error = assertIs<ParsingResult.Error>(result)
        assertEquals("incidentSeverity", error.field)
        assertEquals("URGENT", error.value)
        assertEquals(listOf("MEDIUM", "HIGH", "CRITICAL"), error.expected)
    }
}
