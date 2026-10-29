package com.promoserver.db

import com.promoserver.model.NewDealRequest
import com.promoserver.model.Offer
import com.promoserver.model.PricePointDto
import com.promoserver.model.tags
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.insertAndGetId
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import java.util.UUID

private const val TAG_SEPARATOR = "\u001F"
private const val SNAPSHOT_INTERVAL_MS = 20 * 60 * 60 * 1000L
private const val DAY_MS = 86_400_000L
private const val DELETE_CHUNK = 500

data class OfferRow(
    val id: Long,
    val source: String,
    val store: String,
    val title: String,
    val imageUrl: String,
    val price: Double,
    val originalPrice: Double?,
    val url: String,
    val category: String?,
    val tags: List<String>,
    val author: String?,
    val postedAt: Long,
    val active: Boolean,
    val lastSeenAt: Long = 0,
) {
    val discountPercent: Int
        get() = originalPrice?.takeIf { it > price && it > 0 }?.let { (((it - price) / it) * 100).toInt() } ?: 0
}

data class UpsertResult(val inserted: Int, val updated: Int)

data class StoreStat(val store: String, val activeOffers: Int, val lastSeenAt: Long?)

data class OfferCounts(val active: Int, val inactive: Int, val userPosts: Int)

data class SearchQuery(
    val text: String? = null,
    val stores: Set<String> = emptySet(),
    val category: String? = null,
    val minPrice: Double? = null,
    val maxPrice: Double? = null,
    val sort: String = "relevance",
)

/**
 * Acesso ao banco. Para o MVP, ordenação e busca são feitas em memória sobre as ofertas ativas
 * (centenas ou poucos milhares). Se o catálogo crescer, mova isso para SQL com índices.
 */
class OfferRepository {

    fun upsertCollected(offers: List<Offer>, now: Long): UpsertResult = transaction {
        var inserted = 0
        var updated = 0
        offers.forEach { offer ->
            val existing = OffersTable.selectAll()
                .where { (OffersTable.store eq offer.store) and (OffersTable.externalId eq offer.externalId) }
                .firstOrNull()
            if (existing == null) {
                val id = OffersTable.insertAndGetId {
                    it[source] = "collector"
                    it[store] = offer.store
                    it[externalId] = offer.externalId
                    it[title] = offer.title
                    it[imageUrl] = offer.imageUrl
                    it[price] = offer.price
                    it[originalPrice] = offer.originalPrice
                    it[url] = offer.url
                    it[category] = offer.category
                    it[tags] = offer.tags().joinToString(TAG_SEPARATOR)
                    it[author] = null
                    it[postedAt] = now
                    it[lastSeenAt] = now
                    it[active] = true
                }
                snapshot(id.value, offer.price, now)
                inserted++
            } else {
                val id = existing[OffersTable.id].value
                OffersTable.update({ OffersTable.id eq id }) {
                    it[title] = offer.title
                    it[imageUrl] = offer.imageUrl
                    it[price] = offer.price
                    it[originalPrice] = offer.originalPrice
                    it[url] = offer.url
                    it[category] = offer.category
                    it[tags] = offer.tags().joinToString(TAG_SEPARATOR)
                    it[lastSeenAt] = now
                    it[active] = true
                }
                maybeSnapshot(id, offer.price, now)
                updated++
            }
        }
        UpsertResult(inserted, updated)
    }

    /** Desativa as ofertas de uma loja que não apareceram na última coleta (a promoção acabou). */
    fun deactivateUnseen(store: String, runStartedAt: Long): Int = transaction {
        OffersTable.update({
            (OffersTable.store eq store) and
                (OffersTable.source eq "collector") and
                (OffersTable.active eq true) and
                (OffersTable.lastSeenAt less runStartedAt)
        }) { it[active] = false }
    }

    fun createUserPost(request: NewDealRequest, now: Long): OfferRow = transaction {
        val postTags = Offer(
            store = request.store,
            externalId = "",
            title = request.title,
            imageUrl = "",
            price = request.price,
            originalPrice = request.originalPrice,
            url = request.url,
            freeShipping = request.freeShipping,
            couponCode = request.coupon,
        ).tags()
        val id = OffersTable.insertAndGetId {
            it[source] = "user"
            it[store] = request.store.trim()
            it[externalId] = UUID.randomUUID().toString()
            it[title] = request.title.trim()
            it[imageUrl] = request.imageUrl?.takeIf { url -> url.isNotBlank() } ?: ""
            it[price] = request.price
            it[originalPrice] = request.originalPrice
            it[url] = request.url.trim()
            it[category] = request.category
            it[tags] = postTags.joinToString(TAG_SEPARATOR)
            it[author] = request.author.trim()
            it[postedAt] = now
            it[lastSeenAt] = now
            it[active] = true
        }
        OffersTable.selectAll().where { OffersTable.id eq id.value }.map(::toRow).single()
    }

    /** Registra o clique e devolve o link de destino, ou null se a oferta não existe. */
    fun recordClick(id: Long, now: Long): String? = transaction {
        val row = OffersTable.selectAll().where { OffersTable.id eq id }.firstOrNull() ?: return@transaction null
        ClicksTable.insert {
            it[offerId] = id
            it[ts] = now
        }
        row[OffersTable.url]
    }

    fun clicksSince(since: Long): Long = transaction {
        ClicksTable.selectAll().where { ClicksTable.ts greaterEq since }.count()
    }

    fun counts(): OfferCounts = transaction {
        val all = OffersTable.selectAll().map(::toRow)
        OfferCounts(
            active = all.count { it.active },
            inactive = all.count { !it.active },
            userPosts = all.count { it.source == "user" },
        )
    }

