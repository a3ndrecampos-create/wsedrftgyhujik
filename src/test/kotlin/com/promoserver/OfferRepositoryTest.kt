package com.promoserver

import com.promoserver.db.AppDatabase
import com.promoserver.db.OfferRepository
import com.promoserver.db.SearchQuery
import com.promoserver.model.NewDealRequest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class OfferRepositoryTest {

    private lateinit var repo: OfferRepository
    private val hour = 3_600_000L
    private val day = 24 * hour

    @BeforeTest
    fun setUp() {
        AppDatabase.connect(newTempDb())
        repo = OfferRepository()
    }

    private fun idOf(externalTitle: String, now: Long = 0): Long =
        repo.search(SearchQuery(text = externalTitle), 1, 10).first().id

    @Test
    fun `first sighting inserts the offer and a price snapshot`() {
        val result = repo.upsertCollected(listOf(offer("1")), now = 1_000)

        assertEquals(1, result.inserted)
        assertEquals(0, result.updated)
        val id = idOf("Fone")
        assertEquals(listOf(80.0), repo.priceHistory(id).map { it.price })
    }

    @Test
    fun `price change updates the offer and adds a snapshot, same price within a day does not`() {
        repo.upsertCollected(listOf(offer("1", price = 80.0)), now = 0)
        val id = idOf("Fone")

        repo.upsertCollected(listOf(offer("1", price = 80.0)), now = 2 * hour)
        assertEquals(1, repo.priceHistory(id).size)

        repo.upsertCollected(listOf(offer("1", price = 70.0)), now = 3 * hour)
        assertEquals(listOf(80.0, 70.0), repo.priceHistory(id).map { it.price })
        assertEquals(70.0, repo.findById(id)!!.price)

        repo.upsertCollected(listOf(offer("1", price = 70.0)), now = 30 * hour)
        assertEquals(3, repo.priceHistory(id).size)
    }

    @Test
    fun `second run updates instead of duplicating`() {
        repo.upsertCollected(listOf(offer("1")), now = 0)

        val second = repo.upsertCollected(listOf(offer("1"), offer("2", title = "Air Fryer")), now = hour)

        assertEquals(1, second.inserted)
        assertEquals(1, second.updated)
        assertEquals(2, repo.list("recent", 1, 10, now = hour).size)
    }

    @Test
    fun `offers that stop appearing are deactivated only for that store`() {
        repo.upsertCollected(listOf(offer("1", store = "Amazon"), offer("2", store = "Amazon", title = "Kindle")), now = 0)
        repo.upsertCollected(listOf(offer("9", store = "Shopee", title = "Capa")), now = 0)

        repo.upsertCollected(listOf(offer("1", store = "Amazon")), now = hour)
        val expired = repo.deactivateUnseen("Amazon", runStartedAt = hour)

        assertEquals(1, expired)
        val titles = repo.list("recent", 1, 10, now = hour).map { it.title }
        assertTrue("Kindle" !in titles)
        assertTrue("Capa" in titles)
        assertTrue(repo.findById(idOfInactive())!!.active.not())
    }

    private fun idOfInactive(): Long = 2L

    @Test
    fun `an offer that comes back is reactivated`() {
        repo.upsertCollected(listOf(offer("1")), now = 0)
        repo.deactivateUnseen("Amazon", runStartedAt = hour)
        assertTrue(repo.list("recent", 1, 10, hour).isEmpty())

        repo.upsertCollected(listOf(offer("1")), now = 2 * hour)

        assertEquals(1, repo.list("recent", 1, 10, 2 * hour).size)
    }

    @Test
    fun `tabs sort differently and paginate`() {
        repo.upsertCollected(
            listOf(
                offer("a", title = "Barato", price = 20.0, original = 25.0),
                offer("b", title = "Grande desconto", price = 50.0, original = 100.0),
                offer("c", title = "Caro", price = 500.0, original = 600.0),
            ),
            now = 0,
        )
        repo.upsertCollected(listOf(offer("d", title = "Novo", price = 90.0, original = 100.0)), now = 5 * day)

        fun titles(tab: String, page: Int = 1, size: Int = 10) = repo.list(tab, page, size, now = 5 * day).map { it.title }

        assertEquals("Novo", titles("recent").first())
        assertEquals("Barato", titles("lowest_price").first())
        assertEquals("Grande desconto", titles("trending").first())
        assertEquals("Novo", titles("for_you").first()) // o que chegou hoje vem primeiro
        assertEquals(2, titles("recent", page = 1, size = 2).size)
        assertEquals(2, titles("recent", page = 2, size = 2).size)
        assertTrue(titles("recent", page = 3, size = 2).isEmpty())
    }

    @Test
    fun `search filters by text, store, category, price and sorts`() {
        repo.upsertCollected(
            listOf(
                offer("1", store = "Amazon", title = "Fone Bluetooth", price = 80.0, category = "Áudio"),
                offer("2", store = "Shopee", title = "Fone com fio", price = 30.0, original = 60.0, category = "Áudio"),
                offer("3", store = "Amazon", title = "Air Fryer", price = 300.0, original = 500.0, category = "Casa"),
            ),
            now = 0,
        )

        fun search(q: SearchQuery) = repo.search(q, 1, 10).map { it.title }

        assertEquals(setOf("Fone Bluetooth", "Fone com fio"), search(SearchQuery(text = "fone")).toSet())
        assertEquals(listOf("Fone Bluetooth", "Air Fryer"), search(SearchQuery(stores = setOf("Amazon"), sort = "lowest_price")))
        assertEquals(listOf("Air Fryer"), search(SearchQuery(category = "Casa")))
        assertEquals(listOf("Fone Bluetooth"), search(SearchQuery(minPrice = 50.0, maxPrice = 100.0)))
        assertEquals("Fone com fio", search(SearchQuery(sort = "lowest_price")).first())
        assertEquals("Fone com fio", search(SearchQuery(sort = "biggest_discount")).first())
    }

    @Test
    fun `user posts are stored with author and listed as mine`() {
        val request = NewDealRequest(
            title = "Cadeira gamer ergonômica",
            url = "https://loja.com/cadeira",
            store = "Magalu",
            price = 700.0,
            originalPrice = 1000.0,
            coupon = "CADEIRA10",
            freeShipping = true,
            author = "Ana",
        )

        val created = repo.createUserPost(request, now = 10)

        assertEquals("Ana", created.author)
        assertEquals(listOf("Frete Grátis", "Cupom CADEIRA10"), created.tags)
        assertEquals(listOf(created.id), repo.myPosts().map { it.id })
        assertNotNull(repo.findById(created.id))
        assertTrue(repo.priceHistory(created.id).isEmpty())
    }

    @Test
    fun `filter options list active stores and categories`() {
        repo.upsertCollected(
            listOf(offer("1", store = "Amazon", category = "Áudio"), offer("2", store = "Shopee", title = "Capa", category = null)),
            now = 0,
        )

        val (stores, categories) = repo.filterOptions()

        assertEquals(listOf("Amazon", "Shopee"), stores)
        assertEquals(listOf("Áudio"), categories)
        assertFalse(categories.isEmpty())
    }
}
