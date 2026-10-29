package com.promoserver

import com.promoserver.db.AppDatabase
import com.promoserver.db.OfferRepository
import com.promoserver.db.RunRepository
import com.promoserver.db.SearchQuery
import com.promoserver.model.CollectionReport
import com.promoserver.model.NewDealRequest
import com.promoserver.pipeline.RateLimiter
import com.promoserver.pipeline.RetentionService
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RetentionAndRateLimiterTest {

    private lateinit var offers: OfferRepository
    private lateinit var runs: RunRepository
    private val day = 86_400_000L

    @BeforeTest
    fun setUp() {
        AppDatabase.connect(newTempDb())
        offers = OfferRepository()
        runs = RunRepository()
    }

    private fun idOf(title: String) = offers.search(SearchQuery(text = title), 1, 1).first().id

    @Test
    fun `old expired offers are deleted with their history and clicks, active and user posts stay`() = runTest {
        offers.upsertCollected(listOf(offer("old", title = "Velha"), offer("fresh", title = "Fresca")), now = 0)
        offers.upsertCollected(listOf(offer("old2", title = "Antiga ativa")), now = 100 * day)
        val oldId = idOf("Velha")
        offers.recordClick(oldId, now = 1)
        offers.deactivateUnseen("Amazon", runStartedAt = 50 * day)
        offers.upsertCollected(listOf(offer("fresh", title = "Fresca")), now = 99 * day)
        offers.createUserPost(
            NewDealRequest(title = "Post do usuário aqui", url = "https://x.com/a", store = "Loja", price = 10.0, author = "Ana"),
            now = 0,
        )

        val removed = RetentionService(offers, runs, retentionDays = 60) { 100 * day }.cleanup()

        assertEquals(1, removed)
        assertNull(offers.findById(oldId))
        assertTrue(offers.priceHistory(oldId).isEmpty())
        assertEquals(0L, offers.clicksSince(0))
        assertNotNull(offers.findById(idOf("Fresca")))
        assertEquals(1, offers.myPosts().size)
    }

    @Test
    fun `recently expired offers are kept`() = runTest {
        offers.upsertCollected(listOf(offer("1")), now = 10 * day)
        offers.deactivateUnseen("Amazon", runStartedAt = 20 * day)

        assertEquals(0, RetentionService(offers, runs, retentionDays = 60) { 30 * day }.cleanup())
    }

    @Test
    fun `run history keeps the latest run per collector and old runs are trimmed`() = runTest {
        runs.save(CollectionReport("Amazon", fetched = 5, inserted = 5), startedAt = 1 * day, durationMs = 10)
        runs.save(CollectionReport("Amazon", error = "falhou"), startedAt = 2 * day, durationMs = 20)
        runs.save(CollectionReport("Shopee", fetched = 3, accepted = 2), startedAt = 40 * day, durationMs = 30)

        val latest = runs.latestPerCollector()
        assertEquals(listOf("Amazon", "Shopee"), latest.map { it.collector })
        assertEquals("falhou", latest.first().error)

        RetentionService(offers, runs, retentionDays = 60) { 45 * day }.cleanup()

        assertEquals(listOf("Shopee"), runs.recent().map { it.collector })
    }

    @Test
    fun `store stats count active offers per store`() {
        offers.upsertCollected(listOf(offer("1", store = "Amazon"), offer("2", store = "Amazon", title = "B"), offer("3", store = "Shopee", title = "C")), now = 5)
        offers.deactivateUnseen("Amazon", runStartedAt = 6)

        val stats = offers.storeStats().associateBy { it.store }

        assertEquals(0, stats.getValue("Amazon").activeOffers)
        assertEquals(1, stats.getValue("Shopee").activeOffers)
        assertEquals(5L, stats.getValue("Shopee").lastSeenAt)
        assertEquals(1, offers.counts().active)
        assertEquals(2, offers.counts().inactive)
    }

    @Test
    fun `rate limiter allows the quota, then reports seconds to wait, then resets next minute`() {
        var now = 30_000L
        val limiter = RateLimiter(2) { now }

        assertNull(limiter.check("ip"))
        assertNull(limiter.check("ip"))
        assertEquals(30, limiter.check("ip"))
        assertNull(limiter.check("other"))

        now = 61_000L
        assertNull(limiter.check("ip"))
    }
}
