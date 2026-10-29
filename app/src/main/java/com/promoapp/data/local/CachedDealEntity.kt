package com.promoapp.data.local

import androidx.room.Entity
import androidx.room.Index
import com.promoapp.data.remote.DealDto
import com.promoapp.domain.model.Deal

/** Oferta salva no cache local, separada por aba (`tab`) e ordenada por `position`. */
@Entity(
    tableName = "cached_deals",
    primaryKeys = ["tab", "id"],
    indices = [Index(value = ["tab", "position"])],
)
data class CachedDealEntity(
    val tab: String,
    val id: Long,
    val position: Int,
    val title: String,
    val imageUrl: String,
    val storeName: String,
    val price: Double,
    val originalPrice: Double?,
    val authorName: String,
    val postedAtMillis: Long,
    val temperature: Int,
    val commentCount: Int,
    val tags: String,
    val url: String,
    val cachedAtMillis: Long,
)

private const val TAG_SEPARATOR = "\u001F"

fun DealDto.toEntity(
    tab: String,
    position: Int,
    cachedAtMillis: Long,
) = CachedDealEntity(
    tab = tab,
    id = id,
    position = position,
    title = title,
    imageUrl = imageUrl,
    storeName = storeName,
    price = price,
    originalPrice = originalPrice,
    authorName = authorName,
    postedAtMillis = postedAtMillis,
    temperature = temperature,
    commentCount = commentCount,
    tags = tags.joinToString(TAG_SEPARATOR),
    url = url,
    cachedAtMillis = cachedAtMillis,
)

fun CachedDealEntity.toDomain() =
    Deal(
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
        tags = tags.split(TAG_SEPARATOR).filter { it.isNotEmpty() },
        url = url,
    )
