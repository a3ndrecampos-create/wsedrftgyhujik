package com.promoserver

import com.promoserver.collectors.parseBrlText
import com.promoserver.config.CssSelectors
import com.promoserver.scraping.CssExtractor
import com.promoserver.scraping.JsonLdExtractor
import com.promoserver.scraping.StateJsonExtractor
import org.jsoup.Jsoup
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ExtractorsTest {

    private val page = "https://loja.com/ofertas"

    @Test
    fun `json ld item list with list price, relative urls and a product without price`() {
        val html = """
            <html><head><script type="application/ld+json">
            {"@context":"https://schema.org","@type":"ItemList","itemListElement":[
              {"@type":"ListItem","position":1,"item":{"@type":"Product","name":"Hidratante Corporal","sku":"HC1",
                "image":["/img/hc1.jpg"],"url":"/produto/hc1",
                "offers":{"@type":"Offer","price":"49,90","priceCurrency":"BRL",
                  "priceSpecification":[{"@type":"UnitPriceSpecification","priceType":"https://schema.org/ListPrice","price":"79.90"}]}}},
              {"@type":"ListItem","position":2,"item":{"@type":"Product","name":"Sem preço","url":"/produto/x"}}
            ]}
            </script></head></html>
        """.trimIndent()

        val products = JsonLdExtractor.extract(Jsoup.parse(html, page), page)

        val product = products.single()
        assertEquals("HC1", product.id)
        assertEquals("Hidratante Corporal", product.title)
        assertEquals(49.9, product.price)
        assertEquals(79.9, product.oldPrice)
        assertEquals("https://loja.com/produto/hc1", product.url)
        assertEquals("https://loja.com/img/hc1.jpg", product.imageUrl)
    }

    @Test
    fun `json ld product with type array and offer list takes the url from the offer`() {
        val html = """
            <script type="application/ld+json">
            {"@type":["Product"],"name":"Perfume Floral","offers":[{"price":199.9,"url":"https://loja.com/p/perfume"}]}
            </script>
            <script type="application/ld+json">{ isto não é json </script>
        """.trimIndent()

        val product = JsonLdExtractor.extract(Jsoup.parse(html, page), page).single()

        assertEquals(199.9, product.price)
        assertNull(product.oldPrice)
        assertEquals("https://loja.com/p/perfume", product.url)
    }

    @Test
    fun `state json finds products by common field names and ignores other objects`() {
        val html = """
            <script id="__NEXT_DATA__" type="application/json">
            {"props":{"pageProps":{"products":[
              {"id":"77","name":"Desodorante Colônia","price":59.9,"listPrice":89.9,"link":"/p/77","imageUrl":"https://img.loja.com/77.jpg"},
              {"name":"Menu","url":"/menu"},
              {"title":"Sem link válido","price":10}
            ]}}}
            </script>
        """.trimIndent()

        val product = StateJsonExtractor.extract(Jsoup.parse(html, page), page).single()

        assertEquals("77", product.id)
        assertEquals(59.9, product.price)
        assertEquals(89.9, product.oldPrice)
        assertEquals("https://loja.com/p/77", product.url)
    }

    @Test
    fun `css extractor understands the integer and cents markup`() {
        val html = """
            <div class="poly-card"><a href="/MLB-1-notebook"><img data-src="https://http2.mlstatic.com/a.jpg"></a>
              <h3 class="poly-component__title"><a href="/MLB-1-notebook">Notebook Gamer 16GB</a></h3>
              <s class="andes-money-amount andes-money-amount--previous"><span class="andes-money-amount__fraction">2.999</span><span class="andes-money-amount__cents">90</span></s>
              <div class="poly-price__current"><span class="andes-money-amount"><span class="andes-money-amount__fraction">1.999</span><span class="andes-money-amount__cents">00</span></span></div>
            </div>
            <div class="poly-card"><h3 class="poly-component__title">Card sem preço</h3></div>
        """.trimIndent()
        val selectors = CssSelectors(
            item = ".poly-card",
            title = ".poly-component__title",
            price = ".poly-price__current",
            oldPrice = ".andes-money-amount--previous",
        )

        val product = CssExtractor(selectors).extract(Jsoup.parse(html, "https://www.mercadolivre.com.br/ofertas"), "https://www.mercadolivre.com.br/ofertas").single()

        assertEquals("Notebook Gamer 16GB", product.title)
        assertEquals(1999.0, product.price)
        assertEquals(2999.9, product.oldPrice)
        assertEquals("https://www.mercadolivre.com.br/MLB-1-notebook", product.url)
        assertEquals("https://http2.mlstatic.com/a.jpg", product.imageUrl)
    }

    @Test
    fun `css extractor reads plain price text`() {
        val html = """<ul><li class="p"><a class="t" href="/a">Perfume</a><span class="v">R$ 79,90</span><del class="o">R$ 129,90</del></li></ul>"""
        val selectors = CssSelectors(item = "li.p", title = ".t", price = ".v", oldPrice = ".o")

        val product = CssExtractor(selectors).extract(Jsoup.parse(html, page), page).single()

        assertEquals(79.9, product.price)
        assertEquals(129.9, product.oldPrice)
        assertEquals("https://loja.com/a", product.url)
    }

    @Test
    fun `price text in brazilian formats`() {
        assertEquals(1999.0, parseBrlText("R$ 1.999"))
        assertEquals(79.9, parseBrlText("R$ 79,90"))
        assertEquals(1234.56, parseBrlText("1.234,56"))
        assertEquals(49.9, parseBrlText("49.90"))
        assertEquals(1_234_567.0, parseBrlText("1.234.567"))
        assertNull(parseBrlText(""))
        assertNull(parseBrlText("abc"))
        assertTrue(parseBrlText("R$\u00A0299,00") == 299.0)
    }
}
