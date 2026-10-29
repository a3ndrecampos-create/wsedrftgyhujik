package com.promoserver

import com.promoserver.collectors.ShopeeCollector
import com.promoserver.collectors.sha256Hex
import com.promoserver.config.ShopeeConfig
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ShopeeCollectorTest {

    private val sample = """
        {"data":{"productOfferV2":{"nodes":[
          {"itemId":"555","productName":"Fone Bluetooth","imageUrl":"https://img/555.jpg","offerLink":"https://s.shopee.com.br/abc","priceMin":"75.00","priceDiscountRate":25,"shopName":"Loja X"},
          {"itemId":"556","productName":"Capa de celular","imageUrl":"https://img/556.jpg","offerLink":"https://s.shopee.com.br/def","priceMin":19.9,"priceDiscountRate":0}
        ],"pageInfo":{"hasNextPage":false}}}}
    """.trimIndent()

    private fun collector(keywords: List<String> = emptyList(), onRequest: (String?) -> Unit = {}) = ShopeeCollector(
        config = ShopeeConfig("123", "sec", keywords, pages = 1),
        client = mockClient { request ->
            onRequest(request.headers["Authorization"])
            json(sample)
        },
        nowSeconds = { 1_700_000_000 },
    )

    @Test
    fun `computes the original price from the discount rate`() = runTest {
        val offers = collector().collect()

        assertEquals(2, offers.size)
        assertEquals(75.0, offers[0].price)
        assertEquals(100.0, offers[0].originalPrice)
        assertEquals("https://s.shopee.com.br/abc", offers[0].url)
        assertEquals(null, offers[1].originalPrice)
    }

    @Test
    fun `signs the request with app id, timestamp, payload and secret`() {
        val c = collector()

        assertEquals(sha256Hex("1231700000000{}sec"), c.signature(1_700_000_000, "{}"))
    }

    @Test
    fun `authorization header carries credential and timestamp`() = runTest {
        var header: String? = null
        collector { header = it }.collect()

        assertTrue(header!!.startsWith("SHA256 Credential=123, Timestamp=1700000000, Signature="))
    }

    @Test
    fun `query includes the escaped keyword only when present`() {
        val c = collector()

        assertTrue(c.buildQuery("", 1).contains("productOfferV2(sortType: 2"))
        assertTrue(c.buildQuery("fone \"bt\"", 2).contains("keyword: \"fone \\\"bt\\\"\""))
    }

    @Test
    fun `graphql errors are reported`() {
        assertFailsWith<IllegalStateException> { collector().parse("""{"errors":[{"message":"invalid signature"}]}""") }
    }

    @Test
    fun `the same item found by two keywords is returned once`() = runTest {
        assertEquals(2, collector(keywords = listOf("fone", "capa")).collect().size)
    }
}
