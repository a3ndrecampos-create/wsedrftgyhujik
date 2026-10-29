package com.promoserver.collectors

import com.promoserver.config.AmazonConfig
import com.promoserver.config.AppConfig
import com.promoserver.model.Offer
import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.coroutines.delay
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/**
 * Amazon Creators API (substitui a PA-API 5). Exige conta de Associados com vendas qualificadas.
 * Autenticação OAuth 2.0 (client credentials) e busca em /catalog/v1/searchItems.
 */
class AmazonCollector(
    private val config: AmazonConfig,
    private val client: HttpClient,
    private val apiBase: String = "https://creatorsapi.amazon",
    private val nowMillis: () -> Long = System::currentTimeMillis,
    private val requestDelayMs: Long = 1_000,
) : OfferCollector {

    override val name: String = AppConfig.STORE_AMAZON

    private var token: String? = null
    private var tokenExpiresAt = 0L

    override suspend fun collect(): List<Offer> =
        config.keywords.flatMapIndexed { index, keyword ->
            if (index > 0) delay(requestDelayMs)
            search(keyword)
        }.distinctBy { it.externalId }

    private suspend fun search(keyword: String): List<Offer> {
        val body = buildJsonObject {
            put("keywords", keyword)
            put("partnerTag", config.partnerTag)
            put("marketplace", config.marketplace)
            put(
                "resources",
                buildJsonArray {
                    add(kotlinx.serialization.json.JsonPrimitive("images.primary.large"))
                    add(kotlinx.serialization.json.JsonPrimitive("itemInfo.title"))
                    add(kotlinx.serialization.json.JsonPrimitive("offersV2.listings.price"))
                },
            )
        }.toString()
        val response = client.post("$apiBase/catalog/v1/searchItems") {
            contentType(ContentType.Application.Json)
            header("Authorization", "Bearer ${accessToken()}")
            header("x-marketplace", config.marketplace)
            setBody(body)
        }
        val text = response.bodyAsText()
        check(response.status.isSuccess()) { "Amazon respondeu ${response.status}" }
        return parse(text)
    }

    private suspend fun accessToken(): String {
        token?.takeIf { nowMillis() < tokenExpiresAt }?.let { return it }
        val payload = buildJsonObject {
            put("grant_type", "client_credentials")
            put("client_id", config.credentialId)
            put("client_secret", config.credentialSecret)
            put("scope", "creatorsapi::default")
        }.toString()
        val response = client.post(config.tokenUrl) {
            contentType(ContentType.Application.Json)
            setBody(payload)
        }
        val text = response.bodyAsText()
        check(response.status.isSuccess()) { "Token da Amazon respondeu ${response.status}" }
        val json = Json.parseToJsonElement(text).jsonObject
        val value = json["access_token"]?.jsonPrimitive?.contentOrNull ?: error("Resposta de token sem access_token")
        val expiresIn = json["expires_in"]?.jsonPrimitive?.doubleOrNull?.toLong() ?: 3600
        token = value
        tokenExpiresAt = nowMillis() + (expiresIn - 60) * 1000
        return value
    }

    fun parse(body: String): List<Offer> {
        val items = Json.parseToJsonElement(body).asObject()?.get("searchResult").asObject()?.get("items").asArray() ?: return emptyList()
        return items.mapNotNull { element ->
            val item = element.asObject() ?: return@mapNotNull null
            val listing = item["offersV2"].asObject()?.get("listings").asArray()?.firstOrNull().asObject()
            val priceNode = listing?.get("price").asObject()
            val price = priceNode?.get("money").asObject()?.get("amount").asDouble() ?: return@mapNotNull null
            val basis = priceNode?.get("savingBasis").asObject()?.get("money").asObject()?.get("amount").asDouble()
            val savings = priceNode?.get("savings").asObject()?.get("money").asObject()?.get("amount").asDouble()
            val primary = item["images"].asObject()?.get("primary").asObject()
            val image = listOf("large", "medium", "small").firstNotNullOfOrNull { primary?.get(it).asObject()?.get("url").asString() }
            Offer(
                store = name,
                externalId = item["asin"].asString() ?: return@mapNotNull null,
                title = item["itemInfo"].asObject()?.get("title").asObject()?.get("displayValue").asString() ?: return@mapNotNull null,
                imageUrl = image.orEmpty(),
                price = price,
                originalPrice = basis ?: savings?.let { price + it },
                url = item["detailPageURL"].asString() ?: return@mapNotNull null,
            )
        }
    }
}
