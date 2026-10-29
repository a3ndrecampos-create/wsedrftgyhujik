package com.promoserver

import com.promoserver.config.AppConfig
import com.promoserver.db.AppDatabase
import com.promoserver.db.CouponRepository
import com.promoserver.db.OfferRepository
import com.promoserver.pipeline.CollectionService
import com.promoserver.pipeline.OfferNormalizer
import com.promoserver.scraping.PoliteFetcher
import com.promoserver.scraping.ScrapeEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ScrapePreviewRouteTest {

    private val html = """
        <script type="application/ld+json">
        {"@type":"Product","name":"Hidratante Corporal","sku":"HC1","url":"https://loja.com/p/hc1",
         "offers":{"price":49.9,"priceSpecification":{"priceType":"ListPrice","price":79.9}}}
        </script>
    """.trimIndent()

    private fun ApplicationTestBuilder.setup(robots: String = "", withEngine: Boolean = true) {
        AppDatabase.connect(newTempDb())
        val repo = OfferRepository()
        val engine = if (withEngine) {
            ScrapeEngine(
                PoliteFetcher(
                    mockClient { request ->
                        if (request.url.encodedPath == "/robots.txt") respond(robots, HttpStatusCode.OK) else respond(html, HttpStatusCode.OK)
                    },
                    "PromoAppBot/1.0",
                    minDelayMs = 0,
                    sleep = {},
                ),
            )
        } else {
            null
        }
        val service = CollectionService(emptyList(), OfferNormalizer(10), repo)
        val deps = AppDependencies(AppConfig(adminToken = "segredo"), repo, CouponRepository(), service, scheduler = null, scrapeEngine = engine)
        application { promoModule(deps) }
    }

    private val request = """{"url":"https://loja.com/ofertas"}"""

    @Test
    fun `shows what the robot would find without saving anything`() = testApplication {
        setup()

        val response = client.post("/admin/scrape-preview") {
            header("X-Admin-Token", "segredo")
            contentType(ContentType.Application.Json)
            setBody(request)
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val json = Json.parseToJsonElement(response.bodyAsText()).jsonObject
        assertEquals("jsonld", json["strategy"]!!.jsonPrimitive.content)
        assertEquals("1", json["found"]!!.jsonPrimitive.content)
        assertEquals("1", json["with_discount"]!!.jsonPrimitive.content)
        val sample = json["sample"]!!.jsonArray.first().jsonObject
        assertEquals("Hidratante Corporal", sample["title"]!!.jsonPrimitive.content)
        assertEquals("37", sample["discount_percent"]!!.jsonPrimitive.content)
    }

    @Test
    fun `reports robots txt refusals as forbidden`() = testApplication {
        setup(robots = "User-agent: *\nDisallow: /")

        val response = client.post("/admin/scrape-preview") {
            header("X-Admin-Token", "segredo")
            contentType(ContentType.Application.Json)
            setBody(request)
        }

        assertEquals(HttpStatusCode.Forbidden, response.status)
        assertTrue(response.bodyAsText().contains("robots.txt"))
    }

    @Test
    fun `requires the admin token`() = testApplication {
        setup()

        val response = client.post("/admin/scrape-preview") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }

        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun `is unavailable without an engine`() = testApplication {
        setup(withEngine = false)

        val response = client.post("/admin/scrape-preview") {
            header("X-Admin-Token", "segredo")
            contentType(ContentType.Application.Json)
            setBody(request)
        }

        assertEquals(HttpStatusCode.ServiceUnavailable, response.status)
    }
}
