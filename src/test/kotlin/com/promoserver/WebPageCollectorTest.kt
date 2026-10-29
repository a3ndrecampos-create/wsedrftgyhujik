package com.promoserver

import com.promoserver.collectors.WebPageCollector
import com.promoserver.config.ScrapeSiteConfig
import com.promoserver.pipeline.OfferNormalizer
import com.promoserver.scraping.PoliteFetcher
import com.promoserver.scraping.ScrapeEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class WebPageCollectorTest {

    private val productsHtml = """
        <html><head><script type="application/ld+json">
        {"@type":"ItemList","itemListElement":[
          {"item":{"@type":"Product","name":"Hidratante Corporal","sku":"HC1","url":"/p/hc1","image":"/i/hc1.jpg",
            "offers":{"price":49.9,"priceSpecification":{"priceType":"https://schema.org/ListPrice","price":79.9}}}},
          {"item":{"@type":"Product","name":"Desodorante Colônia","url":"/p/deo","image":"/i/deo.jpg","offers":{"price":99.0}}},
          {"item":{"@type":"Product","name":"Sabonete em barra","sku":"SB1","url":"/p/sb1","offers":{"price":9.9}}}
        ]}
        </script></head><body></body></html>
    """.trimIndent()

    private fun engine(page: String = productsHtml, status: HttpStatusCode = HttpStatusCode.OK) = ScrapeEngine(
        PoliteFetcher(
            client = mockClient { request ->
                if (request.url.encodedPath == "/robots.txt") respond("", HttpStatusCode.NotFound) else respond(page, status)
            },
            userAgent = "PromoAppBot/1.0",
            minDelayMs = 0,
            sleep = {},
        ),
    )

    private fun site(urls: List<String> = listOf("https://loja.com/ofertas"), template: String = "{url}", maxItems: Int = 60) =
        ScrapeSiteConfig(store = "Natura", urls = urls, affiliateUrlTemplate = template, maxItems = maxItems)

    @Test
    fun `turns page products into offers named after the store`() = runTest {
        val offers = WebPageCollector(site(), engine()).collect()

        assertEquals(3, offers.size)
        assertTrue(offers.all { it.store == "Natura" })
        val first = offers.first { it.externalId == "HC1" }
        assertEquals(49.9, first.price)
        assertEquals(79.9, first.originalPrice)
        assertEquals("https://loja.com/p/hc1", first.url)
        assertEquals("https://loja.com/i/hc1.jpg", first.imageUrl)
    }

    @Test
    fun `products without id get a stable id from the url, ignoring the query string`() = runTest {
        val first = WebPageCollector(site(), engine()).collect().first { it.title.startsWith("Desodorante") }
        val again = WebPageCollector(site(), engine()).collect().first { it.title.startsWith("Desodorante") }

        assertEquals(16, first.externalId.length)
        assertEquals(first.externalId, again.externalId)
    }

    @Test
    fun `affiliate template can be added later without code changes`() = runTest {
        val offers = WebPageCollector(site(template = "https://afiliado.com/go?u={url}&tag=abc"), engine()).collect()

        assertEquals("https://afiliado.com/go?u=https://loja.com/p/hc1&tag=abc", offers.first { it.externalId == "HC1" }.url)
    }

    @Test
    fun `limits the number of items per page`() = runTest {
        assertEquals(2, WebPageCollector(site(maxItems = 2), engine()).collect().size)
    }

    @Test
    fun `if any page fails the whole collection fails so nothing expires by mistake`() = runTest {
        val urls = listOf("https://loja.com/ofertas", "https://loja.com/quebrada")
        val client = mockClient { request ->
            when (request.url.encodedPath) {
                "/robots.txt" -> respond("", HttpStatusCode.NotFound)
                "/quebrada" -> respond("erro", HttpStatusCode.InternalServerError)
                else -> respond(productsHtml, HttpStatusCode.OK)
            }
        }
        val engine = ScrapeEngine(PoliteFetcher(client, "PromoAppBot/1.0", minDelayMs = 0, sleep = {}))

        val error = assertFailsWith<IllegalStateException> { WebPageCollector(site(urls), engine).collect() }

        assertTrue(error.message!!.contains("quebrada"))
    }

    @Test
    fun `a blocked site makes the collection fail`() = runTest {
        assertFailsWith<IllegalStateException> {
            WebPageCollector(site(), engine(status = HttpStatusCode.TooManyRequests)).collect()
        }
    }

    @Test
    fun `only real deals survive normalization`() = runTest {
        val offers = WebPageCollector(site(), engine()).collect()
        val normalizer = OfferNormalizer(10)

        val deals = offers.mapNotNull(normalizer::normalize)

        assertEquals(listOf("HC1"), deals.map { it.externalId })
        assertNotNull(deals.single().originalPrice)
    }

    @Test
    fun `page without products reports a helpful warning`() = runTest {
        val result = engine("<html><body>carrega por javascript</body></html>").scrape("https://loja.com/ofertas")

        assertTrue(result.products.isEmpty())
        assertTrue(result.warnings.single().contains("JavaScript"))
    }

    @Test
    fun `css strategy without selectors explains the problem`() = runTest {
        val result = engine().scrape("https://loja.com/ofertas", strategy = "css")

        assertTrue(result.warnings.single().contains("css"))
    }
}
