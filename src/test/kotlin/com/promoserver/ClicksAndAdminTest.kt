package com.promoserver

import com.promoserver.collectors.OfferCollector
import com.promoserver.config.AppConfig
import com.promoserver.db.AppDatabase
import com.promoserver.db.CouponRepository
import com.promoserver.db.OfferRepository
import com.promoserver.db.RunRepository
import com.promoserver.model.Offer
import com.promoserver.pipeline.CollectionService
import com.promoserver.pipeline.OfferNormalizer
import com.promoserver.pipeline.RateLimiter
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.statement.bodyAsText
import io.ktor.client.statement.readRawBytes
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ClicksAndAdminTest {

    private class Stub(override val name: String, private val failure: Boolean = false) : OfferCollector {
        override suspend fun collect(): List<Offer> =
            if (failure) error("Shopee respondeu 500") else listOf(offer("c1", store = name), offer("c2", store = name, title = "Kindle"))
    }

    private fun ApplicationTestBuilder.setup(
        config: AppConfig = AppConfig(adminToken = "segredo"),
        limiter: RateLimiter? = null,
    ): OfferRepository {
        AppDatabase.connect(newTempDb())
        val repo = OfferRepository()
        val runs = RunRepository()
        repo.upsertCollected(listOf(offer("1", title = "Fone Bluetooth")), now = System.currentTimeMillis())
        val service = CollectionService(listOf(Stub("Amazon"), Stub("Shopee", failure = true)), OfferNormalizer(10), repo, runs)
        application { promoModule(AppDependencies(config, repo, CouponRepository(), service, scheduler = null, runs = runs, rateLimiter = limiter)) }
        return repo
    }

    private fun obj(text: String) = Json.parseToJsonElement(text).jsonObject

    @Test
    fun `with a public base url the app link goes through the server`() = testApplication {
        val repo = setup(AppConfig(adminToken = "segredo", publicBaseUrl = "https://api.exemplo.com"))
        val id = repo.search(com.promoserver.db.SearchQuery(text = "Fone"), 1, 1).first().id

        val deal = Json.parseToJsonElement(client.get("/deals").bodyAsText()).jsonArray.first().jsonObject

        assertEquals("https://api.exemplo.com/go/$id", deal["url"]!!.jsonPrimitive.content)
    }

    @Test
    fun `without a public base url the original link is returned`() = testApplication {
        setup()

        val deal = Json.parseToJsonElement(client.get("/deals").bodyAsText()).jsonArray.first().jsonObject

        assertEquals("https://loja.com/1", deal["url"]!!.jsonPrimitive.content)
    }

    @Test
    fun `go redirects to the store and counts the click`() = testApplication {
        val repo = setup()
        val id = repo.search(com.promoserver.db.SearchQuery(text = "Fone"), 1, 1).first().id
        val noRedirect = createClient { followRedirects = false }

        val response = noRedirect.get("/go/$id")

        assertEquals(HttpStatusCode.Found, response.status)
        assertEquals("https://loja.com/1", response.headers[HttpHeaders.Location])
        assertEquals(1L, repo.clicksSince(0))
        val status = obj(client.get("/admin/status") { header("X-Admin-Token", "segredo") }.bodyAsText())
        assertEquals("1", status["clicks_24h"]!!.jsonPrimitive.content)
    }

    @Test
    fun `go with an unknown offer is a 404 and counts nothing`() = testApplication {
        val repo = setup()

        assertEquals(HttpStatusCode.NotFound, client.get("/go/9999").status)
        assertEquals(HttpStatusCode.NotFound, client.get("/go/abc").status)
        assertEquals(0L, repo.clicksSince(0))
    }

    @Test
    fun `dashboard page is public but its data is not`() = testApplication {
        setup()

        val page = client.get("/admin")

        assertEquals(HttpStatusCode.OK, page.status)
        assertTrue(page.bodyAsText().contains("PromoApp"))
        assertNotNull(page.headers["Content-Security-Policy"])
        assertEquals("DENY", page.headers["X-Frame-Options"])
        assertEquals(HttpStatusCode.Unauthorized, client.get("/admin/status").status)
    }

    @Test
    fun `status shows stores, totals and the latest run of each collector`() = testApplication {
        setup()
        client.post("/admin/collect") { header("X-Admin-Token", "segredo") }

        val status = obj(client.get("/admin/status") { header("X-Admin-Token", "segredo") }.bodyAsText())

        assertEquals(listOf("Amazon", "Shopee"), status["collectors"]!!.jsonArray.map { it.jsonPrimitive.content })
        // A oferta que já existia não veio na coleta e expirou; ficam as 2 novas.
        assertEquals("2", status["active_offers"]!!.jsonPrimitive.content)
        assertEquals("1", status["inactive_offers"]!!.jsonPrimitive.content)
        val runs = status["last_runs"]!!.jsonArray.map { it.jsonObject }
        val amazon = runs.first { it["collector"]!!.jsonPrimitive.content == "Amazon" }
        assertEquals("2", amazon["inserted"]!!.jsonPrimitive.content)
        assertNull(amazon["error"])
        val shopee = runs.first { it["collector"]!!.jsonPrimitive.content == "Shopee" }
        assertEquals("Shopee respondeu 500", shopee["error"]!!.jsonPrimitive.content)
        val stores = status["stores"]!!.jsonArray.map { it.jsonObject["store"]!!.jsonPrimitive.content }
        assertTrue("Amazon" in stores)
    }

    @Test
    fun `backup needs the token and returns a consistent sqlite copy`() = testApplication {
        setup()

        assertEquals(HttpStatusCode.Unauthorized, client.get("/admin/backup").status)
        val response = client.get("/admin/backup") { header("X-Admin-Token", "segredo") }
        val bytes = response.readRawBytes()

        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(response.headers[HttpHeaders.ContentDisposition]!!.contains("promo-backup.db"))
        assertEquals("SQLite format 3", String(bytes.copyOf(15), Charsets.US_ASCII))
        assertTrue(String(bytes, Charsets.ISO_8859_1).contains("Fone Bluetooth"))
    }

    @Test
    fun `rate limit answers 429 with retry after, but never for health or admin`() = testApplication {
        setup(limiter = RateLimiter(2) { 0L })

        assertEquals(HttpStatusCode.OK, client.get("/deals").status)
        assertEquals(HttpStatusCode.OK, client.get("/deals").status)
        val limited = client.get("/deals")

        assertEquals(HttpStatusCode.TooManyRequests, limited.status)
        assertEquals("60", limited.headers[HttpHeaders.RetryAfter])
        repeat(5) { assertEquals(HttpStatusCode.OK, client.get("/health").status) }
        assertEquals(HttpStatusCode.OK, client.get("/admin").status)
    }

    @Test
    fun `forwarded ip is used only when the proxy is trusted`() = testApplication {
        setup(config = AppConfig(adminToken = "x", trustProxy = true), limiter = RateLimiter(1) { 0L })

        assertEquals(HttpStatusCode.OK, client.get("/deals") { header("X-Forwarded-For", "1.1.1.1, 10.0.0.1") }.status)
        assertEquals(HttpStatusCode.TooManyRequests, client.get("/deals") { header("X-Forwarded-For", "1.1.1.1") }.status)
        assertEquals(HttpStatusCode.OK, client.get("/deals") { header("X-Forwarded-For", "2.2.2.2") }.status)
    }

    @Test
    fun `forwarded ip is ignored without trust, so a spoofed header cannot dodge the limit`() = testApplication {
        setup(limiter = RateLimiter(1) { 0L })

        assertEquals(HttpStatusCode.OK, client.get("/deals") { header("X-Forwarded-For", "1.1.1.1") }.status)
        assertEquals(HttpStatusCode.TooManyRequests, client.get("/deals") { header("X-Forwarded-For", "2.2.2.2") }.status)
    }
}
