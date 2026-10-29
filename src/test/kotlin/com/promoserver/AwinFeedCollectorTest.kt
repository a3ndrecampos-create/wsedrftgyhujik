package com.promoserver

import com.promoserver.collectors.AwinFeedCollector
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class AwinFeedCollectorTest {

    private val csv = """
        aw_product_id,product_name,aw_deep_link,aw_image_url,search_price,product_price_old,category_name,in_stock
        101,"Desodorante Colônia, 100ml",https://www.awin1.com/cread.php?p=101,http://img/101.jpg,79.90,129.90,Perfumaria,1
        102,Hidratante Corporal,https://www.awin1.com/cread.php?p=102,http://img/102.jpg,"49,90",,Beleza,1
        103,Produto sem estoque,https://www.awin1.com/cread.php?p=103,http://img/103.jpg,10.00,20.00,Beleza,0
        104,,https://www.awin1.com/cread.php?p=104,http://img/104.jpg,10.00,20.00,Beleza,1
    """.trimIndent()

    private fun collector(store: String = "Natura") = AwinFeedCollector(store, "https://feed.example/feed.csv", mockClient { json(csv) })

    @Test
    fun `maps feed rows to offers and skips invalid or out of stock rows`() = runTest {
        val offers = collector().collect()

        assertEquals(listOf("101", "102"), offers.map { it.externalId })
        val first = offers.first()
        assertEquals("Natura", first.store)
        assertEquals("Desodorante Colônia, 100ml", first.title)
        assertEquals(79.9, first.price)
        assertEquals(129.9, first.originalPrice)
        assertEquals("Perfumaria", first.category)
        assertEquals("https://www.awin1.com/cread.php?p=101", first.url)
    }

    @Test
    fun `price with comma and empty old price`() = runTest {
        val second = collector().collect()[1]

        assertEquals(49.9, second.price)
        assertEquals(null, second.originalPrice)
    }

    @Test
    fun `store name comes from the configuration`() = runTest {
        assertEquals("O Boticário", collector("O Boticário").collect().first().store)
    }

    @Test
    fun `http error is reported`() = runTest {
        val failing = AwinFeedCollector("Natura", "https://feed.example/x.csv", mockClient { json("", HttpStatusCode.Unauthorized) })

        assertFailsWith<IllegalStateException> { failing.collect() }
    }
}
