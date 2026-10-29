package com.promoserver.collectors

import com.promoserver.model.Offer
import java.time.LocalDate

/** Gera ofertas de exemplo para testar o app sem credenciais. O preço varia por dia. */
class DemoCollector(
    override val name: String,
    private val today: () -> LocalDate = { LocalDate.now() },
) : OfferCollector {

    private val products = listOf(
        "Perfume Floral 100ml" to "Perfumaria",
        "Hidratante Corporal 400ml" to "Beleza",
        "Fone Bluetooth ANC" to "Áudio",
        "Air Fryer 8L" to "Casa",
        "Tênis de corrida" to "Moda",
        "Smart TV 55\" 4K" to "TVs",
        "Cafeteira expresso" to "Casa",
        "Mochila para notebook" to "Moda",
    )

    override suspend fun collect(): List<Offer> {
        val day = today().toEpochDay()
        return products.mapIndexed { index, (title, category) ->
            val original = 80.0 + ((index + 1) * 37 + name.length * 11) % 600
            val discount = 0.15 + ((day + index) % 5) * 0.07
            Offer(
                store = name,
                externalId = "demo-$index",
                title = "$title ($name)",
                imageUrl = "https://picsum.photos/seed/${name.hashCode()}$index/600/400",
                price = Math.round(original * (1 - discount) * 100) / 100.0,
                originalPrice = original,
                url = "https://example.com/${name.lowercase().replace(" ", "-")}/$index",
                category = category,
                freeShipping = index % 3 == 0,
            )
        }
    }
}
