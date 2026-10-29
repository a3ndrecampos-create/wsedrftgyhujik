package com.promoserver

import com.promoserver.collectors.DemoCollector
import com.promoserver.collectors.OfferCollector
import com.promoserver.db.AppDatabase
import com.promoserver.db.OfferRepository
import com.promoserver.model.Offer
import com.promoserver.pipeline.CollectionService
import com.promoserver.pipeline.OfferNormalizer
import kotlinx.coroutines.test.runTest
import java.time.LocalDate
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CollectionServiceTest {

    private lateinit var repo: OfferRepository
    private var clock = 1_000L

    private class FakeCollector(override val name: String, var offers: List<Offer> = emptyList(), var failure: Exception? = null) : OfferCollector {
        override suspend fun collect(): List<Offer> = failure?.let { throw it } ?: offers
    }

    @BeforeTest
    fun setUp() {
        AppDatabase.connect(newTempDb())
        repo = OfferRepository()
    }

    private fun newService(vararg collectors: OfferCollector) =
        CollectionService(collectors.toList(), OfferNormalizer(10), repo) { clock }

    @Test
    fun `stores only real deals and reports the counts`() = runTest {
        val collector = FakeCollector(
            "Amazon",
            listOf(offer("1"), offer("2", price = 99.0, original = 100.0), offer("3", original = null)),
        )

        val report = newService(collector).runAll().single()

        assertEquals(3, report.fetched)
        assertEquals(1, report.accepted)
        assertEquals(1, report.inserted)
        assertNull(report.error)
    }

    @Test
    fun `a failing collector does not stop the others`() = runTest {
        val broken = FakeCollector("Shopee", failure = IllegalStateException("Shopee respondeu 500"))
        val working = FakeCollector("Amazon", listOf(offer("1")))

        val reports = newService(broken, working).runAll()

        assertEquals("Shopee respondeu 500", reports.first { it.collector == "Shopee" }.error)
        assertEquals(1, reports.first { it.collector == "Amazon" }.inserted)
    }

    @Test
    fun `offers missing from the next run expire, but an empty response does not expire anything`() = runTest {
        val collector = FakeCollector("Amazon", listOf(offer("1"), offer("2", title = "Kindle")))
        val service = newService(collector)
        service.runAll()

        clock = 2_000
        collector.offers = listOf(offer("1"))
        assertEquals(1, service.runAll().single().expired)

        clock = 3_000
        collector.offers = emptyList()
        assertEquals(0, service.runAll().single().expired)
        assertEquals(1, repo.list("recent", 1, 10, clock).size)
    }

    @Test
    fun `only runs the requested store`() = runTest {
        val amazon = FakeCollector("Amazon", listOf(offer("1")))
        val shopee = FakeCollector("Shopee", listOf(offer("9", store = "Shopee", title = "Capa")))

        val reports = newService(amazon, shopee).runAll(only = "shopee")

        assertEquals(listOf("Shopee"), reports.map { it.collector })
    }

    @Test
    fun `demo collector produces valid deals that survive normalization`() = runTest {
        val demo = DemoCollector("Natura") { LocalDate.of(2026, 10, 27) }
        val normalizer = OfferNormalizer(10)

        val offers = demo.collect()

        assertEquals(8, offers.size)
        assertTrue(offers.all { normalizer.normalize(it) != null })
        assertNotNull(offers.first().originalPrice)
    }
}
