package com.promoserver.collectors

import com.promoserver.config.AppConfig
import com.promoserver.scraping.ScrapeEngine
import io.ktor.client.HttpClient

object CollectorFactory {
    /**
     * Monta os coletores conforme o que está configurado:
     * 1. APIs e feeds oficiais (Awin, Shopee, Amazon, Mercado Livre);
     * 2. leitura das páginas públicas (sites.json) para as lojas que ainda não têm API;
     * 3. modo demo para as lojas restantes, se estiver ligado.
     */
    fun create(config: AppConfig, client: HttpClient, engine: ScrapeEngine? = null): List<OfferCollector> {
        val official = buildList<OfferCollector> {
            config.awinFeeds.forEach { add(AwinFeedCollector(it.store, it.url, client)) }
            config.shopee?.let { add(ShopeeCollector(it, client)) }
            config.amazon?.let { add(AmazonCollector(it, client)) }
            config.mercadoLivre?.let { add(MercadoLivreCollector(it, client)) }
        }
        val officialStores = official.map { it.name }.toSet()
        val scraped = if (engine == null) {
            emptyList()
        } else {
            config.scrapeSites.filter { it.enabled && it.store !in officialStores }.map { WebPageCollector(it, engine) }
        }
        val real = official + scraped
        if (!config.demoMode) return real
        val covered = real.map { it.name }.toSet()
        return real + AppConfig.ALL_STORES.filterNot { it in covered }.map { DemoCollector(it) }
    }
}
