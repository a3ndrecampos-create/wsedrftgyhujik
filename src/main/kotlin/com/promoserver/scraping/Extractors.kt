package com.promoserver.scraping

import com.promoserver.collectors.asArray
import com.promoserver.collectors.asDouble
import com.promoserver.collectors.asObject
import com.promoserver.collectors.asString
import com.promoserver.collectors.parseBrlText
import com.promoserver.config.CssSelectors
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.net.URI

data class ScrapedProduct(
    val id: String?,
    val title: String,
    val price: Double,
    val oldPrice: Double?,
    val url: String,
    val imageUrl: String,
    val category: String? = null,
)

interface ProductExtractor {
    val name: String
    fun extract(doc: Document, pageUrl: String): List<ScrapedProduct>
}

internal fun resolveUrl(base: String, href: String?): String? {
    val value = href?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    val resolved = runCatching { URI(base).resolve(value).toString() }.getOrNull() ?: return null
    return resolved.takeIf { it.startsWith("http://") || it.startsWith("https://") }
}

private fun moneyOf(element: JsonElement?): Double? {
    val primitive = element as? JsonPrimitive ?: return null
    if (primitive is JsonNull) return null
    return element.asDouble() ?: parseBrlText(primitive.content)
}

/** Lê dados estruturados schema.org (`<script type="application/ld+json">`), que muitas lojas publicam para o Google. */
object JsonLdExtractor : ProductExtractor {
    override val name = "jsonld"

    override fun extract(doc: Document, pageUrl: String): List<ScrapedProduct> {
        val found = mutableListOf<ScrapedProduct>()
        doc.select("script[type=application/ld+json]").forEach { script ->
            val root = runCatching { Json.parseToJsonElement(script.data()) }.getOrNull() ?: return@forEach
            walk(root, pageUrl, found)
        }
        return found
    }

    private fun walk(element: JsonElement, base: String, out: MutableList<ScrapedProduct>) {
        when (element) {
            is JsonArray -> element.forEach { walk(it, base, out) }
            is JsonObject -> {
                if (isProduct(element)) {
                    toProduct(element, base)?.let { out += it }
                } else {
                    element.values.forEach { walk(it, base, out) }
                }
            }
            else -> Unit
        }
    }

    private fun isProduct(obj: JsonObject): Boolean {
        val type = obj["@type"]
        return type.asString().equals("Product", ignoreCase = true) ||
            type.asArray()?.any { it.asString().equals("Product", ignoreCase = true) } == true
    }

    private fun toProduct(obj: JsonObject, base: String): ScrapedProduct? {
        val offer = obj["offers"].let { it.asArray()?.firstNotNullOfOrNull { entry -> entry.asObject() } ?: it.asObject() }
        val price = moneyOf(offer?.get("price")) ?: moneyOf(offer?.get("lowPrice")) ?: return null
        val specs = offer?.get("priceSpecification").let { it.asArray()?.mapNotNull { entry -> entry.asObject() } ?: listOfNotNull(it.asObject()) }
        val listPrice = specs.firstOrNull { it["priceType"].asString()?.contains("ListPrice", ignoreCase = true) == true }
            ?.let { moneyOf(it["price"]) }
        val image = obj["image"].let { it.asString() ?: it.asArray()?.firstOrNull().let { first -> first.asString() ?: first.asObject()?.get("url").asString() } ?: it.asObject()?.get("url").asString() }
        return ScrapedProduct(
            id = obj["sku"].asString() ?: obj["productID"].asString() ?: obj["mpn"].asString(),
            title = obj["name"].asString()?.takeIf { it.isNotBlank() } ?: return null,
            price = price,
            oldPrice = listPrice,
            url = resolveUrl(base, obj["url"].asString() ?: offer?.get("url").asString()) ?: return null,
            imageUrl = resolveUrl(base, image).orEmpty(),
        )
    }
}

/** Procura produtos em JSON embutido na página (por exemplo `__NEXT_DATA__`), por nomes de campos comuns. */
object StateJsonExtractor : ProductExtractor {
    override val name = "state"

    private val titleKeys = listOf("name", "title", "productName", "displayName")
    private val priceKeys = listOf("price", "salePrice", "sellingPrice", "bestPrice", "currentPrice", "finalPrice", "priceValue")
    private val oldPriceKeys = listOf("listPrice", "oldPrice", "originalPrice", "fullPrice", "regularPrice", "priceFrom", "wasPrice")
    private val urlKeys = listOf("url", "link", "permalink", "productUrl", "canonicalUrl", "href")
    private val imageKeys = listOf("image", "imageUrl", "thumbnail", "picture", "imageSrc")
    private val idKeys = listOf("id", "productId", "sku", "itemId")

