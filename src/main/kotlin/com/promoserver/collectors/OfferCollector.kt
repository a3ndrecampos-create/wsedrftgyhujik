package com.promoserver.collectors

import com.promoserver.model.Offer

/** Um coletor busca as ofertas de UMA loja. O [name] é o nome da loja gravado no banco. */
interface OfferCollector {
    val name: String
    suspend fun collect(): List<Offer>
}
