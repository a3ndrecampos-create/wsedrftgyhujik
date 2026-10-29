package com.promoserver.collectors

import com.promoserver.config.AppConfig
import com.promoserver.config.ShopeeConfig
import com.promoserver.model.Offer
import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.security.MessageDigest

/**
 * Shopee Afiliados (Open API, GraphQL). Precisa de App ID e Secret aprovados no painel de afiliados.
 *
 * ATENÇÃO: a assinatura (SHA256 de appId + timestamp + corpo + secret) e os campos de `productOfferV2`
 * seguem a documentação pública como eu a conheço. Confira com a documentação da sua conta antes de usar em produção.
 */
class ShopeeCollector(
    private val config: ShopeeConfig,
    private val client: HttpClient,
    private val endpoint: String = "https://open-api.affiliate.shopee.com.br/graphql",
    private val nowSeconds: () -> Long = { System.currentTimeMillis() / 1000 },
) : OfferCollector {

    override val name: String = AppConfig.STORE_SHOPEE

    override suspend fun collect(): List<Offer> {
        val keywords = config.keywords.ifEmpty { listOf("") }
        return keywords.flatMap { keyword ->
            (1..config.pages).flatMap { page -> fetchPage(keyword, page) }
        }.distinctBy { it.externalId }
    }

    private suspend fun fetchPage(keyword: String, page: Int): List<Offer> {
        val payload = buildJsonObject { put("query", buildQuery(keyword, page)) }.toString()
        val timestamp = nowSeconds()
        val response = client.post(endpoint) {
            contentType(ContentType.Application.Json)
            header("Authorization", "SHA256 Credential=${config.appId}, Timestamp=$timestamp, Signature=${signature(timestamp, payload)}")
            setBody(payload)
        }
        val body = response.bodyAsText()
        check(response.status.isSuccess()) { "Shopee respondeu ${response.status}" }
        return parse(body)
    }

    fun signature(timestamp: Long, payload: String): String = sha256Hex("${config.appId}$timestamp$payload${config.appSecret}")

    fun buildQuery(keyword: String, page: Int): String {
        val keywordArg = if (keyword.isBlank()) "" else "keyword: ${Json.encodeToString(String.serializer(), keyword)}, "
        return "{ productOfferV2(${keywordArg}sortType: 2, page: $page, limit: $PAGE_LIMIT) { " +
            "nodes { itemId productName imageUrl offerLink priceMin priceDiscountRate shopName } " +
            "pageInfo { hasNextPage } } }"
    }

    fun parse(body: String): List<Offer> {
        val root = Json.parseToJsonElement(body).jsonObject
        root["errors"]?.takeIf { it is JsonArray && it.isNotEmpty() }?.let { error("Shopee devolveu erro: $it") }
        val nodes = root["data"]?.jsonObject?.get("productOfferV2")?.jsonObject?.get("nodes")?.jsonArray ?: return emptyList()
        return nodes.mapNotNull { element ->
            val node = element.jsonObject
            val price = node.double("priceMin") ?: return@mapNotNull null
            val rate = node.double("priceDiscountRate") ?: 0.0
            val original = if (rate > 0 && rate < 100) Math.round(price / (1 - rate / 100) * 100) / 100.0 else null
            Offer(
                store = name,
                externalId = node.string("itemId") ?: return@mapNotNull null,
                title = node.string("productName") ?: return@mapNotNull null,
                imageUrl = node.string("imageUrl").orEmpty(),
                price = price,
                originalPrice = original,
                url = node.string("offerLink") ?: return@mapNotNull null,
            )
        }
    }

    private fun JsonObject.string(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull
    private fun JsonObject.double(key: String): Double? = this[key]?.jsonPrimitive?.doubleOrNull

    private companion object {
        const val PAGE_LIMIT = 50
    }
}

internal fun sha256Hex(value: String): String =
    MessageDigest.getInstance("SHA-256").digest(value.toByteArray()).joinToString("") { "%02x".format(it) }
