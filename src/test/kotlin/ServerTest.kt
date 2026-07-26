package com.doduohor

import com.doduohor.repository.InMemoryFacilityRepository
import com.doduohor.service.FacilityService
import io.ktor.client.request.get
import io.ktor.client.request.post
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

    private fun ApplicationTestBuilder.configureTestApplication() {
        application {
            configureSerialization()
            configureStatusPages()
            configureRouting(FacilityService(InMemoryFacilityRepository()))
        }
    }
}
