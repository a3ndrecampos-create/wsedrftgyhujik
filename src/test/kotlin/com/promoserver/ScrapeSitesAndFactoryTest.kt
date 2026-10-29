package com.promoserver

import com.promoserver.collectors.AmazonCollector
import com.promoserver.collectors.CollectorFactory
import com.promoserver.collectors.DemoCollector
import com.promoserver.collectors.WebPageCollector
import com.promoserver.config.AmazonConfig
import com.promoserver.config.AppConfig
import com.promoserver.config.ScrapeSiteConfig
import com.promoserver.config.ScrapeSites
import com.promoserver.scraping.PoliteFetcher
import com.promoserver.scraping.ScrapeEngine
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ScrapeSitesAndFactoryTest {

    private val sample = """
        [{"store":"Mercado Livre","urls":["https://www.mercadolivre.com.br/ofertas"],
          "css":{"item":".poly-card","title":".t","price":".p"},"extra":"ignorado"},
         {"store":"Natura","enabled":false,"urls":["https://x"]},
         {"store":"O Boticário","urls":[]}]
    """.trimIndent()

    private val client = mockClient { json("{}") }
    private val engine = ScrapeEngine(PoliteFetcher(client, "PromoAppBot/1.0"))

    @Test
    fun `parses the sites file with defaults and ignores unknown fields`() {
        val site = ScrapeSites.parse(sample).first()

        assertEquals("Mercado Livre", site.store)
        assertEquals("auto", site.strategy)
        assertEquals("{url}", site.affiliateUrlTemplate)
        assertEquals(60, site.maxItems)
        assertNotNull(site.css)
    }

    @Test
    fun `load keeps only enabled sites with urls and tolerates a missing or broken file`() {
        val dir = createTempDirectory("sites")
        val good = dir.resolve("sites.json").toFile().apply { writeText(sample) }
        val broken = dir.resolve("broken.json").toFile().apply { writeText("{nao e json") }

        assertEquals(listOf("Mercado Livre"), ScrapeSites.load(good.path).map { it.store })
        assertTrue(ScrapeSites.load(broken.path).isEmpty())
        assertTrue(ScrapeSites.load(dir.resolve("nao-existe.json").toString()).isEmpty())
        assertTrue(ScrapeSites.load(null).isEmpty())
    }

    @Test
    fun `pages are read only for stores without an official collector, demo fills the rest`() {
        val config = AppConfig(
            demoMode = true,
            amazon = AmazonConfig("id", "secret", "tag-20", listOf("oferta")),
            scrapeSites = listOf(
                ScrapeSiteConfig("Amazon", listOf("https://www.amazon.com.br/gp/goldbox")),
                ScrapeSiteConfig("Natura", listOf("https://www.natura.com.br/ofertas")),
            ),
        )

        val collectors = CollectorFactory.create(config, client, engine)

        assertTrue(collectors.single { it.name == "Amazon" } is AmazonCollector)
        assertTrue(collectors.single { it.name == "Natura" } is WebPageCollector)
        assertEquals(setOf("Mercado Livre", "Shopee", "O Boticário"), collectors.filterIsInstance<DemoCollector>().map { it.name }.toSet())
    }

    @Test
    fun `without an engine no page collectors are created`() {
        val config = AppConfig(scrapeSites = listOf(ScrapeSiteConfig("Natura", listOf("https://x.com"))))

        assertTrue(CollectorFactory.create(config, client).isEmpty())
    }
}