    fun storeStats(): List<StoreStat> = transaction {
        OffersTable.selectAll().map(::toRow)
            .groupBy { it.store }
            .map { (store, rows) -> StoreStat(store, rows.count { it.active }, rows.maxOfOrNull { it.lastSeenAt }) }
            .sortedBy { it.store }
    }

    /** Apaga ofertas de coletor que expiraram antes de [cutoff], com histórico de preço e cliques. */
    fun deleteInactiveOlderThan(cutoff: Long): Int = transaction {
        val ids = OffersTable.selectAll()
            .where { (OffersTable.active eq false) and (OffersTable.source eq "collector") and (OffersTable.lastSeenAt less cutoff) }
            .map { it[OffersTable.id].value }
        ids.chunked(DELETE_CHUNK).forEach { chunk ->
            val list = chunk.joinToString(",") // ids numéricos do nosso próprio banco
            exec("DELETE FROM price_history WHERE offer_id IN ($list)")
            exec("DELETE FROM clicks WHERE offer_id IN ($list)")
            exec("DELETE FROM offers WHERE id IN ($list)")
        }
        ids.size
    }

    fun findById(id: Long): OfferRow? = transaction {
        OffersTable.selectAll().where { OffersTable.id eq id }.map(::toRow).firstOrNull()
    }

    fun myPosts(): List<OfferRow> = transaction {
        OffersTable.selectAll().where { OffersTable.source eq "user" }.map(::toRow).sortedByDescending { it.postedAt }
    }

    fun list(tab: String, page: Int, pageSize: Int, now: Long): List<OfferRow> {
        val all = activeOffers()
        val sorted = when (tab) {
            "recent" -> all.sortedByDescending { it.postedAt }
            "trending" -> all.sortedWith(compareByDescending<OfferRow> { it.discountPercent }.thenByDescending { it.postedAt })
            "lowest_price" -> all.sortedBy { it.price }
            else -> all.sortedWith(
                compareByDescending<OfferRow> { (now - it.postedAt) / DAY_MS == 0L }
                    .thenByDescending { it.discountPercent }
                    .thenByDescending { it.postedAt },
            )
        }
        return sorted.page(page, pageSize)
    }

    fun search(query: SearchQuery, page: Int, pageSize: Int): List<OfferRow> {
        val text = query.text?.trim()?.takeIf { it.isNotEmpty() }
        val filtered = activeOffers().filter { row ->
            (text == null || row.title.contains(text, ignoreCase = true) || row.store.contains(text, ignoreCase = true)) &&
                (query.stores.isEmpty() || row.store in query.stores) &&
                (query.category == null || row.category == query.category) &&
                (query.minPrice == null || row.price >= query.minPrice) &&
                (query.maxPrice == null || row.price <= query.maxPrice)
        }
        val sorted = when (query.sort) {
            "lowest_price" -> filtered.sortedBy { it.price }
            "biggest_discount" -> filtered.sortedByDescending { it.discountPercent }
            "newest" -> filtered.sortedByDescending { it.postedAt }
            else -> filtered.sortedWith(compareByDescending<OfferRow> { it.discountPercent }.thenByDescending { it.postedAt })
        }
        return sorted.page(page, pageSize)
    }

    fun priceHistory(id: Long): List<PricePointDto> = transaction {
        PriceHistoryTable.selectAll()
            .where { PriceHistoryTable.offerId eq id }
            .orderBy(PriceHistoryTable.ts to SortOrder.ASC)
            .map { PricePointDto(it[PriceHistoryTable.ts], it[PriceHistoryTable.price]) }
    }

    fun filterOptions(): Pair<List<String>, List<String>> {
        val all = activeOffers()
        return all.map { it.store }.distinct().sorted() to all.mapNotNull { it.category }.distinct().sorted()
    }

    private fun activeOffers(): List<OfferRow> = transaction {
        OffersTable.selectAll().where { OffersTable.active eq true }.map(::toRow)
    }

    private fun List<OfferRow>.page(page: Int, pageSize: Int): List<OfferRow> = drop((page - 1) * pageSize).take(pageSize)

    private fun snapshot(offerId: Long, price: Double, now: Long) {
        PriceHistoryTable.insert {
            it[PriceHistoryTable.offerId] = offerId
            it[ts] = now
            it[PriceHistoryTable.price] = price
        }
    }

    private fun maybeSnapshot(offerId: Long, price: Double, now: Long) {
        val last = PriceHistoryTable.selectAll()
            .where { PriceHistoryTable.offerId eq offerId }
            .orderBy(PriceHistoryTable.ts to SortOrder.DESC)
            .limit(1)
            .firstOrNull()
        val changed = last == null || last[PriceHistoryTable.price] != price
        val stale = last == null || now - last[PriceHistoryTable.ts] >= SNAPSHOT_INTERVAL_MS
        if (changed || stale) snapshot(offerId, price, now)
    }

    private fun toRow(row: ResultRow) = OfferRow(
        id = row[OffersTable.id].value,
        source = row[OffersTable.source],
        store = row[OffersTable.store],
        title = row[OffersTable.title],
        imageUrl = row[OffersTable.imageUrl],
        price = row[OffersTable.price],
        originalPrice = row[OffersTable.originalPrice],
        url = row[OffersTable.url],
        category = row[OffersTable.category],
        tags = row[OffersTable.tags].split(TAG_SEPARATOR).filter { it.isNotEmpty() },
        author = row[OffersTable.author],
        postedAt = row[OffersTable.postedAt],
        active = row[OffersTable.active],
        lastSeenAt = row[OffersTable.lastSeenAt],
    )
}
