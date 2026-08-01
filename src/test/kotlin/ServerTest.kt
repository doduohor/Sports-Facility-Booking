package com.doduohor

import com.doduohor.repository.InMemoryBookingRepository
import com.doduohor.repository.InMemoryEquipmentRepository
import com.doduohor.repository.InMemoryFacilityRepository
import com.doduohor.repository.InMemoryMeasurementRepository
import com.doduohor.service.BookingService
import com.doduohor.service.EquipmentService
import com.doduohor.service.FacilityService
import com.doduohor.service.MeasurementService
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ServerTest {

    @Test
    fun `root returns ok`() = testApplication {
        configureTestApplication()

        val response = client.get("/")

        assertEquals(HttpStatusCode.OK, response.status)
    }

    @Test
    fun `health returns ok`() = testApplication {
        configureTestApplication()

        val response = client.get("/health")
        val body = response.bodyAsText()

        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(body.contains("UP"))
    }

    @Test
    fun `invalid facility id returns bad request`() = testApplication {
        configureTestApplication()

        val response = client.get("/api/facilities/test/readings?limit=10")
        val body = response.bodyAsText()

        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertTrue(body.contains("Invalid"))
    }

    @Test
    fun `invalid limit returns bad request`() = testApplication {
        configureTestApplication()

        val response = client.get("/api/facilities/10000/readings?limit=test")
        val body = response.bodyAsText()

        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertTrue(body.contains("Invalid"))
    }

    @Test
    fun `create facility returns created facility`() = testApplication {
        configureTestApplication()

        val response = client.post("/api/facilities") {
            contentType(ContentType.Application.Json)
            setBody("""
                {
                  "name": "Central Pool",
                  "type": "POOL"
                }
            """.trimIndent())
        }

        assertEquals(HttpStatusCode.Created, response.status)
        val body = response.bodyAsText()
        assertTrue(body.contains("Central Pool"))
        assertTrue(body.contains("pool"))
        assertTrue(body.contains("inactive"))

        val getResponse = client.get("/api/facilities/1")
        assertEquals(HttpStatusCode.OK, getResponse.status)
        assertTrue(getResponse.bodyAsText().contains("Central Pool"))
    }

    @Test
    fun `create facility with unknown type returns bad request`() = testApplication {
        configureTestApplication()

        val response = client.post("/api/facilities") {
            contentType(ContentType.Application.Json)
            setBody(
                """
                {
                  "name": "Cinema Hall",
                  "type": "CINEMA"
                }
                """.trimIndent()
            )
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertTrue(response.bodyAsText().contains("invalidType"))
    }

    @Test
    fun `create facility with client controlled status returns bad request`() = testApplication {
        configureTestApplication()

        val response = client.post("/api/facilities") {
            contentType(ContentType.Application.Json)
            setBody(
                """
                {
                  "name": "Central Pool",
                  "type": "POOL",
                  "status": "ACTIVE"
                }
                """.trimIndent()
            )
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `blank facility name returns bad request`() = testApplication {
        configureTestApplication()

        val response = client.post("/api/facilities") {
            contentType(ContentType.Application.Json)
            setBody(
                """
                {
                  "name": "   ",
                  "type": "POOL"
                }
                """.trimIndent()
            )
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertTrue(response.bodyAsText().contains("invalidName"))
    }

    @Test
    fun `get missing facility returns not found`() = testApplication {
        configureTestApplication()

        val response = client.get("/api/facilities/999999")

        assertEquals(HttpStatusCode.NotFound, response.status)
    }

    @Test
    fun `get facilities returns list`() = testApplication {
        configureTestApplication()

        client.post("/api/facilities") {
            contentType(ContentType.Application.Json)
            setBody(
                """
                {
                  "name": "Central Gym",
                  "type": "GYM"
                }
                """.trimIndent()
            )
        }

        val response = client.get("/api/facilities")
        val body = response.bodyAsText()

        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(body.contains("Central Gym"))
        assertTrue(body.contains("gym"))
    }

    @Test
    fun `activate facility returns active facility`() = testApplication {
        configureTestApplication()
        createFacility("Central Pool", "POOL")

        val response = client.put("/api/facilities/1/activate")
        val body = response.bodyAsText()

        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(body.contains("active"))
    }

    @Test
    fun `activate missing facility returns not found`() = testApplication {
        configureTestApplication()

        val response = client.put("/api/facilities/999999/activate")

        assertEquals(HttpStatusCode.NotFound, response.status)
    }

    @Test
    fun `activate already active facility returns conflict`() = testApplication {
        configureTestApplication()
        createFacility("Central Pool", "POOL")
        client.put("/api/facilities/1/activate")

        val response = client.put("/api/facilities/1/activate")
        val body = response.bodyAsText()

        assertEquals(HttpStatusCode.Conflict, response.status)
        assertTrue(body.contains("alreadyActive"))
    }

    @Test
    fun `create booking for inactive facility returns conflict`() = testApplication {
        configureTestApplication()
        createFacility("Central Pool", "POOL")

        val response = createBooking(facilityId = 1, customerId = 900)
        val body = response.bodyAsText()

        assertEquals(HttpStatusCode.Conflict, response.status)
        assertTrue(body.contains("invalidStatusFacilityId"))
    }

    @Test
    fun `create booking for active facility returns created booking`() = testApplication {
        configureTestApplication()
        createFacility("Central Pool", "POOL")
        client.put("/api/facilities/1/activate")

        val response = createBooking(facilityId = 1, customerId = 900)
        val body = response.bodyAsText()

        assertEquals(HttpStatusCode.Created, response.status)
        assertTrue(body.contains("reserved"))
        assertTrue(body.contains("\"facilityId\":1"))
        assertTrue(body.contains("\"customerId\":900"))
        assertTrue(body.contains("2026-07-28T07:00:00Z"))
        assertTrue(body.contains("2026-07-28T09:00:00Z"))
    }

    @Test
    fun `create overlapping booking returns conflict`() = testApplication {
        configureTestApplication()
        createFacility("Central Pool", "POOL")
        client.put("/api/facilities/1/activate")
        createBooking(facilityId = 1, customerId = 900, startTime = "10:00", endTime = "12:00")

        val response = createBooking(facilityId = 1, customerId = 901, startTime = "11:00", endTime = "13:00")
        val body = response.bodyAsText()

        assertEquals(HttpStatusCode.Conflict, response.status)
        assertTrue(body.contains("unavailableRangeTimeLimit"))
    }

    @Test
    fun `create adjacent booking returns created booking`() = testApplication {
        configureTestApplication()
        createFacility("Central Pool", "POOL")
        client.put("/api/facilities/1/activate")
        createBooking(facilityId = 1, customerId = 900, startTime = "10:00", endTime = "12:00")

        val response = createBooking(facilityId = 1, customerId = 901, startTime = "12:00", endTime = "13:00")
        val body = response.bodyAsText()

        assertEquals(HttpStatusCode.Created, response.status)
        assertTrue(body.contains("\"customerId\":901"))
        assertTrue(body.contains("2026-07-28T09:00:00Z"))
        assertTrue(body.contains("2026-07-28T10:00:00Z"))
    }

    @Test
    fun `get booking by id returns created booking`() = testApplication {
        configureTestApplication()
        createFacility("Central Pool", "POOL")
        client.put("/api/facilities/1/activate")
        val createResponse = createBooking(facilityId = 1, customerId = 900)
        val createdBookingId = extractLongField(createResponse.bodyAsText(), "id")

        val response = client.get("/api/bookings/$createdBookingId")
        val body = response.bodyAsText()

        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(body.contains("\"id\":$createdBookingId"))
        assertTrue(body.contains("reserved"))
    }

    @Test
    fun `get missing booking returns not found`() = testApplication {
        configureTestApplication()

        val response = client.get("/api/bookings/999999")

        assertEquals(HttpStatusCode.NotFound, response.status)
    }

    @Test
    fun `invalid booking id returns bad request`() = testApplication {
        configureTestApplication()

        val response = client.get("/api/bookings/test")

        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `get bookings returns response wrapper`() = testApplication {
        configureTestApplication()
        createFacility("Central Pool", "POOL")
        client.put("/api/facilities/1/activate")
        createBooking(facilityId = 1, customerId = 900)

        val response = client.get("/api/bookings")
        val body = response.bodyAsText()

        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(body.contains("items"))
        assertTrue(body.contains("reserved"))
    }

    @Test
    fun `get bookings by facility id returns response wrapper`() = testApplication {
        configureTestApplication()
        createFacility("Central Pool", "POOL")
        client.put("/api/facilities/1/activate")
        createBooking(facilityId = 1, customerId = 900)

        val response = client.get("/api/facilities/1/bookings")
        val body = response.bodyAsText()

        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(body.contains("items"))
        assertTrue(body.contains("\"facilityId\":1"))
    }

    @Test
    fun `get bookings by invalid facility id returns bad request`() = testApplication {
        configureTestApplication()

        val response = client.get("/api/facilities/0/bookings")
        val body = response.bodyAsText()

        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertTrue(body.contains("invalidFacilityId"))
    }

    @Test
    fun `create booking with missing facility returns not found`() = testApplication {
        configureTestApplication()

        val response = createBooking(facilityId = 1, customerId = 900)
        val body = response.bodyAsText()

        assertEquals(HttpStatusCode.NotFound, response.status)
        assertTrue(body.contains("notFindFacilityId"))
    }

    @Test
    fun `create booking with invalid facility id returns bad request`() = testApplication {
        configureTestApplication()

        val response = createBooking(facilityId = 0, customerId = 900)
        val body = response.bodyAsText()

        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertTrue(body.contains("invalidFacilityId"))
    }

    @Test
    fun `create booking with invalid customer id returns bad request`() = testApplication {
        configureTestApplication()
        createFacility("Central Pool", "POOL")
        client.put("/api/facilities/1/activate")

        val response = createBooking(facilityId = 1, customerId = 899)
        val body = response.bodyAsText()

        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertTrue(body.contains("invalidCustomerId"))
    }

    @Test
    fun `create booking with invalid time interval returns bad request`() = testApplication {
        configureTestApplication()
        createFacility("Central Pool", "POOL")
        client.put("/api/facilities/1/activate")

        val response = createBooking(facilityId = 1, customerId = 900, endTime = "10:00")
        val body = response.bodyAsText()

        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertTrue(body.contains("invalidTimeInterval"))
    }

    @Test
    fun `create equipment for existing facility returns created equipment`() = testApplication {
        configureTestApplication()
        createFacility("Central Pool", "POOL")

        val response = createEquipment(facilityId = 1, name = "Main ventilation", type = "VENTILATION")
        val body = response.bodyAsText()

        assertEquals(HttpStatusCode.Created, response.status)
        assertTrue(body.contains("Main ventilation"))
        assertTrue(body.contains("\"facilityId\":1"))
        assertTrue(body.contains("ventilation"))
        assertTrue(body.contains("disabled"))
    }

    @Test
    fun `create equipment with unknown type returns bad request`() = testApplication {
        configureTestApplication()
        createFacility("Central Pool", "POOL")

        val response = createEquipment(facilityId = 1, name = "Unknown system", type = "UNKNOWN")
        val body = response.bodyAsText()

        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertTrue(body.contains("invalidType"))
    }

    @Test
    fun `create equipment with blank name returns bad request`() = testApplication {
        configureTestApplication()
        createFacility("Central Pool", "POOL")

        val response = createEquipment(facilityId = 1, name = "   ", type = "VENTILATION")
        val body = response.bodyAsText()

        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertTrue(body.contains("invalidName"))
        assertTrue(body.contains("\"code\":400"))
    }

    @Test
    fun `create equipment with invalid facility id returns bad request`() = testApplication {
        configureTestApplication()

        val response = createEquipment(facilityId = 0, name = "Main ventilation", type = "VENTILATION")
        val body = response.bodyAsText()

        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertTrue(body.contains("invalidFacilityId"))
    }

    @Test
    fun `create equipment with missing facility returns not found`() = testApplication {
        configureTestApplication()

        val response = createEquipment(facilityId = 99, name = "Main ventilation", type = "VENTILATION")
        val body = response.bodyAsText()

        assertEquals(HttpStatusCode.NotFound, response.status)
        assertTrue(body.contains("notFindFacilityId"))
    }

    @Test
    fun `get equipment by id returns created equipment`() = testApplication {
        configureTestApplication()
        createFacility("Central Pool", "POOL")
        val createResponse = createEquipment(facilityId = 1, name = "Main ventilation", type = "VENTILATION")
        val createdEquipmentId = extractLongField(createResponse.bodyAsText(), "id")

        val response = client.get("/api/equipments/$createdEquipmentId")
        val body = response.bodyAsText()

        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(body.contains("\"id\":$createdEquipmentId"))
        assertTrue(body.contains("Main ventilation"))
    }

    @Test
    fun `get equipment with invalid id returns bad request`() = testApplication {
        configureTestApplication()

        val response = client.get("/api/equipments/test")
        val body = response.bodyAsText()

        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertTrue(body.contains("invalidEquipmentId"))
    }

    @Test
    fun `get missing equipment returns not found`() = testApplication {
        configureTestApplication()

        val response = client.get("/api/equipments/999999")
        val body = response.bodyAsText()

        assertEquals(HttpStatusCode.NotFound, response.status)
        assertTrue(body.contains("notFound"))
    }

    @Test
    fun `get equipments returns response wrapper`() = testApplication {
        configureTestApplication()
        createFacility("Central Pool", "POOL")
        createEquipment(facilityId = 1, name = "Main ventilation", type = "VENTILATION")

        val response = client.get("/api/equipments")
        val body = response.bodyAsText()

        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(body.contains("items"))
        assertTrue(body.contains("Main ventilation"))
    }

    @Test
    fun `get equipments by facility id returns response wrapper`() = testApplication {
        configureTestApplication()
        createFacility("Central Pool", "POOL")
        createFacility("Central Gym", "GYM")
        createEquipment(facilityId = 1, name = "Pool ventilation", type = "VENTILATION")
        createEquipment(facilityId = 2, name = "Gym heating", type = "HEATING")

        val response = client.get("/api/facilities/1/equipments")
        val body = response.bodyAsText()

        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(body.contains("items"))
        assertTrue(body.contains("Pool ventilation"))
        assertTrue(!body.contains("Gym heating"))
    }

    @Test
    fun `get equipments by invalid facility id returns bad request`() = testApplication {
        configureTestApplication()

        val response = client.get("/api/facilities/0/equipments")
        val body = response.bodyAsText()

        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertTrue(body.contains("invalidFacilityId"))
    }

    @Test
    fun `create measurement for existing equipment returns created measurement`() = testApplication {
        configureTestApplication()
        createFacility("Central Pool", "POOL")
        val equipment = createEquipment(facilityId = 1, name = "Main ventilation", type = "VENTILATION")
        val equipmentId = extractLongField(equipment.bodyAsText(), "id")

        val response = createMeasurement(equipmentId, type = "TEMPERATURE", unit = "CELSIUS", value = 24.5)
        val body = response.bodyAsText()

        assertEquals(HttpStatusCode.Created, response.status)
        assertTrue(body.contains("\"equipmentId\":$equipmentId"))
        assertTrue(body.contains("temperature"))
        assertTrue(body.contains("celsius"))
        assertTrue(body.contains("24.5"))
    }

    @Test
    fun `create measurement with invalid equipment id returns bad request`() = testApplication {
        configureTestApplication()

        val response = createMeasurement(equipmentId = 0, type = "TEMPERATURE", unit = "CELSIUS", value = 24.5)
        val body = response.bodyAsText()

        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertTrue(body.contains("invalidEquipmentId"))
    }

    @Test
    fun `create measurement with missing equipment returns not found`() = testApplication {
        configureTestApplication()

        val response = createMeasurement(equipmentId = 999999, type = "TEMPERATURE", unit = "CELSIUS", value = 24.5)
        val body = response.bodyAsText()

        assertEquals(HttpStatusCode.NotFound, response.status)
        assertTrue(body.contains("notFindEquipmentId"))
    }

    @Test
    fun `create measurement with unknown type returns bad request`() = testApplication {
        configureTestApplication()
        createFacility("Central Pool", "POOL")
        val equipment = createEquipment(facilityId = 1, name = "Main ventilation", type = "VENTILATION")
        val equipmentId = extractLongField(equipment.bodyAsText(), "id")

        val response = createMeasurement(equipmentId, type = "UNKNOWN", unit = "CELSIUS", value = 24.5)
        val body = response.bodyAsText()

        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertTrue(body.contains("invalidType"))
    }

    @Test
    fun `create measurement with unknown unit returns bad request`() = testApplication {
        configureTestApplication()
        createFacility("Central Pool", "POOL")
        val equipment = createEquipment(facilityId = 1, name = "Main ventilation", type = "VENTILATION")
        val equipmentId = extractLongField(equipment.bodyAsText(), "id")

        val response = createMeasurement(equipmentId, type = "TEMPERATURE", unit = "UNKNOWN", value = 24.5)
        val body = response.bodyAsText()

        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertTrue(body.contains("invalidUnit"))
    }

    @Test
    fun `create measurement with mismatched type and unit returns bad request`() = testApplication {
        configureTestApplication()
        createFacility("Central Pool", "POOL")
        val equipment = createEquipment(facilityId = 1, name = "Main ventilation", type = "VENTILATION")
        val equipmentId = extractLongField(equipment.bodyAsText(), "id")

        val response = createMeasurement(equipmentId, type = "TEMPERATURE", unit = "PERCENT", value = 24.5)
        val body = response.bodyAsText()

        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertTrue(body.contains("invalidMappingTypeAndUnit"))
    }

    @Test
    fun `create measurement with invalid value returns bad request`() = testApplication {
        configureTestApplication()
        createFacility("Central Pool", "POOL")
        val equipment = createEquipment(facilityId = 1, name = "Main ventilation", type = "VENTILATION")
        val equipmentId = extractLongField(equipment.bodyAsText(), "id")

        val response = createMeasurement(equipmentId, type = "TEMPERATURE", unit = "CELSIUS", value = 200.0)
        val body = response.bodyAsText()

        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertTrue(body.contains("invalidValue"))
    }

    @Test
    fun `create measurement with unsupported equipment measurement type returns conflict`() = testApplication {
        configureTestApplication()
        createFacility("Central Pool", "POOL")
        val equipment = createEquipment(facilityId = 1, name = "Pool water supply", type = "WATER_SUPPLY")
        val equipmentId = extractLongField(equipment.bodyAsText(), "id")

        val response = createMeasurement(equipmentId, type = "CO2", unit = "PPM", value = 450.0)
        val body = response.bodyAsText()

        assertEquals(HttpStatusCode.Conflict, response.status)
        assertTrue(body.contains("invalidMeasurementType"))
    }

    @Test
    fun `get measurement by id returns created measurement`() = testApplication {
        configureTestApplication()
        createFacility("Central Pool", "POOL")
        val equipment = createEquipment(facilityId = 1, name = "Main ventilation", type = "VENTILATION")
        val equipmentId = extractLongField(equipment.bodyAsText(), "id")
        val measurement = createMeasurement(equipmentId, type = "TEMPERATURE", unit = "CELSIUS", value = 24.5)
        val measurementId = extractLongField(measurement.bodyAsText(), "id")

        val response = client.get("/api/measurements/$measurementId")
        val body = response.bodyAsText()

        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(body.contains("\"id\":$measurementId"))
        assertTrue(body.contains("\"equipmentId\":$equipmentId"))
    }

    @Test
    fun `get measurement with invalid id returns bad request`() = testApplication {
        configureTestApplication()

        val response = client.get("/api/measurements/test")
        val body = response.bodyAsText()

        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertTrue(body.contains("invalidMeasurementId"))
    }

    @Test
    fun `get missing measurement returns not found`() = testApplication {
        configureTestApplication()

        val response = client.get("/api/measurements/999999")
        val body = response.bodyAsText()

        assertEquals(HttpStatusCode.NotFound, response.status)
        assertTrue(body.contains("notFindMeasurementId"))
    }

    @Test
    fun `get measurements returns response wrapper`() = testApplication {
        configureTestApplication()
        createFacility("Central Pool", "POOL")
        val equipment = createEquipment(facilityId = 1, name = "Main ventilation", type = "VENTILATION")
        val equipmentId = extractLongField(equipment.bodyAsText(), "id")
        createMeasurement(equipmentId, type = "HUMIDITY", unit = "PERCENT", value = 45.0)

        val response = client.get("/api/measurements")
        val body = response.bodyAsText()

        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(body.contains("items"))
        assertTrue(body.contains("humidity"))
    }

    @Test
    fun `get measurements by equipment id returns response wrapper`() = testApplication {
        configureTestApplication()
        createFacility("Central Pool", "POOL")
        val ventilation = createEquipment(facilityId = 1, name = "Main ventilation", type = "VENTILATION")
        val waterSupply = createEquipment(facilityId = 1, name = "Water supply", type = "WATER_SUPPLY")
        val ventilationId = extractLongField(ventilation.bodyAsText(), "id")
        val waterSupplyId = extractLongField(waterSupply.bodyAsText(), "id")
        createMeasurement(ventilationId, type = "CO2", unit = "PPM", value = 450.0)
        createMeasurement(waterSupplyId, type = "TEMPERATURE", unit = "CELSIUS", value = 18.0)

        val response = client.get("/api/equipments/$ventilationId/measurements")
        val body = response.bodyAsText()

        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(body.contains("items"))
        assertTrue(body.contains("\"equipmentId\":$ventilationId"))
        assertTrue(!body.contains("\"equipmentId\":$waterSupplyId"))
    }

    @Test
    fun `get measurements by invalid equipment id returns bad request`() = testApplication {
        configureTestApplication()

        val response = client.get("/api/equipments/0/measurements")
        val body = response.bodyAsText()

        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertTrue(body.contains("invalidEquipmentId"))
    }

    @Test
    fun `get measurements by missing equipment id returns not found`() = testApplication {
        configureTestApplication()

        val response = client.get("/api/equipments/999999/measurements")
        val body = response.bodyAsText()

        assertEquals(HttpStatusCode.NotFound, response.status)
        assertTrue(body.contains("notFindEquipmentId"))
    }

    private suspend fun ApplicationTestBuilder.createFacility(name: String, type: String) =
        client.post("/api/facilities") {
            contentType(ContentType.Application.Json)
            setBody(
                """
                {
                  "name": "$name",
                  "type": "$type"
                }
                """.trimIndent()
            )
        }

    private suspend fun ApplicationTestBuilder.createBooking(
        facilityId: Long,
        customerId: Int,
        bookingDate: String = "2026-07-28",
        startTime: String = "10:00",
        endTime: String = "12:00"
    ) =
        client.post("/api/bookings") {
            contentType(ContentType.Application.Json)
            setBody(
                """
                {
                  "facilityId": $facilityId,
                  "customerId": $customerId,
                  "bookingDate": "$bookingDate",
                  "startTime": "$startTime",
                  "endTime": "$endTime"
                }
                """.trimIndent()
            )
        }

    private suspend fun ApplicationTestBuilder.createEquipment(facilityId: Long, name: String, type: String) =
        client.post("/api/equipments") {
            contentType(ContentType.Application.Json)
            setBody(
                """
                {
                  "facilityId": $facilityId,
                  "name": "$name",
                  "type": "$type"
                }
                """.trimIndent()
            )
        }

    private suspend fun ApplicationTestBuilder.createMeasurement(
        equipmentId: Long,
        type: String,
        unit: String,
        value: Double
    ) =
        client.post("/api/measurements") {
            contentType(ContentType.Application.Json)
            setBody(
                """
                {
                  "equipmentId": $equipmentId,
                  "type": "$type",
                  "unit": "$unit",
                  "value": $value
                }
                """.trimIndent()
            )
        }

    private fun extractLongField(body: String, fieldName: String): Long {
        val pattern = Regex(""""$fieldName"\s*:\s*(\d+)""")
        return pattern.find(body)?.groupValues?.get(1)?.toLong()
            ?: error("Response does not contain numeric field '$fieldName': $body")
    }

    private fun ApplicationTestBuilder.configureTestApplication() {
        application {
            configureSerialization()
            configureStatusPages()
            val facilityRepository = InMemoryFacilityRepository()
            val equipmentRepository = InMemoryEquipmentRepository()
            configureRouting(
                FacilityService(facilityRepository),
                BookingService(InMemoryBookingRepository(), facilityRepository),
                EquipmentService(equipmentRepository, facilityRepository),
                MeasurementService(InMemoryMeasurementRepository(), equipmentRepository)
            )
        }
    }
}
