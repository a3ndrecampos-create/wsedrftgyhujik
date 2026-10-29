package com.promoserver

import com.promoserver.collectors.AmazonCollector
import com.promoserver.config.AmazonConfig
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class AmazonCollectorTest {

    private val searchResponse = """
        {"searchResult":{"items":[
          {"asin":"B001","detailPageURL":"https://www.amazon.com.br/dp/B001?tag=seutag-20",
           "images":{"primary":{"large":{"url":"https://m.media-amazon.com/images/I/a.jpg"}}},
           "itemInfo":{"title":{"displayValue":"Echo Dot"}},
           "offersV2":{"listings":[{"price":{"money":{"amount":249.0,"currency":"BRL"},"savingBasis":{"money":{"amount":399.0}}}}]}},
          {"asin":"B002","detailPageURL":"https://www.amazon.com.br/dp/B002?tag=seutag-20",
           "itemInfo":{"title":{"displayValue":"Kindle"}},
           "offersV2":{"listings":[{"price":{"money":{"amount":300.0},"savings":{"money":{"amount":100.0}}}}]}},
          {"asin":"B003","detailPageURL":"https://www.amazon.com.br/dp/B003","itemInfo":{"title":{"displayValue":"Sem oferta"}},"offersV2":null}
        ]}}
    """.trimIndent()

    private val config = AmazonConfig("id", "secret", "seutag-20", listOf("echo", "kindle"))

    @Test
    fun `maps listings to offers using saving basis or savings`() = runTest {
        val offers = AmazonCollector(config, mockClient { json(searchResponse) }, requestDelayMs = 0).parse(searchResponse)

        assertEquals(listOf("B001", "B002"), offers.map { it.externalId })
        assertEquals(249.0, offers[0].price)
        assertEquals(399.0, offers[0].originalPrice)
        assertEquals("https://m.media-amazon.com/images/I/a.jpg", offers[0].imageUrl)
        assertEquals(400.0, offers[1].originalPrice)
        assertEquals("", offers[1].imageUrl)
    }

    @Test
    fun `token is requested once and reused for every search`() = runTest {
        var tokenCalls = 0
        var searchCalls = 0
        var authHeader: String? = null
        val client = mockClient { request ->
            if (request.url.host == "api.amazon.com") {
                tokenCalls++
                json("""{"access_token":"tok-123","expires_in":3600}""")
            } else {
                searchCalls++
                authHeader = request.headers["Authorization"]
                json(searchResponse)
            }
        }

        val offers = AmazonCollector(config, client, requestDelayMs = 0).collect()

        assertEquals(1, tokenCalls)
        assertEquals(2, searchCalls)
        assertEquals("Bearer tok-123", authHeader)
        assertEquals(2, offers.size) // as mesmas duas ofertas das duas buscas, sem repetir
    }

    @Test
    fun `search error is reported`() = runTest {
        val client = mockClient { request ->
            if (request.url.host == "api.amazon.com") json("""{"access_token":"t","expires_in":3600}""") else json("{}", HttpStatusCode.Forbidden)
        }

        assertFailsWith<IllegalStateException> { AmazonCollector(config, client, requestDelayMs = 0).collect() }
    }
}
