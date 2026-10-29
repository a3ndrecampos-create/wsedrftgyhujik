package com.promoapp.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.promoapp.domain.model.PriceAlert

@Entity(tableName = "price_alerts")
data class AlertEntity(
    @PrimaryKey val dealId: Long,
    val title: String,
    val imageUrl: String,
    val storeName: String,
    val url: String,
    val targetPrice: Double,
    val priceWhenCreated: Double,
    val lastKnownPrice: Double,
    val createdAtMillis: Long,
    val triggeredAtMillis: Long?,
)

fun AlertEntity.toDomain() = PriceAlert(
    dealId = dealId,
    title = title,
    imageUrl = imageUrl,
    storeName = storeName,
    url = url,
    targetPrice = targetPrice,
    priceWhenCreated = priceWhenCreated,
    lastKnownPrice = lastKnownPrice,
    createdAtMillis = createdAtMillis,
    triggeredAtMillis = triggeredAtMillis,
)

fun PriceAlert.toEntity() = AlertEntity(
    dealId = dealId,
    title = title,
    imageUrl = imageUrl,
    storeName = storeName,
    url = url,
    targetPrice = targetPrice,
    priceWhenCreated = priceWhenCreated,
    lastKnownPrice = lastKnownPrice,
    createdAtMillis = createdAtMillis,
    triggeredAtMillis = triggeredAtMillis,
)
