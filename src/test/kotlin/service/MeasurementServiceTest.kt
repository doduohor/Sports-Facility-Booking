package com.doduohor.service

import com.doduohor.domain.model.Equipment
import com.doduohor.domain.model.EquipmentStatus
import com.doduohor.domain.model.EquipmentType
import com.doduohor.domain.model.Measurement
import com.doduohor.domain.model.MeasurementReading
import com.doduohor.domain.model.MeasurementType
import com.doduohor.domain.model.MeasurementUnit
import com.doduohor.domain.shared.EquipmentId
import com.doduohor.domain.shared.FacilityId
import com.doduohor.domain.shared.MeasurementId
import com.doduohor.repository.EquipmentRepository
import com.doduohor.repository.MeasurementRepository
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class MeasurementServiceTest {
    @Test
    fun `create returns measurement and passes one reading to repository`() {
        val equipment = equipment(EquipmentType.HEATING)
        val measurementRepository = RecordingMeasurementRepository()
        val service = MeasurementService(
            measurementRepository = measurementRepository,
            equipmentRepository = RecordingEquipmentRepository(equipment)
        )

        val result = service.create(
            equipmentId = equipment.id.value,
            type = MeasurementType.TEMPERATURE,
            unit = MeasurementUnit.CELSIUS,
            value = 24.5
        )

        val success = assertIs<CreateMeasurementResult.Success>(result)
        assertEquals(equipment.id, success.measurement.equipmentId)
        assertEquals(
            MeasurementReading(MeasurementType.TEMPERATURE, MeasurementUnit.CELSIUS, 24.5),
            measurementRepository.createdReading
        )
        assertEquals(1, measurementRepository.createCalls)
    }

    @Test
    fun `create rejects non-positive equipment id without repository writes`() {
        val measurementRepository = RecordingMeasurementRepository()
        val service = MeasurementService(measurementRepository, RecordingEquipmentRepository(null))

        val result = service.create(0, MeasurementType.TEMPERATURE, MeasurementUnit.CELSIUS, 20.0)

        assertIs<CreateMeasurementResult.InvalidEquipmentId>(result)
        assertEquals(0, measurementRepository.createCalls)
    }

    @Test
    fun `create rejects negative equipment id without repository writes`() {
        val measurementRepository = RecordingMeasurementRepository()
        val service = MeasurementService(measurementRepository, RecordingEquipmentRepository(null))

        val result = service.create(-1L, MeasurementType.TEMPERATURE, MeasurementUnit.CELSIUS, 20.0)

        assertIs<CreateMeasurementResult.InvalidEquipmentId>(result)
        assertEquals(0, measurementRepository.createCalls)
    }

    @Test
    fun `create rejects unknown equipment without repository writes`() {
        val measurementRepository = RecordingMeasurementRepository()
        val service = MeasurementService(measurementRepository, RecordingEquipmentRepository(null))

        val result = service.create(999L, MeasurementType.TEMPERATURE, MeasurementUnit.CELSIUS, 20.0)

        assertIs<CreateMeasurementResult.NotFindEquipmentId>(result)
        assertEquals(0, measurementRepository.createCalls)
    }

    @Test
    fun `create rejects measurement type unsupported by equipment without repository writes`() {
        val measurementRepository = RecordingMeasurementRepository()
        val service = service(equipment(EquipmentType.HEATING), measurementRepository)

        val result = service.create(200L, MeasurementType.HUMIDITY, MeasurementUnit.PERCENT, 45.0)

        assertIs<CreateMeasurementResult.InvalidMeasurementType>(result)
        assertEquals(0, measurementRepository.createCalls)
    }

    @Test
    fun `create rejects invalid type and unit mapping without repository writes`() {
        val measurementRepository = RecordingMeasurementRepository()
        val service = service(equipment(EquipmentType.HEATING), measurementRepository)

        val result = service.create(200L, MeasurementType.TEMPERATURE, MeasurementUnit.PERCENT, 20.0)

        assertIs<CreateMeasurementResult.InvalidMappingTypeAndUnit>(result)
        assertEquals(0, measurementRepository.createCalls)
    }

    @Test
    fun `create rejects values outside the configured range without repository writes`() {
        val measurementRepository = RecordingMeasurementRepository()
        val service = service(equipment(EquipmentType.HEATING), measurementRepository)

        val results = listOf(
            service.create(200L, MeasurementType.TEMPERATURE, MeasurementUnit.CELSIUS, -50.1),
            service.create(200L, MeasurementType.TEMPERATURE, MeasurementUnit.CELSIUS, 100.1)
        )

        results.forEach { assertIs<CreateMeasurementResult.InvalidValue>(it) }
        assertEquals(0, measurementRepository.createCalls)
    }

    @Test
    fun `create rejects non-finite values according to MeasurementReading contract`() {
        val measurementRepository = RecordingMeasurementRepository()
        val service = service(equipment(EquipmentType.HEATING), measurementRepository)

        listOf(Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY).forEach { value ->
            assertFailsWith<IllegalArgumentException> {
                service.create(200L, MeasurementType.TEMPERATURE, MeasurementUnit.CELSIUS, value)
            }
        }

        assertEquals(0, measurementRepository.createCalls)
    }

    @Test
    fun `unsupported policy result is exposed as invalid measurement type`() {
        val measurementRepository = RecordingMeasurementRepository()
        val service = service(equipment(EquipmentType.HEATING), measurementRepository)

        val result = service.create(200L, MeasurementType.SMOKE, MeasurementUnit.PERCENT, 12.0)

        // NotSupportedEquipmentType is currently unreachable: the service maps UnsupportedType to InvalidMeasurementType.
        // NotFindMeasurementType and MeasurementRangeNotConfigured are also unreachable because every enum
        // MeasurementType has a type/unit mapping and a configured value range.
        assertIs<CreateMeasurementResult.InvalidMeasurementType>(result)
        assertEquals(0, measurementRepository.createCalls)
    }

    @Test
    fun `find by measurement id returns found and unknown records and passes typed id`() {
        val storedMeasurement = measurement()
        val measurementRepository = RecordingMeasurementRepository(listOf(storedMeasurement))
        val service = MeasurementService(measurementRepository, RecordingEquipmentRepository(null))

        assertEquals(storedMeasurement, service.findByMeasurementId(storedMeasurement.id.value))
        assertEquals(storedMeasurement.id, measurementRepository.lastSearchedMeasurementId)
        assertNull(service.findByMeasurementId(999L))
        assertEquals(MeasurementId(999L), measurementRepository.lastSearchedMeasurementId)
        assertNull(service.findByMeasurementId(0L))
        assertNull(service.findByMeasurementId(-1L))
    }

    @Test
    fun `find by equipment id returns measurements and passes typed id`() {
        val equipment = equipment(EquipmentType.HEATING)
        val storedMeasurement = measurement(equipment.id)
        val measurementRepository = RecordingMeasurementRepository(listOf(storedMeasurement))
        val equipmentRepository = RecordingEquipmentRepository(equipment)
        val service = MeasurementService(measurementRepository, equipmentRepository)

        val result = service.findByEquipmentId(equipment.id.value)

        val success = assertIs<FindEquipmentIdResult.Success>(result)
        assertEquals(listOf(storedMeasurement), success.measurements)
        assertEquals(equipment.id, equipmentRepository.lastSearchedId)
        assertEquals(equipment.id, measurementRepository.lastSearchedEquipmentId)
        assertIs<FindEquipmentIdResult.NotFindEquipmentId>(service.findByEquipmentId(999L))
        assertEquals(EquipmentId(999L), equipmentRepository.lastSearchedId)
        assertIs<FindEquipmentIdResult.InvalidEquipmentId>(service.findByEquipmentId(0L))
        assertIs<FindEquipmentIdResult.InvalidEquipmentId>(service.findByEquipmentId(-1L))
    }

    @Test
    fun `find all returns all repository measurements`() {
        val measurements = listOf(measurement(), measurement(EquipmentId(201L), MeasurementId(401L)))
        val measurementRepository = RecordingMeasurementRepository(measurements)
        val service = MeasurementService(measurementRepository, RecordingEquipmentRepository(null))

        assertEquals(measurements, service.findAll())
    }
}

