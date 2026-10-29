package com.promoserver.db

import com.promoserver.model.CouponDto
import com.promoserver.model.NewCouponRequest
import org.jetbrains.exposed.sql.insertAndGetId
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction

class CouponRepository {

    fun list(): List<CouponDto> = transaction {
        CouponsTable.selectAll().where { CouponsTable.active eq true }.map {
            CouponDto(
                id = it[CouponsTable.id].value,
                code = it[CouponsTable.code],
                store = it[CouponsTable.store],
                description = it[CouponsTable.description],
                discountLabel = it[CouponsTable.discountLabel],
                minPurchase = it[CouponsTable.minPurchase],
                expiresAtMillis = it[CouponsTable.expiresAt],
                url = it[CouponsTable.url],
            )
        }
    }

    fun create(request: NewCouponRequest): Long = transaction {
        CouponsTable.insertAndGetId {
            it[code] = request.code.trim()
            it[store] = request.store.trim()
            it[description] = request.description.trim()
            it[discountLabel] = request.discountLabel.trim()
            it[minPurchase] = request.minPurchase
            it[expiresAt] = request.expiresAtMillis
            it[url] = request.url.trim()
            it[active] = true
        }.value
    }
}
