package com.doduohor.domain.facility

import com.doduohor.domain.model.EquipmentType
import com.doduohor.domain.model.MeasurementType
import com.doduohor.domain.policy.EquipmentCapabilityPolicy
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EquipmentCapabilityPolicyTest {
    @Test
    fun `ventilation supports temperature humidity and co2 measurements`() {
        assertTrue(EquipmentCapabilityPolicy.supports(EquipmentType.VENTILATION, MeasurementType.TEMPERATURE))
        assertTrue(EquipmentCapabilityPolicy.supports(EquipmentType.VENTILATION, MeasurementType.HUMIDITY))
        assertTrue(EquipmentCapabilityPolicy.supports(EquipmentType.VENTILATION, MeasurementType.CO2))
        assertFalse(EquipmentCapabilityPolicy.supports(EquipmentType.VENTILATION, MeasurementType.SMOKE))
    }

    @Test
    fun `heating supports only temperature measurements`() {
        assertTrue(EquipmentCapabilityPolicy.supports(EquipmentType.HEATING, MeasurementType.TEMPERATURE))
        assertFalse(EquipmentCapabilityPolicy.supports(EquipmentType.HEATING, MeasurementType.HUMIDITY))
        assertFalse(EquipmentCapabilityPolicy.supports(EquipmentType.HEATING, MeasurementType.CO2))
        assertFalse(EquipmentCapabilityPolicy.supports(EquipmentType.HEATING, MeasurementType.SMOKE))
    }

    @Test
    fun `water supply supports only temperature measurements`() {
        assertTrue(EquipmentCapabilityPolicy.supports(EquipmentType.WATER_SUPPLY, MeasurementType.TEMPERATURE))
        assertFalse(EquipmentCapabilityPolicy.supports(EquipmentType.WATER_SUPPLY, MeasurementType.HUMIDITY))
        assertFalse(EquipmentCapabilityPolicy.supports(EquipmentType.WATER_SUPPLY, MeasurementType.CO2))
        assertFalse(EquipmentCapabilityPolicy.supports(EquipmentType.WATER_SUPPLY, MeasurementType.SMOKE))
    }

    @Test
    fun `fire alarm supports smoke and temperature measurements`() {
        assertTrue(EquipmentCapabilityPolicy.supports(EquipmentType.FIRE_ALARM, MeasurementType.SMOKE))
        assertTrue(EquipmentCapabilityPolicy.supports(EquipmentType.FIRE_ALARM, MeasurementType.TEMPERATURE))
        assertFalse(EquipmentCapabilityPolicy.supports(EquipmentType.FIRE_ALARM, MeasurementType.HUMIDITY))
        assertFalse(EquipmentCapabilityPolicy.supports(EquipmentType.FIRE_ALARM, MeasurementType.CO2))
    }
}
