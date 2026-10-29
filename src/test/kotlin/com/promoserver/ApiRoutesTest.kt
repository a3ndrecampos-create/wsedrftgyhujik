package com.promoserver

import com.promoserver.collectors.OfferCollector
import com.promoserver.config.AppConfig
import com.promoserver.db.AppDatabase
import com.promoserver.db.CouponRepository
import com.promoserver.db.OfferRepository
import com.promoserver.model.Offer
import com.promoserver.pipeline.CollectionService
import com.promoserver.pipeline.OfferNormalizer
import io.ktor.client.request.get
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
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ApiRoutesTest {

    private class StubCollector : OfferCollector {
        override val name = "Amazon"
        override suspend fun collect(): List<Offer> = listOf(offer("1"), offer("2", title = "Kindle", price = 300.0, original = 500.0))
    }

    private fun ApplicationTestBuilder.setup(config: AppConfig = AppConfig()): OfferRepository {
        AppDatabase.connect(newTempDb())
        val repo = OfferRepository()
        repo.upsertCollected(
            listOf(
                offer("1", title = "Fone Bluetooth", price = 80.0, original = 100.0),
                offer("2", store = "Shopee", title = "Air Fryer", price = 300.0, original = 500.0, category = "Casa"),
            ),
            now = System.currentTimeMillis(),
        )
        val service = CollectionService(listOf(StubCollector()), OfferNormalizer(10), repo)
        application { promoModule(AppDependencies(config, repo, CouponRepository(), service, scheduler = null)) }
        return repo
    }

    private fun String.array(): JsonArray = Json.parseToJsonElement(this).jsonArray
    private fun JsonObject.str(key: String) = this[key]?.jsonPrimitive?.contentOrNull

    @Test
    fun `deals use the snake case contract of the app`() = testApplication {
        setup()

        val response = client.get("/deals?tab=lowest_price")

        assertEquals(HttpStatusCode.OK, response.status)
        val first = response.bodyAsText().array().first().jsonObject
        assertEquals("Fone Bluetooth", first.str("title"))
        assertEquals("Amazon", first.str("store"))
        assertEquals("100.0", first.str("original_price"))
        assertEquals("20", first.str("temperature"))
        assertTrue(first.containsKey("image_url") && first.containsKey("posted_at") && first.containsKey("author"))
    }

    @Test
    fun `pagination parameters are respected`() = testApplication {
        setup()

        val page1 = client.get("/deals?page=1&page_size=1").bodyAsText().array()
        val page3 = client.get("/deals?page=3&page_size=1").bodyAsText().array()

        assertEquals(1, page1.size)
        assertEquals(0, page3.size)
    }

    @Test
    fun `detail, history and 404`() = testApplication {
        val repo = setup()
        val id = repo.search(com.promoserver.db.SearchQuery(text = "Fone"), 1, 1).first().id

        assertEquals(HttpStatusCode.OK, client.get("/deals/$id").status)
        assertEquals(1, client.get("/deals/$id/price-history").bodyAsText().array().size)
        assertEquals(HttpStatusCode.NotFound, client.get("/deals/9999").status)
        assertEquals(HttpStatusCode.NotFound, client.get("/deals/abc").status)
    }

    @Test
    fun `search filters and filters endpoint`() = testApplication {
        setup()

        val found = client.get("/deals/search?q=air&stores=Shopee&min_price=100&sort=lowest_price").bodyAsText().array()
        val options = Json.parseToJsonElement(client.get("/filters").bodyAsText()).jsonObject

        assertEquals(1, found.size)
        assertEquals("Air Fryer", found.first().jsonObject.str("title"))
        assertEquals(listOf("Amazon", "Shopee"), options["stores"]!!.jsonArray.map { it.jsonPrimitive.content })
        assertEquals(listOf("Casa", "Áudio"), options["categories"]!!.jsonArray.map { it.jsonPrimitive.content })
    }

    private val validPost = """
        {"title":"Cadeira gamer ergonômica","url":"https://loja.com/c","store":"Magalu","price":700.0,
         "original_price":1000.0,"free_shipping":true,"author":"Ana"}
    """.trimIndent()

    @Test
    fun `public posting is disabled by default`() = testApplication {
        setup()

        val response = client.post("/deals") {
            contentType(ContentType.Application.Json)
            setBody(validPost)
        }

        assertEquals(HttpStatusCode.Forbidden, response.status)
    }

    @Test
    fun `posting works when enabled, validates and lists my deals`() = testApplication {
        setup(AppConfig(allowPublicPosts = true))

        val created = client.post("/deals") {
            contentType(ContentType.Application.Json)
            setBody(validPost)
        }
        val invalid = client.post("/deals") {
            contentType(ContentType.Application.Json)
            setBody("""{"title":"Oi","url":"x","store":"","price":0,"author":""}""")
        }
        val broken = client.post("/deals") {
            contentType(ContentType.Application.Json)
            setBody("{nao e json")
        }
        val mine = client.get("/deals/mine").bodyAsText().array()

        assertEquals(HttpStatusCode.Created, created.status)
        assertEquals(HttpStatusCode.UnprocessableEntity, invalid.status)
        assertEquals(HttpStatusCode.BadRequest, broken.status)
        assertEquals(1, mine.size)
        assertEquals("Ana", mine.first().jsonObject.str("author"))
    }

    @Test
    fun `admin collect needs the token`() = testApplication {
        setup(AppConfig(adminToken = "segredo"))

        val noHeader = client.post("/admin/collect")
        val wrong = client.post("/admin/collect") { header("X-Admin-Token", "errado") }
        val ok = client.post("/admin/collect?store=Amazon") { header("X-Admin-Token", "segredo") }

        assertEquals(HttpStatusCode.Unauthorized, noHeader.status)
        assertEquals(HttpStatusCode.Unauthorized, wrong.status)
        assertEquals(HttpStatusCode.OK, ok.status)
        assertEquals("Amazon", ok.bodyAsText().array().first().jsonObject.str("collector"))
    }

    @Test
    fun `admin is disabled when no token is configured`() = testApplication {
        setup()

        assertEquals(HttpStatusCode.Forbidden, client.post("/admin/collect").status)
    }

    @Test
    fun `coupons can be created by the admin and listed by the app`() = testApplication {
        setup(AppConfig(adminToken = "segredo"))

        assertEquals("[]", client.get("/coupons").bodyAsText())
        val created = client.post("/admin/coupons") {
            header("X-Admin-Token", "segredo")
            contentType(ContentType.Application.Json)
            setBody("""{"code":"PROMO10","store":"Amazon","description":"10% off","discount_label":"10% OFF","url":"https://loja.com/c"}""")
        }
        val list = client.get("/coupons").bodyAsText().array()

        assertEquals(HttpStatusCode.Created, created.status)
        assertEquals(1, list.size)
        assertEquals("PROMO10", list.first().jsonObject.str("code"))
        assertEquals("10% OFF", list.first().jsonObject.str("discount_label"))
    }

    @Test
    fun `health check`() = testApplication {
        setup()

        assertTrue(client.get("/health").bodyAsText().contains("ok"))
    }
}
