package com.promoserver.db

import org.jetbrains.exposed.dao.id.LongIdTable

object OffersTable : LongIdTable("offers") {
    val origin = varchar("source", 16) // "collector" ou "user"
    val store = varchar("store", 64)
    val externalId = varchar("external_id", 128)
    val title = varchar("title", 200)
    val imageUrl = text("image_url")
    val price = double("price")
    val originalPrice = double("original_price").nullable()
    val url = text("url")
    val category = varchar("category", 64).nullable()
    val tags = text("tags")
    val author = varchar("author", 80).nullable()
    val postedAt = long("posted_at")
    val lastSeenAt = long("last_seen_at")
    val active = bool("active").default(true)

    init {
        uniqueIndex(store, externalId)
        index(false, active, postedAt)
    }
}

object PriceHistoryTable : LongIdTable("price_history") {
    val offerId = reference("offer_id", OffersTable)
    val ts = long("ts")
    val price = double("price")

    init {
        index(false, offerId, ts)
    }
}

object CouponsTable : LongIdTable("coupons") {
    val code = varchar("code", 64)
    val store = varchar("store", 64)
    val description = text("description")
    val discountLabel = varchar("discount_label", 64)
    val minPurchase = double("min_purchase").nullable()
    val expiresAt = long("expires_at").nullable()
    val url = text("url")
    val active = bool("active").default(true)
}

object ClicksTable : LongIdTable("clicks") {
    val offerId = reference("offer_id", OffersTable)
    val ts = long("ts")

    init {
        index(false, offerId)
        index(false, ts)
    }
}

object RunsTable : LongIdTable("collection_runs") {
    val collector = varchar("collector", 64)
    val startedAt = long("started_at")
    val durationMs = long("duration_ms")
    val fetched = integer("fetched")
    val accepted = integer("accepted")
    val inserted = integer("inserted")
    val updated = integer("updated")
    val expired = integer("expired")
    val error = text("error").nullable()

    init {
        index(false, collector, startedAt)
    }
}
