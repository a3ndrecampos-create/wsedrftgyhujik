package com.promoserver.collectors

import com.promoserver.config.AppConfig
import com.promoserver.config.MercadoLivreConfig
import com.promoserver.model.Offer
import io.ktor.client.HttpClient
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import io.ktor.http.parameters
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Mercado Livre. Não existe API pública de "ofertas do dia" para afiliados (a API de ofertas é para vendedores).
 * Por isso este coletor usa a API oficial com um token de aplicativo para:
 *  1. consultar uma lista de anúncios que você escolheu (ML_ITEM_IDS) e
 *  2. buscar por termos (ML_SEARCH_TERMS).
 * O link de afiliado vem do modelo ML_AFFILIATE_URL_TEMPLATE, copiado do seu painel do Mercado Livre Afiliados.
 */
class MercadoLivreCollector(
    private val config: MercadoLivreConfig,
    private val client: HttpClient,
    private val apiBase: String = "https://api.mercadolibre.com",
    private val nowMillis: () -> Long = System::currentTimeMillis,
) : OfferCollector {

    override val name: String = AppConfig.STORE_ML

    private var token: String? = null
    private var tokenExpiresAt = 0L

    override suspend fun collect(): List<Offer> {
        val fromItems = config.itemIds.chunked(ITEMS_PER_CALL).flatMap { fetchItems(it) }
        val fromSearch = config.searchTerms.flatMap { search(it) }
        return (fromItems + fromSearch).distinctBy { it.externalId }
    }

    private suspend fun fetchItems(ids: List<String>): List<Offer> {
        val response = client.get("$apiBase/items") {
            header("Authorization", "Bearer ${accessToken()}")
            parameter("ids", ids.joinToString(","))
        }
        val text = response.bodyAsText()
        check(response.status.isSuccess()) { "Mercado Livre respondeu ${response.status}" }
        return parseItems(text)
    }

    private suspend fun search(term: String): List<Offer> {
        val response = client.get("$apiBase/sites/MLB/search") {
            header("Authorization", "Bearer ${accessToken()}")
            parameter("q", term)
            parameter("limit", SEARCH_LIMIT)
        }
        val text = response.bodyAsText()
        check(response.status.isSuccess()) { "Busca do Mercado Livre respondeu ${response.status}" }
        return parseSearch(text)
    }

    private suspend fun accessToken(): String {
        token?.takeIf { nowMillis() < tokenExpiresAt }?.let { return it }
        val response = client.submitForm(
            url = "$apiBase/oauth/token",
            formParameters = parameters {
                append("grant_type", "client_credentials")
                append("client_id", config.clientId)
                append("client_secret", config.clientSecret)
            },
        )
        val text = response.bodyAsText()
        check(response.status.isSuccess()) { "Token do Mercado Livre respondeu ${response.status}" }
        val json = Json.parseToJsonElement(text).jsonObject
        val value = json["access_token"]?.jsonPrimitive?.contentOrNull ?: error("Resposta de token sem access_token")
        val expiresIn = json["expires_in"]?.jsonPrimitive?.doubleOrNull?.toLong() ?: 3600
        token = value
        tokenExpiresAt = nowMillis() + (expiresIn - 60) * 1000
        return value
    }

    /** Resposta do multiget: lista de { code, body }. */
    fun parseItems(body: String): List<Offer> =
        Json.parseToJsonElement(body).jsonArray.mapNotNull { entry ->
            val obj = entry.jsonObject
            val code = obj["code"]?.jsonPrimitive?.doubleOrNull?.toInt()
            if (code != null && code != 200) return@mapNotNull null
            toOffer(obj["body"]?.jsonObject ?: return@mapNotNull null)
        }

    fun parseSearch(body: String): List<Offer> =
        Json.parseToJsonElement(body).jsonObject["results"]?.jsonArray?.mapNotNull { toOffer(it.jsonObject) }.orEmpty()

    private fun toOffer(item: JsonObject): Offer? {
        if (item["status"]?.jsonPrimitive?.contentOrNull?.let { it != "active" } == true) return null
        val id = item["id"]?.jsonPrimitive?.contentOrNull ?: return null
        val permalink = item["permalink"]?.jsonPrimitive?.contentOrNull ?: return null
        val image = (item["secure_thumbnail"] ?: item["thumbnail"]).str()?.replace("http://", "https://").orEmpty()
        return Offer(
            store = name,
            externalId = id,
            title = item["title"].str() ?: return null,
            imageUrl = image,
            price = item["price"]?.jsonPrimitive?.doubleOrNull ?: return null,
            originalPrice = item["original_price"]?.jsonPrimitive?.doubleOrNull,
            url = config.affiliateUrlTemplate.replace("{url}", permalink).replace("{id}", id),
            freeShipping = item["shipping"].asObject()?.get("free_shipping").asBoolean() ?: false,
        )
    }

    private fun JsonElement?.str(): String? = (this as? kotlinx.serialization.json.JsonPrimitive)?.contentOrNull

    private companion object {
        const val ITEMS_PER_CALL = 20
        const val SEARCH_LIMIT = 50
    }
}
