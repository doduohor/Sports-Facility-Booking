package com.doduohor

import com.doduohor.di.configureTestKoin
import com.doduohor.infrastructure.messaging.MessagePublisher
import io.ktor.client.request.basicAuth
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.config.MapApplicationConfig
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ServerTest {
    private companion object {
        const val TEST_USERNAME = "admin"
        const val TEST_PASSWORD = "admin"
    }

    private class FakeMessagePublisher : MessagePublisher {
        val messages = mutableListOf<String>()

        override suspend fun publish(message: String) {
            messages.add(message)
        }
    }

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
    fun `create facility without auth returns unauthorized`() = testApplication {
        configureTestApplication()

        val response = client.post("/api/facilities") {
            contentType(ContentType.Application.Json)
            setBody(
                """
                {
                  "name": "Central Pool",
                  "type": "POOL"
                }
                """.trimIndent()
            )
        }

        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun `create facility returns created facility`() = testApplication {
        configureTestApplication()

        val response = createFacility("Central Pool", "POOL")

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

        val response = createFacility("Cinema Hall", "CINEMA")

        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertTrue(response.bodyAsText().contains("invalidType"))
    }

    @Test
    fun `create facility with client controlled status returns bad request`() = testApplication {
        configureTestApplication()

        val response = client.post("/api/facilities") {
            basicAuth(TEST_USERNAME, TEST_PASSWORD)
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

        val response = createFacility("   ", "POOL")

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

        createFacility("Central Gym", "GYM")

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

        val response = activateFacility(1)
        val body = response.bodyAsText()

        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(body.contains("active"))
    }

    @Test
    fun `activate missing facility returns not found`() = testApplication {
        configureTestApplication()

        val response = activateFacility(999999)

        assertEquals(HttpStatusCode.NotFound, response.status)
    }

    @Test
    fun `activate already active facility returns conflict`() = testApplication {
        configureTestApplication()
        createFacility("Central Pool", "POOL")
        activateFacility(1)

        val response = activateFacility(1)
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
        activateFacility(1)

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
        activateFacility(1)
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
        activateFacility(1)
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
        activateFacility(1)
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
        activateFacility(1)
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
        activateFacility(1)
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
        activateFacility(1)

        val response = createBooking(facilityId = 1, customerId = 899)
        val body = response.bodyAsText()

        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertTrue(body.contains("invalidCustomerId"))
        assertTrue(body.contains("Customer ID is not allowed to create bookings."))
    }

    @Test
    fun `create booking with invalid time interval returns bad request`() = testApplication {
        configureTestApplication()
        createFacility("Central Pool", "POOL")
        activateFacility(1)

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

    @Test
    fun `create alarming measurement creates incident`() = testApplication {
        configureTestApplication()
        createFacility("Central Pool", "POOL")
        val equipment = createEquipment(facilityId = 1, name = "Fire alarm", type = "FIRE_ALARM")
        val equipmentId = extractLongField(equipment.bodyAsText(), "id")

        val measurementResponse = createMeasurement(equipmentId, type = "SMOKE", unit = "PERCENT", value = 12.0)
        val incidentsResponse = client.get("/api/incidents")
        val incidentsBody = incidentsResponse.bodyAsText()

        assertEquals(HttpStatusCode.Created, measurementResponse.status)
        assertEquals(HttpStatusCode.OK, incidentsResponse.status)
        assertTrue(incidentsBody.contains("smoke_detected"))
        assertTrue(incidentsBody.contains("high"))
        assertTrue(incidentsBody.contains("\"equipmentId\":$equipmentId"))
    }

    @Test
    fun `create normal measurement does not create incident`() = testApplication {
        configureTestApplication()
        createFacility("Central Pool", "POOL")
        val equipment = createEquipment(facilityId = 1, name = "Fire alarm", type = "FIRE_ALARM")
        val equipmentId = extractLongField(equipment.bodyAsText(), "id")

        val measurementResponse = createMeasurement(equipmentId, type = "SMOKE", unit = "PERCENT", value = 1.0)
        val incidentsResponse = client.get("/api/incidents")
        val incidentsBody = incidentsResponse.bodyAsText()

        assertEquals(HttpStatusCode.Created, measurementResponse.status)
        assertEquals(HttpStatusCode.OK, incidentsResponse.status)
        assertTrue(incidentsBody.contains("items"))
        assertTrue(!incidentsBody.contains("smoke_detected"))
    }

    @Test
    fun `create incident returns created incident`() = testApplication {
        configureTestApplication()
        createFacility("Central Pool", "POOL")
        val equipment = createEquipment(facilityId = 1, name = "Fire alarm", type = "FIRE_ALARM")
        val equipmentId = extractLongField(equipment.bodyAsText(), "id")

        val response = createIncident(
            facilityId = 1,
            equipmentId = equipmentId,
            measurementId = 400,
            type = "SMOKE_DETECTED",
            severity = "CRITICAL",
            measurementType = "SMOKE",
            measurementUnit = "PERCENT",
            value = 80.0
        )
        val body = response.bodyAsText()

        assertEquals(HttpStatusCode.Created, response.status)
        assertTrue(body.contains("\"facilityId\":1"))
        assertTrue(body.contains("\"equipmentId\":$equipmentId"))
        assertTrue(body.contains("smoke_detected"))
        assertTrue(body.contains("critical"))
        assertTrue(body.contains("open"))
    }

    @Test
    fun `create incident with invalid facility id returns bad request`() = testApplication {
        configureTestApplication()

        val response = createIncident(
            facilityId = 0,
            equipmentId = 200,
            measurementId = 400,
            type = "SMOKE_DETECTED",
            severity = "CRITICAL",
            measurementType = "SMOKE",
            measurementUnit = "PERCENT",
            value = 80.0
        )
        val body = response.bodyAsText()

        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertTrue(body.contains("invalidFacilityId"))
    }

    @Test
    fun `create incident with missing facility returns not found`() = testApplication {
        configureTestApplication()

        val response = createIncident(
            facilityId = 999999,
            equipmentId = 200,
            measurementId = 400,
            type = "SMOKE_DETECTED",
            severity = "CRITICAL",
            measurementType = "SMOKE",
            measurementUnit = "PERCENT",
            value = 80.0
        )
        val body = response.bodyAsText()

        assertEquals(HttpStatusCode.NotFound, response.status)
        assertTrue(body.contains("notFindFacilityId"))
    }

    @Test
    fun `create incident with equipment from another facility returns conflict`() = testApplication {
        configureTestApplication()
        createFacility("Central Pool", "POOL")
        createFacility("Central Gym", "GYM")
        val equipment = createEquipment(facilityId = 2, name = "Gym ventilation", type = "VENTILATION")
        val equipmentId = extractLongField(equipment.bodyAsText(), "id")

        val response = createIncident(
            facilityId = 1,
            equipmentId = equipmentId,
            measurementId = 400,
            type = "HIGH_CO2",
            severity = "HIGH",
            measurementType = "CO2",
            measurementUnit = "PPM",
            value = 1200.0
        )
        val body = response.bodyAsText()

        assertEquals(HttpStatusCode.Conflict, response.status)
        assertTrue(body.contains("equipmentDoesNotBelongToFacility"))
    }

    @Test
    fun `get incident by id returns created incident`() = testApplication {
        configureTestApplication()
        createFacility("Central Pool", "POOL")
        val equipment = createEquipment(facilityId = 1, name = "Fire alarm", type = "FIRE_ALARM")
        val equipmentId = extractLongField(equipment.bodyAsText(), "id")
        val incident = createIncident(
            facilityId = 1,
            equipmentId = equipmentId,
            measurementId = 400,
            type = "SMOKE_DETECTED",
            severity = "CRITICAL",
            measurementType = "SMOKE",
            measurementUnit = "PERCENT",
            value = 80.0
        )
        val incidentId = extractLongField(incident.bodyAsText(), "id")

        val response = client.get("/api/incidents/$incidentId")
        val body = response.bodyAsText()

        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(body.contains("\"id\":$incidentId"))
        assertTrue(body.contains("smoke_detected"))
    }

    @Test
    fun `get incident with invalid id returns bad request`() = testApplication {
        configureTestApplication()

        val response = client.get("/api/incidents/test")
        val body = response.bodyAsText()

        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertTrue(body.contains("invalidIncidentId"))
    }

    @Test
    fun `get missing incident returns not found`() = testApplication {
        configureTestApplication()

        val response = client.get("/api/incidents/999999")
        val body = response.bodyAsText()

        assertEquals(HttpStatusCode.NotFound, response.status)
        assertTrue(body.contains("notFindIncidentId"))
    }

    @Test
    fun `get incidents returns response wrapper`() = testApplication {
        configureTestApplication()
        createFacility("Central Pool", "POOL")
        val equipment = createEquipment(facilityId = 1, name = "Fire alarm", type = "FIRE_ALARM")
        val equipmentId = extractLongField(equipment.bodyAsText(), "id")
        createIncident(
            facilityId = 1,
            equipmentId = equipmentId,
            measurementId = 400,
            type = "SMOKE_DETECTED",
            severity = "CRITICAL",
            measurementType = "SMOKE",
            measurementUnit = "PERCENT",
            value = 80.0
        )

        val response = client.get("/api/incidents")
        val body = response.bodyAsText()

        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(body.contains("items"))
        assertTrue(body.contains("smoke_detected"))
    }

    @Test
    fun `get incidents by facility id returns response wrapper`() = testApplication {
        configureTestApplication()
        createFacility("Central Pool", "POOL")
        createFacility("Central Gym", "GYM")
        val poolEquipment = createEquipment(facilityId = 1, name = "Pool fire alarm", type = "FIRE_ALARM")
        val gymEquipment = createEquipment(facilityId = 2, name = "Gym ventilation", type = "VENTILATION")
        val poolEquipmentId = extractLongField(poolEquipment.bodyAsText(), "id")
        val gymEquipmentId = extractLongField(gymEquipment.bodyAsText(), "id")
        createIncident(1, poolEquipmentId, 400, "SMOKE_DETECTED", "CRITICAL", "SMOKE", "PERCENT", 80.0)
        createIncident(2, gymEquipmentId, 401, "HIGH_CO2", "HIGH", "CO2", "PPM", 1200.0)

        val response = client.get("/api/facilities/1/incidents")
        val body = response.bodyAsText()

        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(body.contains("items"))
        assertTrue(body.contains("\"facilityId\":1"))
        assertTrue(!body.contains("\"facilityId\":2"))
    }

    @Test
    fun `get incidents by equipment id returns response wrapper`() = testApplication {
        configureTestApplication()
        createFacility("Central Pool", "POOL")
        val fireAlarm = createEquipment(facilityId = 1, name = "Pool fire alarm", type = "FIRE_ALARM")
        val waterSupply = createEquipment(facilityId = 1, name = "Water supply", type = "WATER_SUPPLY")
        val fireAlarmId = extractLongField(fireAlarm.bodyAsText(), "id")
        val waterSupplyId = extractLongField(waterSupply.bodyAsText(), "id")
        createIncident(1, fireAlarmId, 400, "SMOKE_DETECTED", "CRITICAL", "SMOKE", "PERCENT", 80.0)
        createIncident(1, waterSupplyId, 401, "WATER_LEAK", "HIGH", "HUMIDITY", "PERCENT", 90.0)

        val response = client.get("/api/equipments/$fireAlarmId/incidents")
        val body = response.bodyAsText()

        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(body.contains("items"))
        assertTrue(body.contains("\"equipmentId\":$fireAlarmId"))
        assertTrue(!body.contains("\"equipmentId\":$waterSupplyId"))
    }

    private suspend fun ApplicationTestBuilder.createFacility(name: String, type: String) =
        client.post("/api/facilities") {
            basicAuth(TEST_USERNAME, TEST_PASSWORD)
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

    private suspend fun ApplicationTestBuilder.activateFacility(facilityId: Long) =
        client.put("/api/facilities/$facilityId/activate") {
            basicAuth(TEST_USERNAME, TEST_PASSWORD)
        }

    private suspend fun ApplicationTestBuilder.createBooking(
        facilityId: Long,
        customerId: Int,
        bookingDate: String = "2026-07-28",
        startTime: String = "10:00",
        endTime: String = "12:00"
    ) =
        client.post("/api/bookings") {
            basicAuth(TEST_USERNAME, TEST_PASSWORD)
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
            basicAuth(TEST_USERNAME, TEST_PASSWORD)
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
            basicAuth(TEST_USERNAME, TEST_PASSWORD)
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

    private suspend fun ApplicationTestBuilder.createIncident(
        facilityId: Long,
        equipmentId: Long,
        measurementId: Long,
        type: String,
        severity: String,
        measurementType: String,
        measurementUnit: String,
        value: Double
    ) =
        client.post("/api/incidents") {
            basicAuth(TEST_USERNAME, TEST_PASSWORD)
            contentType(ContentType.Application.Json)
            setBody(
                """
                {
                  "facilityId": $facilityId,
                  "equipmentId": $equipmentId,
                  "measurementId": $measurementId,
                  "type": "$type",
                  "severity": "$severity",
                  "measurementType": "$measurementType",
                  "measurementUnit": "$measurementUnit",
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
        environment {
            config = MapApplicationConfig(
                "security.basic.username" to TEST_USERNAME,
                "security.basic.password" to TEST_PASSWORD,
                "database.enabled" to "false",
                "rabbitmq.host" to "localhost",
                "rabbitmq.port" to "5672",
                "rabbitmq.username" to "guest",
                "rabbitmq.password" to "guest",
                "rabbitmq.exchange" to "sports.events",
                "rabbitmq.queue" to "sports.measurements",
                "rabbitmq.routingKey" to "measurement.created",
                "rabbitmq.deadLetterExchange" to "sports.events.dlq",
                "rabbitmq.deadLetterQueue" to "sports.measurements.dlq",
                "rabbitmq.deadLetterRoutingKey" to "measurement.created.dlq"
            )
        }

        application {
            configureTestKoin(FakeMessagePublisher())
            configureSerialization()
            configureStatusPages()
            configureSecurity()
            configureSSE()
            configureRouting()
        }
    }
}
