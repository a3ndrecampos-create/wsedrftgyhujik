package com.promoserver

import com.promoserver.collectors.MercadoLivreCollector
import com.promoserver.config.MercadoLivreConfig
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MercadoLivreCollectorTest {

    private val itemsResponse = """
        [{"code":200,"body":{"id":"MLB1","title":"Notebook 16GB","price":1999.9,"original_price":2999.9,
           "permalink":"https://produto.mercadolivre.com.br/MLB-1","secure_thumbnail":"https://http2.mlstatic.com/a.jpg",
           "status":"active","shipping":{"free_shipping":true}}},
         {"code":404,"body":{"message":"not found"}},
         {"code":200,"body":{"id":"MLB3","title":"Pausado","price":10,"permalink":"https://x/3","status":"paused"}}]
    """.trimIndent()

    private val searchResponse = """
        {"results":[{"id":"MLB9","title":"Smart TV","price":1500,"original_price":2000,
          "permalink":"https://produto.mercadolivre.com.br/MLB-9","thumbnail":"http://http2.mlstatic.com/t.jpg","shipping":{"free_shipping":false}}]}
    """.trimIndent()

    private fun config(template: String = "{url}?ref=abc") =
        MercadoLivreConfig("cid", "csecret", listOf("MLB1", "MLB404", "MLB3"), listOf("smart tv"), template)

    @Test
    fun `parses multiget keeping only active successful items`() {
        val collector = MercadoLivreCollector(config(), mockClient { json("[]") })

        val offers = collector.parseItems(itemsResponse)

        assertEquals(listOf("MLB1"), offers.map { it.externalId })
        assertEquals(1999.9, offers[0].price)
        assertEquals(2999.9, offers[0].originalPrice)
        assertEquals(true, offers[0].freeShipping)
        assertEquals("https://produto.mercadolivre.com.br/MLB-1?ref=abc", offers[0].url)
    }

    @Test
    fun `search results upgrade thumbnails to https`() {
        val offers = MercadoLivreCollector(config(), mockClient { json("{}") }).parseSearch(searchResponse)

        assertEquals("https://http2.mlstatic.com/t.jpg", offers.single().imageUrl)
        assertEquals(false, offers.single().freeShipping)
    }

    @Test
    fun `collect uses an app token for items and search`() = runTest {
        val authHeaders = mutableListOf<String?>()
        var tokenCalls = 0
        val client = mockClient { request ->
            when {
                request.url.encodedPath.endsWith("/oauth/token") -> {
                    tokenCalls++
                    json("""{"access_token":"ml-token","expires_in":21600}""")
                }
                request.url.encodedPath.endsWith("/items") -> {
                    authHeaders += request.headers["Authorization"]
                    json(itemsResponse)
                }
                else -> {
                    authHeaders += request.headers["Authorization"]
                    json(searchResponse)
                }
            }
        }

        val offers = MercadoLivreCollector(config(), client).collect()

        assertEquals(listOf("MLB1", "MLB9"), offers.map { it.externalId })
        assertEquals(1, tokenCalls)
        assertTrue(authHeaders.all { it == "Bearer ml-token" })
    }
}
