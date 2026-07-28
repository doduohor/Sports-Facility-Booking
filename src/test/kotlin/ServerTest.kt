package com.doduohor

import com.doduohor.repository.InMemoryBookingRepository
import com.doduohor.repository.InMemoryFacilityRepository
import com.doduohor.service.BookingService
import com.doduohor.service.FacilityService
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

        val response = createBooking(facilityId = 1, customerId = 100)
        val body = response.bodyAsText()

        assertEquals(HttpStatusCode.Conflict, response.status)
        assertTrue(body.contains("invalidStatusFacilityId"))
    }

    @Test
    fun `create booking for active facility returns created booking`() = testApplication {
        configureTestApplication()
        createFacility("Central Pool", "POOL")
        client.put("/api/facilities/1/activate")

        val response = createBooking(facilityId = 1, customerId = 100)
        val body = response.bodyAsText()

        assertEquals(HttpStatusCode.Created, response.status)
        assertTrue(body.contains("reserved"))
        assertTrue(body.contains("\"facilityId\":1"))
        assertTrue(body.contains("\"customerId\":100"))
        assertTrue(body.contains("2026-07-28T07:00:00Z"))
        assertTrue(body.contains("2026-07-28T09:00:00Z"))
    }

    @Test
    fun `create overlapping booking returns conflict`() = testApplication {
        configureTestApplication()
        createFacility("Central Pool", "POOL")
        client.put("/api/facilities/1/activate")
        createBooking(facilityId = 1, customerId = 100, startTime = "10:00", endTime = "12:00")

        val response = createBooking(facilityId = 1, customerId = 101, startTime = "11:00", endTime = "13:00")
        val body = response.bodyAsText()

        assertEquals(HttpStatusCode.Conflict, response.status)
        assertTrue(body.contains("unavailableRangeTimeLimit"))
    }

    @Test
    fun `create adjacent booking returns created booking`() = testApplication {
        configureTestApplication()
        createFacility("Central Pool", "POOL")
        client.put("/api/facilities/1/activate")
        createBooking(facilityId = 1, customerId = 100, startTime = "10:00", endTime = "12:00")

        val response = createBooking(facilityId = 1, customerId = 101, startTime = "12:00", endTime = "13:00")
        val body = response.bodyAsText()

        assertEquals(HttpStatusCode.Created, response.status)
        assertTrue(body.contains("\"customerId\":101"))
        assertTrue(body.contains("2026-07-28T09:00:00Z"))
        assertTrue(body.contains("2026-07-28T10:00:00Z"))
    }

    @Test
    fun `get booking by id returns created booking`() = testApplication {
        configureTestApplication()
        createFacility("Central Pool", "POOL")
        client.put("/api/facilities/1/activate")
        createBooking(facilityId = 1, customerId = 100)

        val response = client.get("/api/bookings/1")
        val body = response.bodyAsText()

        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(body.contains("\"id\":1"))
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
        createBooking(facilityId = 1, customerId = 100)

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
        createBooking(facilityId = 1, customerId = 100)

        val response = client.get("/api/facilities/1/bookings")
        val body = response.bodyAsText()

        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(body.contains("items"))
        assertTrue(body.contains("\"facilityId\":1"))
    }

    @Test
    fun `create booking with missing facility returns not found`() = testApplication {
        configureTestApplication()

        val response = createBooking(facilityId = 99, customerId = 100)
        val body = response.bodyAsText()

        assertEquals(HttpStatusCode.NotFound, response.status)
        assertTrue(body.contains("notFindFacilityId"))
    }

    @Test
    fun `create booking with invalid facility id returns bad request`() = testApplication {
        configureTestApplication()

        val response = createBooking(facilityId = 0, customerId = 100)
        val body = response.bodyAsText()

        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertTrue(body.contains("invalidFacilityId"))
    }

    @Test
    fun `create booking with invalid customer id returns bad request`() = testApplication {
        configureTestApplication()
        createFacility("Central Pool", "POOL")
        client.put("/api/facilities/1/activate")

        val response = createBooking(facilityId = 1, customerId = 99)
        val body = response.bodyAsText()

        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertTrue(body.contains("invalidCustomerId"))
    }

    @Test
    fun `create booking with invalid time interval returns bad request`() = testApplication {
        configureTestApplication()
        createFacility("Central Pool", "POOL")
        client.put("/api/facilities/1/activate")

        val response = createBooking(facilityId = 1, customerId = 100, endTime = "10:00")
        val body = response.bodyAsText()

        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertTrue(body.contains("invalidTimeInterval"))
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

    private fun ApplicationTestBuilder.configureTestApplication() {
        application {
            configureSerialization()
            configureStatusPages()
            val facilityRepository = InMemoryFacilityRepository()
            configureRouting(
                FacilityService(facilityRepository),
                BookingService(InMemoryBookingRepository(), facilityRepository)
            )
        }
    }
}