    override fun extract(doc: Document, pageUrl: String): List<ScrapedProduct> {
        val found = mutableListOf<ScrapedProduct>()
        doc.select("script#__NEXT_DATA__, script[type=application/json]").forEach { script ->
            val data = script.data()
            if (data.length > MAX_SCRIPT_CHARS) return@forEach
            val root = runCatching { Json.parseToJsonElement(data) }.getOrNull() ?: return@forEach
            walk(root, pageUrl, found)
        }
        return found
    }

    private fun walk(element: JsonElement, base: String, out: MutableList<ScrapedProduct>) {
        when (element) {
            is JsonArray -> element.forEach { walk(it, base, out) }
            is JsonObject -> {
                val product = toProduct(element, base)
                if (product != null) out += product else element.values.forEach { walk(it, base, out) }
            }
            else -> Unit
        }
    }

    private fun toProduct(obj: JsonObject, base: String): ScrapedProduct? {
        val title = titleKeys.firstNotNullOfOrNull { obj[it].asString() }?.trim()?.takeIf { it.length >= MIN_TITLE } ?: return null
        val price = priceKeys.firstNotNullOfOrNull { moneyOf(obj[it]) }?.takeIf { it > 0 } ?: return null
        val url = resolveUrl(base, urlKeys.firstNotNullOfOrNull { obj[it].asString() }) ?: return null
        val oldPrice = oldPriceKeys.firstNotNullOfOrNull { moneyOf(obj[it]) }?.takeIf { it > price }
        val image = imageKeys.firstNotNullOfOrNull { obj[it].asString() }
            ?: obj["images"].asArray()?.firstOrNull().let { it.asString() ?: it.asObject()?.get("url").asString() }
        return ScrapedProduct(
            id = idKeys.firstNotNullOfOrNull { obj[it].asString() },
            title = title,
            price = price,
            oldPrice = oldPrice,
            url = url,
            imageUrl = resolveUrl(base, image).orEmpty(),
        )
    }

    private const val MAX_SCRIPT_CHARS = 3_000_000
    private const val MIN_TITLE = 5
}

/** Lê cartões de produto da tela usando seletores CSS configurados por você. */
class CssExtractor(private val selectors: CssSelectors) : ProductExtractor {
    override val name = "css"

    override fun extract(doc: Document, pageUrl: String): List<ScrapedProduct> =
        doc.select(selectors.item).mapNotNull { card ->
            val title = card.selectFirst(selectors.title)?.text()?.trim()?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
            val price = moneyFromElement(card.selectFirst(selectors.price)) ?: return@mapNotNull null
            val link = if (selectors.link == ":self") card.absUrl("href") else card.selectFirst(selectors.link)?.absUrl("href").orEmpty()
            val image = card.selectFirst(selectors.image)?.let { img ->
                listOf("src", "data-src", "data-lazy").map { img.absUrl(it) }.firstOrNull { it.startsWith("http") }
            }.orEmpty()
            val oldPrice = selectors.oldPrice?.let { moneyFromElement(card.selectFirst(it)) }?.takeIf { it > price }
            ScrapedProduct(
                id = null,
                title = title,
                price = price,
                oldPrice = oldPrice,
                url = link.takeIf { it.startsWith("http") } ?: return@mapNotNull null,
                imageUrl = image,
            )
        }

    /** Entende o formato do Mercado Livre (parte inteira e centavos em elementos separados) e texto comum ("R$ 79,90"). */
    private fun moneyFromElement(element: Element?): Double? {
        element ?: return null
        val fraction = if (element.hasClass("andes-money-amount__fraction")) element else element.selectFirst(".andes-money-amount__fraction")
        if (fraction != null) {
            val whole = fraction.text().filter { it.isDigit() }.toLongOrNull() ?: return null
            val cents = fraction.parent()?.selectFirst(".andes-money-amount__cents")?.text()?.filter { it.isDigit() }?.take(2).orEmpty()
            val centsValue = cents.padEnd(2, '0').toLong()
            return (whole * 100 + centsValue) / 100.0
        }
        return parseBrlText(element.text())
    }
}
