package com.promoserver

import com.promoserver.model.Offer
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlin.io.path.createTempDirectory

fun newTempDb(): String = createTempDirectory("promo-test").resolve("test.db").toString()

fun offer(
    id: String = "1",
    store: String = "Amazon",
    title: String = "Fone Bluetooth",
    price: Double = 80.0,
    original: Double? = 100.0,
    category: String? = "Áudio",
) = Offer(
    store = store,
    externalId = id,
    title = title,
    imageUrl = "https://img.com/$id.jpg",
    price = price,
    originalPrice = original,
    url = "https://loja.com/$id",
    category = category,
)

fun mockClient(handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData) = HttpClient(MockEngine(handler))

fun MockRequestHandleScope.json(body: String, status: HttpStatusCode = HttpStatusCode.OK) =
    respond(body, status, headersOf(HttpHeaders.ContentType, "application/json"))
