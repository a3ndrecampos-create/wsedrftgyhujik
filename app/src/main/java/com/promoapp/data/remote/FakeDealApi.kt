package com.promoapp.data.remote

import kotlinx.coroutines.delay
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.math.sin

/** API falsa para desenvolver a UI sem backend. Gera 5 páginas de ofertas e um histórico de 30 dias. */
class FakeDealApi : DealApi {
    private val stores = listOf("Amazon", "Mercado Livre", "Magalu", "Shopee", "KaBuM!")
    private val products =
        listOf(
            "Fone Bluetooth ANC",
            "Smart TV 55\" 4K",
            "Tênis de corrida",
            "Air Fryer 8L",
            "Monitor Gamer 24\" 180Hz",
            "Notebook 16GB/512GB",
            "Cafeteira expresso",
            "Mochila para notebook",
        )
    private val categories = listOf("Áudio", "TVs", "Moda", "Casa", "Informática", "Informática", "Casa", "Moda")
    private val tagOptions = listOf("Frete Grátis", "Cupom", "Parcelado")
    private val posted = CopyOnWriteArrayList<DealDto>()
    private val postedCategories = ConcurrentHashMap<Long, String>()
    private val authors = listOf("Marcos", "Laura", "Felipe", "Rafael", "Joyce")

    override suspend fun getDeals(
        tab: String,
        page: Int,
        pageSize: Int,
    ): List<DealDto> {
        delay(NETWORK_DELAY_MS)
        if (page > MAX_PAGES) return emptyList()
        val now = System.currentTimeMillis()
        val myPosts = if (page == 1) posted.toList() else emptyList()
        return myPosts + List(pageSize) { i -> buildDeal((page - 1L) * pageSize + i + 1, now) }
    }

    @Suppress("LongParameterList")
    override suspend fun searchDeals(
        query: String?,
        stores: String?,
        category: String?,
        minPrice: Double?,
        maxPrice: Double?,
        sort: String,
        page: Int,
        pageSize: Int,
    ): List<DealDto> {
        delay(NETWORK_DELAY_MS)
        val now = System.currentTimeMillis()
        val storeSet = stores?.split(",")?.toSet().orEmpty()
        val filtered =
            ((1L..CATALOG_SIZE).map { buildDeal(it, now) } + posted).filter { deal ->
                (
                    query.isNullOrBlank() ||
                        deal.title.contains(query, ignoreCase = true) ||
                        deal.storeName.contains(query, ignoreCase = true)
                ) &&
                    (storeSet.isEmpty() || deal.storeName in storeSet) &&
                    (category == null || categoryOf(deal.id) == category) &&
                    (minPrice == null || deal.price >= minPrice) &&
                    (maxPrice == null || deal.price <= maxPrice)
            }
        val sorted =
            when (sort) {
                "lowest_price" -> filtered.sortedBy { it.price }
                "biggest_discount" -> filtered.sortedByDescending { discountOf(it) }
                "newest" -> filtered.sortedByDescending { it.postedAtMillis }
                else -> filtered
            }
        return sorted.drop((page - 1) * pageSize).take(pageSize)
    }

    override suspend fun getFilterOptions(): FilterOptionsDto {
        delay(DETAIL_DELAY_MS)
        return FilterOptionsDto(stores = stores, categories = categories.distinct())
    }

    override suspend fun getDeal(id: Long): DealDto {
        delay(DETAIL_DELAY_MS)
        return posted.firstOrNull { it.id == id } ?: buildDeal(id, System.currentTimeMillis())
    }

    override suspend fun getPriceHistory(id: Long): List<PricePointDto> {
        delay(DETAIL_DELAY_MS)
        if (posted.any { it.id == id }) return emptyList()
        val now = System.currentTimeMillis()
        val deal = buildDeal(id, now)
        val base = deal.originalPrice ?: (deal.price * 1.2)
        return List(HISTORY_DAYS) { day ->
            val isToday = day == HISTORY_DAYS - 1
            val wave = sin(day * 0.6 + id) * 0.08
            PricePointDto(
                timestampMillis = now - (HISTORY_DAYS - 1 - day) * DAY_MS,
                price = if (isToday) deal.price else round2(base * (0.92 + wave)),
            )
        }
    }

    override suspend fun postDeal(request: NewDealRequest): DealDto {
        delay(POST_DELAY_MS)
        val id = POSTED_ID_START + posted.size
        val tags =
            buildList {
                if (request.freeShipping) add("Frete Grátis")
                if (!request.coupon.isNullOrBlank()) add("Cupom ${request.coupon}")
            }
        val deal =
            DealDto(
                id = id,
                title = request.title,
                imageUrl = request.imageUrl ?: "https://picsum.photos/seed/post$id/600/400",
                storeName = request.store,
                price = request.price,
                originalPrice = request.originalPrice,
                authorName = request.author,
                postedAtMillis = System.currentTimeMillis(),
                tags = tags,
                url = request.url,
            )
        posted.add(0, deal)
        request.category?.let { postedCategories[id] = it }
        return deal
    }

    override suspend fun getMyDeals(): List<DealDto> {
        delay(DETAIL_DELAY_MS)
        return posted.toList()
    }

    private fun buildDeal(
        id: Long,
        now: Long,
    ): DealDto {
        val original = 100.0 + (id * 37 % 900)
        // O preço muda a cada hora (até -12%), para simular variação real e testar alertas.
        val drift = 1.0 - ((now / HOUR_MS + id) % 4) * 0.04
        val price = original * (0.4 + (id % 5) * 0.1) * drift
        return DealDto(
            id = id,
            title = "${products[(id % products.size).toInt()]} #$id",
            imageUrl = "https://picsum.photos/seed/promo$id/600/400",
            storeName = stores[(id % stores.size).toInt()],
            price = round2(price),
            originalPrice = if (id % 4 == 0L) null else round2(original),
            authorName = authors[(id % authors.size).toInt()],
            postedAtMillis = now - id * 7 * 60_000,
            temperature = ((id * 13) % 200).toInt(),
            commentCount = (id % 9).toInt(),
            tags = if (id % 3 == 0L) emptyList() else listOf(tagOptions[(id % tagOptions.size).toInt()]),
            url = "https://example.com/oferta/$id",
        )
    }

    private fun categoryOf(id: Long) = postedCategories[id] ?: categories[(id % products.size).toInt()]

    private fun discountOf(deal: DealDto): Double = deal.originalPrice?.takeIf { it > deal.price }?.let { (it - deal.price) / it } ?: 0.0

    private fun round2(value: Double) = Math.round(value * 100) / 100.0

    private companion object {
        const val NETWORK_DELAY_MS = 600L
        const val DETAIL_DELAY_MS = 400L
        const val POST_DELAY_MS = 700L
        const val POSTED_ID_START = 10_000L
        const val MAX_PAGES = 5
        const val CATALOG_SIZE = 100L
        const val HISTORY_DAYS = 30
        const val HOUR_MS = 3_600_000L
        const val DAY_MS = 86_400_000L
    }
}
