package com.promoserver.collectors

import com.promoserver.config.ScrapeSiteConfig
import com.promoserver.model.Offer
import com.promoserver.scraping.ScrapeEngine
import com.promoserver.scraping.ScrapedProduct
import kotlinx.coroutines.CancellationException

/**
 * Lê as páginas públicas de ofertas de uma loja (sem API de afiliado).
 * Se QUALQUER página falhar, a coleta inteira falha: assim nada é expirado por engano.
 */
class WebPageCollector(
    private val site: ScrapeSiteConfig,
    private val engine: ScrapeEngine,
) : OfferCollector {

    override val name: String = site.store

    override suspend fun collect(): List<Offer> {
        val offers = mutableListOf<Offer>()
        val failures = mutableListOf<String>()
        for (url in site.urls) {
            try {
                offers += engine.scrape(url, site.strategy, site.css).products.take(site.maxItems).map(::toOffer)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                failures += "$url: ${e.message}"
            }
        }
        check(failures.isEmpty()) { "Falha ao ler ${failures.size} página(s) de ${site.store}: ${failures.joinToString("; ")}" }
        return offers.distinctBy { it.externalId }
    }

    private fun toOffer(product: ScrapedProduct) = Offer(
        store = site.store,
        externalId = product.id ?: sha256Hex(product.url.substringBefore('?')).take(16),
        title = product.title,
        imageUrl = product.imageUrl,
        price = product.price,
        originalPrice = product.oldPrice,
        url = site.affiliateUrlTemplate.replace("{url}", product.url),
        category = product.category,
    )
}
