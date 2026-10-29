package com.promoserver.collectors

import com.promoserver.model.Offer
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsBytes
import io.ktor.http.isSuccess
import java.io.ByteArrayInputStream
import java.util.zip.GZIPInputStream

/**
 * Lê o feed de produtos da Awin (CSV, normalmente gerado em "Create-a-Feed" no painel de afiliado).
 * Serve para as lojas que estão na Awin, como Natura e O Boticário.
 *
 * Colunas usadas (marque-as ao criar o feed): aw_product_id, product_name, aw_deep_link, aw_image_url,
 * search_price, product_price_old, category_name, in_stock.
 */
class AwinFeedCollector(
    override val name: String,
    private val feedUrl: String,
    private val client: HttpClient,
) : OfferCollector {

    override suspend fun collect(): List<Offer> {
        val response = client.get(feedUrl)
        check(response.status.isSuccess()) { "Feed da Awin ($name) respondeu ${response.status}" }
        return parse(decode(response.bodyAsBytes()))
    }

    fun parse(csv: String): List<Offer> {
        val rows = Csv.parse(csv, Csv.detectDelimiter(csv))
        if (rows.size < 2) return emptyList()
        val header = rows.first().withIndex().associate { (index, column) -> column.trim().lowercase() to index }
        fun List<String>.col(vararg names: String): String? =
            names.firstNotNullOfOrNull { header[it]?.let { index -> getOrNull(index) } }?.trim()?.takeIf { it.isNotEmpty() }

        return rows.drop(1).mapNotNull { row ->
            if (row.col("in_stock") == "0") return@mapNotNull null
            val title = row.col("product_name") ?: return@mapNotNull null
            val url = row.col("aw_deep_link") ?: return@mapNotNull null
            val price = parseMoney(row.col("search_price", "store_price", "product_price")) ?: return@mapNotNull null
            Offer(
                store = name,
                externalId = row.col("aw_product_id", "merchant_product_id") ?: url,
                title = title,
                imageUrl = row.col("aw_image_url", "merchant_image_url").orEmpty(),
                price = price,
                originalPrice = parseMoney(row.col("product_price_old", "rrp_price")),
                url = url,
                category = row.col("category_name", "merchant_category"),
            )
        }
    }

    private fun decode(bytes: ByteArray): String {
        val isGzip = bytes.size > 2 && bytes[0] == 0x1f.toByte() && bytes[1] == 0x8b.toByte()
        val raw = if (isGzip) GZIPInputStream(ByteArrayInputStream(bytes)).use { it.readBytes() } else bytes
        return raw.toString(Charsets.UTF_8).removePrefix("\uFEFF")
    }
}
