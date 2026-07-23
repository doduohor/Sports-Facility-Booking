package com.doduohor

import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import kotlin.test.*

class ServerTest {

    @Test
    fun `test root endpoint`() = testApplication {
        // loads default configuration
        configure()
        // verify server root returns 200
        assertEquals(HttpStatusCode.OK, client.get("/").status)
        assertEquals(HttpStatusCode.OK, client.get("/health").status)
        }

    @Test
    fun `test invalid facilities`() = testApplication{
        configure()
        assertEquals(HttpStatusCode.BadRequest, client.get("/api/facilities/test/readings?limit=10").status)
        assertEquals(HttpStatusCode.BadRequest, client.get("/api/facilities/10000/readings?limit=test").status)
    }

    @Test
    fun `test valid facilities`() = testApplication{
        configure()
        assertEquals(HttpStatusCode.OK, client.get("/api/facilities/10000/readings?limit=10").status)
    }
}
