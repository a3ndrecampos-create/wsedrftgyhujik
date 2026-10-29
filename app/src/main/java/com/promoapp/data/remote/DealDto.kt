package com.promoapp.data.remote

import com.promoapp.domain.model.Deal
import com.promoapp.domain.model.DealDraft
import com.promoapp.domain.model.FilterOptions
import com.promoapp.domain.model.PricePoint
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class DealDto(
    val id: Long,
    val title: String,
    @SerialName("image_url") val imageUrl: String,
    @SerialName("store") val storeName: String,
    val price: Double,
    @SerialName("original_price") val originalPrice: Double? = null,
    @SerialName("author") val authorName: String,
    @SerialName("posted_at") val postedAtMillis: Long,
    val temperature: Int = 0,
    @SerialName("comments") val commentCount: Int = 0,
    val tags: List<String> = emptyList(),
    val url: String,
)

@Serializable
data class NewDealRequest(
    val title: String,
    val url: String,
    val store: String,
    val price: Double,
    @SerialName("original_price") val originalPrice: Double? = null,
    @SerialName("image_url") val imageUrl: String? = null,
    val coupon: String? = null,
    val category: String? = null,
    @SerialName("free_shipping") val freeShipping: Boolean = false,
    val author: String,
)

fun DealDraft.toRequest() = NewDealRequest(
    title = title.trim(),
    url = url.trim(),
    store = store.trim(),
    price = price ?: 0.0,
    originalPrice = originalPrice,
    imageUrl = imageUrl.trim().ifBlank { null },
    coupon = couponCode.trim().ifBlank { null },
    category = category,
    freeShipping = freeShipping,
    author = authorName.trim(),
)

@Serializable
data class PricePointDto(
    @SerialName("timestamp") val timestampMillis: Long,
    val price: Double,
)

@Serializable
data class FilterOptionsDto(
    val stores: List<String>,
    val categories: List<String>,
)

fun FilterOptionsDto.toDomain() = FilterOptions(stores = stores, categories = categories)

fun DealDto.toDomain() = Deal(
    id = id,
    title = title,
    imageUrl = imageUrl,
    storeName = storeName,
    price = price,
    originalPrice = originalPrice,
    authorName = authorName,
    postedAtMillis = postedAtMillis,
    temperature = temperature,
    commentCount = commentCount,
    tags = tags,
    url = url,
)

fun PricePointDto.toDomain() = PricePoint(timestampMillis = timestampMillis, price = price)
