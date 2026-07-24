package com.doduohor

import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ServerTest {

    @Test
    fun `root returns ok`() = testApplication {
        configure()

        val response = client.get("/")

        assertEquals(HttpStatusCode.OK, response.status)
    }

    @Test
    fun `health returns ok`() = testApplication {
        configure()

        val response = client.get("/health")
        val body = response.bodyAsText()

        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(body.contains("UP"))
    }

    @Test
    fun `invalid facility id returns bad request`() = testApplication {
        configure()

        val response = client.get("/api/facilities/test/readings?limit=10")
        val body = response.bodyAsText()

        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertTrue(body.contains("Invalid"))
    }

    @Test
    fun `invalid limit returns bad request`() = testApplication {
        configure()

        val response = client.get("/api/facilities/10000/readings?limit=test")
        val body = response.bodyAsText()

        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertTrue(body.contains("Invalid"))
    }
}