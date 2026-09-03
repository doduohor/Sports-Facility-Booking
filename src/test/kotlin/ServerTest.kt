package com.doduohor

import com.doduohor.di.configureTestKoin
import com.doduohor.infrastructure.messaging.MessagePublisher
import io.ktor.client.request.basicAuth
import io.ktor.client.request.get
import io.ktor.client.request.header
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
import kotlinx.serialization.json.Json
import kotlinx.serialization.decodeFromString
import com.doduohor.api.dto.ErrorResponse
import com.doduohor.api.mapper.ApiError
import com.doduohor.api.mapper.respondApiError
import com.doduohor.domain.model.Measurement
import com.doduohor.domain.model.MeasurementReading
import com.doduohor.domain.model.MeasurementType
import com.doduohor.domain.model.MeasurementUnit
import com.doduohor.domain.shared.EquipmentId
import com.doduohor.domain.shared.MeasurementId
import com.doduohor.service.CreateMeasurementResult
import com.doduohor.service.IncidentServiceResult
import com.doduohor.service.MonitoringServiceResult
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import java.time.Instant
import org.koin.ktor.ext.inject

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
    fun `api error responder preserves status and exact error response`() = testApplication {
        application {
            configureSerialization()
            routing {
                get("/test-api-error") {
                    call.respondApiError(
                        ApiError(
                            HttpStatusCode.Conflict,
                            ErrorResponse(409, "alreadyActive", "The object is already active")
                        )
                    )
                }
            }
        }

        val response = client.get("/test-api-error")

        assertExactJsonError(
            response,
            HttpStatusCode.Conflict,
            "shared API error responder",
            ErrorResponse(409, "alreadyActive", "The object is already active")
        )
    }

    @Test
    fun `measurement monitoring internal errors keep exact API contracts`() = testApplication {
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
            configureSecurity()
            val measurementService by inject<com.doduohor.service.MeasurementService>()
            routing {
                measurementRoutes(
                    measurementService,
                    processMeasurement = { _, _, _, value ->
                        when (value) {
                            1.0 -> MonitoringServiceResult.MeasurementCreateError(
                                CreateMeasurementResult.NotSupportedEquipmentType
                            )
                            2.0 -> MonitoringServiceResult.MeasurementCreateError(
                                CreateMeasurementResult.MeasurementRangeNotConfigured
                            )
                            3.0 -> MonitoringServiceResult.EquipmentContextLost(testMeasurement())
                            4.0 -> MonitoringServiceResult.IncidentCreateError(
                                testMeasurement(),
                                IncidentServiceResult.InvalidValue
                            )
                            else -> MonitoringServiceResult.OutboxPersistenceError("ignored")
                        }
                    }
                )
            }
        }

        val cases = listOf(
            1.0 to ErrorResponse(500, "notSupportedEquipmentType", "Measurement rules are not configured for this equipment type"),
            2.0 to ErrorResponse(500, "measurementRangeNotConfigured", "Measurement value range is not configured"),
            3.0 to ErrorResponse(500, "equipmentContextLost", "Equipment context was not found after measurement creation"),
            4.0 to ErrorResponse(500, "incidentCreateError", "Measurement was created, but incident creation failed"),
            5.0 to ErrorResponse(500, "outboxPersistenceError", "Measurement event could not be stored")
        )
        cases.forEach { (value, expected) ->
            assertExactJsonError(
                client.post("/api/measurements") {
                    basicAuth(TEST_USERNAME, TEST_PASSWORD)
                    contentType(ContentType.Application.Json)
                    setBody("""{"equipmentId":1,"type":"TEMPERATURE","unit":"CELSIUS","value":$value}""")
                },
                HttpStatusCode.InternalServerError,
                "monitoring $value",
                expected
            )
        }
    }

    @Test
    fun `facility booking and equipment service errors keep exact API contracts`() = testApplication {
        configureTestApplication()

        assertExactJsonError(
            createFacility("   ", "POOL"),
            HttpStatusCode.BadRequest,
            "facility 400",
            ErrorResponse(400, "invalidName", "Facility name must not be blank")
        )
        assertExactJsonError(
            activateFacility(999999),
            HttpStatusCode.NotFound,
            "facility 404",
            ErrorResponse(404, "Error", "Not Found")
        )

        assertExactJsonError(
            createBooking(facilityId = 0, customerId = 900),
            HttpStatusCode.BadRequest,
            "booking 400",
            ErrorResponse(400, "invalidFacilityId", "The Facility ID must be positive.")
        )
        assertExactJsonError(
            createBooking(facilityId = 999999, customerId = 900),
            HttpStatusCode.NotFound,
            "booking 404",
            ErrorResponse(404, "notFindFacilityId", "The specified facilityId was not found.")
        )

        assertExactJsonError(
            createEquipment(facilityId = 0, name = "Ventilation", type = "VENTILATION"),
            HttpStatusCode.BadRequest,
            "equipment 400",
            ErrorResponse(400, "invalidFacilityId", "You entered an incorrect facilityId")
        )
        assertExactJsonError(
            createEquipment(facilityId = 99, name = "Ventilation", type = "VENTILATION"),
            HttpStatusCode.NotFound,
            "equipment 404",
            ErrorResponse(404, "notFindFacilityId", "The specified Facility ID does not exist")
        )

        val inactiveFacilityId = extractLongField(createFacility("Inactive Pool", "POOL").bodyAsText(), "id")
        val activeFacilityId = extractLongField(createFacility("Active Pool", "POOL").bodyAsText(), "id")
        activateFacility(activeFacilityId)
        assertExactJsonError(
            activateFacility(activeFacilityId),
            HttpStatusCode.Conflict,
            "facility 409",
            ErrorResponse(409, "alreadyActive", "The object is already active")
        )
        assertExactJsonError(
            createBooking(facilityId = inactiveFacilityId, customerId = 900),
            HttpStatusCode.Conflict,
            "booking 409",
            ErrorResponse(409, "invalidStatusFacilityId", "The status of this facility does not allow bookings to be created.")
        )
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
    fun `malformed JSON returns a stable JSON error`() = testApplication {
        configureTestApplication()

        val response = client.post("/api/facilities") {
            basicAuth(TEST_USERNAME, TEST_PASSWORD)
            contentType(ContentType.Application.Json)
            setBody("{\"name\":\"Central Pool\",")
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertEquals(ContentType.Application.Json, response.contentType()?.withoutParameters())
        assertEquals(ErrorResponse(400, "invalidRequest", "The request body is invalid"), Json.decodeFromString(response.bodyAsText()))
    }

    @Test
    fun `all JSON write endpoints reject malformed JSON with the same contract`() = testApplication {
        configureTestApplication()
        val requests = listOf(
            "/api/facilities" to "{",
            "/api/bookings" to "{",
            "/api/equipments" to "{",
            "/api/measurements" to "{",
            "/api/incidents" to "{",
            "/api/facilities" to "",
            "/api/bookings" to "",
            "/api/equipments" to "",
            "/api/measurements" to "",
            "/api/incidents" to ""
        )

        requests.forEach { (path, body) ->
            val response = client.post(path) {
                basicAuth(TEST_USERNAME, TEST_PASSWORD)
                contentType(ContentType.Application.Json)
                setBody(body)
            }

            assertJsonError(response, HttpStatusCode.BadRequest)
        }
    }

    @Test
    fun `missing and extra JSON fields are rejected`() = testApplication {
        configureTestApplication()
        val requests = listOf(
            "/api/facilities" to "{\"name\":\"Central Pool\"}",
            "/api/bookings" to "{}",
            "/api/equipments" to "{}",
            "/api/measurements" to "{}",
            "/api/incidents" to "{}"
        )

        requests.forEach { (path, body) ->
            val response = client.post(path) {
                basicAuth(TEST_USERNAME, TEST_PASSWORD)
                contentType(ContentType.Application.Json)
                setBody(body)
            }
            assertJsonError(response, HttpStatusCode.BadRequest)
        }

        val extraField = client.post("/api/facilities") {
            basicAuth(TEST_USERNAME, TEST_PASSWORD)
            contentType(ContentType.Application.Json)
            setBody("{\"name\":\"Central Pool\",\"type\":\"POOL\",\"status\":\"ACTIVE\"}")
        }
        assertJsonError(extraField, HttpStatusCode.BadRequest)
    }

    @Test
    fun `array null and wrong scalar JSON values are rejected for every write resource`() = testApplication {
        configureTestApplication()
        val requests = listOf(
            "/api/facilities" to "{\"name\":null,\"type\":\"POOL\"}",
            "/api/bookings" to "[]",
            "/api/equipments" to "{\"facilityId\":\"one\"}",
            "/api/measurements" to "{\"equipmentId\":\"one\"}",
            "/api/incidents" to "{\"facilityId\":\"one\"}"
        )

        requests.forEach { (path, body) ->
            val response = client.post(path) {
                basicAuth(TEST_USERNAME, TEST_PASSWORD)
                contentType(ContentType.Application.Json)
                setBody(body)
            }
            assertJsonError(response, HttpStatusCode.BadRequest)
        }
    }

    @Test
    fun `unknown enum values are rejected by every JSON resource`() = testApplication {
        configureTestApplication()
        val requests = listOf(
            "/api/facilities" to "{\"name\":\"Central Pool\",\"type\":\"\"}",
            "/api/equipments" to "{\"facilityId\":1,\"name\":\"Sensor\",\"type\":\"UNKNOWN\"}",
            "/api/measurements" to "{\"equipmentId\":1,\"type\":\"UNKNOWN\",\"unit\":\"CELSIUS\",\"value\":1}",
            "/api/incidents" to "{\"facilityId\":1,\"equipmentId\":1,\"measurementId\":1,\"type\":\"UNKNOWN\",\"severity\":\"HIGH\",\"measurementType\":\"CO2\",\"measurementUnit\":\"PPM\",\"value\":1}"
        )

        requests.forEach { (path, body) ->
            val response = client.post(path) {
                basicAuth(TEST_USERNAME, TEST_PASSWORD)
                contentType(ContentType.Application.Json)
                setBody(body)
            }
            assertJsonError(response, HttpStatusCode.BadRequest)
        }
    }

    @Test
    fun `unsupported content type and accept return JSON errors`() = testApplication {
        configureTestApplication()

        val contentTypeResponse = client.post("/api/facilities") {
            basicAuth(TEST_USERNAME, TEST_PASSWORD)
            contentType(ContentType.Text.Plain)
            setBody("name=Central Pool&type=POOL")
        }
        assertExactJsonError(
            contentTypeResponse,
            HttpStatusCode.UnsupportedMediaType,
            "non-JSON Content-Type"
        )

        val missingContentTypeResponse = client.post("/api/facilities") {
            basicAuth(TEST_USERNAME, TEST_PASSWORD)
            setBody("{\"name\":\"Central Pool\",\"type\":\"POOL\"}")
        }
        assertExactJsonError(
            missingContentTypeResponse,
            HttpStatusCode.UnsupportedMediaType,
            "missing Content-Type"
        )

        val acceptResponse = client.post("/api/facilities") {
            basicAuth(TEST_USERNAME, TEST_PASSWORD)
            header("Accept", "application/xml")
            contentType(ContentType.Application.Json)
            setBody("{\"name\":\"Central Pool\",\"type\":\"POOL\"}")
        }
        assertExactJsonError(acceptResponse, HttpStatusCode.NotAcceptable, "unsupported Accept")
    }

    @Test
    fun `activate facility rejects an unexpected request body`() = testApplication {
        configureTestApplication()

        val response = client.put("/api/facilities/1/activate") {
            basicAuth(TEST_USERNAME, TEST_PASSWORD)
            contentType(ContentType.Application.Json)
            setBody("[]")
        }

        assertJsonError(response, HttpStatusCode.BadRequest)
    }

    @Test
    fun `all write routes reject malformed JSON request shapes with the exact error contract`() = testApplication {
        configureTestApplication()
        createFacility("Central Pool", "POOL")
        activateFacility(1)
        val routes = listOf(
            WriteRoute("/api/facilities", "{\"name\":\"Central Pool\",\"type\":\"POOL\"}", "name", "type"),
            WriteRoute("/api/bookings", "{\"facilityId\":1,\"customerId\":900,\"startTime\":\"10:00\",\"endTime\":\"11:00\",\"bookingDate\":\"2026-07-28\"}", "startTime", null),
            WriteRoute("/api/equipments", "{\"facilityId\":1,\"name\":\"Thermometer\",\"type\":\"HEATING\"}", "name", "type"),
            WriteRoute("/api/measurements", "{\"equipmentId\":1,\"type\":\"TEMPERATURE\",\"unit\":\"CELSIUS\",\"value\":1.0}", "unit", "type"),
            WriteRoute("/api/incidents", "{\"facilityId\":1,\"equipmentId\":1,\"measurementId\":1,\"type\":\"HIGH_TEMPERATURE\",\"severity\":\"HIGH\",\"measurementType\":\"TEMPERATURE\",\"measurementUnit\":\"CELSIUS\",\"value\":1.0}", "severity", "type")
        )

        routes.forEach { route ->
            listOf(
                "missing ${route.requiredField}" to route.body.replaceFirst(Regex("\\\"${route.requiredField}\\\":(?:\\\"[^\\\"]*\\\"|[^,}]+),?"), ""),
                "null ${route.requiredField}" to route.body.replace("\"${route.requiredField}\":\"${route.requiredValue(route.requiredField)}\"", "\"${route.requiredField}\":null"),
                "wrong scalar type for ${route.requiredField}" to route.body.replace("\"${route.requiredField}\":\"${route.requiredValue(route.requiredField)}\"", "\"${route.requiredField}\":1"),
                "empty ${route.requiredField}" to route.body.replace("\"${route.requiredField}\":\"${route.requiredValue(route.requiredField)}\"", "\"${route.requiredField}\":\"\""),
                "empty request body" to "",
                "array instead of object" to "[]",
                "malformed JSON" to "{",
                "extra field" to route.body.dropLast(1) + ",\"extra\":true}"
            ).forEach { (case, body) ->
                assertExactJsonError(
                    writeRequest(route.path, body),
                    HttpStatusCode.BadRequest,
                    "$case for ${route.path}",
                    route.errorFor(case)
                )
            }

            route.enumCases().forEach { (enumField, expected) ->
                val body = route.body.replace("\"$enumField\":\"${route.requiredValue(enumField)}\"", "\"$enumField\":\"UNKNOWN\"")
                assertExactJsonError(
                    writeRequest(route.path, body),
                    HttpStatusCode.BadRequest,
                    "unknown $enumField enum for ${route.path}",
                    expected
                )
            }
        }
    }

    @Test
    fun `all write routes reject unsupported headers with the exact error contract`() = testApplication {
        configureTestApplication()
        val routes = listOf(
            WriteRoute("/api/facilities", "{\"name\":\"Central Pool\",\"type\":\"POOL\"}", "name", "type"),
            WriteRoute("/api/bookings", "{\"facilityId\":1,\"customerId\":900,\"startTime\":\"10:00\",\"endTime\":\"11:00\",\"bookingDate\":\"2026-07-28\"}", "startTime", null),
            WriteRoute("/api/equipments", "{\"facilityId\":1,\"name\":\"Thermometer\",\"type\":\"HEATING\"}", "name", "type"),
            WriteRoute("/api/measurements", "{\"equipmentId\":1,\"type\":\"TEMPERATURE\",\"unit\":\"CELSIUS\",\"value\":1.0}", "unit", "type"),
            WriteRoute("/api/incidents", "{\"facilityId\":1,\"equipmentId\":1,\"measurementId\":1,\"type\":\"HIGH_TEMPERATURE\",\"severity\":\"HIGH\",\"measurementType\":\"TEMPERATURE\",\"measurementUnit\":\"CELSIUS\",\"value\":1.0}", "severity", "type"),
            WriteRoute("/api/facilities/1/activate", "{}", "", null)
        )

        routes.forEach { route ->
            assertExactJsonError(writeRequest(route.path, "{}", null), HttpStatusCode.UnsupportedMediaType, "missing Content-Type for ${route.path}")
            assertExactJsonError(writeRequest(route.path, "{}", ContentType.Text.Plain), HttpStatusCode.UnsupportedMediaType, "non-JSON Content-Type for ${route.path}")
            assertExactJsonError(
                writeRequest(route.path, route.body, ContentType.Application.Json, "application/xml"),
                HttpStatusCode.NotAcceptable,
                "unsupported Accept for ${route.path}"
            )
        }
    }

    @Test
    fun `activate facility rejects request bodies with the exact error contract`() = testApplication {
        configureTestApplication()

        listOf("{}", "[]", "{").forEach { body ->
            assertExactJsonError(
                writeRequest("/api/facilities/1/activate", body),
                HttpStatusCode.BadRequest,
                "request body $body",
                ErrorResponse(400, "unexpectedRequestBody", "This endpoint does not accept a request body")
            )
        }
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
    fun `booking rejects invalid dates times equal boundaries and unsupported duration`() = testApplication {
        configureTestApplication()
        createFacility("Central Pool", "POOL")
        activateFacility(1)
        assertEquals(HttpStatusCode.Created, createBooking(1, 900, "2026-07-29", "10:00", "11:00").status)
        assertEquals(HttpStatusCode.Created, createBooking(1, 901, "2026-07-30", "10:00", "22:00").status)
        val requests = listOf(
            Triple("2026-02-30", "10:00", "12:00"),
            Triple("2026-07-28", "not-a-time", "12:00"),
            Triple("2026-07-28", "12:00", "12:00"),
            Triple("2026-07-28", "13:00", "12:00"),
            Triple("2026-07-28", "10:00+03:00", "12:00"),
            Triple("2026-07-28", "10:00", "22:01")
        )

        requests.forEach { (date, start, end) ->
            val response = createBooking(1, 900, bookingDate = date, startTime = start, endTime = end)
            assertExactJsonError(
                response,
                HttpStatusCode.BadRequest,
                "booking date/time case: $date $start-$end",
                ErrorResponse(400, "invalidTimeInterval", "The time interval must be between 1 and 12 hours.")
            )
        }
    }

    @Test
    fun `measurement rejects non finite JSON numbers`() = testApplication {
        configureTestApplication()
        val bodies = listOf(
            "{\"equipmentId\":1,\"type\":\"TEMPERATURE\",\"unit\":\"CELSIUS\",\"value\":NaN}",
            "{\"equipmentId\":1,\"type\":\"TEMPERATURE\",\"unit\":\"CELSIUS\",\"value\":Infinity}",
            "{\"equipmentId\":1,\"type\":\"TEMPERATURE\",\"unit\":\"CELSIUS\",\"value\":-Infinity}"
        )

        bodies.forEach { body ->
            val response = client.post("/api/measurements") {
                basicAuth(TEST_USERNAME, TEST_PASSWORD)
                contentType(ContentType.Application.Json)
                setBody(body)
            }
            assertExactJsonError(
                response,
                HttpStatusCode.BadRequest,
                "non-finite measurement: $body",
                ErrorResponse(400, "invalidRequest", "The request body is invalid")
            )
        }
    }

    @Test
    fun `measurement accepts and rejects numeric range boundaries through HTTP`() = testApplication {
        configureTestApplication()
        createFacility("Central Pool", "POOL")
        val equipment = createEquipment(1, "Thermometer", "HEATING")
        val equipmentId = extractLongField(equipment.bodyAsText(), "id")

        listOf(-50.0, 100.0).forEach { value ->
            assertEquals(HttpStatusCode.Created, createMeasurement(equipmentId, "TEMPERATURE", "CELSIUS", value).status)
        }
        listOf(-50.1, 100.1).forEach { value ->
            assertExactJsonError(
                createMeasurement(equipmentId, "TEMPERATURE", "CELSIUS", value),
                HttpStatusCode.BadRequest,
                "out-of-range measurement: $value",
                ErrorResponse(400, "invalidValue", "The measurement value is outside the allowed range")
            )
        }
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

        assertExactJsonError(
            createMeasurement(equipmentId = 0, type = "TEMPERATURE", unit = "CELSIUS", value = 24.5),
            HttpStatusCode.BadRequest,
            "measurement invalid equipment",
            ErrorResponse(400, "invalidEquipmentId", "An incorrect Equipment ID has been specified")
        )
    }

    @Test
    fun `create measurement with missing equipment returns not found`() = testApplication {
        configureTestApplication()

        assertExactJsonError(
            createMeasurement(equipmentId = 999999, type = "TEMPERATURE", unit = "CELSIUS", value = 24.5),
            HttpStatusCode.NotFound,
            "measurement missing equipment",
            ErrorResponse(404, "notFindEquipmentId", "The specified Equipment ID does not exist")
        )
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

        assertExactJsonError(
            createMeasurement(equipmentId, type = "TEMPERATURE", unit = "CELSIUS", value = 200.0),
            HttpStatusCode.BadRequest,
            "measurement invalid value",
            ErrorResponse(400, "invalidValue", "The measurement value is outside the allowed range")
        )
    }

    @Test
    fun `create measurement with unsupported equipment measurement type returns conflict`() = testApplication {
        configureTestApplication()
        createFacility("Central Pool", "POOL")
        val equipment = createEquipment(facilityId = 1, name = "Pool water supply", type = "WATER_SUPPLY")
        val equipmentId = extractLongField(equipment.bodyAsText(), "id")

        assertExactJsonError(
            createMeasurement(equipmentId, type = "CO2", unit = "PPM", value = 450.0),
            HttpStatusCode.Conflict,
            "measurement unsupported type",
            ErrorResponse(409, "invalidMeasurementType", "This measurement type is not supported by the specified equipment")
        )
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

        assertExactJsonError(
            createIncident(
                facilityId = 0,
                equipmentId = 200,
                measurementId = 400,
                type = "SMOKE_DETECTED",
                severity = "CRITICAL",
                measurementType = "SMOKE",
                measurementUnit = "PERCENT",
                value = 80.0
            ),
            HttpStatusCode.BadRequest,
            "incident invalid facility",
            ErrorResponse(400, "invalidFacilityId", "An incorrect Facility ID has been specified")
        )
    }

    @Test
    fun `create incident with missing facility returns not found`() = testApplication {
        configureTestApplication()

        assertExactJsonError(
            createIncident(
                facilityId = 999999,
                equipmentId = 200,
                measurementId = 400,
                type = "SMOKE_DETECTED",
                severity = "CRITICAL",
                measurementType = "SMOKE",
                measurementUnit = "PERCENT",
                value = 80.0
            ),
            HttpStatusCode.NotFound,
            "incident missing facility",
            ErrorResponse(404, "notFindFacilityId", "The specified Facility ID does not exist")
        )
    }

    @Test
    fun `create incident with equipment from another facility returns conflict`() = testApplication {
        configureTestApplication()
        createFacility("Central Pool", "POOL")
        createFacility("Central Gym", "GYM")
        val equipment = createEquipment(facilityId = 2, name = "Gym ventilation", type = "VENTILATION")
        val equipmentId = extractLongField(equipment.bodyAsText(), "id")

        assertExactJsonError(
            createIncident(
                facilityId = 1,
                equipmentId = equipmentId,
                measurementId = 400,
                type = "HIGH_CO2",
                severity = "HIGH",
                measurementType = "CO2",
                measurementUnit = "PPM",
                value = 1200.0
            ),
            HttpStatusCode.Conflict,
            "incident equipment conflict",
            ErrorResponse(409, "equipmentDoesNotBelongToFacility", "The equipment does not belong to the specified facility")
        )
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

    private fun testMeasurement() = Measurement.create(
        MeasurementId(1),
        EquipmentId(1),
        MeasurementReading(MeasurementType.TEMPERATURE, MeasurementUnit.CELSIUS, 20.0),
        Instant.EPOCH
    )

    private data class WriteRoute(
        val path: String,
        val body: String,
        val requiredField: String,
        val enumField: String?
    ) {
        fun requiredValue(field: String): String =
            Regex("\\\"$field\\\":\\\"([^\\\"]*)\\\"").find(body)?.groupValues?.get(1)
                ?: error("Field '$field' is not a string field in $body")

        fun errorFor(case: String): ErrorResponse = when {
            case == "empty request body" -> ErrorResponse(400, "invalidRequest", "The request body is invalid")

            case.startsWith("empty") -> when (path) {
                "/api/facilities" -> ErrorResponse(400, "invalidName", "Facility name must not be blank")
                "/api/bookings" -> ErrorResponse(400, "invalidTimeInterval", "The time interval must be between 1 and 12 hours.")
                "/api/equipments" -> ErrorResponse(400, "invalidName", "An incorrect name has been specified")
                "/api/measurements" -> ErrorResponse(400, "invalidUnit", "An incorrect measurement unit has been specified")
                "/api/incidents" -> ErrorResponse(400, "invalidSeverity", "An incorrect incident severity has been specified")
                else -> error("Unknown route $path")
            }

            case == "unknown enum" -> when (path) {
                "/api/facilities" -> ErrorResponse(400, "invalidType", "The specified type does not exist in the system")
                "/api/equipments" -> ErrorResponse(400, "invalidType", "An incorrect type has been specified")
                "/api/measurements" -> ErrorResponse(400, "invalidType", "An incorrect measurement type has been specified")
                "/api/incidents" -> ErrorResponse(400, "invalidType", "An incorrect incident type has been specified")
                else -> error("Unknown enum route $path")
            }

            else -> ErrorResponse(400, "invalidRequest", "The request body is invalid")
        }

        fun enumCases(): List<Pair<String, ErrorResponse>> = when (path) {
            "/api/facilities" -> listOf(
                "type" to ErrorResponse(400, "invalidType", "The specified type does not exist in the system")
            )

            "/api/equipments" -> listOf(
                "type" to ErrorResponse(400, "invalidType", "An incorrect type has been specified")
            )

            "/api/measurements" -> listOf(
                "type" to ErrorResponse(400, "invalidType", "An incorrect measurement type has been specified"),
                "unit" to ErrorResponse(400, "invalidUnit", "An incorrect measurement unit has been specified")
            )

            "/api/incidents" -> listOf(
                "type" to ErrorResponse(400, "invalidType", "An incorrect incident type has been specified"),
                "severity" to ErrorResponse(400, "invalidSeverity", "An incorrect incident severity has been specified"),
                "measurementType" to ErrorResponse(400, "invalidMeasurementType", "An incorrect measurement type has been specified"),
                "measurementUnit" to ErrorResponse(400, "invalidMeasurementUnit", "An incorrect measurement unit has been specified")
            )

            else -> emptyList()
        }
    }

    private suspend fun ApplicationTestBuilder.writeRequest(
        path: String,
        body: String,
        requestContentType: ContentType? = ContentType.Application.Json,
        accept: String? = null
    ) = when (path) {
        "/api/facilities/1/activate" -> client.put(path) {
            basicAuth(TEST_USERNAME, TEST_PASSWORD)
            requestContentType?.let(::contentType)
            accept?.let { header("Accept", it) }
            setBody(body)
        }

        else -> client.post(path) {
            basicAuth(TEST_USERNAME, TEST_PASSWORD)
            requestContentType?.let(::contentType)
            accept?.let { header("Accept", it) }
            setBody(body)
        }
    }

    private suspend fun assertExactJsonError(
        response: io.ktor.client.statement.HttpResponse,
        status: HttpStatusCode,
        case: String,
        expected: ErrorResponse = ErrorResponse(status.value, errorName(status), errorText(status))
    ) {
        val body = response.bodyAsText()
        assertEquals(status, response.status, "$case: $body")
        assertEquals(ContentType.Application.Json, response.contentType()?.withoutParameters(), "$case: $body")
        assertEquals(
            expected,
            Json.decodeFromString<ErrorResponse>(body),
            "$case: $body"
        )
    }

    private fun errorName(status: HttpStatusCode) = when (status) {
        HttpStatusCode.BadRequest -> "invalidRequest"
        HttpStatusCode.UnsupportedMediaType -> "unsupportedContentType"
        HttpStatusCode.NotAcceptable -> "unsupportedAccept"
        else -> error("Unexpected status $status")
    }

    private fun errorText(status: HttpStatusCode) = when (status) {
        HttpStatusCode.BadRequest -> "The request body is invalid"
        HttpStatusCode.UnsupportedMediaType -> "The Content-Type header is not supported"
        HttpStatusCode.NotAcceptable -> "The Accept header is not supported"
        else -> error("Unexpected status $status")
    }

    private suspend fun assertJsonError(response: io.ktor.client.statement.HttpResponse, status: HttpStatusCode) {
        val body = response.bodyAsText()
        check(status == response.status) { "Expected $status, got ${response.status}: $body" }
        assertEquals(ContentType.Application.Json, response.contentType()?.withoutParameters(), body)
        assertTrue(body.contains("\"code\""), body)
        assertTrue(body.contains("\"name\""), body)
        assertTrue(body.contains("\"text\""), body)
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
