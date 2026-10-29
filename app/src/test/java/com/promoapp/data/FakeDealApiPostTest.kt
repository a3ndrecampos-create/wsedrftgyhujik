package com.promoapp.data

import com.promoapp.data.remote.FakeDealApi
import com.promoapp.data.remote.NewDealRequest
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FakeDealApiPostTest {
    private fun request(
        title: String = "Oferta de teste muito boa",
        category: String? = null,
    ) = NewDealRequest(
        title = title,
        url = "https://loja.com/produto",
        store = "Amazon",
        price = 50.0,
        originalPrice = 100.0,
        coupon = "PROMO10",
        category = category,
        freeShipping = true,
        author = "Ana",
    )

    @Test
    fun `published deal appears first in the feed, in my deals and in detail`() =
        runTest {
            val api = FakeDealApi()

            val posted = api.postDeal(request())

            assertTrue(posted.id >= 10_000)
            assertEquals(posted.id, api.getDeals("for_you", page = 1, pageSize = 20).first().id)
            assertEquals(listOf(posted.id), api.getMyDeals().map { it.id })
            assertEquals(posted.id, api.getDeal(posted.id).id)
        }

    @Test
    fun `later pages do not repeat published deals`() =
        runTest {
            val api = FakeDealApi()
            val posted = api.postDeal(request())

            val page2 = api.getDeals("for_you", page = 2, pageSize = 20)

            assertTrue(page2.none { it.id == posted.id })
        }

    @Test
    fun `tags reflect free shipping and coupon`() =
        runTest {
            val posted = FakeDealApi().postDeal(request())

            assertEquals(listOf("Frete Grátis", "Cupom PROMO10"), posted.tags)
        }

    @Test
    fun `published deal has no price history`() =
        runTest {
            val api = FakeDealApi()
            val posted = api.postDeal(request())

            assertTrue(api.getPriceHistory(posted.id).isEmpty())
        }

    @Test
    fun `published deal can be found by search and category`() =
        runTest {
            val api = FakeDealApi()
            val posted = api.postDeal(request(title = "Cadeira gamer ergonômica", category = "Casa"))

            val byText = api.searchDeals("gamer", null, null, null, null, "relevance", page = 1, pageSize = 20)
            val byCategory = api.searchDeals("cadeira", null, "Casa", null, null, "relevance", page = 1, pageSize = 20)

            assertTrue(byText.any { it.id == posted.id })
            assertTrue(byCategory.any { it.id == posted.id })
        }
}
