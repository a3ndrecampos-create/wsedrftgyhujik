package com.promoserver.pipeline

import com.promoserver.model.Offer

/**
 * Limpa e valida as ofertas dos coletores. Só passa para o app quem é uma oferta de verdade:
 * preço válido, preço original maior que o preço e desconto mínimo.
 */
class OfferNormalizer(private val minDiscountPercent: Int) {

    fun normalize(raw: Offer): Offer? {
        val title = raw.title.replace(Regex("\\s+"), " ").trim().take(MAX_TITLE)
        if (title.isEmpty() || raw.price <= 0 || !raw.price.isFinite()) return null
        if (!raw.url.startsWith("http://") && !raw.url.startsWith("https://")) return null
        val original = raw.originalPrice?.takeIf { it.isFinite() && it > raw.price }
        val offer = raw.copy(
            title = title,
            imageUrl = raw.imageUrl.trim().replace("http://", "https://"),
            url = raw.url.trim(),
            originalPrice = original,
            category = raw.category?.trim()?.take(64)?.takeIf { it.isNotEmpty() },
        )
        return offer.takeIf { it.originalPrice != null && it.discountPercent >= minDiscountPercent }
    }

    private companion object {
        const val MAX_TITLE = 140
    }
}
