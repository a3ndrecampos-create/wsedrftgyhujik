package com.promoserver.scraping

import com.promoserver.config.CssSelectors
import org.jsoup.Jsoup

data class ScrapeResult(
    val strategy: String,
    val products: List<ScrapedProduct>,
    val warnings: List<String> = emptyList(),
)

/** Baixa uma página e extrai os produtos. Com "auto", usa seus seletores CSS (se houver) e depois os dados estruturados. */
class ScrapeEngine(private val fetcher: PoliteFetcher) {

    suspend fun scrape(url: String, strategy: String = "auto", css: CssSelectors? = null): ScrapeResult {
        val doc = Jsoup.parse(fetcher.fetchHtml(url), url)
        val cssExtractor = css?.let { CssExtractor(it) }
        val extractors: List<ProductExtractor> = when (strategy.lowercase()) {
            "jsonld" -> listOf(JsonLdExtractor)
            "state" -> listOf(StateJsonExtractor)
            "css" -> listOfNotNull(cssExtractor)
            else -> listOfNotNull(cssExtractor, JsonLdExtractor, StateJsonExtractor)
        }
        if (extractors.isEmpty()) {
            return ScrapeResult(strategy, emptyList(), listOf("A estratégia css exige o campo \"css\" com os seletores."))
        }
        for (extractor in extractors) {
            val products = extractor.extract(doc, url).distinctBy { it.url }
            if (products.isNotEmpty()) return ScrapeResult(extractor.name, products)
        }
        return ScrapeResult(
            strategy = extractors.last().name,
            products = emptyList(),
            warnings = listOf(
                "Nenhum produto encontrado. Se a página carrega os produtos por JavaScript, o HTML baixado vem sem eles. " +
                    "Teste outra URL (por exemplo, uma página de categoria) ou ajuste os seletores CSS.",
            ),
        )
    }
}