private fun service(
    equipment: Equipment,
    measurementRepository: RecordingMeasurementRepository
): MeasurementService = MeasurementService(
    measurementRepository = measurementRepository,
    equipmentRepository = RecordingEquipmentRepository(equipment)
)

private fun measurement(
    equipmentId: EquipmentId = EquipmentId(200L),
    id: MeasurementId = MeasurementId(400L)
): Measurement = Measurement.create(
    id = id,
    equipmentId = equipmentId,
    measurementReading = MeasurementReading(MeasurementType.TEMPERATURE, MeasurementUnit.CELSIUS, 20.0),
    createdAt = Instant.parse("2026-01-01T00:00:00Z")
)

private fun equipment(type: EquipmentType, id: Long = 200L): Equipment = Equipment(
    id = EquipmentId(id),
    facilityId = FacilityId(100L),
    name = "Test equipment",
    type = type,
    status = EquipmentStatus.ACTIVE
)

private class RecordingEquipmentRepository(
    private val equipment: Equipment?
) : EquipmentRepository {
    var lastSearchedId: EquipmentId? = null

    override fun findByEquipmentId(equipmentId: EquipmentId): Equipment? {
        lastSearchedId = equipmentId
        return equipment?.takeIf { it.id == equipmentId }
    }

    override fun create(facilityId: FacilityId, name: String, type: EquipmentType): CreateEquipmentResult =
        error("Not used by this test")

    override fun findByFacilityId(facilityId: FacilityId): List<Equipment> = error("Not used by this test")

    override fun findAll(): List<Equipment> = error("Not used by this test")
}

private class RecordingMeasurementRepository(
    initialMeasurements: List<Measurement> = emptyList()
) : MeasurementRepository {
    var createCalls = 0
    var createdReading: MeasurementReading? = null
    var lastSearchedMeasurementId: MeasurementId? = null
    var lastSearchedEquipmentId: EquipmentId? = null
    private val measurements = initialMeasurements.associateBy { it.id }

    override fun create(equipmentId: EquipmentId, measurementReading: MeasurementReading): Measurement {
        createCalls++
        createdReading = measurementReading
        return Measurement.create(
            id = MeasurementId(400L),
            equipmentId = equipmentId,
            measurementReading = measurementReading,
            createdAt = Instant.parse("2026-01-01T00:00:00Z")
        )
    }

    override fun findByMeasurementId(measurementId: MeasurementId): Measurement? {
        lastSearchedMeasurementId = measurementId
        return measurements[measurementId]
    }

    override fun findByEquipmentId(equipmentId: EquipmentId): List<Measurement> {
        lastSearchedEquipmentId = equipmentId
        return measurements.values.filter { it.equipmentId == equipmentId }
    }

    override fun findAll(): List<Measurement> = measurements.values.toList()
}
